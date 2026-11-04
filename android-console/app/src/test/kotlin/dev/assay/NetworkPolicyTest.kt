package dev.assay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The console makes network calls only because the user tapped something (BUILD-SPEC §3.7,
 * INT-001). This reads the sources and fails if a network-touching function is reachable from a
 * lifecycle callback, an `init` block, a Compose effect, or any background scheduler.
 */
class NetworkPolicyTest {
    private val main = File("src/main/kotlin")
    private val sources: Map<String, String> = main.walkTopDown().filter { it.extension == "kt" }
        .associate { it.relativeTo(main).path to it.readText() }

    private val clientApi = listOf(
        "currentUser", "defaultBranch", "dispatchWorkflow", "listWorkflowRuns", "readFile", "proposeCallerWorkflow",
    )
    private val networkActions: List<String> = Regex("@NetworkAction\\s+fun\\s+(\\w+)")
        .findAll(sources.getValue("dev/assay/ConsoleViewModel.kt")).map { it.groupValues[1] }.toList()

    @Test
    fun `sources were found and the expected network actions are annotated`() {
        assertTrue(sources.size >= 10)
        assertEquals(
            setOf("verifyToken", "checkWorkflow", "proposeWorkflow", "runAudit", "refresh", "loadCandidates"),
            networkActions.toSet(),
        )
    }

    @Test
    fun `no background scheduling or connectivity listeners exist`() {
        val forbidden = listOf(
            "WorkManager", "androidx.work", "JobScheduler", "JobService", "AlarmManager", "BroadcastReceiver",
            "NetworkCallback", "ConnectivityManager", "startService", "FirebaseMessaging", "SyncAdapter",
        )
        sources.forEach { (file, text) ->
            forbidden.forEach { word -> assertTrue("$file mentions $word", !text.contains(word)) }
        }
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf("<service", "<receiver", "<provider").forEach {
            assertTrue("manifest declares $it", !manifest.contains(it))
        }
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue("cleartext must stay off", manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue("backups must stay off", manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun `no lifecycle callback, init block or compose effect can reach the network`() {
        val reachable = networkActions + clientApi + listOf("GitHubClient", "client.")
        val triggers = listOf(
            "override fun onCreate", "override fun onStart", "override fun onResume", "override fun onPause",
            "override fun onStop", "override fun onDestroy", "override fun onRestart", "override fun onNewIntent",
            "override fun onConfigurationChanged", "init {", "LaunchedEffect(", "DisposableEffect(", "SideEffect {",
            "produceState", "rememberCoroutineScope",
        )
        sources.forEach { (file, text) ->
            triggers.forEach { trigger ->
                var from = 0
                while (true) {
                    val at = text.indexOf(trigger, from)
                    if (at < 0) break
                    val body = block(text, at)
                    reachable.forEach { name ->
                        assertTrue("$file: '$trigger' reaches $name", !Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(body))
                    }
                    from = at + trigger.length
                }
            }
        }
    }

    @Test
    fun `the client is only used by annotated view model functions and built at the composition root`() {
        sources.forEach { (file, text) ->
            if (file != "dev/assay/GitHubClient.kt" && file != "dev/assay/ConsoleViewModel.kt") {
                if (file == "dev/assay/AssayHomeActivity.kt") {
                    assertEquals("constructed once at the composition root", 1, Regex("GitHubClient\\(").findAll(text).count())
                } else {
                    assertTrue("$file uses GitHubClient", !text.contains("GitHubClient"))
                }
            }
        }
        val vm = sources.getValue("dev/assay/ConsoleViewModel.kt")
        Regex("(@NetworkAction\\s+)?fun\\s+(\\w+)").findAll(vm).forEach { match ->
            val body = block(vm, match.range.last)
            if (Regex("\\bclient\\.").containsMatchIn(body)) {
                assertTrue("${match.groupValues[2]} uses client but is not @NetworkAction", match.groupValues[1].isNotEmpty())
            }
        }
    }

    @Test
    fun `ui code can only talk to ConsoleActions`() {
        sources.filterKeys { it.startsWith("dev/assay/ui/") }.forEach { (file, text) ->
            listOf("GitHubClient", "ConsoleViewModel", "viewModel", "HttpURLConnection", "java.net").forEach {
                assertTrue("$file references $it", !text.contains(it))
            }
        }
    }

    /** Text of the brace-delimited block that starts at or after [from] (balanced braces). */
    private fun block(text: String, from: Int): String {
        val open = text.indexOf('{', from)
        if (open < 0) return ""
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(open, index + 1)
            }
        }
        return text.substring(open)
    }
}
