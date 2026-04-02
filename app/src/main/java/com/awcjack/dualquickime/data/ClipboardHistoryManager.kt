package com.awcjack.dualquickime.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages clipboard history persistence and operations.
 * Follows the same singleton pattern as ThemeManager.
 */
object ClipboardHistoryManager {
    private const val PREFS_NAME = "clipboard_history_prefs"
    private const val KEY_HISTORY = "clipboard_history"
    private const val KEY_ENABLED = "clipboard_enabled"

    const val MAX_HISTORY_SIZE = 50          // Maximum non-pinned items
    const val MAX_PINNED_SIZE = 10           // Maximum pinned items
    const val MIN_TEXT_LENGTH = 2            // Minimum text length to store
    const val MAX_TEXT_LENGTH = 5000         // Maximum text length to store

    // Cached data
    private var cachedHistory: MutableList<ClipboardHistoryItem>? = null
    private var cachedEnabled: Boolean? = null

    /**
     * Check if clipboard history is enabled.
     */
    fun isEnabled(context: Context): Boolean {
        if (cachedEnabled == null) {
            cachedEnabled = getPrefs(context).getBoolean(KEY_ENABLED, true)
        }
        return cachedEnabled!!
    }

    /**
     * Enable or disable clipboard history.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        cachedEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Add an item to clipboard history.
     * Handles deduplication - if text exists, moves it to top.
     */
    fun addItem(context: Context, text: String) {
        if (!isEnabled(context)) return
        if (text.length < MIN_TEXT_LENGTH || text.length > MAX_TEXT_LENGTH) return
        if (text.isBlank()) return

        val history = loadHistory(context)

        // Check for duplicate
        val existingIndex = history.indexOfFirst { it.text == text }
        if (existingIndex >= 0) {
            val existing = history[existingIndex]
            if (existing.isPinned) {
                // If pinned, just update timestamp
                history[existingIndex] = existing.copy(timestamp = System.currentTimeMillis())
            } else {
                // Remove old and add to top
                history.removeAt(existingIndex)
                history.add(0, ClipboardHistoryItem.create(text))
            }
        } else {
            // Add new item at top
            history.add(0, ClipboardHistoryItem.create(text))
        }

        // Enforce limits
        enforceHistoryLimits(history)

        saveHistory(context, history)
        cachedHistory = history
    }

    /**
     * Get all clipboard history items (pinned first, then by timestamp).
     */
    fun getHistory(context: Context): List<ClipboardHistoryItem> {
        return loadHistory(context).sortedWith(
            compareByDescending<ClipboardHistoryItem> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
    }

    /**
     * Get only pinned items.
     */
    fun getPinnedItems(context: Context): List<ClipboardHistoryItem> {
        return loadHistory(context)
            .filter { it.isPinned }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Get only non-pinned (recent) items.
     */
    fun getRecentItems(context: Context): List<ClipboardHistoryItem> {
        return loadHistory(context)
            .filter { !it.isPinned }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Toggle pin status of an item.
     */
    fun togglePin(context: Context, itemId: Long) {
        val history = loadHistory(context)
        val index = history.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = history[index]
            val newPinned = !item.isPinned

            // Check pinned limit
            if (newPinned) {
                val pinnedCount = history.count { it.isPinned }
                if (pinnedCount >= MAX_PINNED_SIZE) {
                    return // Don't allow more pinned items
                }
            }

            history[index] = item.copy(isPinned = newPinned)
            saveHistory(context, history)
            cachedHistory = history
        }
    }

    /**
     * Remove an item from history.
     */
    fun removeItem(context: Context, itemId: Long) {
        val history = loadHistory(context)
        history.removeAll { it.id == itemId }
        saveHistory(context, history)
        cachedHistory = history
    }

    /**
     * Clear all history.
     */
    fun clearHistory(context: Context, includePinned: Boolean = false) {
        if (includePinned) {
            saveHistory(context, mutableListOf())
            cachedHistory = mutableListOf()
        } else {
            val history = loadHistory(context)
            history.removeAll { !it.isPinned }
            saveHistory(context, history)
            cachedHistory = history
        }
    }

    /**
     * Invalidate cache (call when settings might have changed externally).
     */
    fun invalidateCache() {
        cachedHistory = null
        cachedEnabled = null
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadHistory(context: Context): MutableList<ClipboardHistoryItem> {
        if (cachedHistory != null) {
            return cachedHistory!!
        }

        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()

        return try {
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<ClipboardHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(deserializeItem(obj))
            }
            cachedHistory = items
            items
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveHistory(context: Context, history: List<ClipboardHistoryItem>) {
        val jsonArray = JSONArray()
        history.forEach { item ->
            jsonArray.put(serializeItem(item))
        }
        getPrefs(context).edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    private fun serializeItem(item: ClipboardHistoryItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("timestamp", item.timestamp)
            put("isPinned", item.isPinned)
        }
    }

    private fun deserializeItem(json: JSONObject): ClipboardHistoryItem {
        return ClipboardHistoryItem(
            id = json.getLong("id"),
            text = json.getString("text"),
            timestamp = json.getLong("timestamp"),
            isPinned = json.optBoolean("isPinned", false)
        )
    }

    private fun enforceHistoryLimits(history: MutableList<ClipboardHistoryItem>) {
        // Separate pinned and non-pinned
        val pinned = history.filter { it.isPinned }.sortedByDescending { it.timestamp }
        val nonPinned = history.filter { !it.isPinned }.sortedByDescending { it.timestamp }

        // Enforce limits
        val limitedPinned = pinned.take(MAX_PINNED_SIZE)
        val limitedNonPinned = nonPinned.take(MAX_HISTORY_SIZE)

        // Rebuild history
        history.clear()
        history.addAll(limitedPinned)
        history.addAll(limitedNonPinned)
    }
}
