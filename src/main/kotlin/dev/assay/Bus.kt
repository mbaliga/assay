package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink

class BusWriter(private val root: Path) {
    fun publish(sourceRepo: String, sourceCommit: String, findings: List<Finding>, now: Instant = Instant.now()): RunRecord {
        require(sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "source commit must be full lowercase SHA-1" }
        Files.createDirectories(root)
        val runId = now.toString().replace("-", "").replace(":", "").replace(".", "") + "-" + sourceCommit.take(7)
        val stage = root.resolve(".stage-$runId")
        val runDir = root.resolve("runs").resolve(runId)
        require(!runDir.exists()) { "run already exists" }
        Files.createDirectories(stage)
        val sarif = Redaction.sanitize(Sarif.encode(findings))
        check(!Redaction.containsSecret(sarif)) { "outgoing bus contains a secret" }
        Files.writeString(stage.resolve("findings.sarif"), sarif)
        val findingsDigest = Fingerprints.sha256(Files.readAllBytes(stage.resolve("findings.sarif")))
        val manifest = "findings.sarif $findingsDigest ${Files.size(stage.resolve("findings.sarif"))}\n"
        Files.writeString(stage.resolve("manifest.sha256"), manifest)
        val manifestDigest = Fingerprints.sha256(manifest.toByteArray())
        val runJson = """{"schemaVersion":"1.1.0","runId":"$runId","sourceRepo":"${escape(sourceRepo)}","sourceCommit":"$sourceCommit","createdAt":"$now","status":"complete","findingsRef":"runs/$runId/findings.sarif","manifestRef":"runs/$runId/manifest.sha256","manifestDigest":"$manifestDigest"}"""
        Files.writeString(stage.resolve("run.json"), runJson)
        Files.createDirectories(runDir.parent)
        Files.move(stage, runDir, StandardCopyOption.ATOMIC_MOVE)
        val index = """{"schemaVersion":"1.1.0","producer":{"app":"Assay","runnerContract":"1.1.0"},"sourceRepo":"${escape(sourceRepo)}","latestComplete":"$runId","activeRuns":[],"runs":[{"runId":"$runId","sourceCommit":"$sourceCommit","createdAt":"$now","status":"complete","path":"runs/$runId","findingsRef":"runs/$runId/findings.sarif","manifestRef":"runs/$runId/manifest.sha256","manifestDigest":"$manifestDigest"}]}"""
        atomicWrite(root.resolve("index.json"), index)
        return RunRecord(runId, sourceRepo, sourceCommit, now, RunState.COMPLETE, "runs/$runId/findings.sarif", "runs/$runId/manifest.sha256", manifestDigest, RunSummary(sev1 = findings.size))
    }

    private fun atomicWrite(path: Path, text: String) {
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, text)
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

class BusReader(private val root: Path?) {
    fun read(): BusState {
        if (root == null) return BusState.NotConfigured
        return try {
            if (!root.exists()) return BusState.Unavailable("bus root absent")
            val index = safeResolve("index.json")
            val text = Files.readString(index)
            if (!text.contains("\"schemaVersion\":\"1.1.0\"")) return BusState.Invalid("unsupported schema")
            val runId = Regex("\"latestComplete\":\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: return BusState.Ready(null, emptyList())
            if (!runId.matches(Regex("[0-9TZ]+-[0-9a-f]{7}"))) return BusState.Invalid("invalid run id")
            val runPath = safeResolve("runs/$runId")
            if (runPath.isSymbolicLink()) return BusState.Invalid("symlinked run")
            val manifestPath = safeResolve("runs/$runId/manifest.sha256")
            val manifest = Files.readString(manifestPath)
            val expectedManifest = Regex("\"manifestDigest\":\"([0-9a-f]{64})\"").find(text)?.groupValues?.get(1)
                ?: return BusState.Invalid("manifest digest absent")
            if (Fingerprints.sha256(manifest.toByteArray()) != expectedManifest) return BusState.Invalid("manifest tampered")
            val parts = manifest.trim().split(' ')
            if (parts.size != 3 || parts[0] != "findings.sarif") return BusState.Invalid("invalid manifest")
            val findingsPath = safeResolve("runs/$runId/findings.sarif")
            if (Fingerprints.sha256(Files.readAllBytes(findingsPath)) != parts[1]) return BusState.Invalid("artifact tampered")
            BusState.Ready(null, emptyList())
        } catch (e: Exception) {
            BusState.Invalid(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun safeResolve(relative: String): Path {
        require(!relative.startsWith('/') && !relative.contains("..")) { "unsafe path" }
        val resolved = root!!.resolve(relative).normalize()
        require(resolved.startsWith(root.normalize())) { "path escapes bus" }
        var cursor = resolved
        while (cursor != root && cursor.parent != null) {
            require(!Files.isSymbolicLink(cursor)) { "symlink rejected" }
            cursor = cursor.parent
        }
        return resolved
    }
}
