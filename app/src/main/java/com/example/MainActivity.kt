package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.model.AppLanguage
import com.example.ui.screens.BiologyQaScreen
import com.example.ui.screens.BodyAtlasScreen
import com.example.ui.screens.Explore3DScreen
import com.example.ui.theme.BioAtlasTheme
import com.example.ui.theme.BioCyan
import com.example.ui.theme.BioTeal
import com.example.viewmodel.AppTab
import com.example.viewmodel.BioAtlasViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(true) }
            BioAtlasTheme(darkTheme = isDarkMode) {
                Bio3DAtlasApp(
                    isDarkMode = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bio3DAtlasApp(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: BioAtlasViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val savedTopics by viewModel.savedTopics.collectAsStateWithLifecycle()

    if (state.activeTab != AppTab.EXPLORE_3D) {
        BackHandler {
            viewModel.setTab(AppTab.EXPLORE_3D)
        }
    }

    val navItems = listOf(
        Triple(
            AppTab.EXPLORE_3D,
            if (language == AppLanguage.HINDI) "3D स्टूडियो" else "3D Studio",
            Pair(Icons.Filled.ViewInAr, Icons.Outlined.ViewInAr)
        ),
        Triple(
            AppTab.AI_QA,
            if (language == AppLanguage.HINDI) "AI प्रश्नोत्तर" else "Web AI Q&A",
            Pair(Icons.Filled.QuestionAnswer, Icons.Outlined.QuestionAnswer)
        ),
        Triple(
            AppTab.ATLAS,
            if (language == AppLanguage.HINDI) "शरीर एटलस" else "Body Atlas",
            Pair(Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Biotech,
                                contentDescription = null,
                                tint = BioCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3D BIO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        Surface(
                            onClick = { viewModel.toggleLanguage() },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("language_toggle_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Switch language",
                                    tint = BioTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = language.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle light or dark theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { (tab, label, icons) ->
                            val selected = state.activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.setTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.15f))
                        navItems.forEach { (tab, label, icons) ->
                            val selected = state.activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.setTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (state.activeTab) {
                        AppTab.EXPLORE_3D -> {
                            Explore3DScreen(
                                state = state,
                                language = language,
                                onAskAndVisualize = viewModel::askAndVisualize,
                                onSelectPreset = viewModel::selectPresetModel,
                                onSelectPart = viewModel::selectPart,
                                onDeepDivePart = viewModel::deepDiveSelectedPart,
                                onRotateDelta = viewModel::rotateDelta,
                                onAdjustZoom = viewModel::adjustZoom,
                                onResetCamera = viewModel::resetCamera,
                                onSetRenderMode = viewModel::setRenderMode,
                                onSetCutaway = viewModel::setCutaway,
                                onToggleLabels = viewModel::toggleLabels,
                                onToggleAutoRotate = viewModel::toggleAutoRotate,
                                onForceCustom3D = viewModel::forceGenerateCustom3D,
                                onGenerateStudioImage = viewModel::generateStudioImage
                            )
                        }
                        AppTab.AI_QA -> {
                            BiologyQaScreen(
                                messages = state.qaMessages,
                                language = language,
                                onAskQuestion = viewModel::askChatQuestion,
                                onViewTopicIn3D = viewModel::askAndVisualize,
                                onGenerateImage = { msg ->
                                    viewModel.generateImageForMessage(
                                        messageId = msg.id,
                                        topic = msg.linkedTopicQuery ?: msg.question
                                    )
                                },
                                onRetryQuestion = viewModel::retryQaMessage,
                                onClearHistory = viewModel::clearConversationHistory
                            )
                        }
                        AppTab.ATLAS -> {
                            BodyAtlasScreen(
                                language = language,
                                savedTopics = savedTopics,
                                onSelectPreset = { model -> viewModel.selectPresetModel(model, explainWithAi = true) },
                                onOpenSavedQueryIn3D = viewModel::askAndVisualize,
                                onDeleteSavedTopic = viewModel::deleteSavedTopic
                            )
                        }
                    }
                }
            }
        }
    }
}
