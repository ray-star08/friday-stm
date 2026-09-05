package com.gynda.fridaystm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [extractYouTubeId] — the only non-trivial Senam logic. */
class YouTubeTest {

    private val id = "dQw4w9WgXcQ" // canonical 11-char id

    @Test
    fun `extracts from standard watch url`() {
        assertEquals(id, extractYouTubeId("https://www.youtube.com/watch?v=$id"))
    }

    @Test
    fun `extracts from watch url with extra params`() {
        assertEquals(id, extractYouTubeId("https://youtube.com/watch?list=abc&v=$id&t=30s"))
    }

    @Test
    fun `extracts from short youtu_be url`() {
        assertEquals(id, extractYouTubeId("https://youtu.be/$id?si=xyz"))
    }

    @Test
    fun `extracts from embed and shorts urls`() {
        assertEquals(id, extractYouTubeId("https://www.youtube.com/embed/$id"))
        assertEquals(id, extractYouTubeId("https://www.youtube.com/shorts/$id"))
    }

    @Test
    fun `accepts a bare id`() {
        assertEquals(id, extractYouTubeId("  $id  "))
    }

    @Test
    fun `returns null for garbage or empty`() {
        assertNull(extractYouTubeId(""))
        assertNull(extractYouTubeId("not a youtube link"))
        assertNull(extractYouTubeId("https://vimeo.com/123456"))
    }
}
