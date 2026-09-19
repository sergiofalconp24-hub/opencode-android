package dev.opencode.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.data.OpenCodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "http://127.0.0.1:4096",
    val password: String = "",
    val autoAccept: Boolean = true,
    val darkTheme: Boolean = true,
    val connectedUrl: String = "",
    val health: String? = null,
    val checking: Boolean = false,
)

class SettingsViewModel : ViewModel() {

    private val repo = OpenCodeRepository.get()

    private val _state = MutableStateFlow(
        SettingsUiState(
            baseUrl = repo.baseUrl(),
            password = repo.password(),
            autoAccept = repo.autoAcceptTools(),
            darkTheme = repo.themeDark(),
            connectedUrl = repo.connectedUrl,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setBaseUrl(v: String) = _state.update { it.copy(baseUrl = v) }
    fun setPassword(v: String) = _state.update { it.copy(password = v) }
    fun setAutoAccept(v: Boolean) {
        repo.setAutoAccept(v)
        _state.update { it.copy(autoAccept = v) }
    }
    fun setDarkTheme(v: Boolean) {
        repo.setThemeDark(v)
        _state.update { it.copy(darkTheme = v) }
    }

    fun save(success: (String) -> Unit, failure: (String) -> Unit) {
        val url = _state.value.baseUrl.trim().ifBlank { "http://127.0.0.1:4096" }
        val pass = _state.value.password
        repo.setConnection(url, pass)
        _state.update { it.copy(connectedUrl = repo.connectedUrl) }
        checkConnection(success, failure)
    }

    fun checkConnection(success: (String) -> Unit, failure: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val client = repo.client()
            val result = runCatching { client?.health() }
            val health = result.getOrNull()
            _state.update { it.copy(checking = false, health = health) }
            if (health != null) success(health) else failure("No se pudo conectar al servidor")
        }
    }
}