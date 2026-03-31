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
