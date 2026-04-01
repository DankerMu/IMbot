package com.imbot.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimeUtilsTest {
    private val now = Instant.parse("2026-03-30T12:00:00Z")

    @Test
    fun `formats just now`() {
        val formatted = formatRelativeTime("2026-03-30T11:59:40Z", now)

        assertEquals("刚刚", formatted)
    }

    @Test
    fun `formats minutes ago`() {
        val formatted = formatRelativeTime("2026-03-30T11:45:00Z", now)

        assertEquals("15 分钟前", formatted)
    }

    @Test
    fun `formats hours ago`() {
        val formatted = formatRelativeTime("2026-03-30T09:00:00Z", now)

        assertEquals("3 小时前", formatted)
    }

    @Test
    fun `formats yesterday`() {
        val formatted = formatRelativeTime("2026-03-29T12:30:00Z", now)

        assertEquals("昨天", formatted)
    }

    @Test
    fun `formats multiple days ago`() {
        val formatted = formatRelativeTime("2026-03-25T12:00:00Z", now)

        assertEquals("5 天前", formatted)
    }

    @Test
    fun `returns unknown for malformed timestamp`() {
        val malformed = formatRelativeTime("", now)
        val invalid = formatRelativeTime("not-an-iso-timestamp", now)

        assertEquals("未知", malformed)
        assertEquals("未知", invalid)
    }

    @Test
    fun `summarizes workspace path to last two segments`() {
        val summary = summarizeWorkspacePath("/Users/danker/Desktop/AI-vault/IMbot/packages/relay")

        assertEquals("packages/relay", summary)
    }

    @Test
    fun `keeps root slash for empty workspace path`() {
        val summary = summarizeWorkspacePath("/")

        assertEquals("/", summary)
    }

    @Test
    fun `idle status is treated as live and sorted with running sessions`() {
        assertEquals(true, isLiveStatus("idle"))
        assertEquals(true, isRunningStatus("idle"))
    }
}
