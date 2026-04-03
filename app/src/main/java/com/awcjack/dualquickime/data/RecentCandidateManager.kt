package com.awcjack.dualquickime.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages recently used candidates to prioritize them in future lookups.
 * Tracks per-code usage: for each input code (e.g., "qr"), stores
 * recently selected characters ordered by recency.
 *
 * Data is persisted in SharedPreferences as a JSON object mapping
 * codes to ordered lists of recently used characters.
 */
object RecentCandidateManager {
    private const val PREFS_NAME = "recent_candidate_prefs"
    private const val KEY_RECENT_DATA = "recent_data"

    /** Maximum number of recent characters stored per input code. */
    const val MAX_RECENT_PER_CODE = 20

    /** Maximum number of distinct codes to track. */
    const val MAX_CODES = 500

    // Cached data: code -> ordered list of recent characters (most recent first)
    private var cachedData: MutableMap<String, MutableList<String>>? = null

    /**
     * Record that a candidate was selected for a given input code.
     * Moves the character to the front of the recent list for that code.
     */
    fun recordUsage(context: Context, code: String, character: String) {
        if (code.isBlank() || character.isBlank()) return

        val data = loadData(context)
        val recentList = data.getOrPut(code) { mutableListOf() }

        // Remove if already present (will be re-added at front)
        recentList.remove(character)
        // Add to front (most recent)
        recentList.add(0, character)

        // Enforce per-code limit
        while (recentList.size > MAX_RECENT_PER_CODE) {
            recentList.removeAt(recentList.lastIndex)
        }

        // Enforce total codes limit (evict least recently updated codes)
        if (data.size > MAX_CODES) {
            // Remove the oldest entries (keys added earliest that aren't this one)
            val keysToRemove = data.keys.toList().dropLast(MAX_CODES)
            keysToRemove.forEach { data.remove(it) }
        }

        saveData(context, data)
        cachedData = data
    }

    /**
     * Reorder candidates for a given code, placing recently used ones first.
     * Characters that were recently used appear at the beginning, in order
     * of most-recent-first. Remaining candidates follow in their original order.
     *
     * @param code The input code
     * @param candidates The original candidate list (frequency-ordered)
     * @return Reordered candidate list with recent candidates first
     */
    fun reorderCandidates(context: Context, code: String, candidates: List<String>): List<String> {
        val data = loadData(context)
        val recentList = data[code] ?: return candidates
        if (recentList.isEmpty()) return candidates

        val recentSet = recentList.toSet()
        val reordered = mutableListOf<String>()

        // Add recent candidates first (in recency order), only if they exist in candidates
        for (recent in recentList) {
            if (recent in candidates) {
                reordered.add(recent)
            }
        }

        // Add remaining candidates in their original order
        for (candidate in candidates) {
            if (candidate !in recentSet) {
                reordered.add(candidate)
            }
        }

        return reordered
    }

    /**
     * Get the recent candidates list for a specific code.
     */
    fun getRecentForCode(context: Context, code: String): List<String> {
        return loadData(context)[code] ?: emptyList()
    }

    /**
     * Clear all recent candidate data.
     */
    fun clearAll(context: Context) {
        saveData(context, mutableMapOf())
        cachedData = mutableMapOf()
    }

    /**
     * Invalidate cache (call when settings might have changed externally).
     */
    fun invalidateCache() {
        cachedData = null
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadData(context: Context): MutableMap<String, MutableList<String>> {
        if (cachedData != null) {
            return cachedData!!
        }

        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_RECENT_DATA, null)
            ?: return mutableMapOf<String, MutableList<String>>().also { cachedData = it }

        return try {
            val jsonObj = JSONObject(jsonString)
            val result = mutableMapOf<String, MutableList<String>>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val arr = jsonObj.getJSONArray(code)
                val chars = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    chars.add(arr.getString(i))
                }
                result[code] = chars
            }
            cachedData = result
            result
        } catch (e: Exception) {
            mutableMapOf<String, MutableList<String>>().also { cachedData = it }
        }
    }

    private fun saveData(context: Context, data: Map<String, List<String>>) {
        val jsonObj = JSONObject()
        for ((code, chars) in data) {
            val arr = JSONArray()
            chars.forEach { arr.put(it) }
            jsonObj.put(code, arr)
        }
        getPrefs(context).edit().putString(KEY_RECENT_DATA, jsonObj.toString()).apply()
    }
}
