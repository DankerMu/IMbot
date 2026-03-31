package com.imbot.android.ui.workspace

import com.imbot.android.FakeRelayWsClient
import com.imbot.android.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init fetches hosts and roots into uiState`() =
        runTest(mainDispatcherRule.dispatcher) {
            val macbook = workspaceHost(id = "macbook-1", type = "macbook")
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    macbook,
                                    workspaceRoot(
                                        id = "root-1",
                                        provider = "claude",
                                        path = "/Users/danker/AI-vault",
                                    ),
                                ),
                            ),
                        )
                }

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            assertEquals(1, repo.getHostsWithRootsCalls)
            assertEquals(1, viewModel.uiState.value.hosts.size)
            assertEquals("macbook-1", viewModel.uiState.value.hosts.first().host.id)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `refresh re-fetches and updates state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeWorkspaceRepository()
            repo.getHostsWithRootsHandler =
                whenCalled(
                    listOf(
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(id = "macbook-1", type = "macbook"),
                                    workspaceRoot(id = "root-1", provider = "claude", path = "/Users/danker/AI-vault"),
                                ),
                            ),
                        ),
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(id = "relay-local", type = "relay_local"),
                                    workspaceRoot(
                                        id = "root-2",
                                        hostId = "relay-local",
                                        provider = "openclaw",
                                        path = "/srv/openclaw",
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(2, repo.getHostsWithRootsCalls)
            assertEquals("relay-local", viewModel.uiState.value.hosts.single().host.id)
        }

    @Test
    fun `empty hosts list is treated as empty state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeWorkspaceRepository().apply { getHostsWithRootsResult = Result.success(emptyList()) }

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hosts.isEmpty())
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `host with multiple roots stays grouped under same host`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(id = "macbook-1", type = "macbook"),
                                    workspaceRoot(
                                        id = "claude-root",
                                        provider = "claude",
                                        path = "/Users/danker/AI-vault",
                                    ),
                                    workspaceRoot(
                                        id = "book-root",
                                        provider = "book",
                                        path = "/Users/danker/novel",
                                    ),
                                ),
                            ),
                        )
                }

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            val host = viewModel.uiState.value.hosts.single()
            assertEquals(2, host.roots.size)
            assertEquals(listOf("claude", "book"), host.roots.map { it.provider })
        }

    @Test
    fun `book roots remain distinct in grouped display`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(id = "macbook-1", type = "macbook"),
                                    workspaceRoot(id = "book-root", provider = "book", path = "/Users/danker/novel"),
                                ),
                            ),
                        )
                }

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            assertEquals("book", viewModel.uiState.value.hosts.single().roots.single().provider)
        }

    @Test
    fun `removeRoot success removes root and emits snackbar message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(id = "macbook-1", type = "macbook"),
                                    workspaceRoot(id = "root-1", provider = "claude", path = "/Users/danker/AI-vault"),
                                ),
                            ),
                        )
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            val event = backgroundScope.async { viewModel.events.first() }

            viewModel.requestRemoveRoot("macbook-1", "root-1", "AI-vault")
            viewModel.confirmRemoveRoot()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hosts.single().roots.isEmpty())
            assertEquals(WorkspaceEvent.ShowMessage("已移除"), event.await())
        }

    @Test
    fun `removeRoot failure preserves root and emits error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(id = "macbook-1", type = "macbook"),
                                    workspaceRoot(id = "root-1", provider = "claude", path = "/Users/danker/AI-vault"),
                                ),
                            ),
                        )
                    removeRootResult = Result.failure(IllegalStateException("boom"))
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            val event = backgroundScope.async { viewModel.events.first() }

            viewModel.requestRemoveRoot("macbook-1", "root-1", "AI-vault")
            viewModel.confirmRemoveRoot()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.hosts.single().roots.size)
            assertEquals(WorkspaceEvent.ShowMessage("删除失败，请重试"), event.await())
        }

    @Test
    fun `requestRemoveRoot exposes confirmation state before API call`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeWorkspaceRepository().apply { getHostsWithRootsResult = Result.success(emptyList()) }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            viewModel.requestRemoveRoot("macbook-1", "root-1", "AI-vault")

            assertNotNull(viewModel.uiState.value.pendingRemoval)
            assertEquals(0, repo.removeRootCalls)
        }

    @Test
    fun `network failure on init sets error and retry can recover`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult = Result.failure(IllegalStateException("加载失败"))
                }

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()
            assertEquals("加载失败", viewModel.uiState.value.error)

            repo.getHostsWithRootsResult =
                Result.success(
                    listOf(
                        hostWithRoots(
                            workspaceHost(id = "relay-local", type = "relay_local"),
                            workspaceRoot(
                                id = "root-2",
                                hostId = "relay-local",
                                provider = "openclaw",
                                path = "/srv/openclaw",
                            ),
                        ),
                    ),
                )

            viewModel.refresh()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            assertEquals("relay-local", viewModel.uiState.value.hosts.single().host.id)
        }

    @Test
    fun `provider to host auto mapping uses macbook for claude and relay local for openclaw`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(workspaceHost(id = "macbook-1", type = "macbook")),
                                hostWithRoots(workspaceHost(id = "relay-local", type = "relay_local")),
                            ),
                        )
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()
            assertEquals("macbook-1", viewModel.addRootState.value.hostId)

            viewModel.selectProvider("openclaw")
            advanceUntilIdle()
            assertEquals("relay-local", viewModel.addRootState.value.hostId)
        }
}

private fun <T> whenCalled(results: List<Result<T>>): suspend () -> Result<T> {
    var index = 0
    return {
        val currentIndex = index
        index = minOf(index + 1, results.lastIndex)
        results[currentIndex]
    }
}
