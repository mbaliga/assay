package dev.assay

import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Exit codes of `assay audit`. */
object AuditExit {
    const val COMPLETE = 0
    const val PARTIAL = 2
    const val FAILED = 3
}

/** Typed configuration error: the audit refuses to start rather than guess. */
class AuditConfigException(message: String) : IllegalArgumentException(message)

data class MobSfAuditInput(
    val image: String,
    val application: Path?,
    val baseUri: URI?,
    val apiKeyFile: Path?,
    val reportFile: Path?,
)

data class AuditConfig(
    val repo: Path,
    val commit: String,
    val sourceRepo: String,
    val gitRemoteUrl: String,
    val outputBranch: String,
    val toolLock: Path,
    val toolPaths: Map<Scanner, Path>,
    val scanners: Set<Scanner>,
    val workDir: Path,
    val semgrepConfig: Path? = null,
    val gitleaksConfig: Path? = null,
    val online: Boolean = false,
    val mobSf: MobSfAuditInput? = null,
    val ai: String = "off",
    val push: Boolean = false,
    val remoteName: String = "origin",
    val internalBranch: String = "assay/audit",
    val fonebrewProjectHint: String? = null,
)

data class AuditResult(
    val exitCode: Int,
    val completeness: AssayCompletenessV1,
    val explanation: String?,
    val runId: String,
    val outcomes: List<ScannerOutcome>,
    val findingCount: Int,
    val outputRoot: Path,
    val internalBusRoot: Path?,
    val pushedOutputCommit: String?,
    val pushedInternalCommit: String?,
)

/**
 * `assay audit`: scanners -> fail-closed bundle -> internal bus -> ratified `.assay/` tree -> push.
 *
 * Findings come only from [ScannerOutcome]s produced by the four deterministic scanners. Nothing
 * in this file constructs a [Finding].
 */
class AuditRunner(
    private val commandRunner: CommandRunner = JvmCommandRunner(),
    private val clock: () -> Instant = { Instant.now() },
) {
    fun run(config: AuditConfig): AuditResult {
        validate(config)
        val startedAt = clock()
        val runId = BusWriter.runIdFor(startedAt, config.commit)
        val cliScanners = config.scanners - Scanner.MOBSF
        val lock = if (cliScanners.isEmpty()) null else ToolLock.parse(Files.readString(config.toolLock), cliScanners)

        val work = config.workDir.toAbsolutePath().normalize()
        Files.createDirectories(work)
        val rawDir = work.resolve("raw").also { Files.createDirectories(it) }
        val outputRoot = work.resolve("output")
        val busRoot = work.resolve("bus")
        require(!Files.exists(outputRoot) && !Files.exists(busRoot)) { "work directory already holds an audit" }

        val orchestrator = ScannerOrchestrator(commandRunner)
        val outcomes = config.scanners.sortedBy { it.wireName }.map { scanner ->
            when (scanner) {
                Scanner.MOBSF -> runMobSf(config)
                else -> {
                    val adapter = adapterFor(scanner)
                    val tool = config.toolPaths[scanner]
                        ?: return@map ScannerOutcome.Unavailable(scanner, "no executable configured")
                    orchestrator.execute(
                        adapter = adapter,
                        installation = ToolInstallation(requireNotNull(lock).require(scanner), tool),
                        target = config.repo,
                        output = rawDir.resolve("${scanner.wireName}.sarif"),
                        config = when (scanner) {
                            Scanner.SEMGREP -> config.semgrepConfig
                            Scanner.GITLEAKS -> config.gitleaksConfig
                            else -> null
                        },
                        offline = !config.online,
                    )
                }
            }
        }

        val successes = outcomes.filterIsInstance<ScannerOutcome.Success>()
        val problems = outcomes.filter { it !is ScannerOutcome.Success }
        val completeness = when {
            successes.isEmpty() -> AssayCompletenessV1.FAILED
            problems.isNotEmpty() -> AssayCompletenessV1.PARTIAL
            else -> AssayCompletenessV1.COMPLETE
        }
        val explanation = if (problems.isEmpty()) null else problems.joinToString("; ") { outcome ->
            val reason = when (outcome) {
                is ScannerOutcome.Failed -> "failed: ${outcome.reason}"
                is ScannerOutcome.Unavailable -> "unavailable: ${outcome.reason}"
                is ScannerOutcome.Success -> ""
            }
            "${outcome.scanner.wireName} $reason"
        }.let { Redaction.sanitize(it) }

        val bundle: ScanBundle? = if (successes.isEmpty()) null else {
            ScanPipeline.bundle(
                sourceRepo = config.sourceRepo,
                sourceCommit = config.commit,
                outcomes = successes,
                requiredScanners = successes.map { it.scanner }.toSet(),
            ).getOrThrow()
        }
        val findings = bundle?.findings.orEmpty()
        require(findings.none { it.properties["assay.fixtureScanner"] == "true" }) {
            "fixture scanner output is rejected by audit"
        }

        // The internal trust-engine bus is fail-closed: it only records fully complete runs.
        val internalBus = if (completeness == AssayCompletenessV1.COMPLETE && bundle != null) {
            BusWriter(busRoot).publish(bundle, startedAt)
            busRoot
        } else {
            null
        }

        val finishedAt = clock()
        AssayOutputWriterV1(outputRoot).write(
            runId = runId,
            projectRef = AssayProjectRefV1(config.gitRemoteUrl, config.fonebrewProjectHint),
            sourceCommit = config.commit,
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            tool = AssayToolRefV1("assay", TOOL_VERSION),
            startedAt = startedAt,
            finishedAt = if (finishedAt < startedAt) startedAt else finishedAt,
            findings = findings,
            completeness = completeness,
            explanation = explanation,
        )

        var outputCommit: String? = null
        var internalCommit: String? = null
        if (config.push) {
            val outputPublisher = GitBusPublisher(config.repo, config.outputBranch)
            outputCommit = outputPublisher.publishV1(
                busRoot = outputRoot,
                remote = config.remoteName,
                expectedRemoteCommit = outputPublisher.observedRemoteHead(config.remoteName),
                indexRelativePath = ".assay/assay-index.v1.json",
                assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
                commitMessage = "Assay output $runId ${config.commit}",
            )
            if (internalBus != null) {
                val internalPublisher = GitBusPublisher(config.repo, config.internalBranch)
                internalCommit = internalPublisher.publish(
                    busRoot = internalBus,
                    remote = config.remoteName,
                    expectedRemoteCommit = internalPublisher.observedRemoteHead(config.remoteName),
                    expectedSourceCommit = config.commit,
                )
            }
        }

        val exit = when (completeness) {
            AssayCompletenessV1.COMPLETE -> AuditExit.COMPLETE
            AssayCompletenessV1.PARTIAL -> AuditExit.PARTIAL
            AssayCompletenessV1.FAILED -> AuditExit.FAILED
        }
        return AuditResult(
            exitCode = exit,
            completeness = completeness,
            explanation = explanation,
            runId = runId,
            outcomes = outcomes,
            findingCount = findings.size,
            outputRoot = outputRoot,
            internalBusRoot = internalBus,
            pushedOutputCommit = outputCommit,
            pushedInternalCommit = internalCommit,
        )
    }

    private fun runMobSf(config: AuditConfig): ScannerOutcome {
        val input = config.mobSf
            ?: return ScannerOutcome.Unavailable(Scanner.MOBSF, "no --apk or MobSF report supplied")
        return try {
            val pin = MobSfContainerPin(ToolCatalog.MOBSF_VERSION, input.image)
            when {
                input.baseUri != null -> {
                    val application = requireNotNull(input.application) { "MobSF live analysis needs --apk" }
                    val keyFile = requireNotNull(input.apiKeyFile) { "MobSF live analysis needs --mobsf-api-key-file" }
                    val artifact = MobSfApiClient(MobSfApiConfig(input.baseUri, readSecret(keyFile)))
                        .analyze(application, config.repo, pin)
                    ScannerOutcome.Success(Scanner.MOBSF, artifact)
                }
                input.reportFile != null -> {
                    val report = Files.readString(input.reportFile)
                    ScanPipeline.mobSfOutcome(
                        report = report,
                        sourceRoot = config.repo,
                        pin = ToolPin(Scanner.MOBSF, pin.version, pin.imageDigest),
                        configDigest = Fingerprints.sha256("mobsf-report-file|${pin.version}".toByteArray()),
                        reportedVersion = pin.version,
                    )
                }
                else -> ScannerOutcome.Unavailable(Scanner.MOBSF, "no MobSF endpoint or report supplied")
            }
        } catch (e: Exception) {
            ScannerOutcome.Failed(Scanner.MOBSF, Redaction.sanitize(e.message ?: e::class.simpleName.orEmpty()))
        }
    }

    private fun readSecret(path: Path): String {
        val absolute = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) { "MobSF API key file is not a regular file" }
        return Files.readString(absolute).trim()
    }

    private fun adapterFor(scanner: Scanner): ExternalScannerAdapter = when (scanner) {
        Scanner.GITLEAKS -> GitleaksAdapter
        Scanner.SEMGREP -> SemgrepAdapter
        Scanner.OSV -> OsvAdapter
        Scanner.MOBSF -> error("MobSF is not a local CLI adapter")
    }

    private fun validate(config: AuditConfig) {
        if (config.ai != "off") {
            throw AuditConfigException(
                "--ai ${config.ai} is not available: the AI layer is not built yet (WP4); use --ai off",
            )
        }
        if (config.scanners.isEmpty()) throw AuditConfigException("at least one scanner is required")
        if (!config.commit.matches(Regex("[0-9a-f]{40}"))) throw AuditConfigException("--commit must be a full SHA-1")
        if (config.sourceRepo.isBlank()) throw AuditConfigException("source repo is blank")
        if (config.gitRemoteUrl.isBlank()) throw AuditConfigException("--remote is blank")
        val repo = config.repo.toAbsolutePath().normalize()
        if (!Files.isDirectory(repo, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(repo)) {
            throw AuditConfigException("--repo must be a real directory")
        }
        val head = git(repo, "rev-parse", "HEAD") ?: throw AuditConfigException("--repo is not a Git worktree")
        if (head != config.commit) {
            throw AuditConfigException("--commit ${config.commit} does not match repository HEAD $head")
        }
        if (config.workDir.toAbsolutePath().normalize().startsWith(repo)) {
            throw AuditConfigException("work directory must not be inside the scanned repository")
        }
    }

    private fun git(repo: Path, vararg args: String): String? = runCatching {
        val process = ProcessBuilder(listOf("git", "-C", repo.toString()) + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.use { it.readAllBytes() }.toString(Charsets.UTF_8).trim()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else if (process.exitValue() == 0) output else null
    }.getOrNull()

    companion object {
        const val TOOL_VERSION = "1.1.0"
    }
}

object AuditCli {
    private const val COMMAND = "audit"

    fun handles(command: String?): Boolean = command == COMMAND

    /** Returns the process exit code: 0 complete, 2 partial, 3 failed. */
    fun run(args: Array<String>): Int {
        return try {
            val config = parse(Options(args.drop(1)))
            val result = AuditRunner().run(config)
            println("audit ${result.runId}: ${result.completeness} (${result.findingCount} finding(s))")
            result.outcomes.forEach { outcome ->
                val state = when (outcome) {
                    is ScannerOutcome.Success -> "ok"
                    is ScannerOutcome.Failed -> "failed: ${outcome.reason}"
                    is ScannerOutcome.Unavailable -> "unavailable: ${outcome.reason}"
                }
                println("  ${outcome.scanner.wireName}: $state")
            }
            result.explanation?.let { println("explanation: $it") }
            println("output: ${result.outputRoot}")
            result.pushedOutputCommit?.let { println("pushed output branch ${config.outputBranch}: $it") }
            result.pushedInternalCommit?.let { println("pushed internal bus ${config.internalBranch}: $it") }
            result.exitCode
        } catch (e: Exception) {
            System.err.println("audit failed: ${Redaction.sanitize(e.message ?: e::class.simpleName.orEmpty())}")
            AuditExit.FAILED
        }
    }

    internal fun parse(options: Options): AuditConfig {
        val scannerNames = options.optional("--scanners")
        val apk = options.optional("--apk")?.let(Path::of)
        val mobSfReport = options.optional("--mobsf-report")?.let(Path::of)
        val mobSfBase = options.optional("--mobsf-base-uri")?.let(URI::create)
        val scanners = if (scannerNames != null) {
            scannerNames.split(',').map { Scanner.fromWireName(it.trim()) }.toSet()
        } else {
            setOf(Scanner.GITLEAKS, Scanner.SEMGREP, Scanner.OSV) +
                (if (apk != null || mobSfReport != null) setOf(Scanner.MOBSF) else emptySet())
        }
        val tools = options.optional("--tools")?.let(Path::of)
        fun tool(option: String, default: String): Path? =
            options.optional(option)?.let(Path::of) ?: tools?.resolve(default)
        val remoteUrl = options.required("--remote")
        return AuditConfig(
            repo = Path.of(options.required("--repo")),
            commit = options.required("--commit"),
            sourceRepo = options.optional("--source-repo") ?: slugFrom(remoteUrl),
            gitRemoteUrl = remoteUrl,
            outputBranch = options.required("--output-branch"),
            toolLock = Path.of(options.required("--tool-lock")),
            toolPaths = listOfNotNull(
                tool("--gitleaks", "gitleaks")?.let { Scanner.GITLEAKS to it },
                tool("--semgrep", "semgrep")?.let { Scanner.SEMGREP to it },
                tool("--osv", "osv-scanner")?.let { Scanner.OSV to it },
            ).toMap(),
            scanners = scanners,
            workDir = options.optional("--work-dir")?.let(Path::of) ?: Files.createTempDirectory("assay-audit-"),
            semgrepConfig = options.optional("--semgrep-config")?.let(Path::of),
            gitleaksConfig = options.optional("--gitleaks-config")?.let(Path::of),
            online = options.flag("--online"),
            mobSf = if (apk != null || mobSfReport != null || mobSfBase != null) {
                MobSfAuditInput(
                    image = options.required("--mobsf-image"),
                    application = apk,
                    baseUri = mobSfBase,
                    apiKeyFile = options.optional("--mobsf-api-key-file")?.let(Path::of),
                    reportFile = mobSfReport,
                )
            } else {
                null
            },
            ai = options.optional("--ai") ?: "off",
            push = options.flag("--push"),
            remoteName = options.optional("--remote-name") ?: "origin",
            fonebrewProjectHint = options.optional("--fonebrew-project-hint"),
        )
    }

    private fun slugFrom(remote: String): String {
        val parts = remote.removeSuffix(".git").trimEnd('/').split('/', ':').filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[parts.size - 2]}/${parts.last()}" else remote
    }

    internal class Options(args: List<String>) {
        private val values = linkedMapOf<String, String?>()

        init {
            var index = 0
            while (index < args.size) {
                val name = args[index]
                require(name.startsWith("--")) { "unexpected argument '$name'" }
                require(name !in values) { "duplicate option $name" }
                val isFlag = name in FLAGS
                if (isFlag) {
                    values[name] = null
                    index += 1
                } else {
                    require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $name" }
                    values[name] = args[index + 1]
                    index += 2
                }
            }
        }

        fun required(name: String): String = values[name] ?: error("missing $name")
        fun optional(name: String): String? = values[name]
        fun flag(name: String): Boolean = name in values

        private companion object {
            val FLAGS = setOf("--online", "--push")
        }
    }
}
