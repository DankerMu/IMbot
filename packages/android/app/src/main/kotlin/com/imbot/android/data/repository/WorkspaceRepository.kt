package com.imbot.android.data.repository

import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.relayValidationError
import com.imbot.android.network.BrowseResult
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWorkspaceRoot
import javax.inject.Inject
import javax.inject.Singleton

data class HostWithRoots(
    val host: RelayHost,
    val roots: List<RelayWorkspaceRoot>,
)

@Singleton
open class WorkspaceRepository
    @Inject
    constructor(
        private val relayHttpClient: RelayHttpClient,
        private val settingsRepository: SettingsRepository,
    ) {
        open suspend fun getHostsWithRoots(): List<HostWithRoots> {
            val settings = requireValidSettings()
            val hosts =
                relayHttpClient.getHosts(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                ).getOrThrow()

            return hosts.map { host ->
                val roots =
                    relayHttpClient.getHostRoots(
                        relayUrl = settings.relayUrl,
                        token = settings.token,
                        hostId = host.id,
                    ).getOrThrow()
                        .sortedBy(RelayWorkspaceRoot::createdAt)

                HostWithRoots(host = host, roots = roots)
            }
        }

        open suspend fun addRoot(
            hostId: String,
            provider: String,
            path: String,
            label: String?,
        ): RelayWorkspaceRoot {
            val settings = requireValidSettings()
            return relayHttpClient.addRoot(
                relayUrl = settings.relayUrl,
                token = settings.token,
                hostId = hostId,
                provider = provider,
                path = path,
                label = label,
            ).getOrThrow()
        }

        open suspend fun removeRoot(
            hostId: String,
            rootId: String,
        ) {
            val settings = requireValidSettings()
            relayHttpClient.removeRoot(
                relayUrl = settings.relayUrl,
                token = settings.token,
                hostId = hostId,
                rootId = rootId,
            ).getOrThrow()
        }

        open suspend fun browseDirectory(
            hostId: String,
            path: String,
        ): BrowseResult {
            val settings = requireValidSettings()
            return relayHttpClient.browseDirectory(
                relayUrl = settings.relayUrl,
                token = settings.token,
                hostId = hostId,
                path = path,
            ).getOrThrow()
        }

        private fun requireValidSettings(): RelaySettings {
            val settings = settingsRepository.load()
            val validationError = settings.relayValidationError()
            require(settings.isConfigured()) { "请先在设置页完成 Relay 配置" }
            require(validationError == null) { validationError.orEmpty() }
            return settings
        }
    }
