package dev.assay

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "scan" -> {
            val repo = required(args, "--repo")
            val bus = required(args, "--bus")
            val sourceRepo = required(args, "--source-repo")
            val sourceCommit = required(args, "--source-commit")
            val findings = DeterministicSecretScanner.scan(Path.of(repo))
            val record = BusWriter(Path.of(bus)).publish(sourceRepo, sourceCommit, findings, Instant.now())
            println("published ${record.runId}: ${findings.size} finding(s)")
        }
        "verify-bus" -> {
            val state = BusReader(Path.of(required(args, "--bus"))).read()
            println(state)
            if (state !is BusState.Ready) error("bus is not ready")
        }
        "fixture" -> {
            val root = Path.of(required(args, "--root")).createDirectories()
            root.resolve("app.properties").toFile().writeText("api_key=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY\n")
            println(root)
        }
        else -> {
            println("Assay v1.0.0")
            println("commands: scan --repo PATH --bus PATH --source-repo SLUG --source-commit SHA | verify-bus --bus PATH | fixture --root PATH")
        }
    }
}

private fun required(args: Array<String>, name: String): String {
    val index = args.indexOf(name)
    require(index >= 0 && index + 1 < args.size) { "missing $name" }
    return args[index + 1]
}
