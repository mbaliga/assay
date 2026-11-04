package dev.assay

import android.util.Base64
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GitHubClientTest {
    private lateinit var server: MockWebServer
    private val token = "github_pat_11TESTTOKEN0123456789"
    private val repo = RepoRef("octo", "widget")

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun client(tokenValue: String? = token) =
        GitHubClient({ tokenValue }, baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun `current user sends the token and the GitHub headers`() {
        server.enqueue(MockResponse().setBody("""{"login":"octocat","id":42}"""))
        val user = client().currentUser()
        assertEquals(GitHubUser("octocat", 42), user)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/user", request.path)
        assertEquals("Bearer $token", request.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Assay-Console/"))
    }

    @Test
    fun `dispatch posts ref and string inputs to the workflow dispatches endpoint`() {
        server.enqueue(MockResponse().setResponseCode(204))
        client().dispatchWorkflow(
            repo, "assay.yml", "main",
            mapOf("scanners" to "gitleaks,semgrep", "output_branch" to "assay/output", "build_apk" to "false"),
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/repos/octo/widget/actions/workflows/assay.yml/dispatches", request.path)
        assertEquals("Bearer $token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("main", body.getString("ref"))
        val inputs = body.getJSONObject("inputs")
        assertEquals("gitleaks,semgrep", inputs.getString("scanners"))
        assertEquals("assay/output", inputs.getString("output_branch"))
        assertEquals("false", inputs.getString("build_apk"))
        assertEquals(3, inputs.length())
    }

    @Test
    fun `workflow runs follow the next link across pages and stop at the limit`() {
        val second = server.url("/repos/octo/widget/actions/workflows/assay.yml/runs?per_page=2&page=2")
        server.enqueue(
            MockResponse()
                .setHeader("Link", "<$second>; rel=\"next\", <$second>; rel=\"last\"")
                .setBody(runsJson(10, 9)),
        )
        server.enqueue(MockResponse().setBody(runsJson(8, 7)))
        val runs = client().listWorkflowRuns(repo, "assay.yml", limit = 3, perPage = 2)
        assertEquals(listOf(10L, 9L, 8L), runs.map { it.id })
        assertEquals("/repos/octo/widget/actions/workflows/assay.yml/runs?per_page=2", server.takeRequest().path)
        assertEquals("/repos/octo/widget/actions/workflows/assay.yml/runs?per_page=2&page=2", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a next link that points at another host is never followed`() {
        server.enqueue(
            MockResponse()
                .setHeader("Link", "<https://evil.example/steal?page=2>; rel=\"next\"")
                .setBody(runsJson(5)),
        )
        val runs = client().listWorkflowRuns(repo, "assay.yml", limit = 10, perPage = 1)
        assertEquals(listOf(5L), runs.map { it.id })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `401 becomes Unauthorized and the message never contains the token`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        val error = assertThrows(GitHubException.Unauthorized::class.java) { client().currentUser() }
        assertEquals(401, error.statusCode)
        assertFalse(error.message!!.contains(token))
        assertTrue(error.message!!.contains("Settings"))
    }

    @Test
    fun `403 with an exhausted rate limit says so`() {
        server.enqueue(
            MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0").setBody("""{"message":"API rate limit exceeded"}"""),
        )
        val error = assertThrows(GitHubException.Forbidden::class.java) { client().currentUser() }
        assertTrue(error.message!!.contains("rate limit"))
    }

    @Test
    fun `422 surfaces GitHub's validation message`() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"Workflow does not have 'workflow_dispatch' trigger"}"""))
        val error = assertThrows(GitHubException.Validation::class.java) {
            client().dispatchWorkflow(repo, "assay.yml", "main", emptyMap())
        }
        assertTrue(error.message!!.contains("workflow_dispatch"))
    }

    @Test
    fun `no saved token fails before any request is made`() {
        assertThrows(GitHubException.NoToken::class.java) { client(tokenValue = null).currentUser() }
        assertThrows(GitHubException.NoToken::class.java) { client(tokenValue = "  ").currentUser() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `readFile returns raw text, encodes the path, and maps 404 to null`() {
        server.enqueue(MockResponse().setBody("""{"schemaVersion":"1.0.0"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        val found = client().readFile(repo, ".assay/assay-index.v1.json", "assay/output")
        assertEquals("""{"schemaVersion":"1.0.0"}""", found)
        val request = server.takeRequest()
        assertEquals("/repos/octo/widget/contents/.assay/assay-index.v1.json?ref=assay%2Foutput", request.path)
        assertEquals("application/vnd.github.raw+json", request.getHeader("Accept"))
        assertNull(client().readFile(repo, "console.json", "assay/audit"))
    }

    @Test
    fun `proposing the caller workflow branches, writes the file there, and opens a pull request`() {
        server.enqueue(MockResponse().setBody("""{"default_branch":"main"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        server.enqueue(MockResponse().setBody("""{"object":{"sha":"abc123"}}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ref":"refs/heads/assay/add-audit-workflow"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"content":{}}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url":"https://github.com/octo/widget/pull/7"}"""))
        val url = client().proposeCallerWorkflow(repo, CallerWorkflow.PATH, "name: Assay audit\n")
        assertEquals("https://github.com/octo/widget/pull/7", url)

        assertEquals("/repos/octo/widget", server.takeRequest().path)
        assertTrue(server.takeRequest().path!!.startsWith("/repos/octo/widget/contents/.github/workflows/assay.yml?ref=main"))
        assertEquals("/repos/octo/widget/git/ref/heads/main", server.takeRequest().path)
        val ref = server.takeRequest()
        assertEquals("POST", ref.method)
        val refBody = JSONObject(ref.body.readUtf8())
        assertEquals("refs/heads/assay/add-audit-workflow", refBody.getString("ref"))
        assertEquals("abc123", refBody.getString("sha"))
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/repos/octo/widget/contents/.github/workflows/assay.yml", put.path)
        val putBody = JSONObject(put.body.readUtf8())
        assertEquals("assay/add-audit-workflow", putBody.getString("branch"))
        assertEquals("name: Assay audit\n", String(Base64.decode(putBody.getString("content"), Base64.DEFAULT)))
        val pull = server.takeRequest()
        assertEquals("/repos/octo/widget/pulls", pull.path)
        val pullBody = JSONObject(pull.body.readUtf8())
        assertEquals("main", pullBody.getString("base"))
        assertEquals("assay/add-audit-workflow", pullBody.getString("head"))
    }

    @Test
    fun `proposing refuses when the workflow already exists and writes nothing`() {
        server.enqueue(MockResponse().setBody("""{"default_branch":"main"}"""))
        server.enqueue(MockResponse().setBody("name: Assay audit\n"))
        val error = assertThrows(GitHubException.Validation::class.java) {
            client().proposeCallerWorkflow(repo, CallerWorkflow.PATH, "x")
        }
        assertTrue(error.message!!.contains("already exists"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an earlier proposal's branch does not dead-end a second proposal`() {
        server.enqueue(MockResponse().setBody("""{"default_branch":"main"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"object":{"sha":"abc123"}}"""))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"Reference already exists"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"html_url":"https://github.com/octo/widget/pull/8"}"""))
        assertEquals("https://github.com/octo/widget/pull/8", client().proposeCallerWorkflow(repo, CallerWorkflow.PATH, "x"))
        repeat(3) { server.takeRequest() }
        assertEquals("refs/heads/assay/add-audit-workflow", JSONObject(server.takeRequest().body.readUtf8()).getString("ref"))
        assertEquals("refs/heads/assay/add-audit-workflow-2", JSONObject(server.takeRequest().body.readUtf8()).getString("ref"))
        assertEquals("assay/add-audit-workflow-2", JSONObject(server.takeRequest().body.readUtf8()).getString("branch"))
    }

    @Test
    fun `a 200 with an unexpected shape is a typed error, not a crash`() {
        server.enqueue(MockResponse().setBody("""{"unexpected":true}"""))
        assertThrows(GitHubException.Unexpected::class.java) { client().currentUser() }
        server.enqueue(MockResponse().setBody("<html>captive portal</html>"))
        assertThrows(GitHubException.Unexpected::class.java) { client().defaultBranch(repo) }
        server.enqueue(MockResponse().setBody("""{"workflow_runs":"nope"}"""))
        assertThrows(GitHubException.Unexpected::class.java) { client().listWorkflowRuns(repo, "assay.yml") }
    }

    @Test
    fun `a moved repository and an oversized response say what happened`() {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "https://api.github.com/x"))
        val moved = assertThrows(GitHubException.Http::class.java) { client().defaultBranch(repo) }
        assertTrue(moved.message!!.contains("renamed or moved"))
        server.enqueue(MockResponse().setBody("x".repeat(2 * 1024 * 1024 + 10)))
        val big = assertThrows(GitHubException.Unexpected::class.java) { client().readFile(repo, "console.json", "assay/audit") }
        assertTrue(big.message!!.contains("2 MB"))
    }

    @Test
    fun `the base URL must be https unless it is loopback`() {
        assertNotNull(GitHubClient({ token }, baseUrl = "https://api.github.com"))
        assertThrows(IllegalArgumentException::class.java) { GitHubClient({ token }, baseUrl = "http://api.github.com") }
        assertThrows(IllegalArgumentException::class.java) { GitHubClient({ token }, baseUrl = "http://evil.example") }
    }

    @Test
    fun `repo references reject anything that could alter the URL path`() {
        assertEquals("octo/widget", RepoRef.parse("octo/widget.git").slug)
        listOf("octo", "octo/widget/extra", "../x", "octo/..", "./x", "octo/wid get", "octo/%2e%2e", "").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { RepoRef.parse(it) }
        }
    }

    private fun runsJson(vararg ids: Long): String {
        val items = ids.joinToString(",") {
            """{"id":$it,"status":"completed","conclusion":"success","html_url":"https://github.com/octo/widget/actions/runs/$it","head_sha":"${"a".repeat(40)}","created_at":"2026-11-04T00:00:00Z","run_number":$it}"""
        }
        return """{"total_count":${ids.size},"workflow_runs":[$items]}"""
    }
}
