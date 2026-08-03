package dev.assay

import java.nio.file.Path

object ScanPipeline {
    fun bundle(
        sourceRepo: String,
        sourceCommit: String,
        outcomes: List<ScannerOutcome>,
        requiredScanners: Set<Scanner>,
    ): Result<ScanBundle> = runCatching {
        require(requiredScanners.isNotEmpty()) { "at least one deterministic scanner is required" }
        val duplicates = outcomes.groupBy { it.scanner }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate scanner outcomes: ${duplicates.joinToString { it.wireName }}" }
        val byScanner = outcomes.associateBy { it.scanner }
        requiredScanners.forEach { scanner ->
            when (val outcome = byScanner[scanner]) {
                null -> error("required scanner ${scanner.wireName} did not run")
                is ScannerOutcome.Unavailable -> error("required scanner ${scanner.wireName} unavailable: ${outcome.reason}")
                is ScannerOutcome.Failed -> error("required scanner ${scanner.wireName} failed: ${outcome.reason}")
                is ScannerOutcome.Success -> Unit
            }
        }
        outcomes.filterIsInstance<ScannerOutcome.Failed>().firstOrNull()?.let {
            error("scanner ${it.scanner.wireName} failed: ${it.reason}")
        }
        outcomes.filterIsInstance<ScannerOutcome.Unavailable>().firstOrNull()?.let {
            error("scanner ${it.scanner.wireName} unavailable: ${it.reason}")
        }
        val artifacts = outcomes.filterIsInstance<ScannerOutcome.Success>()
            .map { it.artifact }
            .sortedBy { it.scanner.wireName }
        artifacts.forEach { artifact ->
            require(!Redaction.containsSecret(artifact.content)) {
                "scanner artifact ${artifact.scanner.wireName} contains an unredacted secret"
            }
        }
        ScanBundle(sourceRepo, sourceCommit, artifacts)
    }

    fun mobSfOutcome(
        report: String,
        sourceRoot: Path,
        pin: ToolPin,
        configDigest: String,
        reportedVersion: String,
        exitCode: Int = 0,
    ): ScannerOutcome = runCatching {
        require(pin.scanner == Scanner.MOBSF) { "MobSF pin mismatch" }
        require(pin.version == ToolCatalog.MOBSF_VERSION) { "MobSF version is not pinned" }
        require(reportedVersion == pin.version) { "MobSF server version does not match the pin" }
        require(configDigest.matches(Regex("[0-9a-f]{64}"))) { "invalid MobSF config digest" }
        require(exitCode in setOf(0, 1)) { "invalid MobSF exit code" }
        val safe = Redaction.sanitize(report)
        require(!Redaction.containsSecret(safe)) { "MobSF report contains an unredacted secret" }
        val findings = MobSfAdapter.normalize(safe, sourceRoot)
        ScannerOutcome.Success(
            Scanner.MOBSF,
            ScannerArtifact(
                scanner = Scanner.MOBSF,
                version = pin.version,
                executableDigest = pin.sha256,
                configDigest = configDigest,
                exitCode = exitCode,
                format = MobSfAdapter.outputFormat,
                content = safe,
                findings = findings,
            ),
        )
    }.getOrElse { ScannerOutcome.Failed(Scanner.MOBSF, it.message ?: "MobSF normalization failed") }
}

class ToolLock private constructor(private val pins: Map<Scanner, ToolPin>) {
    fun require(scanner: Scanner): ToolPin = pins[scanner] ?: error("missing tool pin for ${scanner.wireName}")

    companion object {
        fun parse(text: String, requiredScanners: Set<Scanner> = Scanner.entries.toSet()): ToolLock {
            val root = Json.parse(text).requireObject()
            require(root.values.keys == setOf("schemaVersion", "tools")) { "tool lock keys mismatch" }
            require(root.string("schemaVersion") == "1.0.0") { "unsupported tool lock version" }
            val tools = root.required("tools").requireObject("$.tools")
            val pins = tools.values.map { (name, value) ->
                val scanner = Scanner.fromWireName(name)
                val item = value.requireObject("$.tools.$name")
                require(item.values.keys == setOf("version", "sha256")) { "tool pin keys mismatch for $name" }
                scanner to ToolPin(scanner, item.string("version"), item.string("sha256"))
            }.toMap()
            require(requiredScanners.isNotEmpty()) { "at least one scanner pin is required" }
            require(pins.keys.containsAll(requiredScanners)) {
                "tool lock is missing required scanners: ${requiredScanners.minus(pins.keys).joinToString { it.wireName }}"
            }
            return ToolLock(pins)
        }
    }
}
