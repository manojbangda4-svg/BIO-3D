package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PresetAnatomyCatalog
import com.example.model.AiAnswerState
import com.example.model.Anatomy3DModel
import com.example.model.Anatomy3DPart
import com.example.model.AppLanguage
import com.example.model.ImageGenState
import com.example.model.RenderMode
import com.example.model.WebSource
import com.example.ui.components.Anatomy3DCanvas
import com.example.ui.components.FullscreenImageDialog
import com.example.ui.components.MarkdownRichText
import com.example.ui.theme.BioCoral
import com.example.ui.theme.BioCyan
import com.example.ui.theme.BioGold
import com.example.ui.theme.BioTeal
import com.example.viewmodel.BioStudioState

@Composable
fun Explore3DScreen(
    state: BioStudioState,
    language: AppLanguage,
    onAskAndVisualize: (String) -> Unit,
    onSelectPreset: (Anatomy3DModel, Boolean) -> Unit,
    onSelectPart: (Anatomy3DPart) -> Unit,
    onDeepDivePart: (Anatomy3DPart) -> Unit,
    onRotateDelta: (Float, Float) -> Unit,
    onAdjustZoom: (Float) -> Unit,
    onResetCamera: () -> Unit,
    onSetRenderMode: (RenderMode) -> Unit,
    onSetCutaway: (Float) -> Unit,
    onToggleLabels: () -> Unit,
    onToggleAutoRotate: () -> Unit,
    onForceCustom3D: (String) -> Unit,
    onGenerateStudioImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedPart = state.activeModel.parts.firstOrNull { it.id == state.selectedPartId }
    val listState = rememberLazyListState()

    val submitQuery = {
        if (queryText.isNotBlank()) {
            keyboard?.hide()
            onAskAndVisualize(queryText)
        }
    }

    LaunchedEffect(state.studioAiState, state.studioImageState) {
        if (state.studioAiState is AiAnswerState.Loading || state.studioImageState is ImageGenState.Loading) {
            listState.animateScrollToItem(2)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuerySearchSection(
                        queryText = queryText,
                        onQueryChange = { queryText = it },
                        onSubmit = submitQuery,
                        language = language
                    )
                    PresetStrip(
                        activeModelId = state.activeModel.id,
                        language = language,
                        onSelectPreset = onSelectPreset
                    )
                    Viewport3DCard(
                        state = state,
                        language = language,
                        onRotateDelta = onRotateDelta,
                        onAdjustZoom = onAdjustZoom,
                        onResetCamera = onResetCamera,
                        onSetRenderMode = onSetRenderMode,
                        onSetCutaway = onSetCutaway,
                        onToggleLabels = onToggleLabels,
                        onToggleAutoRotate = onToggleAutoRotate,
                        onSelectPart = onSelectPart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        InternalPartsSection(
                            model = state.activeModel,
                            selectedPartId = state.selectedPartId,
                            selectedPart = selectedPart,
                            language = language,
                            onSelectPart = onSelectPart,
                            onDeepDivePart = onDeepDivePart,
                            onForceCustom3D = {
                                onForceCustom3D(
                                    state.lastQuery.ifBlank { state.activeModel.displayTitle(language) }
                                )
                            }
                        )
                    }
                    item {
                        AiExplanationSection(
                            aiState = state.studioAiState,
                            imageState = state.studioImageState,
                            activeModel = state.activeModel,
                            language = language,
                            onExplainFullOrgan = {
                                onAskAndVisualize(state.activeModel.displayTitle(language))
                            },
                            onGenerateImage = {
                                onGenerateStudioImage(
                                    state.lastQuery.ifBlank { state.activeModel.displayTitle(language) }
                                )
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuerySearchSection(
                        queryText = queryText,
                        onQueryChange = { queryText = it },
                        onSubmit = submitQuery,
                        language = language
                    )
                }
                item {
                    PresetStrip(
                        activeModelId = state.activeModel.id,
                        language = language,
                        onSelectPreset = onSelectPreset
                    )
                }
                item {
                    Viewport3DCard(
                        state = state,
                        language = language,
                        onRotateDelta = onRotateDelta,
                        onAdjustZoom = onAdjustZoom,
                        onResetCamera = onResetCamera,
                        onSetRenderMode = onSetRenderMode,
                        onSetCutaway = onSetCutaway,
                        onToggleLabels = onToggleLabels,
                        onToggleAutoRotate = onToggleAutoRotate,
                        onSelectPart = onSelectPart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(315.dp)
                    )
                }
                item {
                    AiExplanationSection(
                        aiState = state.studioAiState,
                        imageState = state.studioImageState,
                        activeModel = state.activeModel,
                        language = language,
                        onExplainFullOrgan = {
                            onAskAndVisualize(
                                state.lastQuery.ifBlank { state.activeModel.displayTitle(language) }
                            )
                        },
                        onGenerateImage = {
                            onGenerateStudioImage(
                                state.lastQuery.ifBlank { state.activeModel.displayTitle(language) }
                            )
                        }
                    )
                }
                item {
                    InternalPartsSection(
                        model = state.activeModel,
                        selectedPartId = state.selectedPartId,
                        selectedPart = selectedPart,
                        language = language,
                        onSelectPart = onSelectPart,
                        onDeepDivePart = onDeepDivePart,
                        onForceCustom3D = {
                            onForceCustom3D(
                                state.lastQuery.ifBlank { state.activeModel.displayTitle(language) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuerySearchSection(
    queryText: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    language: AppLanguage
) {
    val placeholder = if (language == AppLanguage.HINDI) {
        "कोई भी अंग या सवाल पूछें (जैसे: लीवर, माइटोकॉन्ड्रिया, डीएनए, हड्डी)..."
    } else {
        "Ask about any body part or internal structure (e.g. Liver, Retina, Nephron)..."
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = queryText,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search body part",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (queryText.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("studio_query_input")
        )

        Button(
            onClick = onSubmit,
            enabled = queryText.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("studio_ask_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = if (language == AppLanguage.HINDI) "3D और उत्तर पाएं" else "Ask & View 3D",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (language == AppLanguage.HINDI) "3D जानें" else "3D AI",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PresetStrip(
    activeModelId: String,
    language: AppLanguage,
    onSelectPreset: (Anatomy3DModel, Boolean) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(PresetAnatomyCatalog.allModels, key = { it.id }) { preset ->
            val selected = preset.id == activeModelId
            FilterChip(
                selected = selected,
                onClick = { onSelectPreset(preset, false) },
                label = {
                    Text(
                        text = preset.displayTitle(language),
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("preset_chip_${preset.id}")
            )
        }
    }
}

@Composable
private fun Viewport3DCard(
    state: BioStudioState,
    language: AppLanguage,
    onRotateDelta: (Float, Float) -> Unit,
    onAdjustZoom: (Float) -> Unit,
    onResetCamera: () -> Unit,
    onSetRenderMode: (RenderMode) -> Unit,
    onSetCutaway: (Float) -> Unit,
    onToggleLabels: () -> Unit,
    onToggleAutoRotate: () -> Unit,
    onSelectPart: (Anatomy3DPart) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Strip inside 3D Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.activeModel.displayTitle(language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.activeModel.isAiGenerated) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "AI 3D",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = state.activeModel.displaySystem(language) +
                            if (language == AppLanguage.HINDI) " • घुमाने के लिए ड्रैग करें" else " • Drag to rotate 360°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onToggleAutoRotate,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (state.autoRotate) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle auto rotate",
                            tint = if (state.autoRotate) BioTeal else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleLabels,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (state.showLabels) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle 3D labels",
                            tint = if (state.showLabels) BioCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onResetCamera,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Reset 3D camera"
                        )
                    }
                }
            }

            if (state.isGenerating3D) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (language == AppLanguage.HINDI) {
                        "AI इस भाग का इंटरैक्टिव 3D मॉडल तैयार कर रहा है..."
                    } else {
                        "Building interactive 3D anatomical blueprint..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = BioTeal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            state.blueprintError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // 3D Viewport Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Anatomy3DCanvas(
                    model = state.activeModel,
                    selectedPartId = state.selectedPartId,
                    language = language,
                    renderMode = state.renderMode,
                    cutawayProgress = state.cutawayProgress,
                    showLabels = state.showLabels,
                    autoRotate = state.autoRotate,
                    yaw = state.yaw,
                    pitch = state.pitch,
                    zoom = state.zoom,
                    onRotateDelta = onRotateDelta,
                    onPartSelected = onSelectPart,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left Render Mode selector
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color(0xAA06131F), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderModePill(
                        label = "3D",
                        selected = state.renderMode == RenderMode.SOLID,
                        onClick = { onSetRenderMode(RenderMode.SOLID) }
                    )
                    RenderModePill(
                        label = "X-Ray",
                        selected = state.renderMode == RenderMode.XRAY,
                        onClick = { onSetRenderMode(RenderMode.XRAY) }
                    )
                    RenderModePill(
                        label = "Mesh",
                        selected = state.renderMode == RenderMode.WIREFRAME,
                        onClick = { onSetRenderMode(RenderMode.WIREFRAME) }
                    )
                }

                // Top-right Zoom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xAA06131F), RoundedCornerShape(12.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { onAdjustZoom(0.18f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in 3D",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onAdjustZoom(-0.18f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom out 3D",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Cutaway / Internal Dissection Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Dissection cutaway depth",
                    tint = BioCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == AppLanguage.HINDI) "आंतरिक कट" else "Cutaway",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = state.cutawayProgress,
                    onValueChange = onSetCutaway,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = BioCyan,
                        activeTrackColor = BioTeal
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cutaway_slider")
                )
                Text(
                    text = "${(state.cutawayProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

@Composable
private fun RenderModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) BioCyan else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF06131F) else Color(0xFFE2E8F0),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun InternalPartsSection(
    model: Anatomy3DModel,
    selectedPartId: String?,
    selectedPart: Anatomy3DPart?,
    language: AppLanguage,
    onSelectPart: (Anatomy3DPart) -> Unit,
    onDeepDivePart: (Anatomy3DPart) -> Unit,
    onForceCustom3D: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == AppLanguage.HINDI) {
                        "अंदर के भाग चुनें (${model.parts.size})"
                    } else {
                        "Inspect Internal Parts (${model.parts.size})"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onForceCustom3D,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (language == AppLanguage.HINDI) "AI 3D री-बिल्ड" else "AI 3D Rebuild",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Text(
                text = model.displaySummary(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                model.parts.forEach { part ->
                    val isSel = part.id == selectedPartId
                    val dotColor = Color(part.colorHex)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            )
                            .border(
                                width = if (isSel) 1.5.dp else 1.dp,
                                color = if (isSel) BioGold else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectPart(part) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = part.displayName(language),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedPart != null) {
                selectedPart?.let { part ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(part.colorHex).copy(alpha = 0.14f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color(part.colorHex).copy(alpha = 0.45f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = part.displayName(language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = Color(part.colorHex).copy(alpha = 0.22f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = part.layer.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = part.displayRole(language),
                            style = MaterialTheme.typography.labelLarge,
                            color = BioTeal
                        )
                        Text(
                            text = part.displayDetail(language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { onDeepDivePart(part) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("deep_dive_part_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.HINDI) {
                                    "इस आंतरिक भाग को AI और वेब से विस्तार से जानें"
                                } else {
                                    "Explain This Internal Part with Web AI"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiExplanationSection(
    aiState: AiAnswerState,
    imageState: ImageGenState,
    activeModel: Anatomy3DModel,
    language: AppLanguage,
    onExplainFullOrgan: () -> Unit,
    onGenerateImage: () -> Unit
) {
    val context = LocalContext.current
    var fullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fullscreenBitmap?.let { bmp ->
        FullscreenImageDialog(
            bitmap = bmp,
            onDismiss = { fullscreenBitmap = null }
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Web grounded AI",
                        tint = BioCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == AppLanguage.HINDI) {
                            "वेब-सत्यापित AI 3D व्याख्या"
                        } else {
                            "Web-Grounded AI 3D Explanation"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (imageState !is ImageGenState.Loading) {
                    OutlinedButton(
                        onClick = onGenerateImage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("studio_generate_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = BioGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.HINDI) "HD इमेज" else "HD Image",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Runtime AI Anatomical Image Display
            when (imageState) {
                is ImageGenState.Idle -> Unit
                is ImageGenState.Loading -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = BioGold
                            )
                            Text(
                                text = if (language == AppLanguage.HINDI) {
                                    "AI मेडिकल HD इमेज बना रहा है (30–60 सेकंड लग सकते हैं)..."
                                } else {
                                    "Generating HD anatomical illustration (may take 30–60s)..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = BioGold
                            )
                        }
                    }
                }
                is ImageGenState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Image(
                            bitmap = imageState.bitmap.asImageBitmap(),
                            contentDescription = "AI generated anatomical diagram of ${activeModel.displayTitle(language)}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF071625))
                                .clickable { fullscreenBitmap = imageState.bitmap }
                                .testTag("studio_generated_image")
                        )
                        Text(
                            text = if (language == AppLanguage.HINDI) {
                                "बड़ा देखने के लिए इमेज पर टैप करें"
                            } else {
                                "Tap image to zoom fullscreen"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ImageGenState.Error -> {
                    Surface(
                        color = BioCoral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = imageState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = BioCoral
                            )
                            OutlinedButton(
                                onClick = onGenerateImage,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (language == AppLanguage.HINDI) "इमेज दोबारा बनाएं" else "Retry Image")
                            }
                        }
                    }
                }
            }

            when (aiState) {
                is AiAnswerState.Idle -> {
                    Text(
                        text = if (language == AppLanguage.HINDI) {
                            "ऊपर किसी भी शरीर के भाग (जैसे: कोशिका, मस्तिष्क, हड्डियाँ, लीवर, नेफ्रॉन) के बारे में पूछें, या '${activeModel.displayTitle(language)}' की पूरी वेब-सत्यापित व्याख्या देखने के लिए नीचे टैप करें।"
                        } else {
                            "Ask anything about any organ, cell, or internal structure above, or tap below for a full web-grounded 3D walkthrough of ${activeModel.displayTitle(language)}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onExplainFullOrgan,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("explain_full_organ_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == AppLanguage.HINDI) {
                                "${activeModel.displayTitle(language)} को पूरी तरह समझें"
                            } else {
                                "Explain ${activeModel.displayTitle(language)} in Full Detail"
                            }
                        )
                    }
                }
                is AiAnswerState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = if (language == AppLanguage.HINDI) {
                                "वेब स्रोतों और मेडिकल डेटा से सटीक जानकारी लाई जा रही है..."
                            } else {
                                "Synthesizing web-grounded biology & 3D data..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = BioCyan
                        )
                    }
                }
                is AiAnswerState.Streaming -> {
                    MarkdownRichText(markdown = aiState.text)
                    WebSourcesRow(
                        sources = aiState.sources,
                        language = language,
                        onOpenUrl = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                }
                is AiAnswerState.Success -> {
                    MarkdownRichText(markdown = aiState.text)
                    WebSourcesRow(
                        sources = aiState.sources,
                        language = language,
                        onOpenUrl = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                }
                is AiAnswerState.Error -> {
                    Text(
                        text = aiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BioCoral
                    )
                    OutlinedButton(onClick = onExplainFullOrgan) {
                        Text(if (language == AppLanguage.HINDI) "पुनः प्रयास करें" else "Retry")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebSourcesRow(
    sources: List<WebSource>,
    language: AppLanguage,
    onOpenUrl: (String) -> Unit
) {
    if (sources.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            text = if (language == AppLanguage.HINDI) "सत्यापित वेबसाइट स्रोत:" else "Verified Web Sources:",
            style = MaterialTheme.typography.labelMedium,
            color = BioTeal
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sources.take(6).forEach { src ->
                AssistChip(
                    onClick = { onOpenUrl(src.url) },
                    label = {
                        Text(
                            text = src.title.take(32),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = BioCyan
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                )
            }
        }
    }
}
