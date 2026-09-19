package dev.opencode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.opencode.android.data.OpenCodeRepository
import dev.opencode.android.ui.chat.ChatScreen
import dev.opencode.android.ui.files.FileEditorScreen
import dev.opencode.android.ui.files.FilesMode
import dev.opencode.android.ui.files.FilesScreen
import dev.opencode.android.ui.projects.ProjectsScreen
import dev.opencode.android.ui.settings.SettingsScreen
import dev.opencode.android.ui.theme.OpenCodeTheme

class MainActivity : ComponentActivity() {

    private val repo: OpenCodeRepository get() = OpenCodeRepository.get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenCodeTheme(darkTheme = repo.themeDark()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Main()
                }
            }
        }
    }
}

private data class EditorTarget(
    val mode: FilesMode,
    val path: String,
    val name: String,
)

private enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    FILES("Archivos", Icons.Filled.Folder),
    PROJECTS("Proyectos", Icons.Filled.FolderOpen),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

@Composable
private fun Main() {
    var selected by remember { mutableStateOf(Tab.CHAT) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }

    val editorTarget = editor
    if (editorTarget != null) {
        FileEditorScreen(
            mode = editorTarget.mode,
            path = editorTarget.path,
            name = editorTarget.name,
            onBack = { editor = null },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
            Tab.CHAT -> ChatScreen(modifier = Modifier.paddingOnlyBottom(padding))
            Tab.FILES -> FilesScreen(
                modifier = Modifier.paddingOnlyBottom(padding),
                onOpenFile = { mode, path, name ->
                    editor = EditorTarget(mode, path, name)
                },
            )
            Tab.PROJECTS -> ProjectsScreen(modifier = Modifier.paddingOnlyBottom(padding))
            Tab.SETTINGS -> SettingsScreen(modifier = Modifier.paddingOnlyBottom(padding))
        }
    }
}

private fun Modifier.paddingOnlyBottom(p: PaddingValues): Modifier =
    padding(bottom = p.calculateBottomPadding())