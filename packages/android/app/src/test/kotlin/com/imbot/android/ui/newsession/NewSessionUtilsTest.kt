package com.imbot.android.ui.newsession

import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayWorkspaceRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSessionUtilsTest {
    @Test
    fun `toBreadcrumbs returns single breadcrumb for root path`() {
        val result = "/".toBreadcrumbs()

        assertEquals(listOf(DirectoryBreadcrumb(label = "/", path = "/")), result)
    }

    @Test
    fun `toBreadcrumbs returns all breadcrumb segments for nested path`() {
        val result = "/Users/danker/projects".toBreadcrumbs()

        assertEquals(
            listOf(
                DirectoryBreadcrumb(label = "/", path = "/"),
                DirectoryBreadcrumb(label = "Users", path = "/Users"),
                DirectoryBreadcrumb(label = "danker", path = "/Users/danker"),
                DirectoryBreadcrumb(label = "projects", path = "/Users/danker/projects"),
            ),
            result,
        )
    }

    @Test
    fun `toBreadcrumbs returns empty list for blank path`() {
        assertTrue("".toBreadcrumbs().isEmpty())
        assertTrue("   ".toBreadcrumbs().isEmpty())
    }

    @Test
    fun `filterRootsForProvider filters to only book roots`() {
        val result = filterRootsForProvider("book", sampleRoots())

        assertEquals(listOf(root(id = "root-book", provider = "book", path = "/Users/danker/novel")), result)
    }

    @Test
    fun `filterRootsForProvider returns all roots for claude`() {
        val roots = sampleRoots()

        assertEquals(roots, filterRootsForProvider("claude", roots))
    }

    @Test
    fun `filterRootsForProvider returns all roots for openclaw`() {
        val roots = sampleRoots()

        assertEquals(roots, filterRootsForProvider("openclaw", roots))
    }

    @Test
    fun `findHostForProvider returns online matching host`() {
        val offline = host(id = "offline-macbook", status = "offline", providers = listOf("claude"))
        val online = host(id = "online-macbook", providers = listOf("claude"))

        val result = findHostForProvider("claude", listOf(offline, online))

        assertEquals(online, result)
    }

    @Test
    fun `findHostForProvider falls back to offline host when no online match exists`() {
        val offline = host(id = "offline-macbook", status = "offline", providers = listOf("book"))

        val result = findHostForProvider("book", listOf(offline))

        assertEquals(offline, result)
    }

    @Test
    fun `findHostForProvider returns null when provider has no host`() {
        val result =
            findHostForProvider(
                "openclaw",
                listOf(host(id = "macbook-1", providers = listOf("claude", "book"))),
            )

        assertNull(result)
    }

    @Test
    fun `providerOfflineMessage returns provider specific Chinese copy`() {
        assertEquals("Claude Code 所在主机当前离线，请返回上一步重新选择。", providerOfflineMessage("claude"))
        assertEquals("book 所在主机当前离线，请返回上一步重新选择。", providerOfflineMessage("book"))
        assertEquals("OpenClaw 当前不可用，请稍后重试。", providerOfflineMessage("openclaw"))
    }

    @Test
    fun `providerOfflineMessage returns generic message for unknown provider`() {
        assertEquals("当前 Provider 不可用，请稍后重试。", providerOfflineMessage("custom"))
    }

    @Test
    fun `canMoveNext for step 0 requires provider and host selection`() {
        assertTrue(canMoveNext(NewSessionUiState(step = 0, provider = "claude", hostId = "macbook-1")))
        assertFalse(canMoveNext(NewSessionUiState(step = 0, provider = "claude")))
        assertFalse(canMoveNext(NewSessionUiState(step = 0, hostId = "macbook-1")))
    }

    @Test
    fun `canMoveNext for step 1 requires selected cwd`() {
        assertTrue(canMoveNext(NewSessionUiState(step = 1, cwd = "/Users/danker/projects")))
        assertFalse(canMoveNext(NewSessionUiState(step = 1)))
    }

    @Test
    fun `canMoveNext returns false for step 2 and beyond`() {
        assertFalse(canMoveNext(NewSessionUiState(step = 2, provider = "claude", hostId = "macbook-1", cwd = "/tmp")))
        assertFalse(canMoveNext(NewSessionUiState(step = 3, provider = "claude", hostId = "macbook-1", cwd = "/tmp")))
    }

    @Test
    fun `canCreate returns true when provider host and cwd are present even when prompt is blank`() {
        assertTrue(
            canCreate(
                NewSessionUiState(
                    provider = "claude",
                    hostId = "macbook-1",
                    cwd = "/Users/danker/projects",
                    prompt = "   ",
                ),
            ),
        )
    }

    @Test
    fun `canCreate returns false when provider host or cwd is missing`() {
        val states =
            listOf(
                NewSessionUiState(hostId = "macbook-1", cwd = "/tmp", prompt = "test"),
                NewSessionUiState(provider = "claude", cwd = "/tmp", prompt = "test"),
                NewSessionUiState(provider = "claude", hostId = "macbook-1", prompt = "test"),
            )

        states.forEach { state ->
            assertFalse(canCreate(state))
        }
    }

    private fun sampleRoots(): List<RelayWorkspaceRoot> =
        listOf(
            root(id = "root-claude", provider = "claude", path = "/Users/danker/projects"),
            root(id = "root-book", provider = "book", path = "/Users/danker/novel"),
            root(id = "root-openclaw", hostId = "relay-local", provider = "openclaw", path = "/srv/openclaw"),
        )

    private fun root(
        id: String,
        hostId: String = "macbook-1",
        provider: String,
        path: String,
    ) = RelayWorkspaceRoot(
        id = id,
        hostId = hostId,
        provider = provider,
        path = path,
        label = null,
        createdAt = "2026-03-31T10:00:00Z",
    )

    private fun host(
        id: String,
        status: String = "online",
        providers: List<String>,
    ) = RelayHost(
        id = id,
        name = id,
        type = "companion",
        status = status,
        providers = providers,
    )
}
