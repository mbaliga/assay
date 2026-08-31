package dev.assay

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    val apiKey = "a".repeat(64)
    val requests = CopyOnWriteArrayList<String>()
    val failReport = AtomicBoolean(false)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/v1/upload") { exchange ->
        verify(exchange, apiKey, "POST")
        check(exchange.requestHeaders.getFirst("Content-Type").startsWith("multipart/form-data; boundary="))
        val body = exchange.requestBody.use { it.readAllBytes() }
        check(body.toString(Charsets.UTF_8).contains("fixture.apk"))
        requests += "upload"
        respond(exchange, 200, """{"file_name":"fixture.apk","hash":"${"b".repeat(32)}","scan_type":"apk"}""")
    }
    server.createContext("/api/v1/scan") { exchange ->
        verify(exchange, apiKey, "POST")
        check(form(exchange) == "hash=${"b".repeat(32)}")
        requests += "scan"
        respond(exchange, 200, """{"status":"ok"}""")
    }
    server.createContext("/api/v1/report_json") { exchange ->
        verify(exchange, apiKey, "POST")
        check(form(exchange) == "hash=${"b".repeat(32)}")
        requests += "report"
        if (failReport.getAndSet(false)) {
            respond(exchange, 500, """{"error":"forced failure"}""")
        } else {
            respond(
                exchange,
                200,
                """{"manifest_analysis":{"findings":[{"rule":"app_is_debuggable","severity":"high","description":"Application is debuggable","file":"AndroidManifest.xml","line":1}]}}""",
            )
        }
    }
    server.createContext("/api/v1/delete_scan") { exchange ->
        verify(exchange, apiKey, "POST")
        check(form(exchange) == "hash=${"b".repeat(32)}")
        requests += "delete"
        respond(exchange, 200, """{"deleted":"yes"}""")
    }
    server.start()

    try {
        val workspace = Files.createTempDirectory("assay-mobsf-")
        val apk = workspace.resolve("fixture.apk")
        Files.write(apk, byteArrayOf(0x50, 0x4b, 0x03, 0x04))
        val pin = MobSfContainerPin(
            version = "4.5.1",
            image = "opensecurity/mobile-security-framework-mobsf@sha256:${"c".repeat(64)}",
        )
        val client = MobSfApiClient(
            MobSfApiConfig(
                baseUri = URI.create("http://127.0.0.1:${server.address.port}/api/v1"),
                apiKey = apiKey,
            ),
        )
        val artifact = client.analyze(apk, workspace, pin)
        check(artifact.scanner == Scanner.MOBSF)
        check(artifact.version == "4.5.1")
        check(artifact.executableDigest == "c".repeat(64))
        check(artifact.findings.size == 1)
        check(artifact.findings.single().ruleId == "app_is_debuggable")
        check(requests.toList() == listOf("upload", "scan", "report", "delete"))
        println("PASS MobSF API upload, scan, report, normalization, and cleanup")

        requests.clear()
        failReport.set(true)
        val failed = runCatching { client.analyze(apk, workspace, pin) }
        check(failed.isFailure)
        check(requests.toList() == listOf("upload", "scan", "report", "delete"))
        check(apiKey !in (failed.exceptionOrNull()?.message ?: ""))
        println("PASS MobSF cleanup runs after report failure without leaking API key")

        check(runCatching {
            MobSfApiConfig(URI.create("http://example.com/api/v1"), apiKey)
        }.isFailure)
        println("PASS insecure remote MobSF endpoint rejected")

        val runtime = Path.of("/bin/echo")
        if (Files.isExecutable(runtime)) {
            val command = MobSfContainerCommand.build(runtime, pin, workspace.resolve("state"), 18000)
            check("--pull" in command && "never" in command)
            check("--cap-drop" in command && "ALL" in command)
            check("127.0.0.1:18000:8000" in command)
            check(command.last() == pin.image)
            check(command.none { it.endsWith(":latest") })
            println("PASS pinned least-privilege MobSF container command")
        }
    } finally {
        server.stop(0)
    }
}

private fun verify(exchange: HttpExchange, apiKey: String, method: String) {
    check(exchange.requestMethod == method)
    check(exchange.requestHeaders.getFirst("X-Mobsf-Api-Key") == apiKey)
}

private fun form(exchange: HttpExchange): String = exchange.requestBody.use { it.readAllBytes() }.toString(Charsets.UTF_8)

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
