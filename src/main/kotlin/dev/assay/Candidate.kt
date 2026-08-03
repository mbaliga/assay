package dev.assay

import java.time.Instant

/** Immutable identity for a proposed remediation. */
data class CandidateIdentity(
    val candidateId: String,
    val sourceRepo: String,
    val sourceCommit: String,
    val findingFingerprint: String,
    val provingId: String,
    val patchDigest: String,
    val testDigest: String,
    val fixBranch: String,
) {
    init {
        require(candidateId.matches(CANDIDATE_ID)) { "invalid candidate id" }
        require(sourceRepo.matches(REPOSITORY)) { "invalid source repository" }
        require(sourceCommit.matches(SHA1)) { "invalid source commit" }
        require(findingFingerprint.matches(SHA256)) { "invalid finding fingerprint" }
        require(provingId.matches(ContractValidator.PROVING_ID)) { "invalid proving id" }
        require(provingId.endsWith("-${findingFingerprint.take(16)}")) { "proving id is not bound to finding" }
        require(patchDigest.matches(SHA256) && patchDigest != EMPTY_SHA256) { "invalid or empty patch digest" }
        require(testDigest.matches(SHA256) && testDigest != EMPTY_SHA256) { "invalid or empty test digest" }
        require(fixBranch == "assay/fix/$candidateId") { "candidate must use its dedicated fix branch" }
        require(candidateId == deriveId(sourceRepo, sourceCommit, findingFingerprint, patchDigest, testDigest)) {
            "candidate id does not match immutable identity"
        }
    }

    companion object {
        private val SHA1 = Regex("[0-9a-f]{40}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val CANDIDATE_ID = Regex("cand1-[0-9a-f]{20}")
        private val REPOSITORY = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        private val EMPTY_SHA256 = Fingerprints.sha256(ByteArray(0))

        fun create(
            sourceRepo: String,
            sourceCommit: String,
            findingFingerprint: String,
            provingId: String,
            patchDigest: String,
            testDigest: String,
        ): CandidateIdentity {
            val id = deriveId(sourceRepo, sourceCommit, findingFingerprint, patchDigest, testDigest)
            return CandidateIdentity(
                candidateId = id,
                sourceRepo = sourceRepo,
                sourceCommit = sourceCommit,
                findingFingerprint = findingFingerprint,
                provingId = provingId,
                patchDigest = patchDigest,
                testDigest = testDigest,
                fixBranch = "assay/fix/$id",
            )
        }

        private fun deriveId(
            sourceRepo: String,
            sourceCommit: String,
            findingFingerprint: String,
            patchDigest: String,
            testDigest: String,
        ): String {
            val canonical = listOf(
                "assay-candidate-v1",
                sourceRepo,
                sourceCommit,
                findingFingerprint,
                patchDigest,
                testDigest,
            ).joinToString("\u001f")
            return "cand1-${Fingerprints.sha256(canonical.toByteArray()).take(20)}"
        }
    }
}

data class CandidateEvent(
    val sequence: Int,
    val state: Lifecycle,
    val occurredAt: Instant,
    val actor: String,
    val evidenceDigest: String?,
    val previousEventDigest: String,
    val eventDigest: String,
)

data class CandidateApproval(
    val actor: String,
    val approvedAt: Instant,
    val proofDigest: String,
    val candidateRevision: Int,
    val approvalDigest: String,
)

data class CandidateApplication(
    val actor: String,
    val appliedAt: Instant,
    val sourceCommit: String,
    val fixCommit: String,
    val fixBranch: String,
    val approvalDigest: String,
)

data class CandidateRecord(
    val identity: CandidateIdentity,
    val revision: Int,
    val lifecycle: Lifecycle,
    val events: List<CandidateEvent>,
    val proofDigest: String? = null,
    val approval: CandidateApproval? = null,
    val application: CandidateApplication? = null,
)

/** Revision-safe, append-only candidate state machine. */
class CandidateLedger private constructor(private var record: CandidateRecord) {
    fun snapshot(): CandidateRecord = record

    fun propose(expectedRevision: Int, actor: String, at: Instant): CandidateRecord =
        transition(expectedRevision, Lifecycle.PROPOSED, actor, at, null)

    fun recordProof(
        expectedRevision: Int,
        actor: String,
        at: Instant,
        proofDigest: String,
        proofPassed: Boolean,
    ): CandidateRecord {
        require(proofDigest.matches(SHA256)) { "invalid proof digest" }
        val next = if (proofPassed) Lifecycle.PROOF_PASSED else Lifecycle.PROOF_FAILED
        transition(expectedRevision, next, actor, at, proofDigest)
        record = record.copy(proofDigest = proofDigest)
        validate(record)
        return record
    }

    fun approve(expectedRevision: Int, actor: String, at: Instant): CandidateRecord {
        checkRevision(expectedRevision)
        require(actor.matches(HUMAN_ACTOR)) { "approval requires a human actor" }
        require(record.lifecycle == Lifecycle.PROOF_PASSED) { "candidate is not proof-passed" }
        val proofDigest = requireNotNull(record.proofDigest) { "candidate lacks proof" }
        val approvalDigest = approvalDigest(record.identity.candidateId, actor, at, proofDigest, expectedRevision + 1)
        transition(expectedRevision, Lifecycle.APPROVED, actor, at, proofDigest)
        record = record.copy(
            approval = CandidateApproval(actor, at, proofDigest, record.revision, approvalDigest),
        )
        validate(record)
        return record
    }

    fun apply(
        expectedRevision: Int,
        actor: String,
        at: Instant,
        sourceCommit: String,
        fixCommit: String,
        fixBranch: String,
    ): CandidateRecord {
        checkRevision(expectedRevision)
        require(record.lifecycle == Lifecycle.APPROVED) { "candidate is not approved" }
        require(sourceCommit == record.identity.sourceCommit) { "application source commit drifted" }
        require(fixCommit.matches(SHA1) && fixCommit != sourceCommit) { "invalid fix commit" }
        require(fixBranch == record.identity.fixBranch) { "application is not on dedicated fix branch" }
        val approval = requireNotNull(record.approval)
        transition(
            expectedRevision,
            Lifecycle.APPLIED,
            actor,
            at,
            Fingerprints.sha256(fixCommit.toByteArray()),
        )
        record = record.copy(
            application = CandidateApplication(actor, at, sourceCommit, fixCommit, fixBranch, approval.approvalDigest),
        )
        validate(record)
        return record
    }

    fun markStale(expectedRevision: Int, actor: String, at: Instant, currentSourceCommit: String): CandidateRecord {
        require(currentSourceCommit.matches(SHA1)) { "invalid current source commit" }
        require(currentSourceCommit != record.identity.sourceCommit) { "source has not drifted" }
        return transition(
            expectedRevision,
            Lifecycle.STALE,
            actor,
            at,
            Fingerprints.sha256(currentSourceCommit.toByteArray()),
        )
    }

    private fun transition(
        expectedRevision: Int,
        next: Lifecycle,
        actor: String,
        at: Instant,
        evidenceDigest: String?,
    ): CandidateRecord {
        checkRevision(expectedRevision)
        require(actor.matches(ACTOR)) { "invalid candidate actor" }
        val machine = LifecycleMachine(record.lifecycle)
        machine.move(next)
        val sequence = record.revision + 1
        val previous = record.events.last().eventDigest
        val digest = eventDigest(sequence, next, at, actor, evidenceDigest, previous)
        record = record.copy(
            revision = sequence,
            lifecycle = next,
            events = record.events + CandidateEvent(sequence, next, at, actor, evidenceDigest, previous, digest),
        )
        validate(record)
        return record
    }

    private fun checkRevision(expectedRevision: Int) {
        require(expectedRevision == record.revision) {
            "stale candidate revision: expected $expectedRevision, actual ${record.revision}"
        }
    }

    companion object {
        private val SHA1 = Regex("[0-9a-f]{40}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val ACTOR = Regex("(system|human):[A-Za-z0-9._@+-]{1,128}")
        private val HUMAN_ACTOR = Regex("human:[A-Za-z0-9._@+-]{1,128}")
        private val GENESIS = "0".repeat(64)

        fun create(identity: CandidateIdentity, actor: String, at: Instant): CandidateLedger {
            require(actor.matches(ACTOR)) { "invalid candidate actor" }
            val digest = eventDigest(1, Lifecycle.DETECTED, at, actor, null, GENESIS)
            return CandidateLedger(
                CandidateRecord(
                    identity = identity,
                    revision = 1,
                    lifecycle = Lifecycle.DETECTED,
                    events = listOf(CandidateEvent(1, Lifecycle.DETECTED, at, actor, null, GENESIS, digest)),
                ),
            ).also { validate(it.record) }
        }

        fun validate(record: CandidateRecord) {
            require(record.revision == record.events.size) { "candidate revision/event count mismatch" }
            require(record.events.isNotEmpty()) { "candidate has no events" }
            var previous = GENESIS
            record.events.forEachIndexed { index, event ->
                require(event.sequence == index + 1) { "candidate event sequence gap" }
                require(event.previousEventDigest == previous) { "candidate event chain mismatch" }
                require(event.eventDigest == eventDigest(
                    event.sequence,
                    event.state,
                    event.occurredAt,
                    event.actor,
                    event.evidenceDigest,
                    previous,
                )) { "candidate event digest mismatch" }
                previous = event.eventDigest
            }
            require(record.lifecycle == record.events.last().state) { "candidate lifecycle/event mismatch" }
            record.approval?.let { approval ->
                require(approval.actor.matches(HUMAN_ACTOR)) { "non-human approval" }
                require(record.proofDigest == approval.proofDigest) { "approval proof mismatch" }
                require(approval.approvalDigest == approvalDigest(
                    record.identity.candidateId,
                    approval.actor,
                    approval.approvedAt,
                    approval.proofDigest,
                    approval.candidateRevision,
                )) { "approval digest mismatch" }
            }
            record.application?.let { application ->
                val approval = requireNotNull(record.approval) { "application lacks approval" }
                require(application.sourceCommit == record.identity.sourceCommit) { "application source mismatch" }
                require(application.fixBranch == record.identity.fixBranch) { "application branch mismatch" }
                require(application.approvalDigest == approval.approvalDigest) { "application approval mismatch" }
            }
        }

        private fun eventDigest(
            sequence: Int,
            state: Lifecycle,
            at: Instant,
            actor: String,
            evidenceDigest: String?,
            previous: String,
        ): String = Fingerprints.sha256(
            listOf(
                "assay-candidate-event-v1",
                sequence.toString(),
                state.name.lowercase(),
                at.toString(),
                actor,
                evidenceDigest.orEmpty(),
                previous,
            ).joinToString("\u001f").toByteArray(),
        )

        private fun approvalDigest(
            candidateId: String,
            actor: String,
            at: Instant,
            proofDigest: String,
            revision: Int,
        ): String = Fingerprints.sha256(
            listOf(
                "assay-candidate-approval-v1",
                candidateId,
                actor,
                at.toString(),
                proofDigest,
                revision.toString(),
            ).joinToString("\u001f").toByteArray(),
        )
    }
}
