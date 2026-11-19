package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.QaMessage
import com.example.ui.components.ConversationHistoryList
import com.example.ui.theme.BioCyan
import com.example.ui.theme.BioGold
import com.example.ui.theme.BioTeal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BiologyQaScreen(
    messages: List<QaMessage>,
    language: AppLanguage,
    onAskQuestion: (String, Bitmap?, Boolean) -> Unit,
    onViewTopicIn3D: (String) -> Unit,
    onGenerateImage: (QaMessage) -> Unit,
    onRetryQuestion: (QaMessage) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var input by rememberSaveable { mutableStateOf("") }
    var requestImageMode by rememberSaveable { mutableStateOf(false) }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val maxDim = 1280
                val scale = minOf(
                    1f,
                    maxDim.toFloat() / maxOf(rawBitmap.width, rawBitmap.height).coerceAtLeast(1)
                )
                attachedBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        rawBitmap,
                        (rawBitmap.width * scale).toInt(),
                        (rawBitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    rawBitmap
                }
            }
        }
    }

    val starterQuestions = if (language == AppLanguage.HINDI) {
        listOf(
            "कोशिका के अंदर माइटोकॉन्ड्रिया ऊर्जा कैसे बनाता है?",
            "मस्तिष्क का हिप्पोकैम्पस याददाश्त कैसे स्टोर करता है?",
            "हड्डी के अंदर अस्थि मज्जा रक्त कैसे बनाता है?",
            "हृदय के चारों कक्ष और वाल्व की इमेज और व्याख्या दें",
            "किडनी का नेफ्रॉन खून को कैसे फिल्टर करता है?"
        )
    } else {
        listOf(
            "How do mitochondria generate ATP inside a human cell?",
            "How does the hippocampus inside the brain form memories?",
            "How does red bone marrow inside the femur produce blood cells?",
            "Generate an anatomy diagram and explain the 4 heart chambers",
            "How does a nephron inside the kidney filter blood plasma?"
        )
    }

    val send = {
        if (input.isNotBlank() || attachedBitmap != null) {
            keyboard?.hide()
            onAskQuestion(input, attachedBitmap, requestImageMode)
            input = ""
            attachedBitmap = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ConversationHistoryList(
            messages = messages,
            language = language,
            listState = listState,
            onViewIn3D = onViewTopicIn3D,
            onGenerateImage = onGenerateImage,
            onRetryQuestion = onRetryQuestion,
            onClearHistory = onClearHistory,
            onOpenSource = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            headerContent = {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        BioCyan.copy(alpha = 0.16f),
                                        BioTeal.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = BioCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == AppLanguage.HINDI) {
                                    "3D BIO सुपर-AI (वेब + इमेज + 3D)"
                                } else {
                                    "3D BIO Super-AI (Web + HD Image + 3D)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (language == AppLanguage.HINDI) {
                                "ChatGPT जैसा स्मार्ट बायोलॉजी AI: जीव विज्ञान या शरीर के किसी भी अंग, कोशिका, हड्डी या आंतरिक भाग के बारे में पूछें, फॉलो-अप सवाल करें, और किसी भी भाग की HD मेडिकल इमेज बनाएं।"
                            } else {
                                "ChatGPT-grade multi-turn Biology Super-AI: ask any anatomy, cellular, or internal organ question with web grounding, ask follow-ups, and generate HD anatomical illustrations."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            emptyContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (language == AppLanguage.HINDI) "लोकप्रिय प्रश्न (टैप करें):" else "Try Asking:",
                        style = MaterialTheme.typography.labelLarge,
                        color = BioTeal
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        starterQuestions.forEach { q ->
                            AssistChip(
                                onClick = { onAskQuestion(q, null, requestImageMode) },
                                label = { Text(q, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Attached image preview if selected via Photo Picker
        attachedBitmap?.let { bmp ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Selected diagram image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (language == AppLanguage.HINDI) "चित्र जुड़ा हुआ है" else "Diagram attached",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { attachedBitmap = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove image")
                }
            }
        }

        // Compact AI Mode Strip (Toggle HD Anatomical Image Generation alongside text answer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 12.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = requestImageMode,
                onClick = { requestImageMode = !requestImageMode },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (requestImageMode) BioGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = if (language == AppLanguage.HINDI) {
                            "उत्तर के साथ HD इमेज भी बनाएं"
                        } else {
                            "Also Generate HD Image"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BioGold.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("qa_image_mode_chip")
            )
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = BioCyan,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (language == AppLanguage.HINDI) "वेब-सत्यापित" else "Web AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Bottom Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("qa_attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = if (language == AppLanguage.HINDI) "चित्र जोड़ें" else "Attach biology image",
                    tint = BioTeal
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = if (language == AppLanguage.HINDI) {
                            if (requestImageMode) "किस भाग की इमेज और उत्तर चाहिए?" else "बायोलॉजी का कोई भी प्रश्न पूछें..."
                        } else {
                            if (requestImageMode) "Ask & generate HD diagram for..." else "Ask any biology or anatomy question..."
                        },
                        maxLines = 1
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("qa_input_field")
            )

            Button(
                onClick = send,
                enabled = input.isNotBlank() || attachedBitmap != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("qa_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send question"
                )
            }
        }
    }
}
