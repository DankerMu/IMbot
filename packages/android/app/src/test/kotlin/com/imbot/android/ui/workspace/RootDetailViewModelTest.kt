package com.imbot.android.ui.workspace

import androidx.lifecycle.SavedStateHandle
import com.imbot.android.FakeSessionStore
import com.imbot.android.MainDispatcherRule
import com.imbot.android.data.local.SessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init fetches directory entries for root path`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            val viewModel = createViewModel(repo = repo)
            advanceUntilIdle()

            assertEquals("/Users/danker/projects", repo.browseRequests.first().second)
            assertEquals(2, viewModel.uiState.value.directories.size)
        }

    @Test
    fun `init fetches sessions filtered by path prefix`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sessionStore =
                FakeSessionStore().apply {
                    emitSessions(
                        "/Users/danker/projects",
                        listOf(session(id = "sess-1", workspaceCwd = "/Users/danker/projects/IMbot")),
                    )
                }

            val viewModel = createViewModel(sessionStore = sessionStore)
            advanceUntilIdle()

            assertEquals(listOf("/Users/danker/projects"), sessionStore.requestedPrefixes.distinct())
            assertEquals(1, viewModel.uiState.value.sessions.size)
        }

    @Test
    fun `navigateToSubdirectory updates path and re-fetches directories and sessions`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            val sessionStore = FakeSessionStore()
            val viewModel = createViewModel(repo = repo, sessionStore = sessionStore)
            advanceUntilIdle()

            viewModel.navigateToSubdirectory("/Users/danker/projects/IMbot")
            advanceUntilIdle()

            assertEquals("/Users/danker/projects/IMbot", viewModel.uiState.value.currentPath)
            assertEquals("/Users/danker/projects/IMbot", repo.browseRequests.last().second)
            assertEquals("/Users/danker/projects/IMbot", sessionStore.requestedPrefixes.last())
        }

    @Test
    fun `navigateUp loads parent directory`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            val viewModel = createViewModel(repo = repo)
            advanceUntilIdle()
            viewModel.navigateToSubdirectory("/Users/danker/projects/IMbot")
            advanceUntilIdle()

            viewModel.navigateUp()
            advanceUntilIdle()

            assertEquals("/Users/danker/projects", viewModel.uiState.value.currentPath)
            assertEquals("/Users/danker/projects", repo.browseRequests.last().second)
        }

    @Test
    fun `navigateUp from root path is a no-op`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            val viewModel = createViewModel(repo = repo)
            advanceUntilIdle()

            viewModel.navigateUp()
            advanceUntilIdle()

            assertEquals(1, repo.browseRequests.size)
            assertEquals("/Users/danker/projects", viewModel.uiState.value.currentPath)
        }

    @Test
    fun `paths above root clamp back to root and breadcrumbs start at root`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = configuredRepository()
            val sessionStore = FakeSessionStore()
            val viewModel = createViewModel(repo = repo, sessionStore = sessionStore)
            advanceUntilIdle()

            viewModel.navigateToSubdirectory("/Users/danker/projects/IMbot")
            advanceUntilIdle()
            assertEquals(listOf("projects", "IMbot"), viewModel.uiState.value.breadcrumbs.map { it.label })

            viewModel.navigateToSubdirectory("/Users")
            advanceUntilIdle()

            assertEquals("/Users/danker/projects", viewModel.uiState.value.currentPath)
            assertEquals(listOf("projects"), viewModel.uiState.value.breadcrumbs.map { it.label })
            assertEquals("/Users/danker/projects", repo.browseRequests.last().second)
            assertEquals("/Users/danker/projects", sessionStore.requestedPrefixes.last())
        }

    @Test
    fun `empty directory shows empty list`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                configuredRepository().apply {
                    browseDirectoryHandler = { _, path -> Result.success(browseResult(path)) }
                }
            val viewModel = createViewModel(repo = repo)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.directories.isEmpty())
        }

    @Test
    fun `empty sessions shows empty list`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sessionStore = FakeSessionStore()
            val viewModel = createViewModel(sessionStore = sessionStore)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.sessions.isEmpty())
        }

    @Test
    fun `network failure sets error and retry succeeds`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo =
                configuredRepository().apply {
                    browseDirectoryHandler = { _, _ -> Result.failure(IllegalStateException("加载目录失败")) }
                }
            val viewModel = createViewModel(repo = repo)
            advanceUntilIdle()
            assertEquals("加载目录失败", viewModel.uiState.value.error)

            repo.browseDirectoryHandler =
                { _, path ->
                    Result.success(
                        browseResult(
                            path,
                            browseEntry(name = "IMbot", path = "$path/IMbot"),
                        ),
                    )
                }

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.directories.size)
        }

    private fun createViewModel(
        repo: FakeWorkspaceRepository = configuredRepository(),
        sessionStore: FakeSessionStore = FakeSessionStore(),
    ) = RootDetailViewModel(
        workspaceRepository = repo,
        sessionStore = sessionStore,
        savedStateHandle =
            SavedStateHandle(
                mapOf(
                    RootDetailViewModel.ROOT_ID_ARG to "root-1",
                    RootDetailViewModel.HOST_ID_ARG to "macbook-1",
                    RootDetailViewModel.PATH_ARG to "/Users/danker/projects",
                ),
            ),
    )

    private fun configuredRepository(): FakeWorkspaceRepository =
        FakeWorkspaceRepository().apply {
            getHostsWithRootsResult =
                Result.success(
                    listOf(
                        hostWithRoots(
                            workspaceHost(id = "macbook-1", type = "macbook"),
                            workspaceRoot(
                                id = "root-1",
                                provider = "claude",
                                path = "/Users/danker/projects",
                                label = "projects",
                            ),
                        ),
                    ),
                )
            browseDirectoryHandler =
                { _, path ->
                    Result.success(
                        browseResult(
                            path,
                            browseEntry(name = "IMbot", path = "$path/IMbot"),
                            browseEntry(name = "tools", path = "$path/tools"),
                        ),
                    )
                }
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
