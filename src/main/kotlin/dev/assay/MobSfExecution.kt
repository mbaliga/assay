package dev.assay

import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/** Immutable MobSF container identity. Mutable tags are deliberately rejected. */
data class MobSfContainerPin(
    val version: String,
    val image: String,
) {
    val imageDigest: String = image.substringAfterLast("@sha256:")

    init {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "invalid MobSF version" }
        require(image.matches(Regex("[A-Za-z0-9./_-]+@sha256:[0-9a-f]{64}"))) {
            "MobSF image must be pinned by lowercase sha256 digest"
        }
    }
}

data class MobSfApiConfig(
    val baseUri: URI,
    val apiKey: String,
    val requestTimeout: Duration = Duration.ofMinutes(20),
    val maxResponseBytes: Int = 64 * 1024 * 1024,
) {
    init {
        require(baseUri.scheme in setOf("http", "https")) { "MobSF URI must use HTTP or HTTPS" }
        require(baseUri.userInfo == null && baseUri.query == null && baseUri.fragment == null) {
            "MobSF URI must not contain credentials, query, or fragment"
        }
        require(!baseUri.host.isNullOrBlank()) { "MobSF URI requires a host" }
        val normalizedPath = baseUri.path.trimEnd('/')
        require(normalizedPath == "/api/v1") { "MobSF URI path must be /api/v1" }
        val loopback = runCatching { InetAddress.getByName(baseUri.host).isLoopbackAddress }.getOrDefault(false)
        require(baseUri.scheme == "https" || loopback) { "non-loopback MobSF endpoints require HTTPS" }
        require(apiKey.length in 16..512 && apiKey.none { it.isISOControl() || it.isWhitespace() }) {
            "invalid MobSF API key"
        }
        require(requestTimeout in Duration.ofSeconds(5)..Duration.ofHours(2)) { "invalid MobSF timeout" }
        require(maxResponseBytes in 1_024..128 * 1024 * 1024) { "invalid MobSF response limit" }
    }
}

class MobSfApiClient(
    private val config: MobSfApiConfig,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) {
    fun analyze(
        application: Path,
        sourceRoot: Path,
        pin: MobSfContainerPin,
    ): ScannerArtifact {
        val app = secureApplication(application)
        var uploadedHash: String? = null
        var artifact: ScannerArtifact? = null
        var failure: Throwable? = null
        try {
            val upload = upload(app)
            uploadedHash = upload.hash
            postForm("scan", mapOf("hash" to upload.hash))
            val report = postForm("report_json", mapOf("hash" to upload.hash))
            val sanitized = Redaction.sanitize(report)
            val findings = MobSfNormalizer.decode(sanitized, sourceRoot)
            artifact = ScannerArtifact(
                scanner = Scanner.MOBSF,
                version = pin.version,
                executableDigest = pin.imageDigest,
                configDigest = Fingerprints.sha256(
                    "mobsf-api-v1|${config.baseUri.scheme}|${config.baseUri.host}|${config.baseUri.port}".toByteArray(),
                ),
                exitCode = 0,
                format = "json",
                content = sanitized,
                findings = findings,
            )
        } catch (error: Throwable) {
            failure = error
        } finally {
            if (uploadedHash != null) {
                try {
                    postForm("delete_scan", mapOf("hash" to uploadedHash))
                } catch (cleanup: Throwable) {
                    if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
                }
            }
        }
        if (failure != null) throw failure
        return requireNotNull(artifact) { "MobSF analysis produced no artifact" }
    }

    private fun upload(application: Path): UploadResult {
        val boundary = "assay-${UUID.randomUUID()}"
        val name = application.fileName.toString()
        val prefix = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"${multipartName(name)}\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val publisher = HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofByteArray(prefix),
            HttpRequest.BodyPublishers.ofFile(application),
            HttpRequest.BodyPublishers.ofByteArray(suffix),
        )
        val body = send(
            request("upload")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(publisher)
                .build(),
        )
        val root = Json.parse(body).requireObject()
        require("error" !in root.values) { "MobSF upload failed" }
        val hash = root.string("hash")
        require(hash.matches(Regex("[0-9a-f]{32}"))) { "MobSF returned an invalid upload hash" }
        val scanType = root.string("scan_type")
        require(scanType.lowercase() in setOf("apk", "aab", "zip")) { "MobSF returned unsupported scan type" }
        return UploadResult(hash, scanType)
    }

    private fun postForm(endpoint: String, values: Map<String, String>): String {
        val body = values.entries.sortedBy { it.key }.joinToString("&") { (key, value) ->
            "${form(key)}=${form(value)}"
        }
        return send(
            request(endpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
        )
    }

    private fun request(endpoint: String): HttpRequest.Builder {
        require(endpoint.matches(Regex("[a-z_]+"))) { "invalid MobSF endpoint" }
        val base = config.baseUri.toString().trimEnd('/')
        return HttpRequest.newBuilder(URI.create("$base/$endpoint"))
            .timeout(config.requestTimeout)
            .header("Accept", "application/json")
            .header("X-Mobsf-Api-Key", config.apiKey)
            .header("User-Agent", "Assay/1.1.0")
    }

    private fun send(request: HttpRequest): String {
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { stream ->
            val declared = response.headers().firstValueAsLong("Content-Length")
            require(declared.isEmpty || declared.asLong <= config.maxResponseBytes.toLong()) {
                "MobSF response exceeds configured size limit"
            }
            val bytes = stream.readNBytes(config.maxResponseBytes + 1)
            require(bytes.size <= config.maxResponseBytes) { "MobSF response exceeds configured size limit" }
            val body = bytes.toString(Charsets.UTF_8)
            require(response.statusCode() in 200..299) {
                "MobSF ${request.uri().path.substringAfterLast('/')} failed with HTTP ${response.statusCode()}: " +
                    Redaction.sanitize(body).take(1_024)
            }
            require(body.isNotBlank()) { "MobSF returned an empty response" }
            return body
        }
    }

    private fun secureApplication(path: Path): Path {
        val app = path.toAbsolutePath().normalize()
        require(Files.exists(app, LinkOption.NOFOLLOW_LINKS)) { "application package does not exist" }
        require(!Files.isSymbolicLink(app)) { "application package must not be a symlink" }
        require(Files.isRegularFile(app, LinkOption.NOFOLLOW_LINKS)) { "application package is not a regular file" }
        require(Files.size(app) in 1..2L * 1024 * 1024 * 1024) { "application package size rejected" }
        require(app.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("apk", "aab", "zip")) {
            "unsupported Android application package"
        }
        return app
    }

    private fun multipartName(value: String): String {
        require(value.isNotBlank() && value.none { it == '\r' || it == '\n' || it == '"' || it.isISOControl() }) {
            "unsafe application filename"
        }
        return value
    }

    private fun form(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private data class UploadResult(val hash: String, val scanType: String)
}

object MobSfContainerCommand {
    fun build(
        runtime: Path,
        pin: MobSfContainerPin,
        stateDirectory: Path,
        hostPort: Int = 8000,
        containerName: String = "assay-mobsf",
    ): List<String> {
        val executable = runtime.toAbsolutePath().normalize()
        require(Files.exists(executable, LinkOption.NOFOLLOW_LINKS)) { "container runtime does not exist" }
        require(!Files.isSymbolicLink(executable)) { "container runtime must not be a symlink" }
        require(Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) && Files.isExecutable(executable)) {
            "container runtime is not executable"
        }
        val state = stateDirectory.toAbsolutePath().normalize()
        Files.createDirectories(state)
        require(!Files.isSymbolicLink(state) && Files.isDirectory(state, LinkOption.NOFOLLOW_LINKS)) {
            "MobSF state directory is invalid"
        }
        require(hostPort in 1_024..65_535) { "invalid MobSF host port" }
        require(containerName.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}"))) { "invalid container name" }
        return listOf(
            executable.toString(), "run", "--rm", "--detach",
            "--name", containerName,
            "--pull", "never",
            "--read-only",
            "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges",
            "--pids-limit", "1_024",
            "--memory", "8g",
            "--cpus", "4",
            "--tmpfs", "/tmp:rw,nosuid,nodev,size=2g",
            "--mount", "type=bind,src=$state,dst=/home/mobsf/.MobSF,rw",
            "--publish", "127.0.0.1:$hostPort:8000",
            pin.image,
        )
    }
}

object MobSfExecutionCli {
    private val commands = setOf("run-mobsf", "print-mobsf-container-command")
    fun handles(command: String?): Boolean = command in commands

    fun run(args: Array<String>) {
        val options = Options(args.drop(1))
        val pin = MobSfContainerPin(
            version = options.required("--version"),
            image = options.required("--image"),
        )
        when (args.first()) {
            "run-mobsf" -> {
                val apiKey = secureSecret(Path.of(options.required("--api-key-file")))
                val artifact = MobSfApiClient(
                    MobSfApiConfig(URI.create(options.required("--base-uri")), apiKey),
                ).analyze(
                    application = Path.of(options.required("--application")),
                    sourceRoot = Path.of(options.required("--source-root")),
                    pin = pin,
                )
                val raw = Path.of(options.required("--raw-output")).toAbsolutePath().normalize()
                val canonical = Path.of(options.required("--canonical-output")).toAbsolutePath().normalize()
                Files.createDirectories(raw.parent)
                Files.createDirectories(canonical.parent)
                Files.writeString(raw, artifact.content)
                Files.writeString(canonical, Sarif.encode(artifact.findings))
                println("mobsf: ${artifact.findings.size} finding(s)")
            }
            "print-mobsf-container-command" -> {
                val command = MobSfContainerCommand.build(
                    runtime = Path.of(options.required("--runtime")),
                    pin = pin,
                    stateDirectory = Path.of(options.required("--state")),
                    hostPort = options.optional("--port")?.toIntOrNull() ?: 8000,
                    containerName = options.optional("--name") ?: "assay-mobsf",
                )
                println(command.joinToString(" ") { shell(it) })
            }
        }
    }

    private fun secureSecret(path: Path): String {
        val absolute = path.toAbsolutePath().normalize()
        require(Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) { "MobSF API key file does not exist" }
        require(!Files.isSymbolicLink(absolute)) { "MobSF API key file must not be a symlink" }
        require(Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) { "MobSF API key path is not a regular file" }
        require(Files.size(absolute) in 16..4_096) { "MobSF API key file size rejected" }
        return Files.readString(absolute).trim()
    }

    private fun shell(value: String): String = if (value.matches(Regex("[A-Za-z0-9_./:=,@+-]+"))) value
    else "'${value.replace("'", "'\\''")}'"

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
