package dev.assay

import java.nio.file.Files
import java.time.Instant

fun main() {
    val workspace = Files.createTempDirectory("assay-integrations-")
    val bus = workspace.resolve("bus")
    val candidates = workspace.resolve("candidates")
    val source = "1".repeat(40)
    val finding = Finding(
        scanner = Scanner.SEMGREP,
        ruleId = "android.exported-component",
        message = "Exported component is not permission protected",
        location = Location("app/src/main/AndroidManifest.xml", 14),
        context = "exported activity without permission",
        level = SarifLevel.ERROR,
        securitySeverity = 8.0,
    )
    BusWriter(bus).publish(
        sourceRepo = "mbaliga/example",
        sourceCommit = source,
        findings = listOf(finding),
        now = Instant.parse("2026-08-03T13:00:00Z"),
    )

    val proposed = FonebrewProposalGateway(bus, CandidateStore(candidates)).propose(
        FonebrewProposal(
            sourceCommit = source,
            findingFingerprint = finding.fingerprint,
            patchDigest = "2".repeat(64),
            testDigest = "3".repeat(64),
        ),
        now = Instant.parse("2026-08-03T13:01:00Z"),
    )
    check(proposed.lifecycle == Lifecycle.PROPOSED)
    check(proposed.events.last().actor == "system:fonebrew")
    check(proposed.identity.provingId == "pt1-semgrep-android-exported-component-${finding.fingerprint.take(16)}")
    println("PASS Fonebrew can propose only against a verified deterministic finding")

    check(runCatching {
        FonebrewProposalGateway(bus, CandidateStore(candidates)).propose(
            FonebrewProposal(source, "f".repeat(64), "4".repeat(64), "5".repeat(64)),
        )
    }.isFailure)
    println("PASS Fonebrew cannot originate a finding")

    val snapshot = AssayConsoleReader(bus, candidates).snapshot(
        expectedSourceCommit = source,
        now = Instant.parse("2026-08-03T13:02:00Z"),
    )
    check(snapshot.availability == ConsoleAvailability.READY)
    check(snapshot.findings.size == 1)
    check(snapshot.candidates.size == 1)
    check(snapshot.findings.single().candidates == listOf(proposed.identity.candidateId))
    check(snapshot.unresolvedFindings == 1)
    val encoded = IntegrationCodec.encode(snapshot)
    check(encoded.contains(proposed.identity.candidateId))
    check(!Redaction.containsSecret(encoded))
    println("PASS console snapshot derives only verified bus and candidate state")

    val orrery = ConstellationProjection.orrery(snapshot)
    check(orrery.health == OrreryHealth.DEGRADED)
    check(orrery.findingCount == 1 && orrery.unresolvedFindingCount == 1)
    check(IntegrationCodec.encode(orrery).contains("\"health\": \"degraded\""))
    println("PASS Orrery projection reports degraded health without mutation authority")

    val wrongCommit = AssayConsoleReader(bus, candidates).snapshot("9".repeat(40))
    check(wrongCommit.availability == ConsoleAvailability.INVALID)
    check(wrongCommit.findings.isEmpty())
    check(wrongCommit.reason != null)
    println("PASS console fails closed for stale source evidence")

    val absent = AssayConsoleReader(null, null).snapshot(null)
    check(absent.availability == ConsoleAvailability.NOT_CONFIGURED)
    check(absent.findings.isEmpty())
    println("PASS unconfigured integrations remain distinct from zero findings")

    val explanation = AsomExplanation(
        text = "This explanation is advisory and cannot alter the finding.",
        modelId = "asom/local-model",
        generatedAt = Instant.parse("2026-08-03T13:03:00Z"),
    )
    check(explanation.text.isNotBlank())
    println("PASS optional ASOM output is represented as read-only advisory text")
}
