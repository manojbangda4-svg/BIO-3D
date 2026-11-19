package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.QaMessage
import com.example.model.SavedTopic
import com.example.model.WebSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.bioAtlasDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bio3d_atlas_prefs"
)

class HistoryRepository(private val context: Context) {

    private val keyLanguage = stringPreferencesKey("app_language")
    private val keySavedTopics = stringPreferencesKey("saved_topics_json")
    private val keyQaHistory = stringPreferencesKey("qa_history_json")

    val languageFlow: Flow<AppLanguage> = context.bioAtlasDataStore.data.map { prefs ->
        when (prefs[keyLanguage]) {
            AppLanguage.ENGLISH.code -> AppLanguage.ENGLISH
            else -> AppLanguage.HINDI
        }
    }

    val savedTopicsFlow: Flow<List<SavedTopic>> = context.bioAtlasDataStore.data.map { prefs ->
        val raw = prefs[keySavedTopics].orEmpty()
        if (raw.isBlank()) emptyList() else parseTopics(raw)
    }

    val qaHistoryFlow: Flow<List<QaMessage>> = context.bioAtlasDataStore.data.map { prefs ->
        val raw = prefs[keyQaHistory].orEmpty()
        if (raw.isBlank()) emptyList() else parseQaHistory(raw)
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.bioAtlasDataStore.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }

    suspend fun saveTopic(topic: SavedTopic) {
        context.bioAtlasDataStore.edit { prefs ->
            val current = prefs[keySavedTopics]?.let { parseTopics(it) }.orEmpty()
            val updated = (listOf(topic) + current.filterNot { it.query.equals(topic.query, ignoreCase = true) })
                .take(25)
            prefs[keySavedTopics] = encodeTopics(updated)
        }
    }

    suspend fun removeTopic(id: String) {
        context.bioAtlasDataStore.edit { prefs ->
            val current = prefs[keySavedTopics]?.let { parseTopics(it) }.orEmpty()
            prefs[keySavedTopics] = encodeTopics(current.filterNot { it.id == id })
        }
    }

    suspend fun saveQaHistory(messages: List<QaMessage>) {
        context.bioAtlasDataStore.edit { prefs ->
            val persistable = messages
                .filter { !it.isStreaming && (it.answer.isNotBlank() || it.errorMessage != null) }
                .takeLast(40)
            prefs[keyQaHistory] = encodeQaHistory(persistable)
        }
    }

    suspend fun clearQaHistory() {
        context.bioAtlasDataStore.edit { prefs ->
            prefs.remove(keyQaHistory)
        }
    }

    private fun parseTopics(jsonStr: String): List<SavedTopic> {
        return try {
            val array = JSONArray(jsonStr)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        SavedTopic(
                            id = obj.optString("id"),
                            query = obj.optString("query"),
                            title = obj.optString("title"),
                            systemTag = obj.optString("systemTag"),
                            snippet = obj.optString("snippet"),
                            timestampMillis = obj.optLong("timestampMillis")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeTopics(topics: List<SavedTopic>): String {
        val array = JSONArray()
        topics.forEach { t ->
            array.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("query", t.query)
                    put("title", t.title)
                    put("systemTag", t.systemTag)
                    put("snippet", t.snippet)
                    put("timestampMillis", t.timestampMillis)
                }
            )
        }
        return array.toString()
    }

    private fun parseQaHistory(jsonStr: String): List<QaMessage> {
        return try {
            val array = JSONArray(jsonStr)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val sourcesArray = obj.optJSONArray("sources")
                    val sources = buildList {
                        if (sourcesArray != null) {
                            for (sIdx in 0 until sourcesArray.length()) {
                                val sObj = sourcesArray.optJSONObject(sIdx) ?: continue
                                add(
                                    WebSource(
                                        title = sObj.optString("title"),
                                        url = sObj.optString("url")
                                    )
                                )
                            }
                        }
                    }
                    add(
                        QaMessage(
                            id = obj.optString("id"),
                            question = obj.optString("question"),
                            answer = obj.optString("answer"),
                            sources = sources,
                            linkedTopicQuery = obj.optString("linkedTopicQuery").takeIf { it.isNotBlank() },
                            isStreaming = false,
                            errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                            hasAttachedImage = obj.optBoolean("hasAttachedImage", false),
                            timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeQaHistory(messages: List<QaMessage>): String {
        val array = JSONArray()
        messages.forEach { msg ->
            array.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("question", msg.question)
                    put("answer", msg.answer)
                    put("linkedTopicQuery", msg.linkedTopicQuery.orEmpty())
                    put("errorMessage", msg.errorMessage.orEmpty())
                    put("hasAttachedImage", msg.hasAttachedImage)
                    put("timestampMillis", msg.timestampMillis)
                    put(
                        "sources",
                        JSONArray().apply {
                            msg.sources.forEach { src ->
                                put(
                                    JSONObject().apply {
                                        put("title", src.title)
                                        put("url", src.url)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
        return array.toString()
    }
}
