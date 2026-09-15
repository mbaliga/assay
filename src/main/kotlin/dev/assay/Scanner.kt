package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile

object ToolCatalog {
    const val GITLEAKS_VERSION = "8.30.1"
    const val SEMGREP_VERSION = "1.164.0"
    const val OSV_VERSION = "2.3.8"
    const val MOBSF_VERSION = "4.5.1"
}

data class ScannerCommandContext(
    val executable: Path,
    val target: Path,
    val output: Path,
    val config: Path? = null,
    val offline: Boolean = true,
    val timeout: Duration = Duration.ofMinutes(10),
)

interface ExternalScannerAdapter {
    val scanner: Scanner
    val expectedVersion: String
    val outputFormat: String

    fun versionCommand(executable: Path, workingDirectory: Path): ScannerCommand = ScannerCommand(
        executable = executable,
        arguments = listOf("--version"),
        workingDirectory = workingDirectory,
        allowedExitCodes = setOf(0),
        timeout = Duration.ofSeconds(30),
    )

    fun versionMatches(result: CommandResult): Boolean {
        if (result.exitCode != 0) return false
        val combined = result.stdout + "\n" + result.stderr
        val pattern = Regex("(?<![0-9.])v?${Regex.escape(expectedVersion)}(?![0-9.])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(combined)
    }

    fun command(context: ScannerCommandContext): ScannerCommand
    fun normalize(raw: String, sourceRoot: Path): List<Finding>
}

object GitleaksAdapter : ExternalScannerAdapter {
    override val scanner = Scanner.GITLEAKS
    override val expectedVersion = ToolCatalog.GITLEAKS_VERSION
    override val outputFormat = "sarif"

    override fun command(context: ScannerCommandContext): ScannerCommand {
        val args = mutableListOf(
            "dir",
            "--no-banner",
            "--no-color",
            "--redact=100",
            "--report-format", "sarif",
            "--report-path", context.output.toAbsolutePath().toString(),
            "--timeout", context.timeout.seconds.toString(),
        )
        context.config?.let { args += listOf("--config", it.toAbsolutePath().toString()) }
        args += context.target.toAbsolutePath().toString()
        return ScannerCommand(
            executable = context.executable,
            arguments = args,
            workingDirectory = context.target,
            allowedExitCodes = setOf(0, 1),
            timeout = context.timeout,
        )
    }

    override fun normalize(raw: String, sourceRoot: Path): List<Finding> = Sarif.decode(scanner, raw, sourceRoot)
}

object SemgrepAdapter : ExternalScannerAdapter {
    override val scanner = Scanner.SEMGREP
    override val expectedVersion = ToolCatalog.SEMGREP_VERSION
    override val outputFormat = "sarif"

    override fun command(context: ScannerCommandContext): ScannerCommand {
        val config = requireNotNull(context.config) { "Semgrep requires a pinned local rule configuration" }
        val args = listOf(
            "scan",
            "--config", config.toAbsolutePath().toString(),
            "--sarif",
            "--output", context.output.toAbsolutePath().toString(),
            "--metrics", "off",
            "--disable-version-check",
            "--error",
            context.target.toAbsolutePath().toString(),
        )
        return ScannerCommand(
            executable = context.executable,
            arguments = args,
            workingDirectory = context.target,
            environment = mapOf(
                "SEMGREP_SEND_METRICS" to "off",
                "SEMGREP_ENABLE_VERSION_CHECK" to "0",
            ),
            allowedExitCodes = setOf(0, 1),
            timeout = context.timeout,
        )
    }

    override fun normalize(raw: String, sourceRoot: Path): List<Finding> = Sarif.decode(scanner, raw, sourceRoot)
}

object OsvAdapter : ExternalScannerAdapter {
    override val scanner = Scanner.OSV
    override val expectedVersion = ToolCatalog.OSV_VERSION
    override val outputFormat = "sarif"

    override fun versionCommand(executable: Path, workingDirectory: Path): ScannerCommand = ScannerCommand(
        executable = executable,
        arguments = listOf("--version"),
        workingDirectory = workingDirectory,
        allowedExitCodes = setOf(0),
        timeout = Duration.ofSeconds(30),
    )

    override fun command(context: ScannerCommandContext): ScannerCommand {
        val args = mutableListOf(
            "scan", "source",
            "--recursive",
            "--format", "sarif",
            "--output-file", context.output.toAbsolutePath().toString(),
            "--verbosity", "error",
        )
        if (context.offline) args += "--offline"
        context.config?.let { args += listOf("--config", it.toAbsolutePath().toString()) }
        args += context.target.toAbsolutePath().toString()
        return ScannerCommand(
            executable = context.executable,
            arguments = args,
            workingDirectory = context.target,
            allowedExitCodes = setOf(0, 1),
            timeout = context.timeout,
        )
    }

    override fun normalize(raw: String, sourceRoot: Path): List<Finding> = Sarif.decode(scanner, raw, sourceRoot)
}

object MobSfAdapter {
    val scanner = Scanner.MOBSF
    val expectedVersion = ToolCatalog.MOBSF_VERSION
    val outputFormat = "json"

    fun normalize(raw: String, sourceRoot: Path): List<Finding> = MobSfNormalizer.decode(raw, sourceRoot)
}

interface CommandRunner {
    fun run(command: ScannerCommand): CommandResult
}

class JvmCommandRunner(private val maxCapturedBytes: Long = 4L * 1024 * 1024) : CommandRunner {
    override fun run(command: ScannerCommand): CommandResult {
        require(command.executable.isRegularFile()) { "scanner executable is unavailable" }
        val stdout = Files.createTempFile("assay-scanner-stdout", ".log")
        val stderr = Files.createTempFile("assay-scanner-stderr", ".log")
        val started = Instant.now()
        try {
            val processBuilder = ProcessBuilder(listOf(command.executable.toString()) + command.arguments)
                .directory(command.workingDirectory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
            val env = processBuilder.environment()
            val path = env["PATH"].orEmpty()
            env.clear()
            if (path.isNotBlank()) env["PATH"] = path
            env["LANG"] = "C"
            env["LC_ALL"] = "C"
            env["NO_COLOR"] = "1"
            command.environment.forEach { (key, value) ->
                require(key.matches(Regex("[A-Z0-9_]+"))) { "unsafe environment key" }
                env[key] = value
            }
            val process = processBuilder.start()
            if (!process.waitFor(command.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                error("scanner timed out after ${command.timeout}")
            }
            val stdoutText = readBounded(stdout)
            val stderrText = readBounded(stderr)
            return CommandResult(
                exitCode = process.exitValue(),
                stdout = Redaction.sanitize(stdoutText),
                stderr = Redaction.sanitize(stderrText),
                duration = Duration.between(started, Instant.now()),
            )
        } finally {
            Files.deleteIfExists(stdout)
            Files.deleteIfExists(stderr)
        }
    }

    private fun readBounded(path: Path): String {
        require(Files.size(path) <= maxCapturedBytes) { "scanner output exceeded capture limit" }
        return Files.readString(path)
    }
}

class ScannerOrchestrator(
    private val runner: CommandRunner,
    private val maxArtifactBytes: Long = 32L * 1024 * 1024,
) {
    fun execute(
        adapter: ExternalScannerAdapter,
        installation: ToolInstallation,
        target: Path,
        output: Path,
        config: Path? = null,
        offline: Boolean = true,
    ): ScannerOutcome {
        return try {
            val pin = installation.pin
            require(pin.scanner == adapter.scanner) { "scanner pin mismatch" }
            require(pin.version == adapter.expectedVersion) {
                "${adapter.scanner.wireName} version ${pin.version} is not the pinned ${adapter.expectedVersion}"
            }
            val targetPath = target.toAbsolutePath().normalize()
            val outputPath = output.toAbsolutePath().normalize()
            require(Files.isDirectory(targetPath)) { "scan target is unavailable" }
            val executablePath = installation.executable.toAbsolutePath().normalize().toRealPath()
            require(Files.isRegularFile(executablePath)) { "scanner executable is unavailable" }
            val executableDigest = Fingerprints.sha256(Files.readAllBytes(executablePath))
            require(executableDigest == pin.sha256) { "scanner executable digest mismatch" }
            val configPath = config?.toAbsolutePath()?.normalize()?.toRealPath()
            configPath?.let { require(Files.isRegularFile(it)) { "scanner config is unavailable" } }
            val configDigest = configPath?.let { Fingerprints.sha256(Files.readAllBytes(it)) }
                ?: Fingerprints.sha256(ByteArray(0))
            val versionResult = runner.run(adapter.versionCommand(executablePath, targetPath))
            if (!adapter.versionMatches(versionResult)) {
                return ScannerOutcome.Failed(
                    adapter.scanner,
                    "scanner reported a version other than pinned ${adapter.expectedVersion}",
                    versionResult.exitCode,
                )
            }
            Files.createDirectories(outputPath.parent)
            Files.deleteIfExists(outputPath)
            val command = adapter.command(
                ScannerCommandContext(
                    executable = executablePath,
                    target = targetPath,
                    output = outputPath,
                    config = configPath,
                    offline = offline,
                ),
            )
            val result = runner.run(command)
            if (result.exitCode !in command.allowedExitCodes) {
                return ScannerOutcome.Failed(adapter.scanner, "scanner process failed", result.exitCode)
            }
            if (!Files.isRegularFile(outputPath)) {
                return ScannerOutcome.Failed(adapter.scanner, "scanner produced no report", result.exitCode)
            }
            if (Files.size(outputPath) > maxArtifactBytes) {
                return ScannerOutcome.Failed(adapter.scanner, "scanner report exceeds size limit", result.exitCode)
            }
            val safeRaw = Redaction.sanitize(Files.readString(outputPath))
            if (Redaction.containsSecret(safeRaw)) {
                return ScannerOutcome.Failed(adapter.scanner, "scanner report contains an unredacted secret", result.exitCode)
            }
            replaceWithSanitizedReport(outputPath, safeRaw)
            val findings = adapter.normalize(safeRaw, targetPath)
            val artifact = ScannerArtifact(
                scanner = adapter.scanner,
                version = pin.version,
                executableDigest = executableDigest,
                configDigest = configDigest,
                exitCode = result.exitCode,
                format = adapter.outputFormat,
                content = safeRaw,
                findings = findings,
            )
            ScannerOutcome.Success(adapter.scanner, artifact)
        } catch (e: Exception) {
            ScannerOutcome.Failed(adapter.scanner, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun replaceWithSanitizedReport(output: Path, safeRaw: String) {
        val temp = Files.createTempFile(output.parent, output.fileName.toString(), ".redacted")
        try {
            Files.writeString(temp, safeRaw)
            runCatching {
                Files.move(
                    temp,
                    output,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}

object DeterministicSecretScanner {
    private val candidate = Regex(
        "(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-/.+=]{8,})",
    )

    fun scan(root: Path): List<Finding> {
        val findings = mutableListOf<Finding>()
        Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }
                .filter { !it.toString().replace('\\', '/').contains("/.git/") }
                .filter { !it.fileName.toString().endsWith(".sarif") }
                .sorted()
                .forEach { file ->
                    Files.readAllLines(file).forEachIndexed { index, line ->
                        if (candidate.containsMatchIn(line)) {
                            val relative = Fingerprints.normalizePath(root.relativize(file).toString())
                            findings += Finding(
                                scanner = Scanner.GITLEAKS,
                                ruleId = "generic-hardcoded-secret",
                                message = "Potential hardcoded credential",
                                location = Location(relative, index + 1),
                                context = Redaction.sanitize(line),
                                level = SarifLevel.ERROR,
                                properties = mapOf("assay.fixtureScanner" to "true"),
                            )
                        }
                    }
                }
        }
        return Sarif.merge(findings)
    }
}
