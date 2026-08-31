package dev.assay

import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration

data class RunnerHostFacts(
    val user: String = System.getProperty("user.name").orEmpty(),
    val osName: String = System.getProperty("os.name").orEmpty(),
    val cgroupV2: Boolean = Files.isRegularFile(Path.of("/sys/fs/cgroup/cgroup.controllers")),
)

data class RunnerPreflightConfig(
    val workRoot: Path,
    val toolLock: Path,
    val scannerExecutables: Map<Scanner, Path>,
    val gitExecutable: Path,
    val containerRuntime: Path,
    val minimumUsableBytes: Long = 20L * 1024 * 1024 * 1024,
) {
    init {
        require(scannerExecutables.keys == setOf(Scanner.GITLEAKS, Scanner.SEMGREP, Scanner.OSV)) {
            "runner preflight requires exactly gitleaks, semgrep, and osv executables"
        }
        require(minimumUsableBytes in 1..10L * 1024 * 1024 * 1024 * 1024) { "invalid disk-space threshold" }
    }
}

data class RunnerCheck(val id: String, val passed: Boolean, val detail: String)

data class RunnerPreflightReport(val checks: List<RunnerCheck>) {
    val ready: Boolean get() = checks.all { it.passed }
}

class RunnerPreflight(
    private val host: RunnerHostFacts = RunnerHostFacts(),
    private val commandRunner: CommandRunner = JvmCommandRunner(1L * 1024 * 1024),
) {
    fun inspect(config: RunnerPreflightConfig): RunnerPreflightReport {
        val checks = mutableListOf<RunnerCheck>()
        checks += check("host.linux") {
            require(host.osName.lowercase().contains("linux")) { "runner host is not Linux" }
            "Linux host"
        }
        checks += check("host.non-root") {
            require(host.user.isNotBlank() && host.user != "root") { "runner must not execute as root" }
            "running as ${host.user}"
        }
        checks += check("host.cgroup-v2") {
            require(host.cgroupV2) { "cgroup v2 is unavailable" }
            "cgroup v2 available"
        }

        val workRoot = config.workRoot.toAbsolutePath().normalize()
        checks += check("workspace.secure") {
            requireSecureDirectory(workRoot)
            "private workspace $workRoot"
        }
        checks += check("workspace.capacity") {
            val usable = Files.getFileStore(workRoot).usableSpace
            require(usable >= config.minimumUsableBytes) {
                "workspace has $usable usable bytes; ${config.minimumUsableBytes} required"
            }
            "$usable usable bytes"
        }

        val lock = runCatching {
            val path = secureRegularFile(config.toolLock, 4L * 1024 * 1024, executable = false)
            ToolLock.parse(
                Files.readString(path),
                setOf(Scanner.GITLEAKS, Scanner.SEMGREP, Scanner.OSV),
            )
        }
        checks += RunnerCheck(
            "tools.lock",
            lock.isSuccess,
            lock.fold({ "tool lock valid" }, { Redaction.sanitize(it.message.orEmpty()) }),
        )

        val adapters = mapOf<Scanner, ExternalScannerAdapter>(
            Scanner.GITLEAKS to GitleaksAdapter,
            Scanner.SEMGREP to SemgrepAdapter,
            Scanner.OSV to OsvAdapter,
        )
        config.scannerExecutables.toSortedMap(compareBy { it.wireName }).forEach { (scanner, configuredPath) ->
            val pathResult = runCatching { secureRegularFile(configuredPath, 512L * 1024 * 1024, executable = true) }
            checks += RunnerCheck(
                "tool.${scanner.wireName}.file",
                pathResult.isSuccess,
                pathResult.fold({ it.toString() }, { Redaction.sanitize(it.message.orEmpty()) }),
            )
            val pin = lock.getOrNull()?.let { runCatching { it.require(scanner) }.getOrNull() }
            checks += check("tool.${scanner.wireName}.digest") {
                val path = pathResult.getOrThrow()
                val expected = requireNotNull(pin) { "scanner pin unavailable" }
                val actual = Fingerprints.sha256(Files.readAllBytes(path))
                require(actual == expected.sha256) { "scanner executable digest mismatch" }
                "sha256 verified"
            }
            checks += check("tool.${scanner.wireName}.version") {
                val path = pathResult.getOrThrow()
                val expected = requireNotNull(pin) { "scanner pin unavailable" }
                val adapter = requireNotNull(adapters[scanner])
                require(expected.version == adapter.expectedVersion) { "tool lock version is not the compiled pin" }
                val result = commandRunner.run(adapter.versionCommand(path, workRoot))
                require(adapter.versionMatches(result)) { "scanner did not report pinned version ${adapter.expectedVersion}" }
                "${adapter.expectedVersion} verified"
            }
        }

        checks += executableVersionCheck("runtime.git", config.gitExecutable, workRoot, listOf("--version"), "git version")
        checks += executableVersionCheck(
            "runtime.container",
            config.containerRuntime,
            workRoot,
            listOf("--version"),
            "container runtime",
        )
        return RunnerPreflightReport(checks)
    }

    private fun executableVersionCheck(
        id: String,
        executable: Path,
        workRoot: Path,
        arguments: List<String>,
        expectedText: String,
    ): RunnerCheck = check(id) {
        val path = secureRegularFile(executable, 512L * 1024 * 1024, executable = true)
        val result = commandRunner.run(
            ScannerCommand(
                executable = path,
                arguments = arguments,
                workingDirectory = workRoot,
                allowedExitCodes = setOf(0),
                timeout = Duration.ofSeconds(30),
            ),
        )
        require(result.exitCode == 0 && (result.stdout + result.stderr).lowercase().contains(expectedText)) {
            "$id did not report a recognized version"
        }
        Redaction.sanitize((result.stdout + result.stderr).trim()).take(256)
    }

    private fun requireSecureDirectory(path: Path) {
        rejectSymlinkComponents(path)
        require(Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "workspace does not exist" }
        require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "workspace is not a directory" }
        require(!Files.isSymbolicLink(path)) { "workspace must not be a symlink" }
        runCatching { Files.getPosixFilePermissions(path) }.getOrNull()?.let { permissions ->
            val forbidden = setOf(
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE,
            )
            require(permissions.intersect(forbidden).isEmpty()) { "workspace permissions expose runner data" }
            require(PosixFilePermission.OWNER_READ in permissions && PosixFilePermission.OWNER_WRITE in permissions) {
                "workspace is not owner-readable and writable"
            }
        }
    }

    private fun secureRegularFile(path: Path, maxBytes: Long, executable: Boolean): Path {
        val absolute = path.toAbsolutePath().normalize()
        rejectSymlinkComponents(absolute)
        require(Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) { "file does not exist: $absolute" }
        require(!Files.isSymbolicLink(absolute)) { "file must not be a symlink: $absolute" }
        require(Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) { "not a regular file: $absolute" }
        require(Files.size(absolute) in 1..maxBytes) { "file size rejected: $absolute" }
        if (executable) require(Files.isExecutable(absolute)) { "file is not executable: $absolute" }
        return absolute
    }

    private fun rejectSymlinkComponents(path: Path) {
        var current = path.root ?: Path.of("")
        path.forEach { component ->
            current = current.resolve(component)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current)) { "path contains a symlink: $current" }
            }
        }
    }

    private fun check(id: String, operation: () -> String): RunnerCheck = runCatching(operation).fold(
        onSuccess = { RunnerCheck(id, true, it) },
        onFailure = { RunnerCheck(id, false, Redaction.sanitize(it.message.orEmpty()).take(1_024)) },
    )
}

object RunnerPreflightCodec {
    fun encode(report: RunnerPreflightReport): String = Json.stringify(
        Json.obj(
            "schemaVersion" to Json.str("1.1.0"),
            "ready" to Json.bool(report.ready),
            "checks" to Json.arr(report.checks.map { check ->
                Json.obj(
                    "id" to Json.str(check.id),
                    "passed" to Json.bool(check.passed),
                    "detail" to Json.str(check.detail),
                )
            }),
        ),
        pretty = true,
    ) + "\n"
}

object RunnerPreflightCli {
    private const val COMMAND = "runner-preflight"
    fun handles(command: String?): Boolean = command == COMMAND

    fun run(args: Array<String>) {
        val options = Options(args.drop(1))
        val report = RunnerPreflight().inspect(
            RunnerPreflightConfig(
                workRoot = Path.of(options.required("--work-root")),
                toolLock = Path.of(options.required("--tool-lock")),
                scannerExecutables = mapOf(
                    Scanner.GITLEAKS to Path.of(options.required("--gitleaks")),
                    Scanner.SEMGREP to Path.of(options.required("--semgrep")),
                    Scanner.OSV to Path.of(options.required("--osv")),
                ),
                gitExecutable = Path.of(options.required("--git")),
                containerRuntime = Path.of(options.required("--container-runtime")),
                minimumUsableBytes = options.optional("--minimum-usable-bytes")?.toLongOrNull()
                    ?: 20L * 1024 * 1024 * 1024,
            ),
        )
        val encoded = RunnerPreflightCodec.encode(report)
        options.optional("--output")?.let { output ->
            val path = Path.of(output).toAbsolutePath().normalize()
            Files.createDirectories(path.parent)
            require(!Files.isSymbolicLink(path)) { "preflight output must not be a symlink" }
            Files.writeString(path, encoded)
        } ?: print(encoded)
        require(report.ready) { "runner preflight failed" }
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
