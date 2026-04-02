package com.example.dualquick.data

/**
 * Represents a single character entry from the simplex.cin file.
 *
 * @property code The input code (1-2 lowercase letters, e.g., "ab", "qr")
 * @property character The Chinese character this code maps to (e.g., "明", "招")
 * @property order The position in the .cin file (preserves frequency/Big5 order)
 */
data class SimplexEntry(
    val code: String,
    val character: String,
    val order: Int
)
