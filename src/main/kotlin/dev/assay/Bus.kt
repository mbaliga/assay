package dev.assay

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.io.path.exists

private const val CONTRACT_VERSION = "1.1.0"

data class BusLimits(
    val maxArtifactBytes: Long = 32L * 1024 * 1024,
    val maxRunBytes: Long = 96L * 1024 * 1024,
    val maxIndexRuns: Int = 50,
)

class BusWriter(
    private val root: Path,
    private val limits: BusLimits = BusLimits(),
) {
    fun publish(
        sourceRepo: String,
        sourceCommit: String,
        findings: List<Finding>,
        now: Instant = Instant.now(),
    ): RunRecord {
        val fixtureContent = Sarif.encode(findings)
        val fixtureArtifact = ScannerArtifact(
            scanner = Scanner.GITLEAKS,
            version = "fixture-1",
            executableDigest = Fingerprints.sha256("fixture-scanner".toByteArray()),
            configDigest = Fingerprints.sha256(ByteArray(0)),
            exitCode = if (findings.isEmpty()) 0 else 1,
            format = "sarif",
            content = fixtureContent,
            findings = findings,
        )
        return publish(ScanBundle(sourceRepo, sourceCommit, listOf(fixtureArtifact)), now)
    }

    fun publish(bundle: ScanBundle, now: Instant = Instant.now()): RunRecord {
        Files.createDirectories(root)
        rejectSymlink(root)
        val runId = RUN_ID_FORMAT.format(now) + "-" + bundle.sourceCommit.take(7)
        val runDir = safeResolve("runs/$runId")
        require(!runDir.exists()) { "run already exists" }
        val stage = safeResolve(".stage-$runId-${UUID.randomUUID()}")
        Files.createDirectory(stage)
        try {
            val artifacts = linkedMapOf<String, String>()
            artifacts["findings.sarif"] = Sarif.encode(bundle.findings)
            artifacts["status.json"] = statusJson(bundle.findings)
            bundle.scannerArtifacts.sortedBy { it.scanner.wireName }.forEach { scanner ->
                artifacts["scanners/${scanner.scanner.wireName}.${scanner.format}"] = scanner.content
            }
            artifacts["run.json"] = runJson(bundle, runId, now)

            var totalBytes = 0L
            artifacts.toSortedMap().forEach { (relative, raw) ->
                val safeText = Redaction.sanitize(raw)
                check(!Redaction.containsSecret(safeText)) { "outgoing artifact contains a secret: $relative" }
                val bytes = safeText.toByteArray(Charsets.UTF_8)
                require(bytes.size.toLong() <= limits.maxArtifactBytes) { "artifact exceeds size limit: $relative" }
                totalBytes += bytes.size
                require(totalBytes <= limits.maxRunBytes) { "run exceeds size limit" }
                val path = stageResolve(stage, relative)
                Files.createDirectories(path.parent)
                writeAndSync(path, bytes)
            }

            val manifestLines = artifacts.keys.sorted().joinToString(separator = "\n", postfix = "\n") { relative ->
                val path = stageResolve(stage, relative)
                "$relative ${Fingerprints.sha256(Files.readAllBytes(path))} ${Files.size(path)}"
            }
            val manifestBytes = manifestLines.toByteArray(Charsets.UTF_8)
            require(manifestBytes.size.toLong() <= limits.maxArtifactBytes)
            writeAndSync(stage.resolve("manifest.sha256"), manifestBytes)
            scanOutgoingStage(stage)
            syncDirectory(stage)

            Files.createDirectories(runDir.parent)
            Files.move(stage, runDir, StandardCopyOption.ATOMIC_MOVE)
            syncDirectory(runDir.parent)

            val manifestDigest = Fingerprints.sha256(manifestBytes)
            val summary = RunSummary.from(bundle.findings)
            val record = RunRecord(
                runId = runId,
                sourceRepo = bundle.sourceRepo,
                sourceCommit = bundle.sourceCommit,
                createdAt = now,
                status = RunState.COMPLETE,
                findingsRef = "runs/$runId/findings.sarif",
                manifestRef = "runs/$runId/manifest.sha256",
                manifestDigest = manifestDigest,
                summary = summary,
            )
            updateIndex(record)
            return record
        } catch (e: Exception) {
            deleteRecursively(stage)
            throw e
        }
    }

    private fun runJson(bundle: ScanBundle, runId: String, now: Instant): String {
        val scanners = bundle.scannerArtifacts.sortedBy { it.scanner.wireName }.map { artifact ->
            Json.obj(
                "name" to Json.str(artifact.scanner.wireName),
                "version" to Json.str(artifact.version),
                "executableDigest" to Json.str(artifact.executableDigest),
                "configDigest" to Json.str(artifact.configDigest),
                "exitCode" to Json.num(artifact.exitCode),
                "format" to Json.str(artifact.format),
                "artifactRef" to Json.str("runs/$runId/scanners/${artifact.scanner.wireName}.${artifact.format}"),
            )
        }
        return Json.stringify(
            Json.obj(
                "schemaVersion" to Json.str(CONTRACT_VERSION),
                "runId" to Json.str(runId),
                "sourceRepo" to Json.str(bundle.sourceRepo),
                "sourceCommit" to Json.str(bundle.sourceCommit),
                "createdAt" to Json.str(now.toString()),
                "status" to Json.str("complete"),
                "findingsRef" to Json.str("runs/$runId/findings.sarif"),
                "statusRef" to Json.str("runs/$runId/status.json"),
                "manifestRef" to Json.str("runs/$runId/manifest.sha256"),
                "severityMappingVersion" to Json.str(SeverityPolicy.VERSION),
                "scanners" to Json.arr(scanners),
            ),
            pretty = true,
        )
    }

    private fun statusJson(findings: List<Finding>): String {
        val records = findings.sortedBy { it.fingerprint }.map { finding ->
            Json.obj(
                "findingFingerprint" to Json.str(finding.fingerprint),
                "scanner" to Json.str(finding.scanner.wireName),
                "ruleId" to Json.str(finding.ruleId),
                "severity" to Json.str(finding.severity.name),
                "lifecycle" to Json.str("detected"),
                "provingId" to Json.nullValue,
            )
        }
        return Json.stringify(
            Json.obj(
                "schemaVersion" to Json.str(CONTRACT_VERSION),
                "findings" to Json.arr(records),
            ),
            pretty = true,
        )
    }

    private fun updateIndex(record: RunRecord) {
        val existingRuns = if (root.resolve("index.json").exists()) {
            val existing = Json.parse(Files.readString(root.resolve("index.json"))).requireObject()
            ContractValidator.validateIndex(existing)
            require(existing.string("sourceRepo") == record.sourceRepo) {
                "bus already belongs to a different source repository"
            }
            existing.arrayOrNull("runs")?.values.orEmpty()
        } else {
            emptyList()
        }
        val newEntry = runEntry(record)
        val retained = (listOf(newEntry) + existingRuns.filterNot {
            runCatching { it.requireObject().string("runId") == record.runId }.getOrDefault(false)
        }).take(limits.maxIndexRuns)
        val index = Json.stringify(
            Json.obj(
                "schemaVersion" to Json.str(CONTRACT_VERSION),
                "producer" to Json.obj(
                    "app" to Json.str("Assay"),
                    "runnerContract" to Json.str(CONTRACT_VERSION),
                ),
                "sourceRepo" to Json.str(record.sourceRepo),
                "latestComplete" to Json.str(record.runId),
                "activeRuns" to Json.arr(emptyList()),
                "runs" to Json.arr(retained),
            ),
            pretty = true,
        )
        atomicWriteAndSync(root.resolve("index.json"), index.toByteArray(Charsets.UTF_8))
    }

    private fun runEntry(record: RunRecord): JsonValue = Json.obj(
        "runId" to Json.str(record.runId),
        "sourceCommit" to Json.str(record.sourceCommit),
        "createdAt" to Json.str(record.createdAt.toString()),
        "status" to Json.str("complete"),
        "path" to Json.str("runs/${record.runId}"),
        "runRef" to Json.str("runs/${record.runId}/run.json"),
        "findingsRef" to Json.str(record.findingsRef),
        "statusRef" to Json.str("runs/${record.runId}/status.json"),
        "manifestRef" to Json.str(record.manifestRef),
        "manifestDigest" to Json.str(record.manifestDigest),
        "summary" to Json.obj(
            "sev1" to Json.num(record.summary.sev1),
            "sev2" to Json.num(record.summary.sev2),
            "sev3" to Json.num(record.summary.sev3),
            "sev4" to Json.num(record.summary.sev4),
        ),
        "gate" to Json.obj(
            "provingTestsRequired" to Json.num(record.summary.total),
            "provingTestsPassed" to Json.num(0),
            "passed" to Json.bool(record.summary.total == 0),
        ),
    )

    private fun scanOutgoingStage(stage: Path) {
        var total = 0L
        Files.walk(stage).use { stream ->
            stream.forEach { path ->
                require(!Files.isSymbolicLink(path)) { "symlink in staged bus" }
                if (Files.isRegularFile(path)) {
                    val size = Files.size(path)
                    require(size <= limits.maxArtifactBytes) { "staged artifact exceeds size limit" }
                    total += size
                    require(total <= limits.maxRunBytes) { "staged run exceeds size limit" }
                    val text = Files.readString(path)
                    require(!Redaction.containsSecret(text)) { "staged bus contains a secret" }
                }
            }
        }
    }

    private fun stageResolve(stage: Path, relative: String): Path {
        val normalized = Fingerprints.normalizePath(relative)
        val resolved = stage.resolve(normalized).normalize()
        require(resolved.startsWith(stage)) { "artifact path escapes stage" }
        return resolved
    }

    private fun safeResolve(relative: String): Path {
        val normalized = Fingerprints.normalizePath(relative)
        val resolved = root.resolve(normalized).normalize()
        require(resolved.startsWith(root.normalize())) { "path escapes bus" }
        return resolved
    }

    private fun atomicWriteAndSync(path: Path, bytes: ByteArray) {
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp-${UUID.randomUUID()}")
        writeAndSync(temp, bytes)
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        syncDirectory(path.parent)
    }

    private fun writeAndSync(path: Path, bytes: ByteArray) {
        FileChannel.open(
            path,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.write(java.nio.ByteBuffer.wrap(bytes))
            channel.force(true)
        }
    }

    private fun syncDirectory(directory: Path) {
        runCatching {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun rejectSymlink(path: Path) {
        require(!Files.isSymbolicLink(path)) { "symlinked bus root rejected" }
    }

    private fun deleteRecursively(path: Path) {
        if (!path.exists()) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    companion object {
        private val RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
    }
}

class BusReader(
    private val root: Path?,
    private val limits: BusLimits = BusLimits(),
) {
    fun read(expectedSourceCommit: String? = null): BusState {
        if (root == null) return BusState.NotConfigured
        return try {
            if (!root.exists()) return BusState.Unavailable("bus root absent")
            require(!Files.isSymbolicLink(root)) { "symlinked bus root" }
            val indexPath = safeResolve("index.json")
            if (!Files.isRegularFile(indexPath)) return BusState.Unavailable("bus index absent")
            require(Files.size(indexPath) <= limits.maxArtifactBytes) { "index exceeds size limit" }
            val index = Json.parse(Files.readString(indexPath)).requireObject()
            ContractValidator.validateIndex(index)
            val latest = index.value("latestComplete")
            if (latest == JsonValue.Null) return BusState.Ready(null, emptyList())
            val runId = latest?.requireString("$.latestComplete") ?: return BusState.Ready(null, emptyList())
            require(runId.matches(Regex("[0-9]{8}T[0-9]{6}Z-[0-9a-f]{7}"))) { "invalid run id" }
            val entry = index.required("runs").requireArray("$.runs").values
                .map { it.requireObject("$.runs[]") }
                .singleOrNull { it.string("runId") == runId }
                ?: error("latestComplete does not resolve to exactly one run")
            require(entry.string("status") == "complete") { "latest run is not complete" }
            val sourceCommit = entry.string("sourceCommit")
            if (expectedSourceCommit != null) {
                require(expectedSourceCommit.matches(Regex("[0-9a-f]{40}"))) { "invalid expected source commit" }
                require(sourceCommit == expectedSourceCommit) { "stale evidence for a different source commit" }
            }
            val runPath = entry.string("path")
            require(runPath == "runs/$runId") { "run path mismatch" }
            val runDir = safeResolve(runPath)
            require(Files.isDirectory(runDir) && !Files.isSymbolicLink(runDir)) { "run directory unavailable" }
            val manifestRef = entry.string("manifestRef")
            require(manifestRef == "$runPath/manifest.sha256") { "manifest path mismatch" }
            val manifestPath = safeResolve(manifestRef)
            val manifestBytes = Files.readAllBytes(manifestPath)
            require(manifestBytes.size.toLong() <= limits.maxArtifactBytes) { "manifest exceeds size limit" }
            val expectedManifestDigest = entry.string("manifestDigest")
            require(Fingerprints.sha256(manifestBytes) == expectedManifestDigest) { "manifest tampered" }
            val listed = validateManifest(runDir, manifestBytes.toString(Charsets.UTF_8))
            rejectUnlistedFiles(runDir, listed + "manifest.sha256")

            val runRef = entry.string("runRef")
            val findingsRef = entry.string("findingsRef")
            val statusRef = entry.string("statusRef")
            require(runRef == "$runPath/run.json") { "run metadata path mismatch" }
            require(findingsRef == "$runPath/findings.sarif") { "findings path mismatch" }
            require(statusRef == "$runPath/status.json") { "status path mismatch" }
            require(setOf("run.json", "findings.sarif", "status.json").all { it in listed }) {
                "required artifact is absent from manifest"
            }
            val runJson = Json.parse(Files.readString(safeResolve(runRef))).requireObject()
            ContractValidator.validateRun(runJson, runId, index.string("sourceRepo"), sourceCommit)
            val statusJson = Json.parse(Files.readString(safeResolve(statusRef))).requireObject()
            ContractValidator.validateStatus(statusJson)
            val findings = Sarif.decodeCanonical(Files.readString(safeResolve(findingsRef)))
            ContractValidator.validateStatusAgainstFindings(statusJson, findings)
            ContractValidator.validateIndexEntryAgainstFindings(entry, findings)
            val summary = RunSummary.from(findings)
            val record = RunRecord(
                runId = runId,
                sourceRepo = runJson.string("sourceRepo"),
                sourceCommit = sourceCommit,
                createdAt = Instant.parse(runJson.string("createdAt")),
                status = RunState.COMPLETE,
                findingsRef = findingsRef,
                manifestRef = manifestRef,
                manifestDigest = expectedManifestDigest,
                summary = summary,
            )
            BusState.Ready(record, findings)
        } catch (e: Exception) {
            BusState.Invalid(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun validateManifest(runDir: Path, text: String): Set<String> {
        val listed = linkedSetOf<String>()
        var total = 0L
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(' ')
            require(parts.size == 3) { "invalid manifest line" }
            val relative = Fingerprints.normalizePath(parts[0])
            require(relative != "manifest.sha256") { "manifest cannot list itself" }
            require(listed.add(relative)) { "duplicate manifest entry" }
            val expectedDigest = parts[1]
            val expectedSize = parts[2].toLongOrNull() ?: error("invalid manifest size")
            require(expectedDigest.matches(Regex("[0-9a-f]{64}"))) { "invalid manifest digest" }
            require(expectedSize in 0..limits.maxArtifactBytes) { "artifact size exceeds limit" }
            val path = runDir.resolve(relative).normalize()
            require(path.startsWith(runDir)) { "manifest path escapes run" }
            require(Files.isRegularFile(path) && !Files.isSymbolicLink(path)) { "manifest artifact unavailable" }
            val actualSize = Files.size(path)
            require(actualSize == expectedSize) { "artifact size mismatch" }
            require(Fingerprints.sha256(Files.readAllBytes(path)) == expectedDigest) { "artifact tampered" }
            total += actualSize
            require(total <= limits.maxRunBytes) { "run exceeds size limit" }
        }
        require(listed.isNotEmpty()) { "empty manifest" }
        return listed
    }

    private fun rejectUnlistedFiles(runDir: Path, allowed: Set<String>) {
        Files.walk(runDir).use { stream ->
            stream.forEach { path ->
                require(!Files.isSymbolicLink(path)) { "symlink in run" }
                if (Files.isRegularFile(path)) {
                    val relative = Fingerprints.normalizePath(runDir.relativize(path).toString())
                    require(relative in allowed) { "unlisted artifact: $relative" }
                }
            }
        }
    }

    private fun safeResolve(relative: String): Path {
        val normalized = Fingerprints.normalizePath(relative)
        val resolved = root!!.resolve(normalized).normalize()
        require(resolved.startsWith(root.normalize())) { "path escapes bus" }
        var cursor: Path? = resolved
        while (cursor != null && cursor != root) {
            require(!Files.isSymbolicLink(cursor)) { "symlink rejected" }
            cursor = cursor.parent
        }
        return resolved
    }
}
