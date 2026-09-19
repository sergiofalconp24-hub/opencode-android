package dev.opencode.android.ui.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.data.OpenCodeRepository
import dev.opencode.android.data.model.FileEntry
import dev.opencode.android.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class FilesMode { DEVICE, PROJECT }

data class FilesUiState(
    val mode: FilesMode = FilesMode.DEVICE,
    val currentPath: String? = null,
    val entries: List<FileEntry> = emptyList(),
    val history: List<String> = emptyList(),
    val loading: Boolean = false,
    val hasAccess: Boolean = false,
    val note: String? = null,
)

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = OpenCodeRepository.get()

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        setMode(FilesMode.DEVICE)
    }

    fun setMode(mode: FilesMode) {
        _state.update { it.copy(mode = mode, currentPath = null, entries = emptyList(), history = emptyList(), note = null) }
        when (mode) {
            FilesMode.DEVICE -> refreshDevice()
            FilesMode.PROJECT -> refreshProject(null)
        }
    }

    fun refresh() {
        when (_state.value.mode) {
            FilesMode.DEVICE -> refreshDevice()
            FilesMode.PROJECT -> refreshProject(_state.value.currentPath)
        }
    }

    fun navigateTo(entry: FileEntry) {
        if (!entry.isDirectory) {
            _state.update { it.copy(note = "Toca el archivo para abrirlo en el editor") }
            return
        }
        when (_state.value.mode) {
            FilesMode.DEVICE -> navigateDevice(entry.path)
            FilesMode.PROJECT -> refreshProject(entry.path)
        }
    }

    /** Lee el contenido de un archivo (para el editor). Suspend: llamar en corrutina. */
    suspend fun loadFileContent(entry: FileEntry): String? {
        if (entry.isDirectory) return null
        return when (_state.value.mode) {
            FilesMode.DEVICE -> readDeviceFile(entry.path)
            FilesMode.PROJECT -> repo.client()?.readContent(entry.path.replace("\\", "/"))
        }
    }

    fun goUp() {
        val hist = _state.value.history
        if (hist.isEmpty()) return
        val prev = hist.last()
        val newHist = hist.dropLast(1)
        when (_state.value.mode) {
            FilesMode.DEVICE -> {
                _state.update { it.copy(currentPath = prev, history = newHist) }
                refreshDevice()
            }
            FilesMode.PROJECT -> {
                _state.update { it.copy(currentPath = prev, history = newHist) }
                refreshProject(prev)
            }
        }
    }

    private fun refreshDevice() {
        if (!Permissions.hasReadStorage(getApplication())) {
            _state.update { it.copy(hasAccess = false, entries = emptyList(), note = null) }
            return
        }
        _state.update { it.copy(hasAccess = true, loading = true) }
        val path = _state.value.currentPath ?: deviceRoot()
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { listDeviceFiles(path) }
            _state.update { it.copy(entries = entries, loading = false, currentPath = path) }
        }
    }

    private fun navigateDevice(dir: String) {
        _state.update { it.copy(history = it.history + (it.currentPath ?: deviceRoot())) }
        refreshDeviceAt(dir)
    }

    private fun refreshDeviceAt(dir: String) {
        _state.update { it.copy(currentPath = dir, loading = true) }
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { listDeviceFiles(dir) }
            _state.update { it.copy(entries = entries, loading = false) }
        }
    }

    private fun refreshProject(path: String?) {
        _state.update { it.copy(loading = true, hasAccess = true) }
        viewModelScope.launch {
            val entries = runCatching { repo.client()?.listDir(path) ?: emptyList() }.getOrDefault(emptyList())
            if (entries.isEmpty() && path == null) {
                _state.update { it.copy(loading = false, note = "No hay proyecto conectado. Abre uno en la pestaña Proyectos o usa el chat.") }
            } else {
                _state.update { it.copy(entries = entries, loading = false, currentPath = path, note = null) }
            }
        }
    }

    private suspend fun listDeviceFiles(dir: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val d = File(dir)
        val files = (d.listFiles() ?: emptyArray()).toList()
        files
            .filter { !it.name.startsWith(".") }
            .map { FileEntry(name = it.name, path = it.absolutePath, isDirectory = it.isDirectory) }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private suspend fun readDeviceFile(path: String): String? = withContext(Dispatchers.IO) {
        try {
            File(path).readText()
        } catch (_: Exception) {
            null
        }
    }

    private fun deviceRoot(): String =
        android.os.Environment.getExternalStorageDirectory().absolutePath
}