package dev.assay

import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** `owner/repo`, validated so it can be placed in a URL path without escaping surprises. */
data class RepoRef(val owner: String, val repo: String) {
    init {
        require(PART.matches(owner) && PART.matches(repo) && owner !in DOTS && repo !in DOTS) {
            "Repository must look like owner/repo"
        }
    }

    val slug: String get() = "$owner/$repo"

    companion object {
        private val PART = Regex("[A-Za-z0-9_.-]{1,100}")

        // "." and ".." match the character class but would rewrite the API path.
        private val DOTS = setOf(".", "..")

        fun parse(text: String): RepoRef {
            val parts = text.trim().removeSuffix(".git").split('/')
            require(parts.size == 2) { "Repository must look like owner/repo" }
            return RepoRef(parts[0], parts[1])
        }
    }
}

data class GitHubUser(val login: String, val id: Long)

data class WorkflowRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val headSha: String,
    val createdAt: String,
    val runNumber: Int,
)

/** Typed failures. Messages never contain the token or response bodies beyond GitHub's own message. */
sealed class GitHubException(message: String, val statusCode: Int? = null) : IOException(message) {
    class Unauthorized : GitHubException("GitHub rejected the token (HTTP 401). Check it in Settings.", 401)
    class Forbidden(detail: String) : GitHubException("GitHub refused the request (HTTP 403): $detail", 403)
    class NotFound(what: String) : GitHubException("$what was not found (HTTP 404).", 404)
    class Validation(detail: String) : GitHubException("GitHub rejected the request (HTTP 422): $detail", 422)
    class Http(code: Int, detail: String) : GitHubException("GitHub returned HTTP $code: $detail", code)
    class Network(detail: String) : GitHubException("Could not reach GitHub: $detail")
    class NoToken : GitHubException("No GitHub token is saved. Add one in Settings first.")
    class Unexpected(detail: String) : GitHubException("GitHub sent a response the console could not read ($detail).")
}

/**
 * Minimal GitHub REST client for the console's control-plane actions.
 *
 * - No SDK, no background work: every method is a blocking call the UI layer makes only in
 *   response to a user tap (see NetworkPolicyTest).
 * - The token is read through [tokenProvider] on each call and only ever sent to [baseUrl]'s host.
 * - [baseUrl] must be https, except loopback (used by tests).
 */
class GitHubClient(
    private val tokenProvider: () -> String?,
    baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "Assay-Console/1.2.0",
) {
    private val base: String = baseUrl.trimEnd('/').also { url ->
        val uri = URI(url)
        val loopback = uri.host == "127.0.0.1" || uri.host == "localhost"
        require(uri.scheme == "https" || (uri.scheme == "http" && loopback)) {
            "GitHub base URL must use https"
        }
    }

    fun currentUser(): GitHubUser = parsing("account") {
        val json = JSONObject(request("GET", "/user", what = "Your GitHub account").body)
        GitHubUser(login = json.getString("login"), id = json.getLong("id"))
    }

    fun defaultBranch(repo: RepoRef): String = parsing("repository") {
        JSONObject(request("GET", "/repos/${repo.slug}", what = repo.slug).body).getString("default_branch")
    }

    /** Starts the caller workflow via `workflow_dispatch`. GitHub answers 204 with no body. */
    fun dispatchWorkflow(repo: RepoRef, workflowFile: String, ref: String, inputs: Map<String, String>) {
        require(workflowFile.matches(Regex("[A-Za-z0-9_.-]{1,100}\\.ya?ml"))) { "Invalid workflow file name" }
        require(ref.isNotBlank() && ref.length <= 255) { "Invalid ref" }
        val body = JSONObject()
            .put("ref", ref)
            .put("inputs", JSONObject().also { obj -> inputs.toSortedMap().forEach { (k, v) -> obj.put(k, v) } })
        request(
            "POST",
            "/repos/${repo.slug}/actions/workflows/$workflowFile/dispatches",
            body = body.toString(),
            what = "Workflow $workflowFile in ${repo.slug}",
            expect = setOf(204),
        )
    }

    /** Latest runs of [workflowFile], newest first, following `Link: rel="next"` up to [limit]. */
    fun listWorkflowRuns(repo: RepoRef, workflowFile: String, limit: Int = 10, perPage: Int = 5): List<WorkflowRun> =
        parsing("workflow runs") { listRuns(repo, workflowFile, limit, perPage) }

    private fun listRuns(repo: RepoRef, workflowFile: String, limit: Int, perPage: Int): List<WorkflowRun> {
        require(limit in 1..100 && perPage in 1..100) { "Invalid page size" }
        val runs = mutableListOf<WorkflowRun>()
        var next: String? = "/repos/${repo.slug}/actions/workflows/$workflowFile/runs?per_page=$perPage"
        var pages = 0
        while (next != null && runs.size < limit && pages < 20) {
            val response = request("GET", next, what = "Workflow runs for ${repo.slug}")
            val array = JSONObject(response.body).getJSONArray("workflow_runs")
            for (index in 0 until array.length()) {
                if (runs.size >= limit) break
                val item = array.getJSONObject(index)
                runs += WorkflowRun(
                    id = item.getLong("id"),
                    status = item.getString("status"),
                    conclusion = if (item.isNull("conclusion")) null else item.getString("conclusion"),
                    htmlUrl = item.getString("html_url"),
                    headSha = item.getString("head_sha"),
                    createdAt = item.getString("created_at"),
                    runNumber = item.optInt("run_number", 0),
                )
            }
            next = response.nextPath
            pages++
        }
        return runs
    }

    /** Reads a text file at [ref], or null when it does not exist. */
    fun readFile(repo: RepoRef, path: String, ref: String): String? {
        require(!path.startsWith("/") && path.split('/').none { it == ".." }) { "Invalid path" }
        val encodedPath = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return try {
            request(
                "GET",
                "/repos/${repo.slug}/contents/$encodedPath?ref=${URLEncoder.encode(ref, "UTF-8")}",
                what = "$path on $ref",
                accept = "application/vnd.github.raw+json",
            ).body
        } catch (_: GitHubException.NotFound) {
            null
        }
    }

    /**
     * Adds the caller workflow on a new branch and opens a pull request. Never pushes to the base
     * branch and never merges. Refuses when the workflow already exists on the base branch, and
     * picks the next free branch name when an earlier proposal's branch is still there.
     */
    fun proposeCallerWorkflow(repo: RepoRef, workflowPath: String, workflowText: String): String = parsing("pull request") {
        val baseBranch = defaultBranch(repo)
        if (readFile(repo, workflowPath, baseBranch) != null) {
            throw GitHubException.Validation("$workflowPath already exists on $baseBranch")
        }
        val baseSha = JSONObject(
            request("GET", "/repos/${repo.slug}/git/ref/heads/${encodeRef(baseBranch)}", what = "Branch $baseBranch").body,
        ).getJSONObject("object").getString("sha")
        val branch = createBranch(repo, baseSha)
        request(
            "PUT",
            "/repos/${repo.slug}/contents/$workflowPath",
            body = JSONObject()
                .put("message", "Add Assay audit workflow")
                .put("content", Base64.encodeToString(workflowText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                .put("branch", branch)
                .toString(),
            what = workflowPath,
            expect = setOf(200, 201),
        )
        val pull = JSONObject(
            request(
                "POST",
                "/repos/${repo.slug}/pulls",
                body = JSONObject()
                    .put("title", "Add Assay audit workflow")
                    .put("head", branch)
                    .put("base", baseBranch)
                    .put(
                        "body",
                        "Adds `$workflowPath`, which runs Assay's hosted audit from workflow_dispatch.\n\n" +
                            "Review it before merging. Nothing runs until someone starts it.",
                    )
                    .toString(),
                what = "Pull request",
                expect = setOf(201),
            ).body,
        )
        pull.getString("html_url")
    }

    private fun createBranch(repo: RepoRef, baseSha: String): String {
        for (attempt in 1..MAX_BRANCH_ATTEMPTS) {
            val branch = if (attempt == 1) PROPOSAL_BRANCH else "$PROPOSAL_BRANCH-$attempt"
            try {
                request(
                    "POST",
                    "/repos/${repo.slug}/git/refs",
                    body = JSONObject().put("ref", "refs/heads/$branch").put("sha", baseSha).toString(),
                    what = "Branch $branch",
                    expect = setOf(201),
                )
                return branch
            } catch (e: GitHubException.Validation) {
                // 422 "Reference already exists": an earlier proposal's branch is still there.
                if (attempt == MAX_BRANCH_ATTEMPTS) throw e
            }
        }
        error("unreachable")
    }

    /** JSON the server sent back that is not the shape we expect is an error, never a crash. */
    private fun <T> parsing(what: String, block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw GitHubException.Unexpected(what)
    }

    private data class Response(val code: Int, val body: String, val nextPath: String?)

    private fun request(
        method: String,
        pathOrUrl: String,
        body: String? = null,
        what: String,
        expect: Set<Int> = setOf(200),
        accept: String = "application/vnd.github+json",
    ): Response {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: throw GitHubException.NoToken()
        val url = if (pathOrUrl.startsWith("http")) pathOrUrl else base + pathOrUrl
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", userAgent)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use(::readBounded).orEmpty()
            if (code in expect) {
                return Response(code, text, nextLink(connection.getHeaderField("Link")))
            }
            throw failure(code, text, connection.getHeaderField("X-RateLimit-Remaining"), what)
        } catch (e: GitHubException) {
            throw e
        } catch (e: IOException) {
            throw GitHubException.Network(e.message?.take(200) ?: e::class.java.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun failure(code: Int, text: String, rateRemaining: String?, what: String): GitHubException {
        val detail = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty().take(300)
        return when (code) {
            401 -> GitHubException.Unauthorized()
            403 -> GitHubException.Forbidden(
                if (rateRemaining == "0") "rate limit reached, try again later" else detail.ifBlank { "forbidden" },
            )
            404 -> GitHubException.NotFound(what)
            301, 302, 307, 308 -> GitHubException.Http(code, "the repository may have been renamed or moved; update it in Repos")
            422 -> GitHubException.Validation(detail.ifBlank { "invalid request" })
            else -> GitHubException.Http(code, detail.ifBlank { "request failed" })
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) throw GitHubException.Unexpected("it was larger than 2 MB")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    /** Returns the `rel="next"` target as a path on [base], or null when it points elsewhere. */
    private fun nextLink(header: String?): String? {
        if (header == null) return null
        val match = Regex("<([^>]+)>;\\s*rel=\"next\"").find(header) ?: return null
        val target = match.groupValues[1]
        // Never follow a link off the configured API host: that is where the token would leak.
        return if (target.startsWith("$base/")) target else null
    }

    private fun encodeRef(branch: String): String =
        branch.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com"
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val PROPOSAL_BRANCH = "assay/add-audit-workflow"
        private const val MAX_BRANCH_ATTEMPTS = 5
    }
}
