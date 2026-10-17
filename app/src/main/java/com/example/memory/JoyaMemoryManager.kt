package com.example.memory

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Local persistent memory manager for Joya Assistant.
 * Securely stores user notes, preferences, and custom reminders locally.
 * Prevents saving sensitive credentials like passwords, OTPs, or banking PINs.
 */
class JoyaMemoryManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("JoyaMemoryPrefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        val raw = prefs.getString("saved_memories", null)
        if (!raw.isNullOrBlank()) {
            try {
                val list = json.decodeFromString<List<MemoryItem>>(raw)
                _memories.value = list
            } catch (e: Exception) {
                _memories.value = emptyList()
            }
        }
    }

    private fun saveMemories(list: List<MemoryItem>) {
        _memories.value = list
        try {
            val raw = json.encodeToString(list)
            prefs.edit().putString("saved_memories", raw).apply()
        } catch (e: Exception) {
            // handle error safely
        }
    }

    /**
     * Add a memory item with security guardrails against passwords/PINs.
     */
    fun remember(content: String, category: String = "General"): String {
        val lower = content.lowercase()
        val sensitivePatterns = listOf("password", "pin", "otp", "cvv", "bank account", "atm", "debit card")
        if (sensitivePatterns.any { lower.contains(it) }) {
            return "सुरक्षा कारणों से, मैं पासवर्ड, पिन, या बैंकिंग जानकारी स्टोर नहीं कर सकती। अपनी व्यक्तिगत सुरक्षा हमेशा पहले रखें!"
        }

        val newItem = MemoryItem(content = content.trim(), category = category)
        val updated = listOf(newItem) + _memories.value
        saveMemories(updated)
        return "मैंने याद रख लिया: \"$content\""
    }

    fun getAllMemoriesText(): String {
        val list = _memories.value
        if (list.isEmpty()) {
            return "मेरी मेमोरी अभी खाली है। आप मुझे कोई भी बात याद रखने के लिए कह सकते हैं, जैसे: 'Joya इसे याद रखो'."
        }
        val builder = StringBuilder("आपकी याद रखी हुई बातें:\n")
        list.forEachIndexed { index, item ->
            builder.append("${index + 1}. ${item.content}\n")
        }
        return builder.toString().trim()
    }

    fun searchMemories(query: String): List<MemoryItem> {
        val q = query.lowercase().trim()
        return _memories.value.filter { it.content.lowercase().contains(q) }
    }

    fun deleteMemory(id: String): Boolean {
        val initialSize = _memories.value.size
        val updated = _memories.value.filter { it.id != id }
        if (updated.size != initialSize) {
            saveMemories(updated)
            return true
        }
        return false
    }

    fun forget(contentQuery: String): String {
        val q = contentQuery.lowercase().trim()
        val matches = _memories.value.filter { it.content.lowercase().contains(q) }
        if (matches.isEmpty()) {
            return "\"$contentQuery\" से मिलती-जुलती कोई मेमोरी नहीं मिली।"
        }
        val updated = _memories.value.filterNot { it.content.lowercase().contains(q) }
        saveMemories(updated)
        return "मैंने \"${matches.first().content}\" को मेमोरी से हटा दिया है।"
    }

    fun clearAll(): String {
        saveMemories(emptyList())
        return "मेमोरी पूरी तरह साफ़ कर दी गई है।"
    }
}
