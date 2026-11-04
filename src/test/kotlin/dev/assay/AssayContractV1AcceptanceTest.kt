package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Structural conformance against Fonebrew Core's ratified schema
 * (`schemas/integrations/assay-index.v1.schema.json` / `proving-tests.v1.schema.json`, mirrored
 * here verbatim from `docs/ratified/ASSAY_REPO_CONTRACT_V1.md`'s source repo). There is no
 * JSON-Schema validator library in this project's dependency graph (`build.gradle.kts` pulls in
 * nothing beyond the Kotlin stdlib), so this is a **structural assertion test**: it reads the
 * committed schema JSON directly off disk with the repo's own [Json] parser and asserts our
 * required-field lists, patterns, and enums are byte-identical to what the schema declares —
 * rather than duplicating those constraints as an untethered hardcoded copy. It is not a general
 * JSON-Schema engine (no `allOf`/`if`/`then` interpreter, no recursive `$ref` resolution), but
 * every constraint the ratified contract actually imposes on Assay's output (§2/§3 of
 * ASSAY_REPO_CONTRACT_V1.md) is checked explicitly below, against fixtures copied verbatim from
 * Core plus this repo's own emitted output.
 */
fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        body(); passed++; println("PASS $name")
    }

    val indexSchema = Json.parse(Files.readString(Path.of("schemas/integrations/assay-index.v1.schema.json")))
        .requireObject()
    val provingSchema = Json.parse(Files.readString(Path.of("schemas/integrations/proving-tests.v1.schema.json")))
        .requireObject()

    test("assay-index.v1.schema.json required fields match AssayIndexV1's contract") {
        val required = indexSchema.required("required").requireArray().values.map { it.requireString() }.toSet()
        check(
            required == setOf(
                "schemaVersion", "runId", "projectRef", "sourceCommit", "assayCommit", "tool",
                "startedAt", "finishedAt", "findingFiles", "provingTests", "completeness",
            ),
        ) { "assay-index.v1.schema.json's required[] drifted from what AssayIndexV1 assumes: $required" }
    }

    test("proving-tests.v1.schema.json required fields match ProvingTestsV1's contract") {
        val required = provingSchema.required("required").requireArray().values.map { it.requireString() }.toSet()
        check(required == setOf("schemaVersion", "runId", "generatedAt", "tests")) {
            "proving-tests.v1.schema.json's required[] drifted from what ProvingTestsV1 assumes: $required"
        }
        val entryRequired = provingSchema.required("\$defs").requireObject().required("ProvingTestEntry")
            .requireObject().required("required").requireArray().values.map { it.requireString() }.toSet()
        check(entryRequired == setOf("testId", "targetFindingRef", "testKind", "sourcePath", "status")) {
            "ProvingTestEntry's required[] drifted from what ProvingTestEntryV1 assumes: $entryRequired"
        }
    }

    test("schemaVersion pattern is byte-identical to the schema's") {
        val pattern = indexSchema.required("properties").requireObject().required("schemaVersion")
            .requireObject().string("pattern")
        check(pattern == AssayContractV1.SCHEMA_VERSION_PATTERN.pattern)
        check(AssayContractV1.SCHEMA_VERSION_PATTERN.matches(AssayContractV1.SCHEMA_VERSION))
    }

    test("findingFiles[].sha256 pattern is byte-identical to the schema's") {
        val pattern = indexSchema.required("\$defs").requireObject().required("FindingFileRef").requireObject()
            .required("properties").requireObject().required("sha256").requireObject().string("pattern")
        check(pattern == AssayContractV1.SHA256_PATTERN.pattern)
    }

    test("completeness enum is byte-identical to the schema's") {
        val enum = indexSchema.required("properties").requireObject().required("completeness")
            .requireObject().required("enum").requireArray().values.map { it.requireString() }
        check(enum == AssayCompletenessV1.entries.map { it.name }) { "completeness enum drift: $enum" }
    }

    test("proving-test kind/status enums are byte-identical to the schema's") {
        val defs = provingSchema.required("\$defs").requireObject()
        val kind = defs.required("ProvingTestKind").requireObject().required("enum").requireArray()
            .values.map { it.requireString() }
        check(kind == ProvingTestKindV1.entries.map { it.name }) { "testKind enum drift: $kind" }
        val status = defs.required("ProvingTestStatus").requireObject().required("enum").requireArray()
            .values.map { it.requireString() }
        check(status == ProvingTestStatusV1.entries.map { it.name }) { "status enum drift: $status" }
    }

    test("Core's valid assay-index fixture decodes and satisfies AssayIndexV1's own invariants") {
        val text = Files.readString(Path.of("fixtures/integrations/valid/assay-index-valid.json"))
        val decoded = AssayIndexV1Codec.decode(text)
        check(decoded.runId == "01J9E0000000000000000RN1")
        check(decoded.completeness == AssayCompletenessV1.COMPLETE)
        check(decoded.findingFiles.size == 2)
        check(decoded.findingFiles.all { AssayContractV1.SHA256_PATTERN.matches(it.sha256) })
        // Round-trips through our own encoder and still satisfies every required key.
        val reencoded = Json.parse(AssayIndexV1Codec.encode(decoded)).requireObject()
        val required = indexSchema.required("required").requireArray().values.map { it.requireString() }
        required.forEach { key -> check(key in reencoded.values) { "re-encoded index is missing required key '$key'" } }
    }

    test("Core's invalid assay-index fixture (PARTIAL without explanation) is rejected") {
        val text = Files.readString(
            Path.of("fixtures/integrations/invalid/assay-index-partial-missing-explanation.invalid.json"),
        )
        val failure = runCatching { AssayIndexV1Codec.decode(text) }
        check(failure.isFailure) { "PARTIAL run without a non-blank explanation must be rejected" }
    }

    test("Core's valid proving-tests fixture decodes") {
        val text = Files.readString(Path.of("fixtures/integrations/valid/proving-tests-valid.json"))
        val decoded = ProvingTestsV1Codec.decode(text)
        check(decoded.runId == "01J9E0000000000000000RN1")
        check(decoded.tests.size == 3)
        check(decoded.tests.map { it.status } == listOf(
            ProvingTestStatusV1.PASSING, ProvingTestStatusV1.FAILING, ProvingTestStatusV1.NOT_RUN,
        ))
    }

    test("Core's invalid proving-tests fixture (PASSING without lastRunAtUtc) is rejected") {
        val text = Files.readString(
            Path.of("fixtures/integrations/invalid/proving-tests-passing-without-lastrun.invalid.json"),
        )
        val failure = runCatching { ProvingTestsV1Codec.decode(text) }
        check(failure.isFailure) { "PASSING without lastRunAtUtc must be rejected" }
    }

    test("AssayOutputWriterV1 emits an assay-index.v1.json that satisfies every required field") {
        val workspace = Files.createTempDirectory("assay-contract-v1-")
        val finding = Finding(
            scanner = Scanner.SEMGREP,
            ruleId = "android.exported-component",
            message = "Exported component is not permission protected",
            location = Location("app/src/main/AndroidManifest.xml", 14),
            context = "exported activity without permission",
            level = SarifLevel.ERROR,
        )
        val index = AssayOutputWriterV1(workspace).write(
            runId = "01J9E0000000000000000RN9",
            projectRef = AssayProjectRefV1("https://github.com/owner/target-repo.git", "core-mobile"),
            sourceCommit = "a".repeat(40),
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            tool = AssayToolRefV1("assay-cli", "1.1.0"),
            startedAt = Instant.parse("2026-08-28T02:00:00Z"),
            finishedAt = Instant.parse("2026-08-28T02:14:00Z"),
            findings = listOf(finding),
            completeness = AssayCompletenessV1.COMPLETE,
        )
        check(Files.isRegularFile(workspace.resolve(".assay/assay-index.v1.json")))
        check(Files.isRegularFile(workspace.resolve(".assay/runs/${index.runId}/findings.sarif")))
        check(Files.isRegularFile(workspace.resolve(".assay/runs/${index.runId}/proving-tests.v1.json")))
        check(Files.isDirectory(workspace.resolve(".assay/runs/${index.runId}/evidence")))
        check(Files.isDirectory(workspace.resolve(".assay/tests/proving")))
        check(Files.isRegularFile(workspace.resolve(".assay/README.md")))

        val written = AssayIndexV1Codec.decode(Files.readString(workspace.resolve(".assay/assay-index.v1.json")))
        check(written.findingFiles.single().path == "findings.sarif")
        check(written.findingFiles.single().count == 1)
        val actualBytes = Files.readAllBytes(workspace.resolve(".assay/runs/${index.runId}/findings.sarif"))
        check(written.findingFiles.single().sha256 == Fingerprints.sha256(actualBytes)) {
            "findingFiles[].sha256 must be the recomputed digest of the exact bytes at path, per §2"
        }
        check(written.provingTests == ProvingTestsSummaryV1("proving-tests.v1.json", 0))

        val required = indexSchema.required("required").requireArray().values.map { it.requireString() }
        val encodedKeys = Json.parse(
            Files.readString(workspace.resolve(".assay/assay-index.v1.json")),
        ).requireObject().values.keys
        required.forEach { key -> check(key in encodedKeys) { "emitted index is missing required key '$key'" } }
    }

    test(
        "AssayOutputWriterV1's findings.sarif satisfies ASSAY_REPO_CONTRACT_V1.md §4: every " +
            "results[].properties carries scannerName+ruleId, and provingTestRef wires positionally",
    ) {
        val workspace = Files.createTempDirectory("assay-contract-v1-sarif-")
        val gitleaksFinding = Finding(
            scanner = Scanner.GITLEAKS,
            ruleId = "generic-api-key",
            message = "Hardcoded credential-shaped string detected.",
            location = Location("app/src/main/java/dev/aarso/data/Config.kt", 42),
            context = "token=[REDACTED]",
            level = SarifLevel.ERROR,
        )
        val semgrepFinding = Finding(
            scanner = Scanner.SEMGREP,
            ruleId = "android.exported-component",
            message = "Exported component is not permission protected",
            location = Location("app/src/main/AndroidManifest.xml", 14),
            context = "exported activity without permission",
            level = SarifLevel.ERROR,
        )
        // Sarif.merge sorts results by scanner wire name ("gitleaks" < "semgrep"), so the Gitleaks
        // finding is always results[0] -- that's what makes this targetFindingRef trustworthy.
        val provingTest = ProvingTestEntryV1(
            testId = "01J9F0000000000000000PT2",
            targetFindingRef = "findings.sarif#/runs/0/results/0",
            testKind = ProvingTestKindV1.EXPLOIT_POC,
            sourcePath = "tests/proving/test_generic_api_key.py",
            status = ProvingTestStatusV1.NOT_RUN,
        )

        val index = AssayOutputWriterV1(workspace).write(
            runId = "01J9E0000000000000000RNS",
            projectRef = AssayProjectRefV1("https://github.com/owner/target-repo.git"),
            sourceCommit = "c".repeat(40),
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            tool = AssayToolRefV1("assay-cli", "1.1.0"),
            startedAt = Instant.parse("2026-08-28T02:00:00Z"),
            finishedAt = Instant.parse("2026-08-28T02:14:00Z"),
            // Passed out of merged order deliberately -- Sarif.encode re-merges/sorts, so this also
            // exercises that the positional targetFindingRef survives that re-sort.
            findings = listOf(semgrepFinding, gitleaksFinding),
            completeness = AssayCompletenessV1.COMPLETE,
            provingTests = listOf(provingTest),
        )

        val sarifText = Files.readString(workspace.resolve(".assay/runs/${index.runId}/findings.sarif"))
        val results = Json.parse(sarifText).requireObject()
            .required("runs").requireArray().values.first().requireObject()
            .required("results").requireArray().values
        check(results.size == 2)

        results.forEachIndexed { i, value ->
            val result = value.requireObject()
            val props = result.required("properties").requireObject()
            check(props.string("scannerName").isNotBlank()) {
                "results[$i].properties.scannerName must be present per §4"
            }
            check(props.string("ruleId").isNotBlank()) { "results[$i].properties.ruleId must be present per §4" }
            check(props.string("ruleId") == result.string("ruleId")) {
                "results[$i].properties.ruleId must duplicate the result's own top-level ruleId, per §4"
            }
        }

        val gitleaksResult = results.first { it.requireObject().string("ruleId") == "generic-api-key" }
            .requireObject().required("properties").requireObject()
        check(gitleaksResult.string("scannerName") == "Gitleaks")
        check(gitleaksResult.string("provingTestRef") == provingTest.testId) {
            "the result named by a proving-tests.v1.json entry's targetFindingRef must carry " +
                "provingTestRef == that entry's testId"
        }

        val semgrepResult = results.first { it.requireObject().string("ruleId") == "android.exported-component" }
            .requireObject().required("properties").requireObject()
        check(semgrepResult.string("scannerName") == "Semgrep")
        check("provingTestRef" !in semgrepResult.values) {
            "a result with no matching proving-tests.v1.json entry must omit provingTestRef entirely " +
                "(absent, not null) per §4"
        }
    }

    test("AssayIndexV1 rejects PARTIAL/FAILED completeness without a non-blank explanation") {
        fun index(completeness: AssayCompletenessV1, explanation: String?) = AssayIndexV1(
            runId = "run-1",
            projectRef = AssayProjectRefV1("https://github.com/owner/repo.git"),
            sourceCommit = "a".repeat(40),
            assayCommit = "b".repeat(40),
            tool = AssayToolRefV1("assay-cli", "1.1.0"),
            startedAt = Instant.parse("2026-08-28T00:00:00Z"),
            finishedAt = Instant.parse("2026-08-28T00:01:00Z"),
            findingFiles = emptyList(),
            provingTests = ProvingTestsSummaryV1("proving-tests.v1.json", 0),
            completeness = completeness,
            explanation = explanation,
        )
        check(runCatching { index(AssayCompletenessV1.PARTIAL, null) }.isFailure)
        check(runCatching { index(AssayCompletenessV1.PARTIAL, "   ") }.isFailure)
        check(runCatching { index(AssayCompletenessV1.FAILED, null) }.isFailure)
        index(AssayCompletenessV1.PARTIAL, "Semgrep timed out; MobSF/OSV-Scanner/Gitleaks completed")
        index(AssayCompletenessV1.COMPLETE, null)
    }

    test("ProvingTestEntryV1 rejects a run status without lastRunAtUtc") {
        fun entry(status: ProvingTestStatusV1, lastRun: Instant?) = ProvingTestEntryV1(
            testId = "01J9F0000000000000000PT1",
            targetFindingRef = "findings.sarif#/runs/0/results/0",
            testKind = ProvingTestKindV1.UNIT,
            sourcePath = "tests/proving/test_one.py",
            status = status,
            lastRunAtUtc = lastRun,
        )
        check(runCatching { entry(ProvingTestStatusV1.PASSING, null) }.isFailure)
        check(runCatching { entry(ProvingTestStatusV1.FAILING, null) }.isFailure)
        check(runCatching { entry(ProvingTestStatusV1.ERROR, null) }.isFailure)
        entry(ProvingTestStatusV1.NOT_RUN, null)
        entry(ProvingTestStatusV1.PASSING, Instant.parse("2026-08-28T00:00:00Z"))
    }

    println("$passed assertions passed")
}
