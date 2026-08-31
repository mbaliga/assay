package dev.assay

import java.time.Instant

/**
 * The ratified constellation contract for Assay's publishable output, per
 * `docs/ratified/ASSAY_REPO_CONTRACT_V1.md` (INT-018/019/020) in the Fonebrew core repo and
 * `schemas/integrations/assay-index.v1.schema.json` / `proving-tests.v1.schema.json` there
 * (mirrored locally at `schemas/integrations/`). This file is the spoke's implementation of the
 * hub-ratified shape — Core is the source of truth; do not diverge from its schema here.
 *
 * Contract summary (INT-018): Assay writes its scan output to a user-selected output branch of
 * the SCANNED repository, under `.assay/`:
 *
 *   .assay/assay-index.v1.json        <- AssayIndexV1 (this file's [AssayIndexV1])
 *   .assay/runs/<runId>/findings.sarif
 *   .assay/runs/<runId>/proving-tests.v1.json  <- ProvingTestsV1
 *   .assay/runs/<runId>/evidence/
 *   .assay/tests/proving/
 *   .assay/README.md
 *
 * This is a *different* shape from Assay's own internal `assay/audit` trust-engine bus
 * (`Bus.kt`, `ContractValidator.kt`, `schemas/index.schema.json` et al, contract 1.1.0) — that
 * internal engine keeps the full multi-run history, per-finding lifecycle, and proof evidence
 * Assay needs for its own candidate workflow, and stays intact. The types here are the narrow,
 * single-run *publishable* projection of that trust engine, shaped to satisfy what Fonebrew Core
 * has ratified and actually reads (see `docs/CONTRACT.md` for the map between the two).
 */
object AssayContractV1 {
    const val SCHEMA_VERSION = "1.0.0"
    val SCHEMA_VERSION_PATTERN: Regex = Regex("^1\\.\\d+\\.\\d+$")
    val SHA256_PATTERN: Regex = Regex("^[0-9a-f]{64}$")
}

enum class AssayCompletenessV1 { COMPLETE, PARTIAL, FAILED }

enum class ProvingTestKindV1 { UNIT, INTEGRATION, EXPLOIT_POC, REGRESSION, OTHER }

enum class ProvingTestStatusV1 { PASSING, FAILING, NOT_RUN, ERROR }

data class AssayProjectRefV1(
    val gitRemote: String,
    val fonebrewProjectHint: String? = null,
) {
    init {
        require(gitRemote.isNotBlank()) { "projectRef.gitRemote is blank" }
    }
}

data class AssayToolRefV1(val name: String, val version: String) {
    init {
        require(name.isNotBlank()) { "tool.name is blank" }
        require(version.isNotBlank()) { "tool.version is blank" }
    }
}

data class FindingFileRefV1(val path: String, val sha256: String, val count: Int) {
    init {
        require(path.isNotBlank()) { "findingFiles[].path is blank" }
        require(path.split('/').none { it == ".." }) { "findingFiles[].path contains a '..' segment" }
        require(AssayContractV1.SHA256_PATTERN.matches(sha256)) { "findingFiles[].sha256 must be lowercase sha256" }
        require(count >= 0) { "findingFiles[].count must be >= 0" }
    }
}

data class ProvingTestsSummaryV1(val path: String, val count: Int) {
    init {
        require(path.isNotBlank()) { "provingTests.path is blank" }
        require(path.split('/').none { it == ".." }) { "provingTests.path contains a '..' segment" }
        require(count >= 0) { "provingTests.count must be >= 0" }
    }
}

/**
 * `.assay/assay-index.v1.json` — the top-level manifest Core reads only after the user selects
 * repo+branch and taps "Check branch" (INT-020). Mirrors
 * `schemas/integrations/assay-index.v1.schema.json` field-for-field.
 */
data class AssayIndexV1(
    val schemaVersion: String = AssayContractV1.SCHEMA_VERSION,
    val runId: String,
    val projectRef: AssayProjectRefV1,
    val sourceCommit: String,
    val assayCommit: String,
    val tool: AssayToolRefV1,
    val startedAt: Instant,
    val finishedAt: Instant,
    val findingFiles: List<FindingFileRefV1>,
    val provingTests: ProvingTestsSummaryV1,
    val completeness: AssayCompletenessV1,
    val explanation: String? = null,
) {
    init {
        require(AssayContractV1.SCHEMA_VERSION_PATTERN.matches(schemaVersion)) {
            "unsupported assay-index schemaVersion '$schemaVersion'"
        }
        require(runId.isNotBlank()) { "runId is blank" }
        require(sourceCommit.isNotBlank()) { "sourceCommit is blank" }
        require(assayCommit.isNotBlank()) { "assayCommit is blank" }
        // Structurally enforced allOf/if/then, docs/ratified/ASSAY_REPO_CONTRACT_V1.md §2:
        // completeness PARTIAL/FAILED requires a required, non-blank explanation.
        if (completeness != AssayCompletenessV1.COMPLETE) {
            require(!explanation.isNullOrBlank()) {
                "explanation is required (non-blank) whenever completeness is $completeness"
            }
        }
    }
}

/** `runs/<runId>/proving-tests.v1.json` — designed in the ratified doc, not in the source pack. */
data class ProvingTestEntryV1(
    val testId: String,
    val targetFindingRef: String,
    val testKind: ProvingTestKindV1,
    val sourcePath: String,
    val status: ProvingTestStatusV1,
    val description: String? = null,
    val lastRunAtUtc: Instant? = null,
) {
    init {
        require(testId.isNotBlank()) { "tests[].testId is blank" }
        require(targetFindingRef.isNotBlank()) { "tests[].targetFindingRef is blank" }
        require(sourcePath.isNotBlank()) { "tests[].sourcePath is blank" }
        require(sourcePath.split('/').none { it == ".." }) { "tests[].sourcePath contains a '..' segment" }
        // Structurally enforced allOf/if/then, docs/ratified/ASSAY_REPO_CONTRACT_V1.md §3:
        // a test cannot be reported as having run without lastRunAtUtc.
        if (status != ProvingTestStatusV1.NOT_RUN) {
            require(lastRunAtUtc != null) { "lastRunAtUtc is required whenever status is not NOT_RUN" }
        }
    }
}

data class ProvingTestsV1(
    val schemaVersion: String = AssayContractV1.SCHEMA_VERSION,
    val runId: String,
    val generatedAt: Instant,
    val tests: List<ProvingTestEntryV1> = emptyList(),
) {
    init {
        require(AssayContractV1.SCHEMA_VERSION_PATTERN.matches(schemaVersion)) {
            "unsupported proving-tests schemaVersion '$schemaVersion'"
        }
        require(runId.isNotBlank()) { "runId is blank" }
    }
}

object AssayIndexV1Codec {
    fun encode(index: AssayIndexV1): String = Json.stringify(
        Json.obj(
            "schemaVersion" to Json.str(index.schemaVersion),
            "runId" to Json.str(index.runId),
            "projectRef" to Json.obj(
                "gitRemote" to Json.str(index.projectRef.gitRemote),
                "fonebrewProjectHint" to nullable(index.projectRef.fonebrewProjectHint),
            ),
            "sourceCommit" to Json.str(index.sourceCommit),
            "assayCommit" to Json.str(index.assayCommit),
            "tool" to Json.obj(
                "name" to Json.str(index.tool.name),
                "version" to Json.str(index.tool.version),
            ),
            "startedAt" to Json.str(index.startedAt.toString()),
            "finishedAt" to Json.str(index.finishedAt.toString()),
            "findingFiles" to Json.arr(
                index.findingFiles.map { file ->
                    Json.obj(
                        "path" to Json.str(file.path),
                        "sha256" to Json.str(file.sha256),
                        "count" to Json.num(file.count),
                    )
                },
            ),
            "provingTests" to Json.obj(
                "path" to Json.str(index.provingTests.path),
                "count" to Json.num(index.provingTests.count),
            ),
            "completeness" to Json.str(index.completeness.name),
            "explanation" to nullable(index.explanation),
            "unknownFields" to Json.obj(),
        ),
        pretty = true,
    ) + "\n"

    fun decode(text: String): AssayIndexV1 {
        val root = Json.parse(text).requireObject()
        val projectRefObj = root.required("projectRef", "$").requireObject("$.projectRef")
        return AssayIndexV1(
            schemaVersion = root.string("schemaVersion"),
            runId = root.string("runId"),
            projectRef = AssayProjectRefV1(
                gitRemote = projectRefObj.string("gitRemote"),
                fonebrewProjectHint = projectRefObj.stringOrNull("fonebrewProjectHint"),
            ),
            sourceCommit = root.string("sourceCommit"),
            assayCommit = root.string("assayCommit"),
            tool = root.required("tool", "$").requireObject("$.tool").let {
                AssayToolRefV1(it.string("name"), it.string("version"))
            },
            startedAt = Instant.parse(root.string("startedAt")),
            finishedAt = Instant.parse(root.string("finishedAt")),
            findingFiles = root.required("findingFiles", "$").requireArray("$.findingFiles").values.map { value ->
                val obj = value.requireObject("$.findingFiles[]")
                FindingFileRefV1(obj.string("path"), obj.string("sha256"), obj.doubleOrNull("count")!!.toInt())
            },
            provingTests = root.required("provingTests", "$").requireObject("$.provingTests").let {
                ProvingTestsSummaryV1(it.string("path"), it.doubleOrNull("count")!!.toInt())
            },
            completeness = AssayCompletenessV1.valueOf(root.string("completeness")),
            explanation = root.stringOrNull("explanation"),
        )
    }

    private fun nullable(value: String?): JsonValue = value?.let(Json::str) ?: Json.nullValue
}

object ProvingTestsV1Codec {
    fun encode(tests: ProvingTestsV1): String = Json.stringify(
        Json.obj(
            "schemaVersion" to Json.str(tests.schemaVersion),
            "runId" to Json.str(tests.runId),
            "generatedAt" to Json.str(tests.generatedAt.toString()),
            "tests" to Json.arr(
                tests.tests.map { entry ->
                    Json.obj(
                        "testId" to Json.str(entry.testId),
                        "targetFindingRef" to Json.str(entry.targetFindingRef),
                        "testKind" to Json.str(entry.testKind.name),
                        "sourcePath" to Json.str(entry.sourcePath),
                        "status" to Json.str(entry.status.name),
                        "description" to nullable(entry.description),
                        "lastRunAtUtc" to nullable(entry.lastRunAtUtc?.toString()),
                    )
                },
            ),
            "unknownFields" to Json.obj(),
        ),
        pretty = true,
    ) + "\n"

    fun decode(text: String): ProvingTestsV1 {
        val root = Json.parse(text).requireObject()
        return ProvingTestsV1(
            schemaVersion = root.string("schemaVersion"),
            runId = root.string("runId"),
            generatedAt = Instant.parse(root.string("generatedAt")),
            tests = root.required("tests", "$").requireArray("$.tests").values.map { value ->
                val obj = value.requireObject("$.tests[]")
                ProvingTestEntryV1(
                    testId = obj.string("testId"),
                    targetFindingRef = obj.string("targetFindingRef"),
                    testKind = ProvingTestKindV1.valueOf(obj.string("testKind")),
                    sourcePath = obj.string("sourcePath"),
                    status = ProvingTestStatusV1.valueOf(obj.string("status")),
                    description = obj.stringOrNull("description"),
                    lastRunAtUtc = obj.stringOrNull("lastRunAtUtc")?.let(Instant::parse),
                )
            },
        )
    }

    private fun nullable(value: String?): JsonValue = value?.let(Json::str) ?: Json.nullValue
}
