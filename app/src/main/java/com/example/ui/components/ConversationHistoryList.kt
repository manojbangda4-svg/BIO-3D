package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppLanguage
import com.example.model.QaMessage
import com.example.ui.screens.WebSourcesRow
import com.example.ui.theme.BioCoral
import com.example.ui.theme.BioCyan
import com.example.ui.theme.BioGold
import com.example.ui.theme.BioTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationHistoryList(
    messages: List<QaMessage>,
    language: AppLanguage,
    onViewIn3D: (String) -> Unit,
    onGenerateImage: (QaMessage) -> Unit,
    onRetryQuestion: (QaMessage) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    headerContent: (@Composable () -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var fullscreenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(messages.size, messages.lastOrNull()?.answer?.length, messages.lastOrNull()?.generatedImage) {
        if (messages.isNotEmpty()) {
            val targetIndex = (if (headerContent != null) 1 else 0) + messages.size - 1
            listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
        }
    }

    fullscreenBitmap?.let { bmp ->
        FullscreenImageDialog(
            bitmap = bmp,
            onDismiss = { fullscreenBitmap = null }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.testTag("conversation_history_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (headerContent != null) {
            item(key = "conversation_header") {
                headerContent()
            }
        }

        if (messages.isNotEmpty()) {
            item(key = "conversation_toolbar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == AppLanguage.HINDI) {
                            "वार्तालाप इतिहास (${messages.size})"
                        } else {
                            "Conversation History (${messages.size})"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BioTeal
                    )
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_conversation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.HINDI) "साफ़ करें" else "Clear",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        } else if (emptyContent != null) {
            item(key = "conversation_empty") {
                emptyContent()
            }
        }

        items(messages, key = { it.id }) { msg ->
            ConversationTurnItem(
                message = msg,
                language = language,
                formattedTime = timeFormatter.format(Date(msg.timestampMillis)),
                onViewIn3D = { onViewIn3D(msg.linkedTopicQuery ?: msg.question) },
                onGenerateImage = { onGenerateImage(msg) },
                onRetry = { onRetryQuestion(msg) },
                onOpenSource = onOpenSource,
                onZoomImage = { bmp -> fullscreenBitmap = bmp }
            )
        }
    }
}

@Composable
private fun ConversationTurnItem(
    message: QaMessage,
    language: AppLanguage,
    formattedTime: String,
    onViewIn3D: () -> Unit,
    onGenerateImage: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: (String) -> Unit,
    onZoomImage: (Bitmap) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // User Query Bubble (Right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 4.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("conversation_query_${message.id}")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (language == AppLanguage.HINDI) "आपका प्रश्न" else "You",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    if (message.hasAttachedImage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = BioTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (language == AppLanguage.HINDI) "चित्र संलग्न" else "Image attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = BioTeal
                            )
                        }
                    }

                    Text(
                        text = message.question,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // AI Response Card (Left-aligned)
        Card(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("conversation_response_${message.id}")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(BioCyan.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BioCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3D BIO Super-AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = BioCyan,
                        modifier = Modifier.weight(1f)
                    )
                    if (message.answer.isNotBlank() && !message.isStreaming) {
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(message.answer)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = if (language == AppLanguage.HINDI) "कॉपी करें" else "Copy answer",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (message.isStreaming && message.answer.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (language == AppLanguage.HINDI) {
                                "सटीक उत्तर तैयार किया जा रहा है..."
                            } else {
                                "Generating scientific response..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = BioCyan
                        )
                    }
                }

                if (message.answer.isNotBlank()) {
                    MarkdownRichText(markdown = message.answer)
                }

                // Runtime AI Image Output / Loading / Real Error
                if (message.isGeneratingImage) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
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
                } else if (message.generatedImage != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Image(
                            bitmap = message.generatedImage.asImageBitmap(),
                            contentDescription = "AI generated anatomy image for ${message.question}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF071625))
                                .clickable { onZoomImage(message.generatedImage) }
                                .testTag("generated_image_${message.id}")
                        )
                        Text(
                            text = if (language == AppLanguage.HINDI) "बड़ा देखने के लिए इमेज पर टैप करें" else "Tap image to zoom fullscreen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (message.imageError != null) {
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
                                text = message.imageError,
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

                message.errorMessage?.let { err ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = BioCoral
                        )
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == AppLanguage.HINDI) "पुनः प्रयास करें" else "Retry")
                        }
                    }
                }

                WebSourcesRow(
                    sources = message.sources,
                    language = language,
                    onOpenUrl = onOpenSource
                )

                if (!message.isStreaming && message.answer.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewIn3D,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("view_in_3d_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = BioTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (language == AppLanguage.HINDI) "3D में देखें" else "View 3D",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }

                        if (!message.isGeneratingImage) {
                            OutlinedButton(
                                onClick = onGenerateImage,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("generate_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = BioGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (language == AppLanguage.HINDI) "HD इमेज बनाएं" else "HD Image",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE040D17))
                .clickable(onClick = onDismiss)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Fullscreen generated anatomy illustration",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color(0xAA06131F), CircleShape)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close fullscreen image",
                    tint = Color.White
                )
            }
        }
    }
}
