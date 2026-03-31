package com.imbot.android.data.repository

import com.imbot.android.FakeRelayHttpClient
import com.imbot.android.FakeSettingsRepository
import com.imbot.android.MainDispatcherRule
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.ui.workspace.workspaceHost
import com.imbot.android.ui.workspace.workspaceRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkspaceRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `getHostsWithRoots combines host list with roots per host`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult =
                        Result.success(
                            listOf(
                                workspaceHost(id = "macbook-1", type = "macbook"),
                                workspaceHost(id = "relay-local", type = "relay_local"),
                            ),
                        )
                    getHostRootsHandler =
                        { _, _, hostId ->
                            Result.success(
                                when (hostId) {
                                    "macbook-1" ->
                                        listOf(
                                            workspaceRoot(
                                                id = "root-1",
                                                provider = "claude",
                                                path = "/Users/danker/projects",
                                            ),
                                        )

                                    else ->
                                        listOf(
                                            workspaceRoot(
                                                id = "root-2",
                                                hostId = "relay-local",
                                                provider = "openclaw",
                                                path = "/srv/openclaw",
                                            ),
                                        )
                                },
                            )
                        }
                }

            val repository = WorkspaceRepository(relay, FakeSettingsRepository())

            val result = repository.getHostsWithRoots()

            assertEquals(1, relay.getHostsCalls)
            assertEquals(listOf("macbook-1", "relay-local"), relay.rootRequests.map { it.hostId })
            assertEquals(2, result.size)
            assertEquals("root-1", result.first().roots.single().id)
            assertEquals("root-2", result.last().roots.single().id)
        }

    @Test
    fun `addRoot calls POST with provider path and label`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    addRootResult =
                        Result.success(
                            workspaceRoot(
                                id = "root-1",
                                provider = "claude",
                                path = "/Users/danker/projects",
                                label = "projects",
                            ),
                        )
                }

            val repository = WorkspaceRepository(relay, FakeSettingsRepository())
            repository.addRoot(
                hostId = "macbook-1",
                provider = "claude",
                path = "/Users/danker/projects",
                label = "projects",
            )

            assertEquals(
                listOf(
                    com.imbot.android.AddRootRequest(
                        relayUrl = "https://relay.example.com",
                        token = "test-token",
                        hostId = "macbook-1",
                        provider = "claude",
                        path = "/Users/danker/projects",
                        label = "projects",
                    ),
                ),
                relay.addRootRequests,
            )
        }

    @Test
    fun `removeRoot calls DELETE with hostId and rootId`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val repository = WorkspaceRepository(relay, FakeSettingsRepository())

            repository.removeRoot(hostId = "macbook-1", rootId = "root-1")

            assertEquals(
                listOf(
                    com.imbot.android.RemoveRootRequest(
                        relayUrl = "https://relay.example.com",
                        token = "test-token",
                        hostId = "macbook-1",
                        rootId = "root-1",
                    ),
                ),
                relay.removeRootRequests,
            )
        }

    @Test
    fun `browseDirectory calls GET with hostId and path`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val repository = WorkspaceRepository(relay, FakeSettingsRepository())

            repository.browseDirectory(
                hostId = "macbook-1",
                path = "/Users/danker/projects",
            )

            assertEquals(
                listOf(
                    com.imbot.android.BrowseRequest(
                        relayUrl = "https://relay.example.com",
                        token = "test-token",
                        hostId = "macbook-1",
                        path = "/Users/danker/projects",
                    ),
                ),
                relay.browseRequests,
            )
        }

    @Test
    fun `getSessionsByPathPrefix returns sessions whose cwd matches prefix`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sessionStore =
                com.imbot.android.FakeSessionStore().apply {
                    emitSessions(
                        "/Users/danker/projects",
                        listOf(
                            session(id = "sess-1", workspaceCwd = "/Users/danker/projects/IMbot"),
                            session(id = "sess-2", workspaceCwd = "/Users/danker/projects/tools"),
                        ),
                    )
                }

            val result = sessionStore.getSessionsByPathPrefix("/Users/danker/projects").first()

            assertEquals(listOf("sess-1", "sess-2"), result.map(SessionEntity::id))
        }
}

private fun session(
    id: String,
    workspaceCwd: String,
) = SessionEntity(
    id = id,
    provider = "claude",
    hostId = "macbook-1",
    workspaceCwd = workspaceCwd,
    initialPrompt = "test",
    model = "sonnet",
    status = "running",
    errorMessage = null,
    createdAt = "2026-03-31T10:00:00Z",
    updatedAt = "2026-03-31T10:00:00Z",
    lastActiveAt = "2026-03-31T10:00:00Z",
)
