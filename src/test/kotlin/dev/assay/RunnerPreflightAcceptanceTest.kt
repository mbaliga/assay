package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

fun main() {
    val workspace = Files.createTempDirectory("assay-runner-")
    setPrivateDirectory(workspace)
    val tools = workspace.resolve("tools")
    Files.createDirectory(tools)
    setPrivateDirectory(tools)

    val gitleaks = tool(tools.resolve("gitleaks"), "gitleaks 8.30.1")
    val semgrep = tool(tools.resolve("semgrep"), "1.164.0")
    val osv = tool(tools.resolve("osv-scanner"), "osv-scanner version 2.3.8")
    val git = tool(tools.resolve("git"), "git version 2.50.0")
    val runtime = tool(tools.resolve("podman"), "container runtime podman version 5.0")
    val lock = workspace.resolve("tool-lock.json")
    Files.writeString(
        lock,
        Json.stringify(
            Json.obj(
                "schemaVersion" to Json.str("1.0.0"),
                "tools" to Json.obj(
                    "gitleaks" to pin("8.30.1", gitleaks),
                    "semgrep" to pin("1.164.0", semgrep),
                    "osv" to pin("2.3.8", osv),
                ),
            ),
            pretty = true,
        ),
    )

    val config = RunnerPreflightConfig(
        workRoot = workspace,
        toolLock = lock,
        scannerExecutables = mapOf(
            Scanner.GITLEAKS to gitleaks,
            Scanner.SEMGREP to semgrep,
            Scanner.OSV to osv,
        ),
        gitExecutable = git,
        containerRuntime = runtime,
        minimumUsableBytes = 1,
    )
    val preflight = RunnerPreflight(
        RunnerHostFacts(user = "assay", osName = "Linux", cgroupV2 = true),
    )
    val report = preflight.inspect(config)
    check(report.ready) {
        RunnerPreflightCodec.encode(report)
    }
    check(report.checks.none { !it.passed })
    println("PASS runner preflight verifies host, workspace, pins, versions, Git, and container runtime")

    Files.writeString(gitleaks, Files.readString(gitleaks) + "# tampered\n")
    val tampered = preflight.inspect(config)
    check(!tampered.ready)
    check(tampered.checks.single { it.id == "tool.gitleaks.digest" }.passed.not())
    println("PASS runner preflight rejects scanner binary drift")

    val rootReport = RunnerPreflight(
        RunnerHostFacts(user = "root", osName = "Linux", cgroupV2 = true),
    ).inspect(config)
    check(!rootReport.ready)
    check(rootReport.checks.single { it.id == "host.non-root" }.passed.not())
    println("PASS runner preflight rejects root execution")

    setOpenDirectory(workspace)
    val exposed = preflight.inspect(config)
    check(!exposed.ready)
    check(exposed.checks.single { it.id == "workspace.secure" }.passed.not())
    println("PASS runner preflight rejects exposed workspace permissions")
}

private fun pin(version: String, path: Path): JsonValue.Obj = Json.obj(
    "version" to Json.str(version),
    "sha256" to Json.str(Fingerprints.sha256(Files.readAllBytes(path))),
)

private fun tool(path: Path, version: String): Path {
    Files.writeString(path, "#!/bin/sh\nprintf '%s\\n' '$version'\n")
    path.toFile().setExecutable(true, true)
    return path
}

private fun setPrivateDirectory(path: Path) {
    runCatching {
        Files.setPosixFilePermissions(
            path,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )
    }
}

private fun setOpenDirectory(path: Path) {
    runCatching {
        Files.setPosixFilePermissions(
            path,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
            ),
        )
    }
}
