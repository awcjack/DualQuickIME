package com.awcjack.dualquickime.data

/**
 * Represents a single clipboard history item.
 */
data class ClipboardHistoryItem(
    val id: Long,                    // Unique identifier (timestamp-based)
    val text: String,                // The actual clipboard content
    val timestamp: Long,             // When it was added
    val isPinned: Boolean = false    // Whether this item is pinned
) {
    companion object {
        fun create(text: String, isPinned: Boolean = false): ClipboardHistoryItem {
            val now = System.currentTimeMillis()
            return ClipboardHistoryItem(
                id = now,
                text = text,
                timestamp = now,
                isPinned = isPinned
            )
        }
    }
}
