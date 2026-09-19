package dev.opencode.android.ui.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.data.OpenCodeRepository
import dev.opencode.android.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorUiState(
    val mode: FilesMode = FilesMode.DEVICE,
    val path: String = "",
    val name: String = "",
    val text: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val canWrite: Boolean = false,
    val lastSaved: Boolean = false,
)

class FileEditorViewModel(
    app: Application,
    mode: FilesMode,
    path: String,
    name: String,
) : AndroidViewModel(app) {

    private val repo = OpenCodeRepository.get()

    private val _state = MutableStateFlow(
        EditorUiState(
            mode = mode,
            path = path,
            name = name,
            canWrite = mode == FilesMode.DEVICE && Permissions.hasAllFilesAccess(),
        )
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                when (_state.value.mode) {
                    FilesMode.DEVICE -> readFile()
                    FilesMode.PROJECT -> repo.client()?.readContent(_state.value.path.replace("\\", "/"))
                }
            }
            _state.update {
                it.copy(
                    text = content ?: "",
                    loading = false,
                    error = if (content == null) "No se pudo leer el archivo" else null,
                    lastSaved = false,
                )
            }
        }
    }

    fun setText(t: String) {
        _state.update { it.copy(text = t) }
    }

    /** Guarda en el dispositivo. En modo PROYECTO avisa que no hay ruta de escritura HTTP. */
    fun saveToDevice() {
        if (_state.value.mode == FilesMode.DEVICE && _state.value.canWrite) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        File(_state.value.path).writeText(_state.value.text)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                _state.update { it.copy(lastSaved = ok, canWrite = _state.value.canWrite) }
            }
        } else {
            _state.update { it.copy(lastSaved = false) }
        }
    }

    fun markSaved() {
        _state.update { it.copy(lastSaved = true, canWrite = true) }
    }

    /** Escribe en un destino SAF elegido por el usuario. */
    fun writeTo(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>().contentResolver
                    app.openOutputStream(uri)?.use { it.write(_state.value.text.toByteArray()) } != null
                } catch (_: Exception) {
                    false
                }
            }
            _state.update { it.copy(lastSaved = ok, canWrite = true) }
        }
    }

    private fun readFile(): String? {
        return try {
            File(_state.value.path).readText()
        } catch (_: Exception) {
            null
        }
    }
}