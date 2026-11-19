package com.example.model

import android.graphics.Bitmap

enum class AppLanguage(val code: String, val label: String) {
    HINDI("hi", "हिन्दी"),
    ENGLISH("en", "EN")
}

enum class RenderMode {
    SOLID,
    XRAY,
    WIREFRAME
}

enum class AnatomyLayer(val order: Int) {
    OUTER(0),
    MIDDLE(1),
    INNER(2),
    CELLULAR(3)
}

enum class PartShape {
    ELLIPSOID,
    SPHERE,
    CYLINDER,
    CAPSULE,
    TORUS,
    HELIX,
    BRANCHING
}

data class Anatomy3DPart(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val layer: AnatomyLayer,
    val shape: PartShape,
    val x: Float,
    val y: Float,
    val z: Float,
    val rx: Float,
    val ry: Float,
    val rz: Float,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val colorHex: Long,
    val opacity: Float = 1f,
    val roleEn: String,
    val roleHi: String,
    val detailEn: String,
    val detailHi: String
) {
    fun displayName(lang: AppLanguage): String = if (lang == AppLanguage.HINDI) nameHi else nameEn
    fun displayRole(lang: AppLanguage): String = if (lang == AppLanguage.HINDI) roleHi else roleEn
    fun displayDetail(lang: AppLanguage): String = if (lang == AppLanguage.HINDI) detailHi else detailEn
}

data class Anatomy3DModel(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val systemEn: String,
    val systemHi: String,
    val summaryEn: String,
    val summaryHi: String,
    val scaleLabel: String,
    val parts: List<Anatomy3DPart>,
    val isAiGenerated: Boolean = false
) {
    fun displayTitle(lang: AppLanguage): String = if (lang == AppLanguage.HINDI) titleHi else titleEn
    fun displaySystem(lang: AppLanguage): String = if (lang == AppLanguage.HINDI) systemHi else systemEn
    fun displaySummary(lang: AppLanguage): String = if (lang == AppLanguage.HINDI) summaryHi else summaryEn
}

data class WebSource(
    val title: String,
    val url: String
)

data class QaMessage(
    val id: String,
    val question: String,
    val answer: String,
    val sources: List<WebSource> = emptyList(),
    val linkedTopicQuery: String? = null,
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val hasAttachedImage: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis(),
    val generatedImage: Bitmap? = null,
    val isGeneratingImage: Boolean = false,
    val imageError: String? = null
)

data class SavedTopic(
    val id: String,
    val query: String,
    val title: String,
    val systemTag: String,
    val snippet: String,
    val timestampMillis: Long
)

sealed interface AiAnswerState {
    data object Idle : AiAnswerState
    data object Loading : AiAnswerState
    data class Streaming(
        val text: String,
        val sources: List<WebSource>
    ) : AiAnswerState
    data class Success(
        val text: String,
        val sources: List<WebSource>
    ) : AiAnswerState
    data class Error(val message: String) : AiAnswerState
}

sealed interface ImageGenState {
    data object Idle : ImageGenState
    data object Loading : ImageGenState
    data class Success(val bitmap: Bitmap) : ImageGenState
    data class Error(val message: String) : ImageGenState
}
