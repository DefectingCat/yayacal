package plus.rua.project

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MomentsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val prefs = MomentsVmTestInMemoryPrefs()
    private val storage = MomentsStorage(prefs)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "moments_vm_test")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun initialState_loadsFromStorage() = runTest(testDispatcher) {
        storage.clear()
        storage.saveUsername("CustomUser")
        storage.saveAvatarPath("/custom/avatar.jpg")

        val viewModel =
            MomentsViewModel(
                storage = storage,
                filesDir = tempDir,
                ioDispatcher = testDispatcher,
            )

        val state = viewModel.uiState.value
        assertEquals("CustomUser", state.username)
        assertEquals("/custom/avatar.jpg", state.avatarPath)
        assertNull(state.coverPath)
    }

    @Test
    fun setAvatarPath_updatesStorageAndUiState() = runTest(testDispatcher) {
        storage.clear()
        val viewModel =
            MomentsViewModel(
                storage = storage,
                filesDir = tempDir,
                ioDispatcher = testDispatcher,
            )

        assertNull(viewModel.uiState.value.avatarPath)

        viewModel.setAvatarPath("/new/avatar.jpg")
        assertEquals("/new/avatar.jpg", viewModel.uiState.value.avatarPath)
        assertEquals("/new/avatar.jpg", storage.getAvatarPath())
    }

    @Test
    fun setCoverPath_updatesStorageAndUiState() = runTest(testDispatcher) {
        storage.clear()
        val viewModel =
            MomentsViewModel(
                storage = storage,
                filesDir = tempDir,
                ioDispatcher = testDispatcher,
            )

        assertNull(viewModel.uiState.value.coverPath)

        viewModel.setCoverPath("/new/cover.jpg")
        assertEquals("/new/cover.jpg", viewModel.uiState.value.coverPath)
        assertEquals("/new/cover.jpg", storage.getCoverPath())
    }

    @Test
    fun switchAccount_updatesUsernameAvatarAndStorage() = runTest(testDispatcher) {
        storage.clear()
        val viewModel =
            MomentsViewModel(
                storage = storage,
                filesDir = tempDir,
                ioDispatcher = testDispatcher,
            )

        viewModel.switchAccount(MomentAccount.ACCOUNT_XIAOBAI, "/path/to/avatar_xiaobai.jpg")
        assertEquals(MomentAccount.ID_XIAOBAI, viewModel.uiState.value.currentAccountId)
        assertEquals("小白", viewModel.uiState.value.username)
        assertEquals("/path/to/avatar_xiaobai.jpg", viewModel.uiState.value.avatarPath)
        assertEquals(MomentAccount.ID_XIAOBAI, storage.getCurrentAccountId())
        assertEquals("小白", storage.getUsername())
        assertEquals("/path/to/avatar_xiaobai.jpg", storage.getAvatarPath())

        viewModel.switchAccount(MomentAccount.ACCOUNT_XIAOJIMAO, "/path/to/avatar_xiaojimao.jpg")
        assertEquals(MomentAccount.ID_XIAOJIMAO, viewModel.uiState.value.currentAccountId)
        assertEquals("小鸡毛", viewModel.uiState.value.username)
        assertEquals("/path/to/avatar_xiaojimao.jpg", viewModel.uiState.value.avatarPath)
        assertEquals(MomentAccount.ID_XIAOJIMAO, storage.getCurrentAccountId())
        assertEquals("小鸡毛", storage.getUsername())
        assertEquals("/path/to/avatar_xiaojimao.jpg", storage.getAvatarPath())
    }

    @Test
    fun interactions_persistAndRejectEmptyOrDeletedPosts() = runTest(testDispatcher) {
        storage.savePost(MomentPost(id = "post", timestamp = 100L))
        storage.saveUsername("鸭鸭")
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(200L)
        }
        val viewModel = MomentsViewModel(storage, tempDir, testDispatcher, clock)

        viewModel.toggleLike("post")
        assertTrue(storage.getPosts().single().isLikedByMe)
        assertFalse(viewModel.addComment("post", " \n "))
        assertTrue(viewModel.addComment("post", "  好可爱🐱 \n", replyToName = "朋友"))
        assertTrue(viewModel.addComment("post", "", photoPath = "/private/comment.jpg"))

        val reloaded = MomentsViewModel(storage, tempDir, testDispatcher).uiState.value.posts.single()
        assertTrue(reloaded.isLikedByMe)
        assertEquals(2, reloaded.comments.size)
        assertEquals("好可爱🐱", reloaded.comments[0].text)
        assertEquals("鸭鸭", reloaded.comments[0].authorName)
        assertEquals("朋友", reloaded.comments[0].replyToName)
        assertEquals(200L, reloaded.comments[0].timestamp)
        assertEquals("/private/comment.jpg", reloaded.comments[1].photoPath)

        viewModel.toggleLike("post")
        assertFalse(storage.getPosts().single().isLikedByMe)
        storage.deletePost("post")
        viewModel.toggleLike("post")
        assertFalse(viewModel.addComment("post", "不会复活已删除的动态"))
        assertTrue(storage.getPosts().isEmpty())
    }

    @Test
    fun deletePost_cleansOwnedPhotosAndKeepsSharedOrExternalFiles() = runTest(testDispatcher) {
        val photoDir = File(tempDir, MomentsViewModel.MOMENTS_DIR_NAME).apply { mkdirs() }
        val photo = File(photoDir, "post.jpg").apply { writeText("post") }
        val commentPhoto = File(photoDir, "comment.jpg").apply { writeText("comment") }
        val sharedPhoto = File(photoDir, "shared.jpg").apply { writeText("shared") }
        val externalPhoto = File(tempDir, "keep.jpg").apply { writeText("external") }
        storage.savePost(
            MomentPost(
                id = "delete",
                photoPaths = listOf(photo.path, sharedPhoto.path, externalPhoto.path),
                comments = listOf(MomentComment(authorName = "鸭鸭", text = "", photoPath = commentPhoto.path)),
            ),
        )
        storage.savePost(MomentPost(id = "keep", photoPaths = listOf(sharedPhoto.path)))
        val viewModel = MomentsViewModel(storage, tempDir, testDispatcher)

        viewModel.deletePost("delete")
        testScheduler.runCurrent()

        assertEquals(listOf("keep"), viewModel.uiState.value.posts.map { it.id })
        assertEquals(listOf("keep"), storage.getPosts().map { it.id })
        assertFalse(photo.exists())
        assertFalse(commentPhoto.exists())
        assertTrue(sharedPhoto.exists())
        assertTrue(externalPhoto.exists())
    }
}

private class MomentsVmTestInMemoryPrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? Set<String> ?: defValues
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = data[key] as? Int ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = data[key] as? Long ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = data[key] as? Float ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor {
            temp[key] = values
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            temp[key] = this
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for ((k, v) in temp) {
                if (v === this) data.remove(k) else data[k] = v
            }
            temp.clear()
            clear = false
        }
    }
}
