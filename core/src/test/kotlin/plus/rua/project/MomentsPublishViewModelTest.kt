package plus.rua.project

import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MomentsPublishViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val storage = MomentsStorage(MomentsPublishVmTestInMemoryPrefs())
    private val repository = FakeMomentsRepository()
    private val dir = Files.createTempDirectory("moments-publish-test").toFile()

    @Before fun before() {
        Dispatchers.setMain(dispatcher)
        storage.saveCurrentAccountId("xiaobai")
    }

    @After fun after() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }
    private fun vm(prepare: suspend (File, File) -> File = { file, _ -> file }) = MomentsPublishViewModel(storage, dir, repository, ioDispatcher = dispatcher, preparePhoto = prepare)

    @Test fun preparePhotos_cancel_preservesFinishedDraftAndClearsBusyState() = runTest(dispatcher) {
        val vm = vm()
        vm.onTextChanged("取消后仍保留的草稿")
        var cancelled = false
        vm.preparePhotos(
            listOf(
                suspend { File.createTempFile("prepared-", ".image", dir).apply { writeText("finished") } },
                suspend {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                },
            ),
        )
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.photos.size)
        assertTrue(vm.uiState.value.isPreparingPhotos)
        vm.viewModelScope.cancel()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertFalse(vm.uiState.value.isPreparingPhotos)
        val restored = vm()
        assertEquals("取消后仍保留的草稿", restored.uiState.value.text)
        assertEquals("finished", File(restored.uiState.value.photos.single()).readText())
    }

    @Test fun publish_draftSaveFailure_keepsLegacySourceForRestart() = runTest(dispatcher) {
        val vm = vm { _, directory -> File.createTempFile("converted-", ".image", directory).apply { writeText("compressed") } }
        val draftDirectory = File(dir, "moments/drafts/${"default".hashCode()}/xiaobai")
        val original = File(draftDirectory, "legacy.image").apply { writeText("original") }
        vm.addPhotoPaths(listOf(original.absolutePath))
        File(draftDirectory, "draft.tmp").mkdir()
        repository.failPublish = true
        vm.publish {}
        advanceUntilIdle()
        assertTrue(original.exists())
        assertEquals("original", File(vm().uiState.value.photos.single()).readText())
    }

    @Test fun preparePhotos_partialFailure_retryKeepsFinishedPhotosAndReportsIndex() = runTest(dispatcher) {
        val calls = IntArray(3)
        var fail = true
        val vm = vm()
        val tasks = (0..2).map { index ->
            suspend {
                calls[index]++
                if (index == 1 && fail) error("图片压缩失败")
                File.createTempFile("prepared-test-", ".image", dir).apply { writeText("photo-$index") }
            }
        }
        vm.preparePhotos(tasks)
        assertTrue(vm.uiState.value.isPreparingPhotos)
        assertEquals(3, vm.uiState.value.preparingPhotoCount)
        assertFalse(vm.uiState.value.canPublish)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.failedPhotoIndex)
        assertEquals(MomentsPublishErrorSource.Photo, vm.uiState.value.errorSource)
        assertEquals(listOf("photo-0"), vm.uiState.value.photos.map { File(it).readText() })
        fail = false
        vm.retryPreparingPhotos()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 1), calls.toList())
        assertEquals(listOf("photo-0", "photo-1", "photo-2"), vm.uiState.value.photos.map { File(it).readText() })
        assertEquals(null, vm.uiState.value.failedPhotoIndex)
        assertFalse(vm.uiState.value.isPreparingPhotos)
        assertTrue(vm.uiState.value.canPublish)
        val restored = vm { _, _ -> error("已处理副本不应重复压缩") }
        restored.publish {}
        advanceUntilIdle()
        assertEquals(3, repository.uploads.size)
        assertFalse(File(vm.uiState.value.photos.first()).exists())
    }

    @Test fun publish_legacyPhoto_preparesOnceAndPersistsCopyAcrossRetry() = runTest(dispatcher) {
        val original = File(dir, "legacy.image").apply { writeText("original") }
        var calls = 0
        val vm = vm { _, directory ->
            calls++
            File.createTempFile("converted-", ".image", directory).apply { writeText("compressed") }
        }
        vm.addPhotoPaths(listOf(original.absolutePath))
        repository.failPublish = true
        vm.publish {}
        advanceUntilIdle()
        assertEquals("original", original.readText())
        assertEquals("compressed", File(vm.uiState.value.photos.single()).readText())
        assertEquals(1, calls)
        val restored = vm { _, _ -> error("重试不应重复压缩") }
        repository.failPublish = false
        restored.publish {}
        advanceUntilIdle()
        assertEquals(1, repository.uploads.size)
        assertEquals(repository.attempts.first(), repository.attempts.last())
        assertTrue(original.exists())
    }

    @Test fun unchangedPickers_afterFailure_reusesRequestId() = runTest(dispatcher) {
        val vm = vm()
        vm.onTextChanged("重试同一条动态")
        repository.failPublish = true
        vm.publish {}
        advanceUntilIdle()
        vm.openVisibilityPicker()
        vm.selectVisibility("公开")
        vm.openLocationPicker()
        vm.selectLocation(null)
        vm.publish {}
        advanceUntilIdle()
        assertEquals(repository.attempts.first().second, repository.attempts.last().second)
    }

    @Test fun failedPublish_restoreAndRetry_reusesRequestAndMedia() = runTest(dispatcher) {
        val photo = File(dir, "photo").apply { writeText("fixture") }
        val first = vm()
        first.onTextChanged("晚安 🐶")
        first.addPhotoPaths(listOf(photo.absolutePath))
        repository.failPublish = true
        var success = false
        first.publish { success = true }
        advanceUntilIdle()
        assertFalse(success)
        assertEquals("晚安 🐶", first.uiState.value.text)
        assertFalse(first.uiState.value.isPublishing)
        assertEquals(MomentsPublishErrorSource.Publish, first.uiState.value.errorSource)
        assertEquals("fixture", File(first.uiState.value.photos.single()).readText())
        val restored = vm()
        assertEquals(first.uiState.value.text, restored.uiState.value.text)
        repository.failPublish = false
        restored.publish { success = true }
        advanceUntilIdle()
        assertTrue(success)
        assertEquals(repository.attempts.first(), repository.attempts.last())
        assertEquals(1, repository.uploads.size)
        assertTrue(vm().uiState.value.text.isEmpty())
        assertTrue(storage.getPosts().isEmpty())
    }

    @Test
    fun expiredTemporaryMedia_retryUploadsAgainWithoutLosingDraft() = runTest(dispatcher) {
        val photo = File(dir, "photo").apply { writeText("fixture") }
        val vm = vm()
        vm.addPhotoPaths(listOf(photo.absolutePath))
        repository.publishFailure = MomentsApiException(400, "图片不存在或不属于当前账号")
        vm.publish {}
        advanceUntilIdle()
        assertEquals("fixture", File(vm.uiState.value.photos.single()).readText())
        repository.publishFailure = null
        vm.publish {}
        advanceUntilIdle()
        assertEquals(2, repository.uploads.size)
        assertEquals(repository.attempts.first().second, repository.attempts.last().second)
    }

    @Test fun switchGlobalAccount_existingDraftKeepsOriginalAuthor() = runTest(dispatcher) {
        val first = vm()
        first.onTextChanged("小白的草稿")
        storage.saveCurrentAccountId("xiaojimao")
        assertTrue(vm().uiState.value.text.isEmpty())
        first.publish {}
        advanceUntilIdle()
        assertEquals("xiaobai", repository.attempts.single().first)
    }

    @Test fun changeAfterTimeout_usesNewRequestId() = runTest(dispatcher) {
        val vm = vm()
        vm.onTextChanged("原文")
        repository.failPublish = true
        vm.publish {}
        advanceUntilIdle()
        vm.onTextChanged("修订内容")
        vm.publish {}
        advanceUntilIdle()
        assertEquals(2, repository.attempts.map { it.second }.distinct().size)
    }

    @Test fun emptyDraftCannotPublish_andPhotosAreCapped() = runTest(dispatcher) {
        val vm = vm()
        vm.publish { error("空草稿不能发表") }
        assertFalse(vm.uiState.value.canPublish)
        vm.addPhotoPaths((1..12).map { "$it.jpg" })
        assertEquals(9, vm.uiState.value.photos.size)
        vm.removePhotoAt(0)
        assertEquals(8, vm.uiState.value.photos.size)
        vm.selectVisibility("私密")
        assertEquals("私密", vm.uiState.value.visibility)
    }

    @Test fun draftSaveFailure_preservesTextAndDoesNotOfferPublishRetry() {
        val vm = vm()
        val draftDirectory = File(dir, "moments/drafts/${"default".hashCode()}/xiaobai")
        File(draftDirectory, "draft.tmp").mkdir()
        vm.onTextChanged("还在编辑中的内容")
        assertEquals("还在编辑中的内容", vm.uiState.value.text)
        assertEquals(MomentsPublishErrorSource.Draft, vm.uiState.value.errorSource)
        assertTrue(repository.attempts.isEmpty())
    }

    @Test fun editAfterPublishFailure_clearsOldOperationError() = runTest(dispatcher) {
        val vm = vm()
        vm.onTextChanged("原文")
        repository.failPublish = true
        vm.publish {}
        advanceUntilIdle()
        vm.onTextChanged("修改后的文字")
        assertEquals(null, vm.uiState.value.error)
        assertEquals(null, vm.uiState.value.errorSource)
    }
}

private class MomentsPublishVmTestInMemoryPrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? Set<String> ?: defValues
    }
    override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            temp[key] = values
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            temp[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
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
