package dev.assay

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsoleViewModelTest {
    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()
    private val target = RepoTarget(RepoRef("octo", "widget"), "assay/output")

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun viewModel(tokens: TokenStore = InMemoryTokenStore("github_pat_11TESTTOKEN0123456789"), io: CoroutineDispatcher = dispatcher) =
        ConsoleViewModel(
            tokens,
            InMemoryRepoStore(listOf(target)),
            GitHubClient({ tokens.get() }, baseUrl = server.url("/").toString().trimEnd('/')),
            io,
        )

    @Test
    fun `a double tap on Run audit starts one audit`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{"default_branch":"main"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        val vm = viewModel()
        vm.runAudit(target.key, "gitleaks,semgrep,osv", false)
        assertTrue(vm.ui.targets.single().busy)
        vm.runAudit(target.key, "gitleaks,semgrep,osv", false)
        advanceUntilIdle()
        assertEquals(2, server.requestCount) // one repo lookup + one dispatch, not four
        assertFalse(vm.ui.targets.single().busy)
        assertEquals(RunDisplay.QUEUED, vm.ui.targets.single().display)
    }

    @Test
    fun `an unexpected response is shown as an error and never crashes`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("<html>nope</html>"))
        val vm = viewModel()
        vm.runAudit(target.key, "gitleaks", false)
        advanceUntilIdle()
        val state = vm.ui.targets.single()
        assertFalse(state.busy)
        assertTrue(state.message!!.isError)
    }

    @Test
    fun `a malformed token is rejected, reports false, and stores nothing`() {
        val tokens = InMemoryTokenStore()
        val vm = viewModel(tokens)
        assertFalse(vm.saveToken("short"))
        assertFalse(vm.saveToken("has a space in it but is long enough"))
        assertNull(tokens.get())
        assertTrue(vm.ui.banner!!.isError)
        assertTrue(vm.saveToken("github_pat_11TESTTOKEN0123456789"))
        assertEquals("github_pat_11TESTTOKEN0123456789", tokens.get())
    }

    @Test
    fun `a Keystore failure while saving is reported, not thrown`() {
        val failing = object : TokenStore {
            override fun get(): String? = null
            override fun put(token: String) = throw java.security.KeyStoreException("unavailable")
            override fun clear() = throw java.security.KeyStoreException("unavailable")
        }
        val vm = viewModel(failing)
        assertFalse(vm.saveToken("github_pat_11TESTTOKEN0123456789"))
        assertTrue(vm.ui.banner!!.isError)
        assertFalse(vm.ui.tokenSaved)
        vm.clearToken()
        assertTrue(vm.ui.banner!!.isError)
    }

    @Test
    fun `main thread and background updates do not lose each other`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{"login":"octocat","id":1}"""))
        val vm = viewModel()
        vm.verifyToken()
        vm.addTarget("octo/gadget", "assay/output")
        advanceUntilIdle()
        assertEquals("octocat", vm.ui.account)
        assertEquals(2, vm.ui.targets.size)
    }

    @Test
    fun `without a token no request is made and the error says why`() = runTest(dispatcher) {
        val vm = viewModel(InMemoryTokenStore())
        vm.refresh(target.key)
        advanceUntilIdle()
        assertEquals(0, server.requestCount)
        assertTrue(vm.ui.targets.single().message!!.text.contains("token"))
    }
}
