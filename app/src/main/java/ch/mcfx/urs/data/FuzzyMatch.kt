package ch.mcfx.urs.data

/**
 * Typo- and word-order-tolerant ranking for short candidate lists (tag
 * autocomplete). Each whitespace/`-`/`_`-separated query token must
 * approximately occur somewhere in the candidate — within an edit-distance
 * budget that grows with token length — so a missing or swapped letter, or
 * a different word order, still matches. Results are ordered by total edit
 * distance, then prefix matches first, then alphabetically.
 */
object FuzzyMatch {

    private val SEPARATORS = Regex("[\\s_-]+")

    private data class Scored(val name: String, val distance: Int, val prefix: Boolean)

    fun rank(candidates: List<String>, query: String, limit: Int = 10): List<String> {
        val normalizedQuery = query.trim().lowercase()
        val tokens = normalizedQuery.split(SEPARATORS).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return candidates
            .mapNotNull { name ->
                val lower = name.lowercase()
                var total = 0
                for (token in tokens) {
                    val distance = substringDistance(token, lower)
                    if (distance > tolerance(token.length)) return@mapNotNull null
                    total += distance
                }
                Scored(name, total, lower.startsWith(normalizedQuery))
            }
            .sortedWith(compareBy<Scored> { it.distance }.thenBy { !it.prefix }.thenBy { it.name })
            .take(limit)
            .map { it.name }
    }

    private fun tolerance(tokenLength: Int): Int = when {
        tokenLength <= 3 -> 0
        tokenLength <= 6 -> 1
        else -> 2
    }

    // Minimum edit distance between [token] and any substring of [text]
    // (Sellers' approximate substring matching): the first DP row is all
    // zeros, so a match may start anywhere in the text.
    private fun substringDistance(token: String, text: String): Int {
        var column = IntArray(token.length + 1) { it }
        var best = column[token.length]
        for (c in text) {
            val next = IntArray(token.length + 1)
            for (i in 1..token.length) {
                val substitution = column[i - 1] + if (token[i - 1] == c) 0 else 1
                next[i] = minOf(substitution, column[i] + 1, next[i - 1] + 1)
            }
            column = next
            best = minOf(best, column[token.length])
        }
        return best
    }
}
