package plus.rua.project

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionViewModelTest {
    private val prefs = InMemoryTestPrefs()
    private val settings = MomentsConnectionSettings(prefs, isDebug = true, defaultUrl = "http://10.0.2.2:8088", onlineUrl = "https://yaya.rua.plus")
    private val probed = mutableListOf<String>()
    private val online = ServerStatus.Online("0.2.0", moments = true, period = true)
    private var answer: suspend (String) -> ServerStatus = { online }
    private val viewModel by lazy {
        ServerConnectionViewModel(settings) { url ->
            probed += url
            answer(url)
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_probesSavedAddress() {
        viewModel.refresh()

        assertEquals(listOf("http://10.0.2.2:8088"), probed)
        assertEquals(online, viewModel.uiState.value.status)
    }

    @Test
    fun test_invalidDraft_reportsErrorWithoutProbing() {
        viewModel.test("https://yaya.rua.plus/api/v1")

        assertEquals("请输入服务根地址，例如 https://yaya.example.com", viewModel.uiState.value.draftError)
        assertNull(viewModel.uiState.value.draftStatus)
        assertTrue(probed.isEmpty())
    }

    @Test
    fun test_validDraft_probesNormalizedAddressWithoutSaving() {
        val gate = CompletableDeferred<ServerStatus>()
        answer = { gate.await() }

        viewModel.test(" http://127.0.0.1:8088/ ")
        assertEquals(ServerStatus.Checking, viewModel.uiState.value.draftStatus)
        gate.complete(ServerStatus.Offline("无法连接到服务器"))

        assertEquals(listOf("http://127.0.0.1:8088"), probed)
        assertEquals(ServerStatus.Offline("无法连接到服务器"), viewModel.uiState.value.draftStatus)
        assertEquals("http://10.0.2.2:8088", settings.url())
    }

    @Test
    fun save_afterSuccessfulTest_reusesResultAndCountsSave() {
        viewModel.test("http://127.0.0.1:8088")

        assertTrue(viewModel.save("http://127.0.0.1:8088/"))

        val state = viewModel.uiState.value
        assertEquals("http://127.0.0.1:8088", settings.url())
        assertEquals("http://127.0.0.1:8088", state.url)
        assertEquals(online, state.status)
        assertEquals(1, state.savedCount)
        assertNull(state.draftStatus)
        assertEquals(1, probed.size)
    }

    @Test
    fun save_untestedDraft_probesNewAddress() {
        assertTrue(viewModel.save("https://yaya.rua.plus"))

        assertEquals(listOf("https://yaya.rua.plus"), probed)
        assertEquals(online, viewModel.uiState.value.status)
    }

    @Test
    fun save_invalidDraft_keepsPreviousAddress() {
        assertFalse(viewModel.save("ftp://yaya.rua.plus"))

        assertEquals("http://10.0.2.2:8088", settings.url())
        assertEquals("服务地址需要使用 HTTPS", viewModel.uiState.value.draftError)
        assertEquals(0, viewModel.uiState.value.savedCount)
    }

    @Test
    fun clearDraft_dropsTestResult() {
        viewModel.test("http://127.0.0.1:8088")

        viewModel.clearDraft()

        assertNull(viewModel.uiState.value.draftStatus)
        assertNull(viewModel.uiState.value.draftError)
    }
}
