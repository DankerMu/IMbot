@file:Suppress("MaxLineLength")

package com.imbot.android.ui.workspace

import com.imbot.android.FakeRelayWsClient
import com.imbot.android.MainDispatcherRule
import com.imbot.android.network.BrowseResult
import com.imbot.android.network.RelayApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddRootStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `provider selection auto resolves hostId`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()

            assertEquals("macbook-1", viewModel.addRootState.value.hostId)
        }

    @Test
    fun `offline host selection keeps submit disabled by clearing current path`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(workspaceHost(id = "macbook-1", type = "macbook", status = "offline")),
                                hostWithRoots(workspaceHost(id = "relay-local", type = "relay_local")),
                            ),
                        )
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()

            assertEquals("", viewModel.addRootState.value.currentPath)
            assertEquals("macbook-1 离线，无法浏览目录", viewModel.addRootState.value.error)
            assertTrue(repo.browseRequests.isEmpty())
        }

    @Test
    fun `directory browse updates currentPath and entries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.browseDirectoryHandler =
                { _, path ->
                    Result.success(
                        browseResult(
                            path,
                            browseEntry(name = "IMbot", path = "$path/IMbot"),
                            browseEntry(name = "tools", path = "$path/tools"),
                        ),
                    )
                }

            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()
            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()

            viewModel.browseAddRootDirectory("/Users/danker/projects")
            advanceUntilIdle()

            assertEquals("/Users/danker/projects", viewModel.addRootState.value.currentPath)
            assertEquals(2, viewModel.addRootState.value.directories.size)
        }

    @Test
    fun `stale browse responses are ignored after provider switch`() =
        runTest(mainDispatcherRule.dispatcher) {
            val claudeBrowseGate = CompletableDeferred<Result<BrowseResult>>()
            val openClawBrowseGate = CompletableDeferred<Result<BrowseResult>>()
            val repo =
                configuredRepository().apply {
                    browseDirectoryHandler =
                        { hostId, path ->
                            when (hostId to path) {
                                "macbook-1" to "/Users" -> claudeBrowseGate.await()
                                "relay-local" to "/" -> openClawBrowseGate.await()
                                else -> Result.failure(IllegalStateException("unexpected browse request"))
                            }
                        }
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()

            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            runCurrent()
            viewModel.selectProvider("openclaw")
            runCurrent()

            claudeBrowseGate.complete(
                Result.success(
                    browseResult(
                        "/Users",
                        browseEntry(name = "claude-only", path = "/Users/claude-only"),
                    ),
                ),
            )
            runCurrent()

            assertEquals("openclaw", viewModel.addRootState.value.provider)
            assertEquals("/", viewModel.addRootState.value.currentPath)
            assertTrue(viewModel.addRootState.value.directories.isEmpty())

            openClawBrowseGate.complete(
                Result.success(
                    browseResult(
                        "/",
                        browseEntry(name = "relay-dir", path = "/relay-dir"),
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals("openclaw", viewModel.addRootState.value.provider)
            assertEquals("relay-local", viewModel.addRootState.value.hostId)
            assertEquals("/", viewModel.addRootState.value.currentPath)
            assertEquals(listOf("relay-dir"), viewModel.addRootState.value.directories.map { it.name })
        }

    @Test
    fun `breadcrumb navigation browses parent directory`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.browseDirectoryHandler =
                { _, path ->
                    Result.success(
                        BrowseResult(
                            path = path,
                            directories = emptyList(),
                        ),
                    )
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()
            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()

            viewModel.browseAddRootDirectory("/Users/danker/projects/app")
            advanceUntilIdle()
            viewModel.browseAddRootUp()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "macbook-1" to "/Users",
                    "macbook-1" to "/Users/danker/projects/app",
                    "macbook-1" to "/Users/danker/projects",
                ),
                repo.browseRequests,
            )
        }

    @Test
    fun `browse caps large directory listings at two hundred entries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.browseDirectoryHandler =
                { _, path ->
                    Result.success(
                        BrowseResult(
                            path = path,
                            directories =
                                (250 downTo 1).map { index ->
                                    val directoryName = "dir-${index.toString().padStart(3, '0')}"
                                    browseEntry(
                                        name = directoryName,
                                        path = "$path/$directoryName",
                                    )
                                },
                        ),
                    )
                }
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()
            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()

            viewModel.browseAddRootDirectory("/Users/danker/projects")
            advanceUntilIdle()

            assertEquals(200, viewModel.addRootState.value.directories.size)
            assertEquals("dir-001", viewModel.addRootState.value.directories.first().name)
            assertEquals("dir-200", viewModel.addRootState.value.directories.last().name)
            assertEquals("目录条目过多，仅显示前 200 项", viewModel.addRootState.value.warning)
        }

    @Test
    fun `submit with valid fields calls API and dismisses sheet`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.addRootResult =
                Result.success(
                    workspaceRoot(
                        id = "root-1",
                        provider = "claude",
                        path = "/Users/danker/projects/IMbot",
                        label = "IMbot",
                    ),
                )
            val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
            advanceUntilIdle()
            viewModel.showAddRootSheet()
            viewModel.selectProvider("claude")
            advanceUntilIdle()
            viewModel.browseAddRootDirectory("/Users/danker/projects/IMbot")
            advanceUntilIdle()

            viewModel.submitAddRoot()
            advanceUntilIdle()

            assertEquals(1, repo.addRootCalls)
            assertFalse(viewModel.addRootState.value.isVisible)
        }

    @Test
    fun `submit conflict shows duplicate inline error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.addRootResult =
                Result.failure(
                    RelayApiException(
                        statusCode = 409,
                        code = "state_conflict",
                        message = "conflict",
                    ),
                )
            val viewModel = preparedViewModel(repo)

            viewModel.submitAddRoot()
            advanceUntilIdle()

            assertEquals("该目录已添加", viewModel.addRootState.value.error)
        }

    @Test
    fun `submit host offline shows host offline error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.addRootResult =
                Result.failure(
                    RelayApiException(
                        statusCode = 502,
                        code = "host_offline",
                        message = "offline",
                    ),
                )
            val viewModel = preparedViewModel(repo)

            viewModel.submitAddRoot()
            advanceUntilIdle()

            assertEquals("主机离线", viewModel.addRootState.value.error)
        }

    @Test
    fun `submit network error shows retryable inline error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.addRootResult = Result.failure(IllegalStateException("网络抖动"))
            val viewModel = preparedViewModel(repo)

            viewModel.submitAddRoot()
            advanceUntilIdle()

            assertEquals("网络抖动", viewModel.addRootState.value.error)
        }

    @Test
    fun `double tap submit guard ignores second tap while submitting`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Result<com.imbot.android.network.RelayWorkspaceRoot>>()
            val repo = configuredRepository()
            repo.addRootHandler = { _, _, _, _ -> gate.await() }
            val viewModel = preparedViewModel(repo)

            viewModel.submitAddRoot()
            runCurrent()
            viewModel.submitAddRoot()

            assertEquals(1, repo.addRootCalls)

            gate.complete(
                Result.success(
                    workspaceRoot(
                        id = "root-1",
                        provider = "claude",
                        path = "/Users/danker/projects/IMbot",
                    ),
                ),
            )
            advanceUntilIdle()
        }

    @Test
    fun `empty label defaults to directory basename`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            repo.addRootResult =
                Result.success(
                    workspaceRoot(
                        id = "root-1",
                        provider = "claude",
                        path = "/Users/danker/projects/IMbot",
                    ),
                )
            val viewModel = preparedViewModel(repo)
            viewModel.updateAddRootLabel("")

            viewModel.submitAddRoot()
            advanceUntilIdle()

            assertEquals("IMbot", repo.addRootRequests.single().label)
        }

    private fun createViewModel(): WorkspaceViewModel = WorkspaceViewModel(configuredRepository(), FakeRelayWsClient())

    private fun configuredRepository(): FakeWorkspaceRepository =
        FakeWorkspaceRepository().apply {
            getHostsWithRootsResult =
                Result.success(
                    listOf(
                        hostWithRoots(workspaceHost(id = "macbook-1", type = "macbook")),
                        hostWithRoots(workspaceHost(id = "relay-local", type = "relay_local")),
                    ),
                )
            browseDirectoryHandler =
                { _, path ->
                    Result.success(
                        browseResult(
                            path,
                            browseEntry(name = "child", path = "$path/child"),
                        ),
                    )
                }
        }

    private suspend fun kotlinx.coroutines.test.TestScope.preparedViewModel(repo: FakeWorkspaceRepository): WorkspaceViewModel {
        val viewModel = WorkspaceViewModel(repo, FakeRelayWsClient())
        advanceUntilIdle()
        viewModel.showAddRootSheet()
        viewModel.selectProvider("claude")
        advanceUntilIdle()
        viewModel.browseAddRootDirectory("/Users/danker/projects/IMbot")
        advanceUntilIdle()
        return viewModel
    }
}
