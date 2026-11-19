package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.Anatomy3DModel
import com.example.model.Anatomy3DPart
import com.example.model.AnatomyLayer
import com.example.model.AppLanguage
import com.example.model.PartShape
import com.example.model.QaMessage
import com.example.model.WebSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AnatomyAiRepo"

data class StreamChunkUpdate(
    val accumulatedText: String,
    val sources: List<WebSource>
)

class AnatomyAiRepository {

    @Volatile
    private var searchToolAvailable: Boolean = true

    private val qaSystemInstruction = content {
        text(
            """
            You are 3D BIO Super-AI, a world-class ChatGPT-grade human anatomy, cell biology, physiology, genetics, biochemistry, and medical science professor.
            Rules:
            1. Answer ANY biology or human body question (cells, organelles, brain regions, bones, organs, tissues, blood, enzymes, DNA, diseases, physiology) with deep, crystal-clear, 100% accurate scientific facts synthesized from authoritative medical & biological references (PubMed, NCBI, Gray's Anatomy, Britannica, MedlinePlus, NCERT, Khan Academy).
            2. Maintain multi-turn conversational context seamlessly so follow-up questions build on previous turns.
            3. Explain spatially like a 3D anatomy professor: walk through outer layer -> middle structures -> deep internal sub-parts -> cellular/molecular mechanism.
            4. Language: If the user asks in Hindi or Hinglish (or Hindi mode is active), respond in clear, natural Hindi (Devanagari) with standard scientific terms in English parentheses. If the user asks in English, respond in clear English.
            5. Structure every deep answer cleanly with bold headings:
               - **परिचय / Overview**
               - **3D बनावट और अंदर के भाग / 3D Structure & Internal Parts**
               - **कार्यप्रणाली / Physiological Mechanism**
               - **कोशिकीय और आणविक स्तर / Cellular & Molecular Detail**
               - **मुख्य वैज्ञानिक तथ्य / Key Scientific Facts**
            """.trimIndent()
        )
    }

    private val blueprintSchema = Schema.obj(
        properties = mapOf(
            "titleEn" to Schema.string("Short English anatomical title"),
            "titleHi" to Schema.string("Short Hindi anatomical title with English term in parentheses"),
            "systemEn" to Schema.string("Biological system in English, e.g. Nervous System"),
            "systemHi" to Schema.string("Biological system in Hindi"),
            "summaryEn" to Schema.string("1-2 sentence scientific summary in English"),
            "summaryHi" to Schema.string("1-2 sentence scientific summary in Hindi"),
            "scaleLabel" to Schema.string("Scale and size label e.g. ~10 µm | Microscopic or ~300g | Organ"),
            "parts" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string("Unique snake_case id"),
                        "nameEn" to Schema.string("Part name in English"),
                        "nameHi" to Schema.string("Part name in Hindi"),
                        "layer" to Schema.enumeration(listOf("OUTER", "MIDDLE", "INNER", "CELLULAR")),
                        "shape" to Schema.enumeration(listOf("ELLIPSOID", "SPHERE", "CYLINDER", "CAPSULE", "TORUS", "HELIX", "BRANCHING")),
                        "x" to Schema.double("Normalized X position between -0.6 and 0.6"),
                        "y" to Schema.double("Normalized Y position between -0.6 and 0.6"),
                        "z" to Schema.double("Normalized Z position between -0.6 and 0.6"),
                        "rx" to Schema.double("X radius between 0.05 and 0.72"),
                        "ry" to Schema.double("Y radius between 0.05 and 0.72"),
                        "rz" to Schema.double("Z radius between 0.05 and 0.72"),
                        "rotX" to Schema.double("Rotation X radians between -1.6 and 1.6"),
                        "rotY" to Schema.double("Rotation Y radians between -1.6 and 1.6"),
                        "rotZ" to Schema.double("Rotation Z radians between -1.6 and 1.6"),
                        "colorHex" to Schema.string("6-char hex color like FB7185 or 38BDF8 without #"),
                        "opacity" to Schema.double("Opacity 0.25 to 1.0 (use 0.28-0.55 for OUTER shell so internal parts show)"),
                        "roleEn" to Schema.string("Concise 1-line function in English"),
                        "roleHi" to Schema.string("Concise 1-line function in Hindi"),
                        "detailEn" to Schema.string("2-sentence anatomical detail in English"),
                        "detailHi" to Schema.string("2-sentence anatomical detail in Hindi")
                    )
                ),
                description = "6 to 9 distinct 3D sub-parts representing outer shell and internal parts"
            )
        )
    )

    // Primary model with Google Search grounding tool
    private val webGroundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch()),
            systemInstruction = qaSystemInstruction
        )
    }

    // Direct Gemini 3.8 Flash model without tools
    private val standardQaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = qaSystemInstruction
        )
    }

    // Runtime anatomical/biological image generation model ("Nano Banana 2.1")
    private val imageGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    // Structured JSON model that constructs an interactive 3D multi-part model for ANY queried body part
    private val blueprint3dModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = blueprintSchema
            },
            systemInstruction = content {
                text(
                    """
                    You are a 3D Anatomical Geometry Architect.
                    Given ANY human body part, organ, cell, organelle, tissue, or biological structure, design a scientifically faithful 3D composite assembly using 6 to 9 sub-parts in normalized [-0.7, 0.7] 3D space.
                    Rules:
                    - Always include at least 1 OUTER layer part (opacity 0.28..0.52) so internal structures remain visible in X-Ray/Cutaway mode.
                    - Include MIDDLE, INNER, and CELLULAR internal sub-parts inside/around the structure with distinct, vivid anatomical colors (hex without #, e.g. 38BDF8, FB7185, 2DD4BF, FBBF24, A78BFA, 34D399, F43F5E).
                    - Choose realistic coordinates and radii so parts nest or connect like the real anatomy.
                    - Provide accurate bilingual English & Hindi labels and descriptions.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamBiologyAnswer(
        question: String,
        activeModelTitle: String?,
        selectedSubPart: String?,
        language: AppLanguage,
        attachedBitmap: Bitmap? = null,
        conversationHistory: List<QaMessage> = emptyList()
    ): Flow<StreamChunkUpdate> = flow {
        val langDirective = if (language == AppLanguage.HINDI) {
            "Respond in clear, natural Hindi (Devanagari) with important scientific/anatomical terms also shown in English parentheses."
        } else {
            "Respond in clear, precise English."
        }

        val contextNote = buildString {
            if (!activeModelTitle.isNullOrBlank()) {
                append("Active 3D Model: $activeModelTitle. ")
            }
            if (!selectedSubPart.isNullOrBlank()) {
                append("Focused internal part: $selectedSubPart. ")
            }
        }

        val promptText = """
            $langDirective
            $contextNote
            User Question: $question
            
            Provide a complete, authoritative scientific explanation covering both the whole part and its internal sub-parts in 3D spatial order.
        """.trimIndent()

        val inputContent: Content = content {
            if (attachedBitmap != null) {
                image(attachedBitmap)
            }
            text(promptText)
        }

        // Convert recent completed turns into multi-turn chat history
        val chatHistory: List<Content> = conversationHistory
            .filter { !it.isStreaming && it.answer.isNotBlank() }
            .takeLast(6)
            .flatMap { turn ->
                listOf(
                    content("user") { text(turn.question) },
                    content("model") { text(turn.answer) }
                )
            }

        val accumulated = StringBuilder()
        val collectedSources = linkedMapOf<String, WebSource>()

        var streamedFromSearch = false
        if (searchToolAvailable) {
            try {
                withTimeout(6_000L) {
                    val flow = if (chatHistory.isEmpty()) {
                        webGroundedModel.generateContentStream(inputContent)
                    } else {
                        webGroundedModel.startChat(history = chatHistory).sendMessageStream(inputContent)
                    }
                    flow.collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                        }

                        chunk.candidates.forEach { candidate ->
                            candidate.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                                val web = gChunk.web
                                val uri = web?.uri
                                val title = web?.title
                                if (!uri.isNullOrBlank()) {
                                    collectedSources[uri] = WebSource(
                                        title = title?.takeIf { it.isNotBlank() } ?: uri,
                                        url = uri
                                    )
                                }
                            }
                        }

                        if (accumulated.isNotEmpty()) {
                            streamedFromSearch = true
                            emit(
                                StreamChunkUpdate(
                                    accumulatedText = accumulated.toString(),
                                    sources = collectedSources.values.toList()
                                )
                            )
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Search-grounded stream timed out after 6s; falling back to standard Gemini 3.8 Flash", e)
                if (!streamedFromSearch) {
                    searchToolAvailable = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search-grounded stream failed (${e.message}); falling back to standard Gemini 3.8 Flash", e)
                if (!streamedFromSearch) {
                    searchToolAvailable = false
                }
            }
        }

        if (!streamedFromSearch || accumulated.isBlank()) {
            accumulated.clear()
            var streamedFromStandard = false
            try {
                withTimeout(45_000L) {
                    val flow = if (chatHistory.isEmpty()) {
                        standardQaModel.generateContentStream(inputContent)
                    } else {
                        standardQaModel.startChat(history = chatHistory).sendMessageStream(inputContent)
                    }
                    flow.collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            streamedFromStandard = true
                            emit(
                                StreamChunkUpdate(
                                    accumulatedText = accumulated.toString(),
                                    sources = collectedSources.values.toList()
                                )
                            )
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Standard stream timed out", e)
                if (!streamedFromStandard) throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Standard stream failed (${e.message}); trying unary generateContent", e)
                if (streamedFromStandard) throw e
            }

            if (!streamedFromStandard || accumulated.isBlank()) {
                val unary = withTimeout(45_000L) {
                    if (chatHistory.isEmpty()) {
                        standardQaModel.generateContent(inputContent)
                    } else {
                        standardQaModel.startChat(history = chatHistory).sendMessage(inputContent)
                    }
                }
                val unaryText = unary.text.orEmpty()
                if (unaryText.isNotBlank()) {
                    emit(
                        StreamChunkUpdate(
                            accumulatedText = unaryText,
                            sources = collectedSources.values.toList()
                        )
                    )
                }
            }
        }
    }

    suspend fun generateAnatomyImage(topic: String): Bitmap {
        val imagePrompt = """
            High-precision, scientifically accurate 3D medical anatomy cutaway illustration of: $topic.
            Show clear outer silhouette and vivid detailed internal anatomical sub-parts in cross-section, clean studio volumetric lighting against a deep dark obsidian-teal medical observatory background (#071625), ultra-crisp textbook scientific visualization, no distorted text.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            imageGenModel.generateContent(imagePrompt)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("No image was returned by the model.")
    }

    suspend fun generate3DBlueprint(query: String): Anatomy3DModel {
        val prompt = """
            Design a detailed interactive 3D anatomical/biological model for: "$query".
            Break it down into 6 to 9 distinct 3D outer and internal sub-parts with realistic spatial positions, shapes, layers, colors, and accurate Hindi + English explanations.
        """.trimIndent()

        val response = withTimeout(45_000L) {
            blueprint3dModel.generateContent(prompt)
        }

        val jsonText = response.text
            ?: throw IllegalStateException("AI returned empty 3D blueprint response.")

        return parseBlueprintJson(query, jsonText)
    }

    private fun parseBlueprintJson(query: String, jsonText: String): Anatomy3DModel {
        val root = JSONObject(jsonText)
        val partsArray = root.getJSONArray("parts")
        val parts = buildList {
            for (i in 0 until partsArray.length()) {
                val p = partsArray.getJSONObject(i)
                val hexClean = p.optString("colorHex", "38BDF8")
                    .removePrefix("#")
                    .take(6)
                    .padStart(6, '0')
                val colorValue = ("FF$hexClean").toLongOrNull(16) ?: 0xFF38BDF8L

                val layer = runCatching {
                    AnatomyLayer.valueOf(p.optString("layer", "INNER").uppercase())
                }.getOrDefault(AnatomyLayer.INNER)

                val shape = runCatching {
                    PartShape.valueOf(p.optString("shape", "SPHERE").uppercase())
                }.getOrDefault(PartShape.SPHERE)

                add(
                    Anatomy3DPart(
                        id = p.optString("id", "part_$i"),
                        nameEn = p.optString("nameEn", "Part ${i + 1}"),
                        nameHi = p.optString("nameHi", p.optString("nameEn", "भाग ${i + 1}")),
                        layer = layer,
                        shape = shape,
                        x = p.optDouble("x", 0.0).toFloat().coerceIn(-0.75f, 0.75f),
                        y = p.optDouble("y", 0.0).toFloat().coerceIn(-0.75f, 0.75f),
                        z = p.optDouble("z", 0.0).toFloat().coerceIn(-0.75f, 0.75f),
                        rx = p.optDouble("rx", 0.2).toFloat().coerceIn(0.04f, 0.75f),
                        ry = p.optDouble("ry", 0.2).toFloat().coerceIn(0.04f, 0.75f),
                        rz = p.optDouble("rz", 0.2).toFloat().coerceIn(0.04f, 0.75f),
                        rotX = p.optDouble("rotX", 0.0).toFloat(),
                        rotY = p.optDouble("rotY", 0.0).toFloat(),
                        rotZ = p.optDouble("rotZ", 0.0).toFloat(),
                        colorHex = colorValue,
                        opacity = p.optDouble("opacity", if (layer == AnatomyLayer.OUTER) 0.40 else 0.95)
                            .toFloat().coerceIn(0.22f, 1.0f),
                        roleEn = p.optString("roleEn", ""),
                        roleHi = p.optString("roleHi", ""),
                        detailEn = p.optString("detailEn", ""),
                        detailHi = p.optString("detailHi", "")
                    )
                )
            }
        }

        if (parts.isEmpty()) {
            throw IllegalStateException("3D blueprint contained no parts.")
        }

        return Anatomy3DModel(
            id = "ai_${query.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(24)}_${System.currentTimeMillis()}",
            titleEn = root.optString("titleEn", query),
            titleHi = root.optString("titleHi", query),
            systemEn = root.optString("systemEn", "Human Biology"),
            systemHi = root.optString("systemHi", "मानव जीव विज्ञान"),
            summaryEn = root.optString("summaryEn", ""),
            summaryHi = root.optString("summaryHi", ""),
            scaleLabel = root.optString("scaleLabel", "3D AI Model"),
            parts = parts,
            isAiGenerated = true
        )
    }
}
