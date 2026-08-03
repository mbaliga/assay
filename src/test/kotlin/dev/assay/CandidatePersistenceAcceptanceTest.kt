package dev.assay

import java.nio.file.Files
import java.time.Instant

fun main() {
    val source = "1".repeat(40)
    val finding = "2".repeat(64)
    val patch = "3".repeat(64)
    val test = "4".repeat(64)
    val identity = CandidateIdentity.create(
        sourceRepo = "mbaliga/example",
        sourceCommit = source,
        findingFingerprint = finding,
        provingId = "prove-${finding.take(16)}",
        patchDigest = patch,
        testDigest = test,
    )
    val created = CandidateLedger.create(identity, "system:assay", Instant.parse("2026-08-03T00:00:00Z")).snapshot()
    val root = Files.createTempDirectory("assay-candidates-")
    val store = CandidateStore(root)

    val path = store.create(created)
    check(Files.exists(path))
    check(store.read(identity.candidateId) == created)
    println("PASS candidate codec roundtrip and atomic create")

    val proposed = CandidateLedger.create(identity, "system:assay", Instant.parse("2026-08-03T00:00:00Z"))
        .propose(1, "human:reviewer", Instant.parse("2026-08-03T00:01:00Z"))
    store.update(proposed, expectedRevision = 1)
    check(store.read(identity.candidateId).revision == 2)
    println("PASS optimistic persistent update")

    val stale = runCatching { store.update(proposed, expectedRevision = 1) }
    check(stale.isFailure)
    println("PASS stale persistent update rejected")

    val tampered = Files.readString(path).replace("\"revision\": 2", "\"revision\": 7")
    Files.writeString(path, tampered)
    val rejected = runCatching { store.read(identity.candidateId) }
    check(rejected.isFailure)
    println("PASS tampered candidate rejected")

    val symlinkRoot = Files.createTempDirectory("assay-candidate-links-")
    val linked = symlinkRoot.resolve("${identity.candidateId}.json")
    val symlinkResult = runCatching { Files.createSymbolicLink(linked, path) }
    if (symlinkResult.isSuccess) {
        val linkStore = CandidateStore(symlinkRoot)
        check(runCatching { linkStore.read(identity.candidateId) }.isFailure)
        println("PASS candidate symlink rejected")
    } else {
        println("SKIP candidate symlink test unsupported")
    }
}
