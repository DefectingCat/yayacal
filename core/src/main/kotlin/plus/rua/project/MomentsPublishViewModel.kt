package plus.rua.project

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import java.util.UUID

/** 发布草稿、上传状态及位置/可见性选择状态；失败时保留全部草稿。 */
data class MomentsPublishUiState(
    val text: String = "",
    val photos: List<String> = emptyList(),
    val selectedLocation: MomentLocationItem? = null,
    val availableLocations: List<MomentLocationItem> = MomentsLocationProvider.getDefaultLocations(),
    val locationSearchQuery: String = "",
    val visibility: String = "公开",
    val visibilityTags: List<String> = emptyList(),
    val isLocationPickerVisible: Boolean = false,
    val isVisibilityPickerVisible: Boolean = false,
    val isPublishing: Boolean = false,
    val isPreparingPhotos: Boolean = false,
    val error: String? = null,
) {
    val canPublish: Boolean get() = (text.isNotBlank() || photos.isNotEmpty()) && !isPublishing && !isPreparingPhotos
    val remainingPhotoSlots: Int get() = (MAX_PHOTOS - photos.size).coerceAtLeast(0)
    val displayedLocations: List<MomentLocationItem> get() = MomentsLocationProvider.filterLocations(availableLocations, locationSearchQuery)
    val formattedVisibility: String get() = visibility
    companion object {
        const val MAX_PHOTOS = 9
    }
}

/** 草稿按服务地址和账号隔离；发布身份在打开页面时固定，不随全局选择改变。 */
class MomentsPublishViewModel(
    storage: MomentsStorage,
    private val filesDir: File,
    private val repository: MomentsRepository,
    initialVisibility: String = "公开",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    draftNamespace: String = "default",
) : ViewModel() {
    private val actor = MomentAccount.findById(storage.getCurrentAccountId()).id
    private val draftDir = File(filesDir, "moments/drafts/${draftNamespace.hashCode()}/$actor").apply { mkdirs() }
    private val draftFile = File(draftDir, "draft.properties")
    private var requestId = UUID.randomUUID().toString()
    private val uploaded = mutableMapOf<String, String>()
    private val _uiState = MutableStateFlow(restore(initialVisibility))
    val uiState = _uiState.asStateFlow()

    private fun restore(initialVisibility: String): MomentsPublishUiState = runCatching {
        if (!draftFile.exists()) return@runCatching MomentsPublishUiState(visibility = initialVisibility)
        val data = Properties().apply { draftFile.inputStream().use { load(it) } }
        requestId = data.getProperty("requestId", requestId)
        val photos = data.getProperty("photos", "").split('\n').filter { it.isNotBlank() && File(it).isFile }
        photos.forEach { path -> data.getProperty("media:$path")?.let { uploaded[path] = it } }
        MomentsPublishUiState(
            text = data.getProperty("text", ""),
            photos = photos,
            visibility = data.getProperty("visibility", initialVisibility),
            selectedLocation = data.getProperty("location")?.let { MomentLocationItem(it, data.getProperty("address")) },
        )
    }.getOrDefault(MomentsPublishUiState(visibility = initialVisibility))

    private fun saveDraft() {
        val state = _uiState.value
        val data = Properties().apply {
            setProperty("requestId", requestId)
            setProperty("text", state.text)
            setProperty("photos", state.photos.joinToString("\n"))
            setProperty("visibility", state.visibility)
            state.selectedLocation?.let {
                setProperty("location", it.name)
                it.address?.let { address -> setProperty("address", address) }
            }
            uploaded.forEach { (path, id) -> setProperty("media:$path", id) }
        }
        try {
            val temp = File(draftDir, "draft.tmp")
            temp.outputStream().use { data.store(it, null) }
            check(temp.renameTo(draftFile)) { "草稿保存失败" }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "草稿保存失败，请检查存储空间") }
        }
    }

    private fun edit(change: (MomentsPublishUiState) -> MomentsPublishUiState) {
        if (_uiState.value.isPublishing) return
        val old = _uiState.value
        val updated = change(old)
        if (old != updated) requestId = UUID.randomUUID().toString()
        _uiState.value = updated.copy(error = null)
        saveDraft()
    }
    fun onTextChanged(text: String) = edit { it.copy(text = text) }
    fun addPhotoPaths(paths: List<String>) = edit { it.copy(photos = (it.photos + paths).take(9)) }
    fun addPhotosFromUris(context: Context, uris: List<Uri>) {
        if (_uiState.value.isPublishing || _uiState.value.isPreparingPhotos) return
        val selected = uris.take(_uiState.value.remainingPhotoSlots)
        _uiState.update { it.copy(isPreparingPhotos = true, error = null) }
        viewModelScope.launch {
            try {
                for (uri in selected) {
                    val path = withContext(ioDispatcher) {
                        val temp = copyMomentPhoto(context, uri)
                        try {
                            val photo = File(draftDir, "${UUID.randomUUID()}.image")
                            temp.copyTo(photo)
                            photo.absolutePath
                        } finally {
                            temp.delete()
                        }
                    }
                    addPhotoPaths(listOf(path))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message ?: "读取图片失败") }
            } finally {
                _uiState.update { it.copy(isPreparingPhotos = false) }
            }
        }
    }
    fun removePhotoAt(index: Int) {
        if (_uiState.value.isPublishing) return
        val path = _uiState.value.photos.getOrNull(index) ?: return
        edit { it.copy(photos = it.photos.filterIndexed { i, _ -> i != index }) }
        uploaded.remove(path)
        if (File(path).parentFile == draftDir) File(path).delete()
    }
    fun openLocationPicker() {
        _uiState.update { it.copy(isLocationPickerVisible = true, locationSearchQuery = "") }
    }
    fun closeLocationPicker() {
        _uiState.update { it.copy(isLocationPickerVisible = false) }
    }
    fun openVisibilityPicker() {
        _uiState.update { it.copy(isVisibilityPickerVisible = true) }
    }
    fun closeVisibilityPicker() {
        _uiState.update { it.copy(isVisibilityPickerVisible = false) }
    }
    fun selectVisibility(visibility: String, tags: List<String> = emptyList()) {
        require(visibility in listOf("公开", "私密") && tags.isEmpty())
        edit { it.copy(visibility = visibility, visibilityTags = emptyList(), isVisibilityPickerVisible = false) }
    }
    fun onLocationSearchQueryChanged(query: String) {
        _uiState.update { it.copy(locationSearchQuery = query) }
    }
    fun selectLocation(item: MomentLocationItem?) = edit { it.copy(selectedLocation = item?.takeUnless { location -> location.isNone }, isLocationPickerVisible = false, locationSearchQuery = "") }
    fun autoDetectLocation(context: Context) {
        viewModelScope.launch {
            val locations = MomentsLocationProvider.resolveLocation(context)
            _uiState.update { it.copy(availableLocations = locations) }
        }
    }

    /** 只有服务端确认成功才触发 onSuccess；失败或取消不清空草稿。 */
    fun publish(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canPublish) return
        _uiState.update { it.copy(isPublishing = true, error = null) }
        viewModelScope.launch {
            try {
                val media = state.photos.map { path ->
                    uploaded[path] ?: repository.upload(actor, File(path)).also {
                        uploaded[path] = it
                        saveDraft()
                    }
                }
                repository.publish(actor, requestId, state.text.trim(), media, state.visibility, state.selectedLocation?.name, state.selectedLocation?.address)
                draftFile.delete()
                state.photos.forEach { if (File(it).parentFile == draftDir) File(it).delete() }
                _uiState.update { it.copy(isPublishing = false) }
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isPublishing = false, error = e.message ?: "发布失败，请重试") }
            }
        }
    }
}
