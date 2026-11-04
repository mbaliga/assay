package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * `assay audit` end to end against fake scanner binaries (shell scripts that honour the real
 * adapters' argument shapes) in a real local Git repo with a real local bare remote. The scanner
 * *contracts* are exercised against real binaries by scanner-contracts.yml on CI; this harness
 * proves the orchestration: both output trees, completeness semantics, exit codes, push.
 */
fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        body()
        passed++
        println("PASS $name")
    }

    val base = Files.createTempDirectory("assay-audit-test-")
    val tools = Files.createDirectory(base.resolve("tools"))
    val sarifBy = mapOf(
        "gitleaks" to sarif("generic-api-key", "app/src/Keys.kt", 4, "error"),
        "semgrep" to sarif("android.webview-js-enabled", "app/src/Web.kt", 9, "warning"),
        "osv" to sarif("GHSA-test-0001", "gradle/libs.versions.toml", 2, "error"),
    )
    fun installTools(failing: Set<String> = emptySet(), fixtureLeak: Boolean = false) {
        val versions = mapOf("gitleaks" to "gitleaks 8.30.1", "semgrep" to "1.164.0", "osv" to "osv-scanner version 2.3.8")
        val files = mapOf("gitleaks" to "gitleaks", "semgrep" to "semgrep", "osv" to "osv-scanner")
        versions.forEach { (name, version) ->
            val path = tools.resolve(files.getValue(name))
            Files.deleteIfExists(path)
            Files.writeString(
                path,
                fakeTool(
                    version = version,
                    sarif = sarifBy.getValue(name).let { if (fixtureLeak && name == "gitleaks") it.replace("\"properties\":{}", "\"properties\":{\"assay.fixtureScanner\":\"true\"}") else it },
                    exitCode = if (name in failing) 7 else 1,
                ),
            )
            path.toFile().setExecutable(true, true)
        }
    }
    fun writeLock(): Path {
        val lock = base.resolve("tool-lock-${System.nanoTime()}.json")
        Files.writeString(
            lock,
            Json.stringify(
                Json.obj(
                    "schemaVersion" to Json.str("1.0.0"),
                    "tools" to Json.obj(
                        "gitleaks" to pin("8.30.1", tools.resolve("gitleaks")),
                        "semgrep" to pin("1.164.0", tools.resolve("semgrep")),
                        "osv" to pin("2.3.8", tools.resolve("osv-scanner")),
                    ),
                ),
                pretty = true,
            ),
        )
        return lock
    }

    val repo = Files.createDirectory(base.resolve("scanned"))
    Files.writeString(repo.resolve("README.md"), "sample\n")
    git(repo, "init", "-q", "-b", "main")
    git(repo, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "add", "-A")
    git(repo, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "commit", "-q", "-m", "init")
    val commit = git(repo, "rev-parse", "HEAD")
    val semgrepRules = Files.writeString(base.resolve("rules.yml"), "rules: []\n")

    fun config(
        work: String,
        push: Boolean = false,
        remote: String = "origin",
        ai: String = "off",
        sha: String = commit,
    ): AuditConfig = AuditConfig(
        repo = repo,
        commit = sha,
        sourceRepo = "owner/sample",
        gitRemoteUrl = "https://github.com/owner/sample.git",
        outputBranch = "assay/output",
        toolLock = writeLock(),
        toolPaths = mapOf(
            Scanner.GITLEAKS to tools.resolve("gitleaks"),
            Scanner.SEMGREP to tools.resolve("semgrep"),
            Scanner.OSV to tools.resolve("osv-scanner"),
        ),
        scanners = setOf(Scanner.GITLEAKS, Scanner.SEMGREP, Scanner.OSV),
        workDir = base.resolve(work),
        semgrepConfig = semgrepRules,
        ai = ai,
        push = push,
        remoteName = remote,
    )

    test("complete audit writes the ratified tree and the internal bus") {
        installTools()
        val result = AuditRunner().run(config("work-complete"))
        check(result.exitCode == AuditExit.COMPLETE) { "exit ${result.exitCode}" }
        check(result.completeness == AssayCompletenessV1.COMPLETE && result.explanation == null)
        check(result.findingCount == 3) { "findings ${result.findingCount}" }

        val index = Json.parse(Files.readString(result.outputRoot.resolve(".assay/assay-index.v1.json"))).requireObject()
        check(index.string("completeness") == "COMPLETE")
        check(index.string("sourceCommit") == commit)
        IntegrationSchemas.validate(IntegrationSchemas.ASSAY_INDEX, Files.readString(result.outputRoot.resolve(".assay/assay-index.v1.json")))
        val sarifText = Files.readString(result.outputRoot.resolve(".assay/runs/${result.runId}/findings.sarif"))
        val results = Json.parse(sarifText).requireObject().required("runs").requireArray().values.single()
            .requireObject().required("results").requireArray().values.map { it.requireObject() }
        check(results.size == 3)
        results.forEach { r ->
            val props = r.required("properties").requireObject()
            check(props.string("scannerName").isNotBlank() && props.string("ruleId") == r.string("ruleId"))
        }
        check(results.map { it.required("properties").requireObject().string("scannerName") }.toSet() ==
            setOf("Gitleaks", "Semgrep", "OSV-Scanner"))

        val bus = requireNotNull(result.internalBusRoot)
        val state = BusReader(bus).read(commit)
        check(state is BusState.Ready && state.findings.size == 3) { "internal bus: $state" }
        check(Files.readString(bus.resolve("index.json")).contains(result.runId))
    }

    test("a failing scanner yields PARTIAL with an explanation and no internal bus") {
        installTools(failing = setOf("osv"))
        val result = AuditRunner().run(config("work-partial"))
        check(result.exitCode == AuditExit.PARTIAL) { "exit ${result.exitCode}" }
        check(result.completeness == AssayCompletenessV1.PARTIAL)
        check(result.explanation!!.contains("osv")) { result.explanation.orEmpty() }
        check(result.findingCount == 2)
        check(result.internalBusRoot == null)
        val index = Json.parse(Files.readString(result.outputRoot.resolve(".assay/assay-index.v1.json"))).requireObject()
        check(index.string("completeness") == "PARTIAL" && index.string("explanation").isNotBlank())
    }

    test("all scanners failing yields FAILED, exit 3, and no findings") {
        installTools(failing = setOf("gitleaks", "semgrep", "osv"))
        val result = AuditRunner().run(config("work-failed"))
        check(result.exitCode == AuditExit.FAILED && result.completeness == AssayCompletenessV1.FAILED)
        check(result.findingCount == 0 && result.internalBusRoot == null)
        val index = Json.parse(Files.readString(result.outputRoot.resolve(".assay/assay-index.v1.json"))).requireObject()
        check(index.string("completeness") == "FAILED" && index.string("explanation").isNotBlank())
    }

    test("CLI maps outcomes to exit codes 0, 2, 3") {
        fun cli(work: String) = AuditCli.run(
            arrayOf(
                "audit", "--repo", repo.toString(), "--commit", commit, "--remote", "https://github.com/owner/sample.git",
                "--output-branch", "assay/output", "--tool-lock", writeLock().toString(), "--tools", tools.toString(),
                "--semgrep-config", semgrepRules.toString(), "--work-dir", base.resolve(work).toString(),
            ),
        )
        installTools()
        check(cli("cli-0") == 0)
        installTools(failing = setOf("semgrep"))
        check(cli("cli-2") == 2)
        installTools(failing = setOf("gitleaks", "semgrep", "osv"))
        check(cli("cli-3") == 3)
    }

    test("config errors fail closed") {
        installTools()
        check(runCatching { AuditRunner().run(config("work-sha", sha = "0".repeat(40))) }.exceptionOrNull() is AuditConfigException)
        val ai = runCatching { AuditRunner().run(config("work-ai", ai = "asom")) }.exceptionOrNull()
        check(ai is AuditConfigException && ai.message!!.contains("not available"))
    }

    test("fixture-scanner output is rejected by audit") {
        installTools(fixtureLeak = true)
        val failure = runCatching { AuditRunner().run(config("work-fixture")) }.exceptionOrNull()
        check(failure != null && failure.message!!.contains("fixture scanner")) { "got $failure" }
    }

    test("--push publishes the ratified branch and the internal bus to the remote") {
        installTools()
        val bare = base.resolve("remote.git")
        git(base, "init", "-q", "--bare", bare.toString())
        git(repo, "remote", "add", "origin", bare.toString())
        val result = AuditRunner().run(config("work-push", push = true))
        check(result.exitCode == 0)
        val outputCommit = requireNotNull(result.pushedOutputCommit)
        check(git(bare, "rev-parse", "refs/heads/assay/output") == outputCommit)
        val publishedIndex = git(bare, "show", "assay/output:.assay/assay-index.v1.json")
        check(!publishedIndex.contains(AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER)) { "placeholder leaked" }
        check(publishedIndex.contains("\"completeness\"") && publishedIndex.contains(result.runId))
        val internal = requireNotNull(result.pushedInternalCommit)
        check(git(bare, "rev-parse", "refs/heads/assay/audit") == internal)
        check(git(bare, "show", "assay/audit:index.json").contains(result.runId))
    }

    test("write-time schema validation rejects a document Core would reject") {
        val bad = Files.readString(Path.of("fixtures/integrations/invalid/assay-index-partial-missing-explanation.invalid.json"))
        check(runCatching { IntegrationSchemas.validate(IntegrationSchemas.ASSAY_INDEX, bad) }.isFailure)
        val bad2 = Files.readString(Path.of("fixtures/integrations/invalid/proving-tests-passing-without-lastrun.invalid.json"))
        check(runCatching { IntegrationSchemas.validate(IntegrationSchemas.PROVING_TESTS, bad2) }.isFailure)
        IntegrationSchemas.validate(IntegrationSchemas.ASSAY_INDEX, Files.readString(Path.of("fixtures/integrations/valid/assay-index-valid.json")))
        IntegrationSchemas.validate(IntegrationSchemas.PROVING_TESTS, Files.readString(Path.of("fixtures/integrations/valid/proving-tests-valid.json")))
    }

    println("$passed audit assertions passed")
}

private fun sarif(rule: String, file: String, line: Int, level: String): String =
    """{"version":"2.1.0","runs":[{"tool":{"driver":{"name":"fake","rules":[]}},"results":[{"ruleId":"$rule","level":"$level","message":{"text":"fixture finding $rule"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"$file"},"region":{"startLine":$line,"snippet":{"text":"val placeholder = 1"}}}}],"properties":{}}]}]}"""

private fun fakeTool(version: String, sarif: String, exitCode: Int): String = """#!/bin/sh
if [ "${'$'}1" = "--version" ]; then printf '%s\n' '$version'; exit 0; fi
out=""
while [ ${'$'}# -gt 0 ]; do
  case "${'$'}1" in
    --report-path|--output|--output-file) out="${'$'}2"; shift;;
  esac
  shift
done
if [ $exitCode -eq 7 ]; then exit 7; fi
cat > "${'$'}out" <<'SARIF_EOF'
$sarif
SARIF_EOF
exit $exitCode
"""

private fun pin(version: String, path: Path): JsonValue.Obj = Json.obj(
    "version" to Json.str(version),
    "sha256" to Json.str(Fingerprints.sha256(Files.readAllBytes(path))),
)

private fun git(dir: Path, vararg args: String): String {
    val process = ProcessBuilder(listOf("git", "-C", dir.toString()) + args).redirectErrorStream(true).start()
    val output = process.inputStream.use { it.readAllBytes() }.toString(Charsets.UTF_8).trim()
    check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ${args.toList()} failed: $output" }
    return output
}
