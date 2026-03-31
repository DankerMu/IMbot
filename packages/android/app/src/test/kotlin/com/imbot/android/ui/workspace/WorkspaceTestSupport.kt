package com.imbot.android.ui.workspace

import com.imbot.android.FakeRelayHttpClient
import com.imbot.android.FakeSettingsRepository
import com.imbot.android.data.repository.HostWithRoots
import com.imbot.android.data.repository.WorkspaceRepository
import com.imbot.android.network.BrowseEntry
import com.imbot.android.network.BrowseResult
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayWorkspaceRoot

class FakeWorkspaceRepository : WorkspaceRepository(FakeRelayHttpClient(), FakeSettingsRepository()) {
    var getHostsWithRootsResult: Result<List<HostWithRoots>> = Result.success(emptyList())
    var browseDirectoryResult: Result<BrowseResult> =
        Result.success(BrowseResult(path = "/", directories = emptyList()))
    var addRootResult: Result<RelayWorkspaceRoot> =
        Result.failure(IllegalStateException("addRootResult not configured"))
    var removeRootResult: Result<Unit> = Result.success(Unit)

    var getHostsWithRootsCalls = 0
    var browseDirectoryCalls = 0
    var addRootCalls = 0
    var removeRootCalls = 0

    var getHostsWithRootsHandler: suspend () -> Result<List<HostWithRoots>> = { getHostsWithRootsResult }
    var browseDirectoryHandler: suspend (String, String) -> Result<BrowseResult> =
        { _, _ -> browseDirectoryResult }
    var addRootHandler: suspend (String, String, String, String?) -> Result<RelayWorkspaceRoot> =
        { _, _, _, _ -> addRootResult }
    var removeRootHandler: suspend (String, String) -> Result<Unit> = { _, _ -> removeRootResult }

    val browseRequests = mutableListOf<Pair<String, String>>()
    val addRootRequests = mutableListOf<AddRootArgs>()
    val removeRootRequests = mutableListOf<Pair<String, String>>()

    override suspend fun getHostsWithRoots(): List<HostWithRoots> {
        getHostsWithRootsCalls++
        return getHostsWithRootsHandler().getOrThrow()
    }

    override suspend fun browseDirectory(
        hostId: String,
        path: String,
    ): BrowseResult {
        browseDirectoryCalls++
        browseRequests += hostId to path
        return browseDirectoryHandler(hostId, path).getOrThrow()
    }

    override suspend fun addRoot(
        hostId: String,
        provider: String,
        path: String,
        label: String?,
    ): RelayWorkspaceRoot {
        addRootCalls++
        addRootRequests += AddRootArgs(hostId = hostId, provider = provider, path = path, label = label)
        return addRootHandler(hostId, provider, path, label).getOrThrow()
    }

    override suspend fun removeRoot(
        hostId: String,
        rootId: String,
    ) {
        removeRootCalls++
        removeRootRequests += hostId to rootId
        removeRootHandler(hostId, rootId).getOrThrow()
    }
}

data class AddRootArgs(
    val hostId: String,
    val provider: String,
    val path: String,
    val label: String?,
)

fun workspaceHost(
    id: String,
    type: String,
    status: String = "online",
) = RelayHost(
    id = id,
    name = id,
    type = type,
    status = status,
    providers =
        when (type) {
            "macbook", "companion" -> listOf("claude", "book")
            else -> listOf("openclaw")
        },
)

fun workspaceRoot(
    id: String,
    hostId: String = "macbook-1",
    provider: String,
    path: String,
    label: String? = null,
    createdAt: String = "2026-03-31T10:00:00Z",
) = RelayWorkspaceRoot(
    id = id,
    hostId = hostId,
    provider = provider,
    path = path,
    label = label,
    createdAt = createdAt,
)

fun hostWithRoots(
    host: RelayHost,
    vararg roots: RelayWorkspaceRoot,
) = HostWithRoots(host = host, roots = roots.toList())

fun browseResult(
    path: String,
    vararg entries: BrowseEntry,
) = BrowseResult(path = path, directories = entries.toList())

fun browseEntry(
    name: String,
    path: String,
) = BrowseEntry(name = name, path = path)
