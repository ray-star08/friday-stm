package com.gynda.fridaystm.domain

/**
 * Extracts the 11-char YouTube video id from whatever an instructor pasted:
 * a full `watch?v=`, `youtu.be/`, `/embed/`, `/shorts/` URL, or a bare id.
 *
 * Pure domain logic (no Android) so it is trivially unit-testable (SKILL.md §6);
 * the Senam layer stores only this id, not a full URL, so the player wrapper can
 * build both the embed and thumbnail deterministically.
 *
 * @return the 11-char id, or `null` if [input] contains no recognizable id.
 */
fun extractYouTubeId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // Already a bare id.
    if (trimmed.matches(ID_REGEX)) return trimmed

    // First 11-char id-shaped token following any known marker.
    for (regex in URL_REGEXES) {
        val id = regex.find(trimmed)?.groupValues?.getOrNull(1)
        if (id != null && id.matches(ID_REGEX)) return id
    }
    return null
}

/** A YouTube id is exactly 11 chars of `[A-Za-z0-9_-]`. */
private val ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

private val URL_REGEXES: List<Regex> = listOf(
    Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
    Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    Regex("""/embed/([A-Za-z0-9_-]{11})"""),
    Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
)
