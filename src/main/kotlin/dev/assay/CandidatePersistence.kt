package dev.assay

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

object CandidateCodec {
    private const val SCHEMA_VERSION = "1.1.0"

    fun encode(record: CandidateRecord): String {
        CandidateLedger.validate(record)
        return Json.stringify(
            Json.obj(
                "schemaVersion" to Json.str(SCHEMA_VERSION),
                "identity" to encodeIdentity(record.identity),
                "revision" to Json.num(record.revision),
                "lifecycle" to Json.str(record.lifecycle.name.lowercase()),
                "events" to Json.arr(record.events.map(::encodeEvent)),
                "proofDigest" to nullableString(record.proofDigest),
                "approval" to (record.approval?.let(::encodeApproval) ?: Json.nullValue),
                "application" to (record.application?.let(::encodeApplication) ?: Json.nullValue),
            ),
            pretty = true,
        ) + "\n"
    }

    fun decode(text: String): CandidateRecord {
        val root = Json.parse(text).requireObject()
        requireExactKeys(
            root,
            setOf("schemaVersion", "identity", "revision", "lifecycle", "events", "proofDigest", "approval", "application"),
            "$",
        )
        require(root.string("schemaVersion") == SCHEMA_VERSION) { "unsupported candidate schema version" }
        val identity = decodeIdentity(root.required("identity").requireObject("$.identity"))
        val revision = integer(root.required("revision"), "$.revision")
        val lifecycle = lifecycle(root.string("lifecycle"))
        val events = root.required("events").requireArray("$.events").values.mapIndexed { index, value ->
            decodeEvent(value.requireObject("$.events[$index]"), index)
        }
        val proofDigest = nullableString(root.required("proofDigest"), "$.proofDigest")
        val approval = nullableObject(root.required("approval"), "$.approval")?.let(::decodeApproval)
        val application = nullableObject(root.required("application"), "$.application")?.let(::decodeApplication)
        return CandidateRecord(identity, revision, lifecycle, events, proofDigest, approval, application)
            .also(CandidateLedger::validate)
    }

    private fun encodeIdentity(value: CandidateIdentity): JsonValue.Obj = Json.obj(
        "candidateId" to Json.str(value.candidateId),
        "sourceRepo" to Json.str(value.sourceRepo),
        "sourceCommit" to Json.str(value.sourceCommit),
        "findingFingerprint" to Json.str(value.findingFingerprint),
        "provingId" to Json.str(value.provingId),
        "patchDigest" to Json.str(value.patchDigest),
        "testDigest" to Json.str(value.testDigest),
        "fixBranch" to Json.str(value.fixBranch),
    )

    private fun decodeIdentity(value: JsonValue.Obj): CandidateIdentity {
        requireExactKeys(value, setOf("candidateId", "sourceRepo", "sourceCommit", "findingFingerprint", "provingId", "patchDigest", "testDigest", "fixBranch"), "$.identity")
        return CandidateIdentity(
            value.string("candidateId", "$.identity"),
            value.string("sourceRepo", "$.identity"),
            value.string("sourceCommit", "$.identity"),
            value.string("findingFingerprint", "$.identity"),
            value.string("provingId", "$.identity"),
            value.string("patchDigest", "$.identity"),
            value.string("testDigest", "$.identity"),
            value.string("fixBranch", "$.identity"),
        )
    }

    private fun encodeEvent(value: CandidateEvent): JsonValue.Obj = Json.obj(
        "sequence" to Json.num(value.sequence),
        "state" to Json.str(value.state.name.lowercase()),
        "occurredAt" to Json.str(value.occurredAt.toString()),
        "actor" to Json.str(value.actor),
        "evidenceDigest" to nullableString(value.evidenceDigest),
        "previousEventDigest" to Json.str(value.previousEventDigest),
        "eventDigest" to Json.str(value.eventDigest),
    )

    private fun decodeEvent(value: JsonValue.Obj, index: Int): CandidateEvent {
        val path = "$.events[$index]"
        requireExactKeys(value, setOf("sequence", "state", "occurredAt", "actor", "evidenceDigest", "previousEventDigest", "eventDigest"), path)
        return CandidateEvent(
            integer(value.required("sequence", path), "$path.sequence"),
            lifecycle(value.string("state", path)),
            Instant.parse(value.string("occurredAt", path)),
            value.string("actor", path),
            nullableString(value.required("evidenceDigest", path), "$path.evidenceDigest"),
            value.string("previousEventDigest", path),
            value.string("eventDigest", path),
        )
    }

    private fun encodeApproval(value: CandidateApproval): JsonValue.Obj = Json.obj(
        "actor" to Json.str(value.actor),
        "approvedAt" to Json.str(value.approvedAt.toString()),
        "proofDigest" to Json.str(value.proofDigest),
        "candidateRevision" to Json.num(value.candidateRevision),
        "approvalDigest" to Json.str(value.approvalDigest),
    )

    private fun decodeApproval(value: JsonValue.Obj): CandidateApproval {
        requireExactKeys(value, setOf("actor", "approvedAt", "proofDigest", "candidateRevision", "approvalDigest"), "$.approval")
        return CandidateApproval(
            value.string("actor", "$.approval"),
            Instant.parse(value.string("approvedAt", "$.approval")),
            value.string("proofDigest", "$.approval"),
            integer(value.required("candidateRevision", "$.approval"), "$.approval.candidateRevision"),
            value.string("approvalDigest", "$.approval"),
        )
    }

    private fun encodeApplication(value: CandidateApplication): JsonValue.Obj = Json.obj(
        "actor" to Json.str(value.actor),
        "appliedAt" to Json.str(value.appliedAt.toString()),
        "sourceCommit" to Json.str(value.sourceCommit),
        "fixCommit" to Json.str(value.fixCommit),
        "fixBranch" to Json.str(value.fixBranch),
        "approvalDigest" to Json.str(value.approvalDigest),
    )

    private fun decodeApplication(value: JsonValue.Obj): CandidateApplication {
        requireExactKeys(value, setOf("actor", "appliedAt", "sourceCommit", "fixCommit", "fixBranch", "approvalDigest"), "$.application")
        return CandidateApplication(
            value.string("actor", "$.application"),
            Instant.parse(value.string("appliedAt", "$.application")),
            value.string("sourceCommit", "$.application"),
            value.string("fixCommit", "$.application"),
            value.string("fixBranch", "$.application"),
            value.string("approvalDigest", "$.application"),
        )
    }

    private fun lifecycle(value: String): Lifecycle = Lifecycle.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: error("invalid lifecycle '$value'")

    private fun integer(value: JsonValue, path: String): Int {
        val number = (value as? JsonValue.Num)?.value ?: error("expected integer at $path")
        require(number.isFinite() && number % 1.0 == 0.0 && number in 0.0..Int.MAX_VALUE.toDouble()) { "invalid integer at $path" }
        return number.toInt()
    }

    private fun nullableString(value: String?): JsonValue = value?.let(Json::str) ?: Json.nullValue
    private fun nullableString(value: JsonValue, path: String): String? = when (value) {
        JsonValue.Null -> null
        is JsonValue.Str -> value.value
        else -> error("expected string or null at $path")
    }
    private fun nullableObject(value: JsonValue, path: String): JsonValue.Obj? = when (value) {
        JsonValue.Null -> null
        is JsonValue.Obj -> value
        else -> error("expected object or null at $path")
    }
    private fun requireExactKeys(value: JsonValue.Obj, expected: Set<String>, path: String) {
        val actual = value.values.keys
        require(actual == expected) { "unexpected keys at $path: expected $expected, got $actual" }
    }
}

class CandidateStore(private val root: Path, private val maxBytes: Long = 1_048_576) {
    init {
        require(maxBytes in 1..16_777_216) { "invalid candidate size limit" }
    }

    fun create(record: CandidateRecord): Path {
        val target = path(record.identity.candidateId)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "candidate already exists" }
        writeAtomic(target, CandidateCodec.encode(record))
        return target
    }

    fun read(candidateId: String): CandidateRecord {
        val target = path(candidateId)
        require(Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "candidate does not exist" }
        require(!Files.isSymbolicLink(target)) { "candidate file must not be a symlink" }
        require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) { "candidate path is not a regular file" }
        val size = Files.size(target)
        require(size in 1..maxBytes) { "candidate file size rejected" }
        return CandidateCodec.decode(Files.readString(target))
    }

    fun update(record: CandidateRecord, expectedRevision: Int): Path {
        val current = read(record.identity.candidateId)
        require(current.revision == expectedRevision) { "stale candidate revision: expected $expectedRevision, actual ${current.revision}" }
        require(record.revision > expectedRevision) { "candidate update did not advance revision" }
        val target = path(record.identity.candidateId)
        writeAtomic(target, CandidateCodec.encode(record))
        return target
    }

    private fun path(candidateId: String): Path {
        require(candidateId.matches(Regex("cand1-[0-9a-f]{20}"))) { "invalid candidate id" }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val target = normalizedRoot.resolve("$candidateId.json").normalize()
        require(target.parent == normalizedRoot) { "candidate path escaped store" }
        return target
    }

    private fun writeAtomic(target: Path, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= maxBytes) { "candidate exceeds size limit" }
        Files.createDirectories(target.parent)
        require(!Files.isSymbolicLink(target.parent)) { "candidate store directory must not be a symlink" }
        val temporary = Files.createTempFile(target.parent, ".candidate-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                channel.write(java.nio.ByteBuffer.wrap(bytes))
                channel.force(true)
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            FileChannel.open(target.parent, StandardOpenOption.READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
