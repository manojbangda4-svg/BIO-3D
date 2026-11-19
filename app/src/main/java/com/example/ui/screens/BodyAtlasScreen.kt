package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PresetAnatomyCatalog
import com.example.model.Anatomy3DModel
import com.example.model.AppLanguage
import com.example.model.SavedTopic
import com.example.ui.theme.BioCoral
import com.example.ui.theme.BioCyan
import com.example.ui.theme.BioGold
import com.example.ui.theme.BioTeal

@Composable
fun BodyAtlasScreen(
    language: AppLanguage,
    savedTopics: List<SavedTopic>,
    onSelectPreset: (Anatomy3DModel) -> Unit,
    onOpenSavedQueryIn3D: (String) -> Unit,
    onDeleteSavedTopic: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AtlasHeroCard(language = language)
        }

        item {
            Text(
                text = if (language == AppLanguage.HINDI) {
                    "मानव शरीर व कोशिकीय 3D एटलस (${PresetAnatomyCatalog.allModels.size})"
                } else {
                    "Human Body & Cellular 3D Atlas (${PresetAnatomyCatalog.allModels.size})"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(PresetAnatomyCatalog.allModels, key = { it.id }) { model ->
            AtlasModelCard(
                model = model,
                language = language,
                onClick = { onSelectPreset(model) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = BioGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == AppLanguage.HINDI) {
                        "हालिया खोजे गए विषय (${savedTopics.size})"
                    } else {
                        "Recent Explored Topics (${savedTopics.size})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (savedTopics.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (language == AppLanguage.HINDI) {
                            "जब आप किसी अंग या कोशिका के बारे में AI से पूछेंगे, तो आपका इतिहास यहाँ सुरक्षित रहेगा।"
                        } else {
                            "Questions you ask about any body part or biology topic will appear here for quick revisit."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(savedTopics, key = { it.id }) { topic ->
                SavedTopicCard(
                    topic = topic,
                    onClick = { onOpenSavedQueryIn3D(topic.query) },
                    onDelete = { onDeleteSavedTopic(topic.id) }
                )
            }
        }
    }
}

@Composable
private fun AtlasHeroCard(language: AppLanguage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A243B), Color(0xFF0C3B50), Color(0xFF071625))
                )
            )
            .border(1.dp, BioCyan.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BioCyan.copy(alpha = 0.25f), Color.Transparent)
                ),
                radius = h * 0.85f,
                center = Offset(w * 0.85f, h * 0.4f)
            )
            drawCircle(
                color = BioTeal.copy(alpha = 0.35f),
                radius = h * 0.34f,
                center = Offset(w * 0.84f, h * 0.50f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = BioCoral.copy(alpha = 0.45f),
                radius = h * 0.17f,
                center = Offset(w * 0.84f, h * 0.50f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (language == AppLanguage.HINDI) {
                    "3D मानव शरीर और कोशिका एटलस"
                } else {
                    "3D Human Anatomy & Cell Atlas"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (language == AppLanguage.HINDI) {
                    "मस्तिष्क, हृदय, कोशिका, हड्डियों से लेकर डीएनए तक किसी भी हिस्से को 3D में खोलकर देखें।"
                } else {
                    "Inspect organs, internal layers, cells, bones, and DNA in interactive 3D with web-verified science."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.fillMaxWidth(0.76f)
            )
        }
    }
}

@Composable
private fun AtlasModelCard(
    model: Anatomy3DModel,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val accent = model.parts.firstOrNull()?.let { Color(it.colorHex) } ?: BioCyan
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("atlas_card_${model.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.5.dp, accent.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = accent
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayTitle(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${model.displaySystem(language)} • ${model.parts.size} " +
                        if (language == AppLanguage.HINDI) "आंतरिक भाग" else "internal parts",
                    style = MaterialTheme.typography.labelSmall,
                    color = BioTeal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = model.displaySummary(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SavedTopicCard(
    topic: SavedTopic,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = topic.systemTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = topic.query,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = topic.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete saved topic",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
