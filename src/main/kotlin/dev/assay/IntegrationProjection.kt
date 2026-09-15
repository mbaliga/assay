package dev.assay

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant

enum class ConsoleAvailability { NOT_CONFIGURED, UNAVAILABLE, INVALID, READY }
enum class OrreryHealth { UNKNOWN, HEALTHY, DEGRADED, BLOCKED }

data class ConsoleFinding(
    val fingerprint: String,
    val scanner: String,
    val ruleId: String,
    val severity: String,
    val message: String,
    val file: String,
    val startLine: Int,
    val candidates: List<String>,
)

data class ConsoleCandidate(
    val candidateId: String,
    val findingFingerprint: String,
    val lifecycle: String,
    val revision: Int,
    val fixBranch: String,
    val humanApproved: Boolean,
)

data class AssayConsoleSnapshot(
    val schemaVersion: String = "1.1.0",
    val generatedAt: Instant,
    val availability: ConsoleAvailability,
    val reason: String?,
    val sourceRepo: String?,
    val sourceCommit: String?,
    val runId: String?,
    val findings: List<ConsoleFinding>,
    val candidates: List<ConsoleCandidate>,
) {
    val unresolvedFindings: Int get() = findings.count { finding ->
        val states = candidates.filter { it.findingFingerprint == finding.fingerprint }.map { it.lifecycle }
        states.none { it == "applied" || it == "obsolete" }
    }
}

data class OrreryAssayStatus(
    val schemaVersion: String = "1.1.0",
    val generatedAt: Instant,
    val health: OrreryHealth,
    val sourceRepo: String?,
    val sourceCommit: String?,
    val runId: String?,
    val findingCount: Int,
    val unresolvedFindingCount: Int,
    val candidateCount: Int,
    val reason: String?,
)

class AssayConsoleReader(
    private val busRoot: Path?,
    private val candidateRoot: Path?,
) {
    fun snapshot(expectedSourceCommit: String?, now: Instant = Instant.now()): AssayConsoleSnapshot {
        val busState = BusReader(busRoot).read(expectedSourceCommit)
        if (busState !is BusState.Ready) {
            return AssayConsoleSnapshot(
                generatedAt = now,
                availability = when (busState) {
                    BusState.NotConfigured -> ConsoleAvailability.NOT_CONFIGURED
                    is BusState.Unavailable -> ConsoleAvailability.UNAVAILABLE
                    is BusState.Invalid -> ConsoleAvailability.INVALID
                    is BusState.Ready -> error("unreachable")
                },
                reason = when (busState) {
                    BusState.NotConfigured -> "audit bus not configured"
                    is BusState.Unavailable -> busState.reason
                    is BusState.Invalid -> busState.reason
                    is BusState.Ready -> null
                },
                sourceRepo = null,
                sourceCommit = expectedSourceCommit,
                runId = null,
                findings = emptyList(),
                candidates = emptyList(),
            )
        }
        val run = busState.run
        val records = readCandidates()
            .filter { candidate ->
                run == null || (
                    candidate.identity.sourceRepo == run.sourceRepo &&
                        candidate.identity.sourceCommit == run.sourceCommit
                    )
            }
            .sortedBy { it.identity.candidateId }
        val candidateViews = records.map { candidate ->
            ConsoleCandidate(
                candidateId = candidate.identity.candidateId,
                findingFingerprint = candidate.identity.findingFingerprint,
                lifecycle = candidate.lifecycle.name.lowercase(),
                revision = candidate.revision,
                fixBranch = candidate.identity.fixBranch,
                humanApproved = candidate.approval != null,
            )
        }
        val byFinding = candidateViews.groupBy { it.findingFingerprint }
        val findings = busState.findings.sortedWith(
            compareBy<Finding>({ it.severity.ordinal }, { it.scanner.wireName }, { it.ruleId }, { it.fingerprint }),
        ).map { finding ->
            ConsoleFinding(
                fingerprint = finding.fingerprint,
                scanner = finding.scanner.wireName,
                ruleId = finding.ruleId,
                severity = finding.severity.name,
                message = Redaction.sanitize(finding.message),
                file = finding.location.file,
                startLine = finding.location.startLine,
                candidates = byFinding[finding.fingerprint].orEmpty().map { it.candidateId },
            )
        }
        return AssayConsoleSnapshot(
            generatedAt = now,
            availability = ConsoleAvailability.READY,
            reason = null,
            sourceRepo = run?.sourceRepo,
            sourceCommit = run?.sourceCommit ?: expectedSourceCommit,
            runId = run?.runId,
            findings = findings,
            candidates = candidateViews,
        )
    }

    private fun readCandidates(): List<CandidateRecord> {
        val root = candidateRoot ?: return emptyList()
        val normalized = root.toAbsolutePath().normalize()
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(normalized)) { "candidate store must not be a symlink" }
        return CandidateStore(normalized).list()
    }
}

data class FonebrewProposal(
    val sourceCommit: String,
    val findingFingerprint: String,
    val patchDigest: String,
    val testDigest: String,
)

/**
 * The only Fonebrew write capability in the core contract. It can propose a patch for an
 * already verified finding; it cannot originate findings, record proof, approve, or apply.
 */
class FonebrewProposalGateway(
    private val busRoot: Path,
    private val candidateStore: CandidateStore,
) {
    fun propose(request: FonebrewProposal, now: Instant = Instant.now()): CandidateRecord {
        val ready = BusReader(busRoot).read(request.sourceCommit) as? BusState.Ready
            ?: error("verified audit evidence is unavailable")
        val run = requireNotNull(ready.run) { "audit bus has no completed run" }
        val finding = ready.findings.singleOrNull { it.fingerprint == request.findingFingerprint }
            ?: error("proposal does not reference exactly one verified finding")
        val provingId = provingId(finding)
        val identity = CandidateIdentity.create(
            sourceRepo = run.sourceRepo,
            sourceCommit = run.sourceCommit,
            findingFingerprint = finding.fingerprint,
            provingId = provingId,
            patchDigest = request.patchDigest,
            testDigest = request.testDigest,
        )
        val proposed = CandidateLedger.create(identity, "system:assay", now)
            .propose(1, "system:fonebrew", now)
        candidateStore.create(proposed)
        return proposed
    }

    private fun provingId(finding: Finding): String {
        val slug = finding.ruleId.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "rule" }
        return "pt1-${finding.scanner.wireName}-$slug-${finding.fingerprint.take(16)}"
    }
}

object ConstellationProjection {
    fun orrery(snapshot: AssayConsoleSnapshot): OrreryAssayStatus = OrreryAssayStatus(
        generatedAt = snapshot.generatedAt,
        health = when (snapshot.availability) {
            ConsoleAvailability.NOT_CONFIGURED, ConsoleAvailability.UNAVAILABLE -> OrreryHealth.UNKNOWN
            ConsoleAvailability.INVALID -> OrreryHealth.BLOCKED
            ConsoleAvailability.READY -> if (snapshot.unresolvedFindings == 0) OrreryHealth.HEALTHY else OrreryHealth.DEGRADED
        },
        sourceRepo = snapshot.sourceRepo,
        sourceCommit = snapshot.sourceCommit,
        runId = snapshot.runId,
        findingCount = snapshot.findings.size,
        unresolvedFindingCount = snapshot.unresolvedFindings,
        candidateCount = snapshot.candidates.size,
        reason = snapshot.reason,
    )
}

/** Optional ASOM integration is deliberately read-only and untrusted. */
data class AsomExplanationRequest(
    val finding: ConsoleFinding,
    val evidenceRunId: String,
)

data class AsomExplanation(
    val text: String,
    val modelId: String,
    val generatedAt: Instant,
) {
    init {
        require(text.isNotBlank() && text.length <= 32_768) { "invalid ASOM explanation" }
        require(modelId.matches(Regex("[A-Za-z0-9._:/-]{1,256}"))) { "invalid ASOM model id" }
    }
}

fun interface AsomExplanationProvider {
    fun explain(request: AsomExplanationRequest): AsomExplanation
}

object IntegrationCodec {
    fun encode(snapshot: AssayConsoleSnapshot): String = Json.stringify(
        Json.obj(
            "schemaVersion" to Json.str(snapshot.schemaVersion),
            "generatedAt" to Json.str(snapshot.generatedAt.toString()),
            "availability" to Json.str(snapshot.availability.name.lowercase()),
            "reason" to nullable(snapshot.reason),
            "sourceRepo" to nullable(snapshot.sourceRepo),
            "sourceCommit" to nullable(snapshot.sourceCommit),
            "runId" to nullable(snapshot.runId),
            "findings" to Json.arr(snapshot.findings.map { finding ->
                Json.obj(
                    "fingerprint" to Json.str(finding.fingerprint),
                    "scanner" to Json.str(finding.scanner),
                    "ruleId" to Json.str(finding.ruleId),
                    "severity" to Json.str(finding.severity),
                    "message" to Json.str(finding.message),
                    "file" to Json.str(finding.file),
                    "startLine" to Json.num(finding.startLine),
                    "candidates" to Json.arr(finding.candidates.map(Json::str)),
                )
            }),
            "candidates" to Json.arr(snapshot.candidates.map { candidate ->
                Json.obj(
                    "candidateId" to Json.str(candidate.candidateId),
                    "findingFingerprint" to Json.str(candidate.findingFingerprint),
                    "lifecycle" to Json.str(candidate.lifecycle),
                    "revision" to Json.num(candidate.revision),
                    "fixBranch" to Json.str(candidate.fixBranch),
                    "humanApproved" to Json.bool(candidate.humanApproved),
                )
            }),
        ),
        pretty = true,
    ) + "\n"

    fun encode(status: OrreryAssayStatus): String = Json.stringify(
        Json.obj(
            "schemaVersion" to Json.str(status.schemaVersion),
            "generatedAt" to Json.str(status.generatedAt.toString()),
            "health" to Json.str(status.health.name.lowercase()),
            "sourceRepo" to nullable(status.sourceRepo),
            "sourceCommit" to nullable(status.sourceCommit),
            "runId" to nullable(status.runId),
            "findingCount" to Json.num(status.findingCount),
            "unresolvedFindingCount" to Json.num(status.unresolvedFindingCount),
            "candidateCount" to Json.num(status.candidateCount),
            "reason" to nullable(status.reason),
        ),
        pretty = true,
    ) + "\n"

    private fun nullable(value: String?): JsonValue = value?.let(Json::str) ?: Json.nullValue
}

object IntegrationCli {
    private val commands = setOf("console-snapshot", "orrery-status", "fonebrew-propose")
    fun handles(command: String?): Boolean = command in commands

    fun run(args: Array<String>) {
        val options = Options(args.drop(1))
        when (args.first()) {
            "console-snapshot", "orrery-status" -> {
                val snapshot = AssayConsoleReader(
                    busRoot = options.optional("--bus")?.let(Path::of),
                    candidateRoot = options.optional("--candidates")?.let(Path::of),
                ).snapshot(options.optional("--expected-source-commit"))
                val output = if (args.first() == "console-snapshot") {
                    IntegrationCodec.encode(snapshot)
                } else {
                    IntegrationCodec.encode(ConstellationProjection.orrery(snapshot))
                }
                writeOrPrint(options.optional("--output"), output)
            }
            "fonebrew-propose" -> {
                val record = FonebrewProposalGateway(
                    Path.of(options.required("--bus")),
                    CandidateStore(Path.of(options.required("--candidates"))),
                ).propose(
                    FonebrewProposal(
                        sourceCommit = options.required("--source-commit"),
                        findingFingerprint = options.required("--finding-fingerprint"),
                        patchDigest = options.required("--patch-digest"),
                        testDigest = options.required("--test-digest"),
                    ),
                )
                print(CandidateCodec.encode(record))
            }
        }
    }

    private fun writeOrPrint(output: String?, content: String) {
        if (output == null) {
            print(content)
            return
        }
        val path = Path.of(output).toAbsolutePath().normalize()
        Files.createDirectories(path.parent)
        require(!Files.isSymbolicLink(path)) { "integration output must not be a symlink" }
        Files.writeString(path, content)
    }

    private class Options(args: List<String>) {
        private val values: Map<String, String>
        init {
            val parsed = linkedMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val name = args[index]
                require(name.startsWith("--")) { "unexpected argument '$name'" }
                require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $name" }
                require(name !in parsed) { "duplicate option $name" }
                parsed[name] = args[index + 1]
                index += 2
            }
            values = parsed
        }
        fun required(name: String): String = values[name] ?: error("missing $name")
        fun optional(name: String): String? = values[name]
    }
}
