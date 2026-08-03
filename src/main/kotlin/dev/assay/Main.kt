package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "scan", "scan-fixture" -> scanFixture(args)
        "normalize" -> normalize(args)
        "merge" -> merge(args)
        "verify-bus" -> verifyBus(args)
        "print-command" -> printCommand(args)
        "run-scanner" -> runScanner(args)
        "verify-tool-lock" -> verifyToolLock(args)
        "fixture" -> createFixture(args)
        null, "help", "--help", "-h" -> usage()
        else -> error("unknown command '${args.first()}'")
    }
}

private fun scanFixture(args: Array<String>) {
    val repo = Path.of(required(args, "--repo"))
    val bus = Path.of(required(args, "--bus"))
    val sourceRepo = required(args, "--source-repo")
    val sourceCommit = required(args, "--source-commit")
    val findings = DeterministicSecretScanner.scan(repo)
    val record = BusWriter(bus).publish(sourceRepo, sourceCommit, findings, Instant.now())
    println("published ${record.runId}: ${findings.size} finding(s)")
}

private fun normalize(args: Array<String>) {
    val scanner = Scanner.fromWireName(required(args, "--scanner"))
    val input = Path.of(required(args, "--input"))
    val root = Path.of(required(args, "--root"))
    val output = Path.of(required(args, "--output"))
    val raw = Files.readString(input)
    val findings = when (scanner) {
        Scanner.GITLEAKS -> GitleaksAdapter.normalize(Redaction.sanitize(raw), root)
        Scanner.SEMGREP -> SemgrepAdapter.normalize(Redaction.sanitize(raw), root)
        Scanner.OSV -> OsvAdapter.normalize(Redaction.sanitize(raw), root)
        Scanner.MOBSF -> MobSfAdapter.normalize(Redaction.sanitize(raw), root)
    }
    Files.createDirectories(output.toAbsolutePath().parent)
    Files.writeString(output, Sarif.encode(findings))
    println("normalized ${findings.size} ${scanner.wireName} finding(s)")
}

private fun merge(args: Array<String>) {
    val root = Path.of(required(args, "--root"))
    val output = Path.of(required(args, "--output"))
    val inputs = values(args, "--input")
    require(inputs.isNotEmpty()) { "at least one --input scanner=path is required" }
    val findings = inputs.flatMap { input ->
        val parts = input.split('=', limit = 2)
        require(parts.size == 2) { "invalid --input '$input'; expected scanner=path" }
        val scanner = Scanner.fromWireName(parts[0])
        val raw = Redaction.sanitize(Files.readString(Path.of(parts[1])))
        when (scanner) {
            Scanner.GITLEAKS -> GitleaksAdapter.normalize(raw, root)
            Scanner.SEMGREP -> SemgrepAdapter.normalize(raw, root)
            Scanner.OSV -> OsvAdapter.normalize(raw, root)
            Scanner.MOBSF -> MobSfAdapter.normalize(raw, root)
        }
    }
    val merged = Sarif.merge(findings)
    Files.createDirectories(output.toAbsolutePath().parent)
    Files.writeString(output, Sarif.encode(merged))
    println("merged ${merged.size} canonical finding(s)")
}

private fun verifyBus(args: Array<String>) {
    val expected = optional(args, "--expected-source-commit")
    val state = BusReader(Path.of(required(args, "--bus"))).read(expected)
    println(state)
    if (state !is BusState.Ready) error("bus is not ready")
}

private fun printCommand(args: Array<String>) {
    val scanner = Scanner.fromWireName(required(args, "--scanner"))
    require(scanner != Scanner.MOBSF) { "MobSF is an HTTP/container report source, not a local CLI adapter" }
    val context = ScannerCommandContext(
        executable = Path.of(required(args, "--tool")),
        target = Path.of(required(args, "--target")),
        output = Path.of(required(args, "--output")),
        config = optional(args, "--config")?.let(Path::of),
        offline = optional(args, "--online") == null,
    )
    val command = when (scanner) {
        Scanner.GITLEAKS -> GitleaksAdapter.command(context)
        Scanner.SEMGREP -> SemgrepAdapter.command(context)
        Scanner.OSV -> OsvAdapter.command(context)
        Scanner.MOBSF -> error("unreachable")
    }
    println((listOf(command.executable.toString()) + command.arguments).joinToString(" ") { shellQuote(it) })
}

private fun runScanner(args: Array<String>) {
    val scanner = Scanner.fromWireName(required(args, "--scanner"))
    require(scanner != Scanner.MOBSF) { "MobSF reports are ingested through the HTTP/container adapter" }
    val lock = ToolLock.parse(
        Files.readString(Path.of(required(args, "--tool-lock"))),
        requiredScanners = setOf(scanner),
    )
    val installation = ToolInstallation(lock.require(scanner), Path.of(required(args, "--tool")))
    val target = Path.of(required(args, "--target"))
    val rawOutput = Path.of(required(args, "--raw-output"))
    val canonicalOutput = Path.of(required(args, "--canonical-output"))
    val config = optional(args, "--config")?.let(Path::of)
    val adapter = when (scanner) {
        Scanner.GITLEAKS -> GitleaksAdapter
        Scanner.SEMGREP -> SemgrepAdapter
        Scanner.OSV -> OsvAdapter
        Scanner.MOBSF -> error("unreachable")
    }
    when (
        val outcome = ScannerOrchestrator(JvmCommandRunner()).execute(
            adapter = adapter,
            installation = installation,
            target = target,
            output = rawOutput,
            config = config,
            offline = optional(args, "--online") == null,
        )
    ) {
        is ScannerOutcome.Success -> {
            Files.createDirectories(canonicalOutput.toAbsolutePath().parent)
            Files.writeString(canonicalOutput, Sarif.encode(outcome.artifact.findings))
            println("${scanner.wireName}: ${outcome.artifact.findings.size} finding(s)")
        }
        is ScannerOutcome.Unavailable -> error("${scanner.wireName} unavailable: ${outcome.reason}")
        is ScannerOutcome.Failed -> error("${scanner.wireName} failed: ${outcome.reason}")
    }
}

private fun verifyToolLock(args: Array<String>) {
    val lock = ToolLock.parse(Files.readString(Path.of(required(args, "--file"))))
    Scanner.entries.forEach { scanner ->
        val pin = lock.require(scanner)
        println("${scanner.wireName} ${pin.version} ${pin.sha256}")
    }
}

private fun createFixture(args: Array<String>) {
    val root = Path.of(required(args, "--root")).createDirectories()
    root.resolve("app.properties").toFile().writeText("api_key=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY\n")
    println(root)
}

private fun usage() {
    println("Assay v1.1.0")
    println("commands:")
    println("  scan-fixture --repo PATH --bus PATH --source-repo SLUG --source-commit SHA")
    println("  normalize --scanner NAME --input FILE --root PATH --output FILE")
    println("  merge --root PATH --input scanner=FILE [--input scanner=FILE...] --output FILE")
    println("  verify-bus --bus PATH [--expected-source-commit SHA]")
    println("  print-command --scanner NAME --tool PATH --target PATH --output FILE [--config FILE] [--online]")
    println("  run-scanner --scanner NAME --tool PATH --tool-lock FILE --target PATH --raw-output FILE --canonical-output FILE [--config FILE] [--online]")
    println("  verify-tool-lock --file FILE")
    println("  fixture --root PATH")
}

private fun required(args: Array<String>, name: String): String = optional(args, name)
    ?: error("missing $name")

private fun optional(args: Array<String>, name: String): String? {
    val index = args.indexOf(name)
    if (index < 0) return null
    require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $name" }
    return args[index + 1]
}

private fun values(args: Array<String>, name: String): List<String> = args.indices
    .filter { args[it] == name }
    .map { index ->
        require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $name" }
        args[index + 1]
    }

private fun shellQuote(value: String): String = if (value.matches(Regex("[A-Za-z0-9_./:=+-]+"))) value
else "'${value.replace("'", "'\\''")}'"
