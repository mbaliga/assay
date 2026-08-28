package dev.assay

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant

/**
 * Stages the ratified `.assay/` tree (docs/ratified/ASSAY_REPO_CONTRACT_V1.md §1, INT-018) into a
 * local directory, ready to be handed to [GitBusPublisher.publishV1]. This is the *external*,
 * publishable projection of a run — it does not replace [BusWriter], which still maintains
 * Assay's own internal multi-run trust-engine bus (`assay/audit`, contract 1.1.0) that the
 * candidate lifecycle, proof verification, and console/Orrery projections continue to read.
 */
class AssayOutputWriterV1(private val root: Path) {
    /**
     * Writes `.assay/assay-index.v1.json` (with `assayCommit` left as [assayCommitPlaceholder] —
     * the caller/publisher fills in the real value once it is known, see [GitBusPublisher.publishV1]
     * for why that has to happen in a second pass) plus `.assay/runs/<runId>/...`.
     *
     * Returns the [AssayIndexV1] that was written (still carrying the placeholder in
     * [AssayIndexV1.assayCommit]).
     */
    fun write(
        runId: String,
        projectRef: AssayProjectRefV1,
        sourceCommit: String,
        assayCommitPlaceholder: String,
        tool: AssayToolRefV1,
        startedAt: Instant,
        finishedAt: Instant,
        findings: List<Finding>,
        completeness: AssayCompletenessV1,
        explanation: String? = null,
        provingTests: List<ProvingTestEntryV1> = emptyList(),
    ): AssayIndexV1 {
        require(assayCommitPlaceholder.matches(Regex("[0-9a-f]{40}"))) { "invalid assayCommit placeholder" }
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root)) { "output staging root must not be a symlink" }

        val assayDir = safeResolve(".assay")
        val runDir = safeResolve(".assay/runs/$runId")
        require(!Files.exists(runDir)) { "run $runId is already staged" }
        Files.createDirectories(runDir.resolve("evidence"))
        Files.createDirectories(safeResolve(".assay/tests/proving"))

        val findingsSarif = writeArtifact(runDir.resolve("findings.sarif"), Sarif.encode(findings))
        val findingFiles = listOf(
            FindingFileRefV1(
                path = "findings.sarif",
                sha256 = Fingerprints.sha256(findingsSarif),
                count = findings.size,
            ),
        )

        val provingContainer = ProvingTestsV1(
            runId = runId,
            generatedAt = finishedAt,
            tests = provingTests,
        )
        writeArtifact(runDir.resolve("proving-tests.v1.json"), ProvingTestsV1Codec.encode(provingContainer))

        writeArtifact(
            assayDir.resolve("README.md"),
            """
            |# Assay output — `.assay/`
            |
            |This tree is real scan output, readable and actionable with zero Fonebrew installed
            |(docs/ratified/ASSAY_REPO_CONTRACT_V1.md, INT-002). `assay-index.v1.json` is this
            |branch's current run manifest; `runs/$runId/` holds that run's SARIF findings and
            |proving tests; `tests/proving/` holds the generated proving-test sources referenced
            |from `runs/$runId/proving-tests.v1.json`.
            |
            |Fonebrew Core only reads this tree after a user explicitly selects this repo+branch
            |and taps "Check branch" (INT-020) — nothing here is polled or auto-imported.
            |""".trimMargin(),
        )

        val index = AssayIndexV1(
            runId = runId,
            projectRef = projectRef,
            sourceCommit = sourceCommit,
            assayCommit = assayCommitPlaceholder,
            tool = tool,
            startedAt = startedAt,
            finishedAt = finishedAt,
            findingFiles = findingFiles,
            provingTests = ProvingTestsSummaryV1(path = "proving-tests.v1.json", count = provingTests.size),
            completeness = completeness,
            explanation = explanation,
        )
        writeArtifact(assayDir.resolve("assay-index.v1.json"), AssayIndexV1Codec.encode(index))
        return index
    }

    private fun writeArtifact(path: Path, content: String): ByteArray {
        val safe = Redaction.sanitize(content)
        check(!Redaction.containsSecret(safe)) { "outgoing Assay output artifact contains a secret: $path" }
        val bytes = safe.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= MAX_ARTIFACT_BYTES) { "output artifact exceeds size limit: $path" }
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return bytes
    }

    private fun safeResolve(relative: String): Path {
        val normalized = Fingerprints.normalizePath(relative)
        val base = root.toAbsolutePath().normalize()
        val resolved = base.resolve(normalized).normalize()
        require(resolved.startsWith(base)) { "path escapes output staging root" }
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(resolved)) { "symlink rejected in output staging tree" }
        }
        return resolved
    }

    companion object {
        private const val MAX_ARTIFACT_BYTES = 32L * 1024 * 1024

        /**
         * A well-formed 40-hex sentinel, astronomically unlikely to collide with a real Git
         * commit id — see [GitBusPublisher.publishV1] for why a placeholder is needed at all.
         */
        const val ASSAY_COMMIT_PLACEHOLDER: String = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
    }
}

/**
 * CLI entry point for the ratified output contract: stage `.assay/` (docs/ratified/
 * ASSAY_REPO_CONTRACT_V1.md) and publish it to a user-selected branch of the scanned repository.
 * Superficially parallel to [RemoteBusCli], but for the external `assay-index.v1.json` shape
 * rather than Assay's internal `assay/audit` trust-engine bus.
 */
object AssayOutputCli {
    private const val COMMAND = "publish-output-git"

    fun handles(command: String?): Boolean = command == COMMAND

    fun run(args: Array<String>) {
        require(args.firstOrNull() == COMMAND) { "unsupported output publish command" }
        val options = Options(args.drop(1))
        val runId = options.required("--run-id")
        val sourceCommit = options.required("--source-commit")
        require(sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "invalid source commit" }
        val findings = Sarif.decodeCanonical(Files.readString(Path.of(options.required("--findings-sarif"))))
        val provingTests = options.repeated("--proving-test").map(::parseProvingTest)

        val bus = Files.createTempDirectory("assay-output-v1-")
        val index = AssayOutputWriterV1(bus).write(
            runId = runId,
            projectRef = AssayProjectRefV1(
                gitRemote = options.required("--git-remote"),
                fonebrewProjectHint = options.optional("--fonebrew-project-hint"),
            ),
            sourceCommit = sourceCommit,
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            tool = AssayToolRefV1(options.required("--tool-name"), options.required("--tool-version")),
            startedAt = Instant.parse(options.required("--started-at")),
            finishedAt = Instant.parse(options.required("--finished-at")),
            findings = findings,
            completeness = AssayCompletenessV1.valueOf(options.optional("--completeness") ?: "COMPLETE"),
            explanation = options.optional("--explanation"),
            provingTests = provingTests,
        )

        val expectedRemote = options.required("--expected-remote").let { value ->
            if (value == "absent") null else value.also {
                require(it.matches(Regex("[0-9a-f]{40}"))) { "invalid expected remote commit" }
            }
        }
        val commit = GitBusPublisher(
            repository = Path.of(options.required("--repo")),
            branch = options.required("--branch"),
        ).publishV1(
            busRoot = bus,
            remote = options.optional("--remote") ?: "origin",
            expectedRemoteCommit = expectedRemote,
            indexRelativePath = ".assay/assay-index.v1.json",
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            commitMessage = "Assay output ${index.runId} ${index.sourceCommit}",
        )
        println(commit)
    }

    private fun parseProvingTest(raw: String): ProvingTestEntryV1 {
        val parts = raw.split('|')
        require(parts.size == 6) {
            "invalid --proving-test '$raw'; expected testId|targetFindingRef|testKind|sourcePath|status|lastRunAtUtcOrNull"
        }
        return ProvingTestEntryV1(
            testId = parts[0],
            targetFindingRef = parts[1],
            testKind = ProvingTestKindV1.valueOf(parts[2]),
            sourcePath = parts[3],
            status = ProvingTestStatusV1.valueOf(parts[4]),
            lastRunAtUtc = parts[5].takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse),
        )
    }

    private class Options(args: List<String>) {
        private val values: Map<String, String>
        private val allValues: List<Pair<String, String>>

        init {
            val parsed = linkedMapOf<String, String>()
            val all = mutableListOf<Pair<String, String>>()
            var index = 0
            while (index < args.size) {
                val name = args[index]
                require(name.startsWith("--")) { "unexpected argument '$name'" }
                require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $name" }
                all += name to args[index + 1]
                parsed[name] = args[index + 1]
                index += 2
            }
            values = parsed
            allValues = all
        }

        fun required(name: String): String = values[name] ?: error("missing $name")
        fun optional(name: String): String? = values[name]
        fun repeated(name: String): List<String> = allValues.filter { it.first == name }.map { it.second }
    }
}
