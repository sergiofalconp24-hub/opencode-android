package dev.opencode.android.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opencode.android.data.OpenCodeRepository
import dev.opencode.android.data.model.ProjectInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val loading: Boolean = true,
    val currentProject: ProjectInfo? = null,
    val projects: List<ProjectInfo> = emptyList(),
    val defaultModel: String? = null,
    val agents: List<String> = emptyList(),
    val error: String? = null,
)

class ProjectsViewModel : ViewModel() {

    private val repo = OpenCodeRepository.get()

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val client = repo.client()
            if (client == null) {
                _state.update { it.copy(loading = false, error = "Servidor no configurado") }
                return@launch
            }
            val currentP = runCatching { client.currentProject() }.getOrNull()
            val projects = runCatching { client.listProjects() }.getOrElse { emptyList() }
            val model = runCatching { client.defaultModel() }.getOrNull()
            val agents = runCatching { client.agents().map { it.name } }.getOrElse { emptyList() }
            _state.update {
                it.copy(
                    loading = false,
                    currentProject = currentP,
                    projects = projects,
                    defaultModel = model,
                    agents = agents,
                    error = null,
                )
            }
        }
    }
}