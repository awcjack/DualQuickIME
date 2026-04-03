package com.awcjack.dualquickime.data

import com.awcjack.dualquickime.util.KeyMapping

/**
 * Tracks the current composition state during input.
 * Supports pagination for candidate selection.
 *
 * @property rawKeys The English letters typed (e.g., "qrofvd")
 * @property candidates All matching Chinese characters (ordered by frequency)
 * @property currentPage Current page index (0-based)
 * @property pageSize Number of candidates per page (default: 9)
 * @property activeKeyLength How many chars from the start of rawKeys are used for the current candidate lookup (1 or 2)
 */
data class CompositionState(
    val rawKeys: String = "",
    val candidates: List<String> = emptyList(),
    val currentPage: Int = 0,
    val pageSize: Int = 9,
    val activeKeyLength: Int = 0
) {
    /**
     * The radical display string (e.g., "手口" for "qr")
     */
    val radicalDisplay: String
        get() = KeyMapping.getRadicalSequence(rawKeys)

    /**
     * Total number of pages
     */
    val totalPages: Int
        get() = if (candidates.isEmpty()) 0 else (candidates.size + pageSize - 1) / pageSize

    /**
     * Candidates for the current page only
     */
    val currentPageCandidates: List<String>
        get() {
            if (candidates.isEmpty()) return emptyList()
            val start = currentPage * pageSize
            val end = minOf(start + pageSize, candidates.size)
            return if (start < candidates.size) candidates.subList(start, end) else emptyList()
        }

    /**
     * Whether there are any candidates
     */
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    /**
     * Whether the composition is empty (no keys typed)
     */
    val isEmpty: Boolean
        get() = rawKeys.isEmpty()

    /**
     * Move to the next page of candidates (wraps around to page 0)
     */
    fun nextPage(): CompositionState {
        if (totalPages <= 1) return this
        val next = if (currentPage + 1 >= totalPages) 0 else currentPage + 1
        return copy(currentPage = next)
    }

    /**
     * Reset to page 0
     */
    fun resetPage(): CompositionState = copy(currentPage = 0)

    companion object {
        val EMPTY = CompositionState()
    }
}
