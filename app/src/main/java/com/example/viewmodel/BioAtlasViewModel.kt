package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AnatomyAiRepository
import com.example.data.HistoryRepository
import com.example.data.PresetAnatomyCatalog
import com.example.model.AiAnswerState
import com.example.model.Anatomy3DModel
import com.example.model.Anatomy3DPart
import com.example.model.AnatomyLayer
import com.example.model.AppLanguage
import com.example.model.ImageGenState
import com.example.model.QaMessage
import com.example.model.RenderMode
import com.example.model.SavedTopic
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BioAtlasViewModel"

enum class AppTab {
    EXPLORE_3D,
    AI_QA,
    ATLAS
}

data class BioStudioState(
    val activeTab: AppTab = AppTab.EXPLORE_3D,
    val activeModel: Anatomy3DModel = PresetAnatomyCatalog.allModels.first(),
    val selectedPartId: String? = null,
    val renderMode: RenderMode = RenderMode.SOLID,
    val cutawayProgress: Float = 0.22f,
    val showLabels: Boolean = true,
    val autoRotate: Boolean = true,
    val yaw: Float = 0.35f,
    val pitch: Float = -0.18f,
    val zoom: Float = 1.0f,
    val isGenerating3D: Boolean = false,
    val blueprintError: String? = null,
    val lastQuery: String = "",
    val studioAiState: AiAnswerState = AiAnswerState.Idle,
    val studioImageState: ImageGenState = ImageGenState.Idle,
    val qaMessages: List<QaMessage> = emptyList()
)

class BioAtlasViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepo = AnatomyAiRepository()
    private val historyRepo = HistoryRepository(application.applicationContext)

    val language: StateFlow<AppLanguage> = historyRepo.languageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.HINDI)

    val savedTopics: StateFlow<List<SavedTopic>> = historyRepo.savedTopicsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(BioStudioState())
    val state: StateFlow<BioStudioState> = _state.asStateFlow()

    private var studioAnswerJob: Job? = null
    private var studioImageJob: Job? = null
    private var blueprintJob: Job? = null
    private var chatJob: Job? = null

    init {
        viewModelScope.launch {
            historyRepo.qaHistoryFlow.collect { saved ->
                _state.update { current ->
                    // Keep any actively streaming items while hydrating persisted history
                    val activeStreaming = current.qaMessages.filter { it.isStreaming }
                    val merged = if (activeStreaming.isEmpty()) saved else saved + activeStreaming
                    current.copy(qaMessages = merged)
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (language.value == AppLanguage.HINDI) AppLanguage.ENGLISH else AppLanguage.HINDI
            historyRepo.setLanguage(next)
        }
    }

    fun selectPresetModel(model: Anatomy3DModel, explainWithAi: Boolean = false) {
        _state.update {
            it.copy(
                activeTab = AppTab.EXPLORE_3D,
                activeModel = model,
                selectedPartId = model.parts.firstOrNull { p -> p.layer != AnatomyLayer.OUTER }?.id
                    ?: model.parts.firstOrNull()?.id,
                cutawayProgress = 0.28f,
                yaw = 0.35f,
                pitch = -0.18f,
                zoom = 1.0f,
                blueprintError = null,
                studioImageState = ImageGenState.Idle,
                studioAiState = if (explainWithAi) AiAnswerState.Loading else AiAnswerState.Idle
            )
        }
        if (explainWithAi) {
            val query = model.displayTitle(language.value)
            streamStudioAnswer(query = query, activeModelTitle = model.displayTitle(language.value), focusedPart = null)
        }
    }

    fun selectPart(part: Anatomy3DPart) {
        val suggestedCutaway = when (part.layer) {
            AnatomyLayer.OUTER -> _state.value.cutawayProgress.coerceAtMost(0.25f)
            AnatomyLayer.MIDDLE -> _state.value.cutawayProgress.coerceAtLeast(0.45f)
            AnatomyLayer.INNER, AnatomyLayer.CELLULAR -> _state.value.cutawayProgress.coerceAtLeast(0.72f)
        }
        _state.update {
            it.copy(
                selectedPartId = part.id,
                cutawayProgress = suggestedCutaway,
                autoRotate = false
            )
        }
    }

    fun deepDiveSelectedPart(part: Anatomy3DPart) {
        selectPart(part)
        val lang = language.value
        val modelTitle = _state.value.activeModel.displayTitle(lang)
        val partTitle = part.displayName(lang)
        val prompt = if (lang == AppLanguage.HINDI) {
            "$modelTitle के आंतरिक भाग '$partTitle' की 3D संरचना, मुख्य कार्य, कोशिकीय विवरण और मेडिकल महत्व को विस्तार से समझाएं।"
        } else {
            "Explain the internal part '$partTitle' inside '$modelTitle' in full 3D anatomical, cellular, and physiological detail."
        }
        streamStudioAnswer(
            query = prompt,
            activeModelTitle = modelTitle,
            focusedPart = partTitle
        )
    }

    fun rotateDelta(dYaw: Float, dPitch: Float) {
        _state.update { s ->
            s.copy(
                yaw = s.yaw + dYaw,
                pitch = (s.pitch + dPitch).coerceIn(-1.35f, 1.35f)
            )
        }
    }

    fun adjustZoom(delta: Float) {
        _state.update { s ->
            s.copy(zoom = (s.zoom + delta).coerceIn(0.65f, 2.2f))
        }
    }

    fun resetCamera() {
        _state.update {
            it.copy(yaw = 0.35f, pitch = -0.18f, zoom = 1.0f, cutawayProgress = 0.25f)
        }
    }

    fun setRenderMode(mode: RenderMode) {
        _state.update { it.copy(renderMode = mode) }
    }

    fun setCutaway(progress: Float) {
        _state.update { it.copy(cutawayProgress = progress.coerceIn(0f, 1f)) }
    }

    fun toggleLabels() {
        _state.update { it.copy(showLabels = !it.showLabels) }
    }

    fun toggleAutoRotate() {
        _state.update { it.copy(autoRotate = !it.autoRotate) }
    }

    fun askAndVisualize(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) return

        val matchedPreset = PresetAnatomyCatalog.findPresetForQuery(query)
        _state.update {
            it.copy(
                activeTab = AppTab.EXPLORE_3D,
                lastQuery = query,
                blueprintError = null,
                isGenerating3D = matchedPreset == null,
                activeModel = matchedPreset ?: it.activeModel,
                selectedPartId = matchedPreset?.parts?.firstOrNull { p -> p.layer != AnatomyLayer.OUTER }?.id
                    ?: it.selectedPartId,
                cutawayProgress = if (matchedPreset != null) 0.32f else it.cutawayProgress,
                studioImageState = ImageGenState.Idle,
                studioAiState = AiAnswerState.Loading
            )
        }

        // 1. Stream web-grounded scientific explanation
        streamStudioAnswer(
            query = query,
            activeModelTitle = matchedPreset?.displayTitle(language.value),
            focusedPart = null
        )

        // 2. If not a direct built-in preset, build a custom 3D model via AI in parallel
        if (matchedPreset == null) {
            generateCustom3DModel(query)
        }
    }

    fun forceGenerateCustom3D(query: String) {
        val clean = query.trim().ifBlank { _state.value.activeModel.displayTitle(language.value) }
        generateCustom3DModel(clean)
    }

    private fun generateCustom3DModel(query: String) {
        blueprintJob?.cancel()
        blueprintJob = viewModelScope.launch {
            _state.update { it.copy(isGenerating3D = true, blueprintError = null) }
            try {
                val model = aiRepo.generate3DBlueprint(query)
                _state.update {
                    it.copy(
                        activeModel = model,
                        selectedPartId = model.parts.firstOrNull { p -> p.layer != AnatomyLayer.OUTER }?.id
                            ?: model.parts.firstOrNull()?.id,
                        cutawayProgress = 0.30f,
                        yaw = 0.35f,
                        pitch = -0.18f,
                        zoom = 1.0f,
                        isGenerating3D = false
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "3D blueprint generation timed out", e)
                _state.update {
                    it.copy(
                        isGenerating3D = false,
                        blueprintError = if (language.value == AppLanguage.HINDI) {
                            "3D मॉडल बनाने में समय सीमा समाप्त हुई। कृपया पुनः प्रयास करें।"
                        } else {
                            "3D model generation timed out. Please try again."
                        }
                    )
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "3D blueprint quota exceeded", e)
                _state.update {
                    it.copy(
                        isGenerating3D = false,
                        blueprintError = formatTextQuotaOrError(e, language.value)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate custom 3D model", e)
                _state.update {
                    it.copy(
                        isGenerating3D = false,
                        blueprintError = formatTextQuotaOrError(e, language.value)
                    )
                }
            }
        }
    }

    private fun streamStudioAnswer(
        query: String,
        activeModelTitle: String?,
        focusedPart: String?
    ) {
        studioAnswerJob?.cancel()
        studioAnswerJob = viewModelScope.launch {
            _state.update { it.copy(studioAiState = AiAnswerState.Loading) }
            var finalAnswer = ""
            try {
                aiRepo.streamBiologyAnswer(
                    question = query,
                    activeModelTitle = activeModelTitle,
                    selectedSubPart = focusedPart,
                    language = language.value
                ).collect { update ->
                    finalAnswer = update.accumulatedText
                    _state.update {
                        it.copy(
                            studioAiState = AiAnswerState.Streaming(
                                text = update.accumulatedText,
                                sources = update.sources
                            )
                        )
                    }
                }

                if (finalAnswer.isNotBlank()) {
                    val currentSources = (_state.value.studioAiState as? AiAnswerState.Streaming)?.sources.orEmpty()
                    _state.update {
                        it.copy(
                            studioAiState = AiAnswerState.Success(
                                text = finalAnswer,
                                sources = currentSources
                            )
                        )
                    }
                    saveExploredTopic(query, finalAnswer)
                    recordCompletedConversationTurn(
                        question = query,
                        answer = finalAnswer,
                        sources = currentSources,
                        linkedTopicQuery = query
                    )
                } else {
                    _state.update {
                        it.copy(
                            studioAiState = AiAnswerState.Error(
                                if (language.value == AppLanguage.HINDI) "AI से कोई उत्तर प्राप्त नहीं हुआ।"
                                else "Model returned an empty response."
                            )
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Studio answer timed out", e)
                _state.update {
                    it.copy(
                        studioAiState = AiAnswerState.Error(
                            if (language.value == AppLanguage.HINDI) "उत्तर लोड करने में 60 सेकंड से अधिक समय लगा। कृपया पुनः प्रयास करें।"
                            else "Request timed out after 60 seconds. Please try again."
                        )
                    )
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Studio answer quota exceeded", e)
                _state.update {
                    it.copy(
                        studioAiState = AiAnswerState.Error(
                            formatTextQuotaOrError(e, language.value)
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Studio answer failed", e)
                _state.update {
                    it.copy(
                        studioAiState = AiAnswerState.Error(
                            formatTextQuotaOrError(e, language.value)
                        )
                    )
                }
            }
        }
    }

    fun askChatQuestion(
        question: String,
        attachedBitmap: Bitmap? = null,
        requestImage: Boolean = false
    ) {
        val clean = question.trim()
        if (clean.isBlank() && attachedBitmap == null) return

        val displayQ = if (clean.isNotBlank()) clean else {
            if (language.value == AppLanguage.HINDI) "इस बायोलॉजी चित्र / अंग की विस्तृत व्याख्या करें।"
            else "Analyze and explain this biology/anatomy image in detail."
        }
        val lower = displayQ.lowercase()
        val wantsImage = requestImage ||
            lower.contains("image") ||
            lower.contains("photo") ||
            lower.contains("picture") ||
            lower.contains("diagram") ||
            lower.contains("draw") ||
            lower.contains("इमेज") ||
            lower.contains("फोटो") ||
            lower.contains("चित्र") ||
            lower.contains("तस्वीर") ||
            lower.contains("डायग्राम")

        val historySnapshot = _state.value.qaMessages
        val msgId = "qa_${System.currentTimeMillis()}"
        val placeholder = QaMessage(
            id = msgId,
            question = displayQ,
            answer = "",
            linkedTopicQuery = clean.ifBlank { null },
            isStreaming = true,
            hasAttachedImage = attachedBitmap != null,
            isGeneratingImage = wantsImage
        )

        _state.update { it.copy(qaMessages = it.qaMessages + placeholder) }

        if (wantsImage) {
            generateImageForMessage(msgId, displayQ)
        }

        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            try {
                aiRepo.streamBiologyAnswer(
                    question = displayQ,
                    activeModelTitle = null,
                    selectedSubPart = null,
                    language = language.value,
                    attachedBitmap = attachedBitmap,
                    conversationHistory = historySnapshot
                ).collect { update ->
                    updateQaMessage(msgId) { msg ->
                        msg.copy(
                            answer = update.accumulatedText,
                            sources = update.sources,
                            isStreaming = true
                        )
                    }
                }
                updateQaMessage(msgId) { msg ->
                    msg.copy(isStreaming = false)
                }
                val finalMsg = _state.value.qaMessages.firstOrNull { it.id == msgId }
                if (finalMsg != null && finalMsg.answer.isNotBlank()) {
                    saveExploredTopic(displayQ, finalMsg.answer)
                }
                historyRepo.saveQaHistory(_state.value.qaMessages)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                updateQaMessage(msgId) {
                    it.copy(
                        isStreaming = false,
                        errorMessage = if (language.value == AppLanguage.HINDI) "समय सीमा समाप्त हुई। कृपया पुनः पूछें।"
                        else "Response timed out. Please try again."
                    )
                }
                historyRepo.saveQaHistory(_state.value.qaMessages)
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Chat quota exceeded", e)
                updateQaMessage(msgId) {
                    it.copy(
                        isStreaming = false,
                        errorMessage = formatTextQuotaOrError(e, language.value)
                    )
                }
                historyRepo.saveQaHistory(_state.value.qaMessages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                updateQaMessage(msgId) {
                    it.copy(
                        isStreaming = false,
                        errorMessage = formatTextQuotaOrError(e, language.value)
                    )
                }
                historyRepo.saveQaHistory(_state.value.qaMessages)
            }
        }
    }

    fun generateImageForMessage(messageId: String, topic: String) {
        viewModelScope.launch {
            updateQaMessage(messageId) {
                it.copy(isGeneratingImage = true, imageError = null)
            }
            try {
                val bitmap = aiRepo.generateAnatomyImage(topic)
                updateQaMessage(messageId) {
                    it.copy(generatedImage = bitmap, isGeneratingImage = false, imageError = null)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Message image generation timed out", e)
                updateQaMessage(messageId) {
                    it.copy(
                        isGeneratingImage = false,
                        imageError = if (language.value == AppLanguage.HINDI) {
                            "इमेज बनाने में 60 सेकंड से अधिक समय लगा। कृपया पुनः प्रयास करें।"
                        } else {
                            "Image generation timed out after 60 seconds."
                        }
                    )
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                updateQaMessage(messageId) {
                    it.copy(
                        isGeneratingImage = false,
                        imageError = formatImageQuotaOrError(e, language.value)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Message image generation failed", e)
                updateQaMessage(messageId) {
                    it.copy(
                        isGeneratingImage = false,
                        imageError = formatImageQuotaOrError(e, language.value)
                    )
                }
            }
        }
    }

    fun generateStudioImage(topic: String) {
        studioImageJob?.cancel()
        studioImageJob = viewModelScope.launch {
            _state.update { it.copy(studioImageState = ImageGenState.Loading) }
            try {
                val bmp = aiRepo.generateAnatomyImage(topic)
                _state.update { it.copy(studioImageState = ImageGenState.Success(bmp)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Studio image timed out", e)
                _state.update {
                    it.copy(
                        studioImageState = ImageGenState.Error(
                            if (language.value == AppLanguage.HINDI) {
                                "इमेज बनाने में 60 सेकंड से अधिक समय लगा। कृपया पुनः प्रयास करें।"
                            } else {
                                "Image generation timed out after 60 seconds."
                            }
                        )
                    )
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Studio image quota exceeded", e)
                _state.update {
                    it.copy(
                        studioImageState = ImageGenState.Error(
                            formatImageQuotaOrError(e, language.value)
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Studio image failed", e)
                _state.update {
                    it.copy(
                        studioImageState = ImageGenState.Error(
                            formatImageQuotaOrError(e, language.value)
                        )
                    )
                }
            }
        }
    }

    fun retryQaMessage(message: QaMessage) {
        _state.update { s ->
            s.copy(qaMessages = s.qaMessages.filterNot { it.id == message.id })
        }
        askChatQuestion(message.question, attachedBitmap = null, requestImage = message.isGeneratingImage || message.generatedImage != null)
    }

    fun clearConversationHistory() {
        chatJob?.cancel()
        _state.update { it.copy(qaMessages = emptyList()) }
        viewModelScope.launch {
            historyRepo.clearQaHistory()
        }
    }

    private fun recordCompletedConversationTurn(
        question: String,
        answer: String,
        sources: List<com.example.model.WebSource>,
        linkedTopicQuery: String?
    ) {
        val turn = QaMessage(
            id = "turn_${System.currentTimeMillis()}",
            question = question,
            answer = answer,
            sources = sources,
            linkedTopicQuery = linkedTopicQuery,
            isStreaming = false
        )
        _state.update { s -> s.copy(qaMessages = s.qaMessages + turn) }
        viewModelScope.launch {
            historyRepo.saveQaHistory(_state.value.qaMessages)
        }
    }

    private fun updateQaMessage(id: String, transform: (QaMessage) -> QaMessage) {
        _state.update { s ->
            s.copy(qaMessages = s.qaMessages.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun saveExploredTopic(query: String, answer: String) {
        viewModelScope.launch {
            val active = _state.value.activeModel
            val snippet = answer.replace("**", "").replace("#", "").trim().take(130)
            historyRepo.saveTopic(
                SavedTopic(
                    id = "topic_${query.hashCode()}_${System.currentTimeMillis()}",
                    query = query,
                    title = active.displayTitle(language.value),
                    systemTag = active.displaySystem(language.value),
                    snippet = snippet,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteSavedTopic(id: String) {
        viewModelScope.launch {
            historyRepo.removeTopic(id)
        }
    }

    private fun isQuotaOrBillingError(e: Exception): Boolean {
        if (e is QuotaExceededException) return true
        val msg = e.localizedMessage?.lowercase().orEmpty()
        return msg.contains("quota") ||
            msg.contains("billing") ||
            msg.contains("429") ||
            msg.contains("resource_exhausted") ||
            msg.contains("limit")
    }

    private fun formatTextQuotaOrError(e: Exception, lang: AppLanguage): String {
        if (isQuotaOrBillingError(e)) {
            return if (lang == AppLanguage.HINDI) {
                "फ्री AI कोटा की सीमा अस्थायी रूप से पूरी हो गई है। कृपया कुछ सेकंड बाद पुनः प्रयास करें — तब तक आप 3D स्टूडियो के सभी अंग और कटअवे देख सकते हैं।"
            } else {
                "Free AI rate limit reached. Please retry in a few seconds — you can continue exploring all interactive 3D anatomy models right now."
            }
        }
        return e.localizedMessage ?: if (lang == AppLanguage.HINDI) {
            "AI अनुरोध पूरा नहीं हो सका। कृपया इंटरनेट कनेक्शन जांचें और पुनः प्रयास करें।"
        } else {
            "AI request failed. Please check your internet connection and retry."
        }
    }

    private fun formatImageQuotaOrError(e: Exception, lang: AppLanguage): String {
        if (isQuotaOrBillingError(e)) {
            return if (lang == AppLanguage.HINDI) {
                "AI HD इमेज जनरेशन के लिए Firebase Blaze कोटा आवश्यक है या फ्री इमेज सीमा पूरी हो चुकी है। आप अभी भी 3D स्टूडियो और AI प्रश्नोत्तर का मुफ्त उपयोग कर सकते हैं।"
            } else {
                "AI HD image generation requires a Firebase Blaze quota or the free image limit was reached. Interactive 3D Studio and Biology Q&A remain free to use."
            }
        }
        return e.localizedMessage ?: if (lang == AppLanguage.HINDI) {
            "इमेज जनरेट नहीं हो सकी। कृपया पुनः प्रयास करें।"
        } else {
            "Failed to generate image. Please try again."
        }
    }
}
