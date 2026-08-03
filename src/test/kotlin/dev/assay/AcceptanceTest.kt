package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        body(); passed++; println("PASS $name")
    }

    test("strict deterministic JSON") {
        check(Json.stringify(Json.parse("""{"b":2,"a":1}""")) == """{"a":1,"b":2}""")
        check(runCatching { Json.parse("""{"a":1,"a":2}""") }.isFailure)
    }

    test("stable identity and redaction") {
        val first = Finding(Scanner.GITLEAKS, "Hardcoded Secret", "m", Location("src/A.kt", 4), "token=[REDACTED]")
        val second = Finding(Scanner.GITLEAKS, "hardcoded secret", "m", Location("./src\\A.kt", 4), "token=[REDACTED]")
        check(first.fingerprint == second.fingerprint)
        check(Fingerprints.provingId(first).matches(ContractValidator.PROVING_ID))
        val safe = Redaction.sanitize("api_key=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY ghp_abcdefghijklmnopqrstuvwxyz123456")
        check(!Redaction.containsSecret(safe))
    }

    test("scanner command contracts") {
        val root = Files.createTempDirectory("assay-command")
        val config = Files.writeString(root.resolve("rules.yml"), "rules: []")
        val output = root.resolve("out.sarif")
        val gitleaks = GitleaksAdapter.command(ScannerCommandContext(root.resolve("gitleaks"), root, output, config))
        check("--redact=100" in gitleaks.arguments)
        val semgrep = SemgrepAdapter.command(ScannerCommandContext(root.resolve("semgrep"), root, output, config))
        check(listOf("--metrics", "off").isSubsequenceOf(semgrep.arguments))
        check(semgrep.environment["SEMGREP_SEND_METRICS"] == "off")
        val osv = OsvAdapter.command(ScannerCommandContext(root.resolve("osv"), root, output, offline = true))
        check("--offline" in osv.arguments)
        check(OsvAdapter.versionCommand(root.resolve("osv"), root).arguments == listOf("version"))
    }

    test("SARIF normalization and severity floors") {
        val root = Files.createTempDirectory("assay-sarif")
        val file = Files.writeString(root.resolve("build.gradle.kts"), "dependencies {}")
        val gitleaks = Sarif.decode(Scanner.GITLEAKS, sarif("secret", file, "error", mapOf("commit" to "a".repeat(40))), root).single()
        check(gitleaks.severity == Severity.SEV1)
        val osv = Sarif.decode(Scanner.OSV, sarif("CVE-1", file, "warning", mapOf("cisaKev" to "true")), root).single()
        check(osv.severity == Severity.SEV1)
        val roundTrip = Sarif.decodeCanonical(Sarif.encode(listOf(gitleaks, osv)))
        check(roundTrip.map { it.fingerprint } == Sarif.merge(listOf(gitleaks, osv)).map { it.fingerprint })
    }

    test("MobSF JSON normalization") {
        val report = """{"manifest_analysis":{"exported_activity":{"severity":"high","description":"Exported activity without permission","file":"/tmp/x/AndroidManifest.xml","line":12}}}"""
        val finding = MobSfAdapter.normalize(report, Files.createTempDirectory("mobsf")).single()
        check(finding.location.file == "mobsf/AndroidManifest.xml")
        check(finding.severity == Severity.SEV2)
    }

    test("tool digest and reported version are enforced") {
        val root = Files.createTempDirectory("assay-tool")
        val tool = Files.writeString(root.resolve("gitleaks"), "binary")
        val target = Files.createDirectory(root.resolve("repo"))
        val source = Files.writeString(target.resolve("x.txt"), "safe")
        val output = root.resolve("out.sarif")
        val raw = sarif("rule", source, "error")
        val runner = object : CommandRunner {
            override fun run(command: ScannerCommand): CommandResult = if (command.arguments == listOf("--version")) {
                CommandResult(0, "gitleaks ${ToolCatalog.GITLEAKS_VERSION}", "", Duration.ZERO)
            } else {
                Files.writeString(output, raw)
                CommandResult(1, "", "", Duration.ZERO)
            }
        }
        val pin = ToolPin(Scanner.GITLEAKS, ToolCatalog.GITLEAKS_VERSION, Fingerprints.sha256(Files.readAllBytes(tool)))
        check(ScannerOrchestrator(runner).execute(GitleaksAdapter, ToolInstallation(pin, tool), target, output) is ScannerOutcome.Success)
        check(ScannerOrchestrator(runner).execute(GitleaksAdapter, ToolInstallation(pin.copy(sha256 = "0".repeat(64)), tool), target, output) is ScannerOutcome.Failed)
    }

    test("pipeline fails closed") {
        check(ScanPipeline.bundle("example/repo", "a".repeat(40), emptyList(), setOf(Scanner.GITLEAKS)).isFailure)
        check(ScanPipeline.bundle("example/repo", "a".repeat(40), listOf(ScannerOutcome.Failed(Scanner.GITLEAKS, "failed")), setOf(Scanner.GITLEAKS)).isFailure)
    }

    test("bus publication and tamper rejection") {
        val repo = Files.createTempDirectory("assay-repo")
        Files.writeString(repo.resolve("config.txt"), "token=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY\n")
        val bus = Files.createTempDirectory("assay-bus")
        val record = BusWriter(bus).publish("example/repo", "b".repeat(40), DeterministicSecretScanner.scan(repo), Instant.parse("2026-08-03T09:00:00Z"))
        val ready = BusReader(bus).read("b".repeat(40))
        check(ready is BusState.Ready && ready.findings.size == 1)
        check("FAKE_CREDENTIAL" !in Files.walk(bus).filter(Files::isRegularFile).toList().joinToString("\n") { Files.readString(it) })
        Files.writeString(bus.resolve(record.findingsRef), "tampered")
        check(BusReader(bus).read() is BusState.Invalid)
    }

    test("bus ownership, stale evidence, and typed states") {
        check(BusReader(null).read() is BusState.NotConfigured)
        check(BusReader(Files.createTempDirectory("missing").resolve("x")).read() is BusState.Unavailable)
        val bus = Files.createTempDirectory("assay-owner")
        val writer = BusWriter(bus)
        writer.publish("example/repo", "c".repeat(40), emptyList(), Instant.parse("2026-08-03T09:01:00Z"))
        check(BusReader(bus).read("d".repeat(40)) is BusState.Invalid)
        check(runCatching { writer.publish("other/repo", "e".repeat(40), emptyList(), Instant.parse("2026-08-03T09:02:00Z")) }.isFailure)
    }

    test("proof and approval cannot be bypassed") {
        val digest = "c".repeat(64)
        val evidence = ProofEvidence(
            "pt1-semgrep-sql-injection-${digest.take(16)}", digest, "1".repeat(40), "1".repeat(40), "2".repeat(40),
            "3".repeat(64), "4".repeat(64), "5".repeat(64), "6".repeat(64), "sqlInjectionIsRejected",
            listOf(TestAttemptEvidence(1, "7".repeat(64), true, true), TestAttemptEvidence(1, "7".repeat(64), true, true)),
            listOf(TestAttemptEvidence(0, "8".repeat(64), true, false), TestAttemptEvidence(0, "8".repeat(64), true, false)),
            "9".repeat(64), "a".repeat(64), true, true,
        )
        check(ProofGate.validate(evidence).isSuccess)
        check(ProofGate.validate(evidence.copy(replayClearedAfter = false)).isFailure)
        val lifecycle = LifecycleMachine()
        lifecycle.move(Lifecycle.PROPOSED)
        check(runCatching { lifecycle.move(Lifecycle.APPLIED) }.isFailure)
        lifecycle.move(Lifecycle.PROOF_PASSED); lifecycle.move(Lifecycle.APPROVED); lifecycle.move(Lifecycle.APPLIED)
    }

    test("tool lock and schemas") {
        val lock = """{"schemaVersion":"1.0.0","tools":{"gitleaks":{"version":"${ToolCatalog.GITLEAKS_VERSION}","sha256":"${"1".repeat(64)}"}}}"""
        check(ToolLock.parse(lock, setOf(Scanner.GITLEAKS)).require(Scanner.GITLEAKS).version == ToolCatalog.GITLEAKS_VERSION)
        listOf("index.schema.json", "run.schema.json", "status.schema.json", "proof.schema.json", "tool-lock.schema.json")
            .forEach { Json.parse(Files.readString(Path.of("schemas", it))).requireObject() }
    }

    println("$passed acceptance checks passed")
}

private fun sarif(rule: String, file: Path, level: String, properties: Map<String, String> = emptyMap()): String = Json.stringify(
    Json.obj(
        "version" to Json.str("2.1.0"),
        "runs" to Json.arr(listOf(Json.obj(
            "tool" to Json.obj("driver" to Json.obj("name" to Json.str("fixture"), "rules" to Json.arr(emptyList()))),
            "results" to Json.arr(listOf(Json.obj(
                "ruleId" to Json.str(rule),
                "level" to Json.str(level),
                "message" to Json.obj("text" to Json.str("fixture finding")),
                "locations" to Json.arr(listOf(Json.obj("physicalLocation" to Json.obj(
                    "artifactLocation" to Json.obj("uri" to Json.str(file.toUri().toString())),
                    "region" to Json.obj("startLine" to Json.num(1), "endLine" to Json.num(1), "snippet" to Json.obj("text" to Json.str("fixture"))),
                )))),
                "properties" to JsonValue.Obj(LinkedHashMap(properties.mapValues { Json.str(it.value) })),
            ))),
        ))),
    ),
)

private fun <T> List<T>.isSubsequenceOf(other: List<T>): Boolean = other.indices.any { start ->
    start + size <= other.size && other.subList(start, start + size) == this
}
