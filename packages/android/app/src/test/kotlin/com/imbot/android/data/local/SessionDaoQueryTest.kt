package com.imbot.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.DriverManager

class SessionDaoQueryTest {
    @Test
    fun `path prefix query excludes sibling directories`() {
        val ids =
            queryIdsByPrefix(
                prefix = "/Users/danker/projects",
                rows =
                    listOf(
                        sessionRow("sess-root", "/Users/danker/projects", "2026-03-31T10:00:00Z"),
                        sessionRow("sess-child", "/Users/danker/projects/IMbot", "2026-03-31T11:00:00Z"),
                        sessionRow("sess-sibling", "/Users/danker/projects-old", "2026-03-31T12:00:00Z"),
                    ),
            )

        assertEquals(listOf("sess-child", "sess-root"), ids)
    }

    @Test
    fun `path prefix query escapes sql wildcard characters`() {
        val ids =
            queryIdsByPrefix(
                prefix = "/Users/danker/proj_ects%20",
                rows =
                    listOf(
                        sessionRow("sess-root", "/Users/danker/proj_ects%20", "2026-03-31T10:00:00Z"),
                        sessionRow("sess-child", "/Users/danker/proj_ects%20/IMbot", "2026-03-31T11:00:00Z"),
                        sessionRow("sess-wildcard", "/Users/danker/projXectsA20/IMbot", "2026-03-31T12:00:00Z"),
                    ),
            )

        assertEquals(listOf("sess-child", "sess-root"), ids)
    }

    private fun queryIdsByPrefix(
        prefix: String,
        rows: List<SessionRow>,
    ): List<String> =
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE sessions (
                        id TEXT PRIMARY KEY NOT NULL,
                        provider TEXT NOT NULL,
                        host_id TEXT NOT NULL,
                        workspace_cwd TEXT NOT NULL,
                        initial_prompt TEXT,
                        model TEXT,
                        status TEXT NOT NULL,
                        error_message TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        last_active_at TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }

            val insertSql =
                """
                INSERT INTO sessions (
                    id,
                    provider,
                    host_id,
                    workspace_cwd,
                    initial_prompt,
                    model,
                    status,
                    error_message,
                    created_at,
                    updated_at,
                    last_active_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            connection.prepareStatement(insertSql).use { statement ->
                rows.forEach { row ->
                    statement.setString(1, row.id)
                    statement.setString(2, "claude")
                    statement.setString(3, "macbook-1")
                    statement.setString(4, row.workspaceCwd)
                    statement.setString(5, "prompt")
                    statement.setString(6, "sonnet")
                    statement.setString(7, "running")
                    statement.setString(8, null)
                    statement.setString(9, row.lastActiveAt)
                    statement.setString(10, row.lastActiveAt)
                    statement.setString(11, row.lastActiveAt)
                    statement.executeUpdate()
                }
            }

            connection.prepareStatement(PATH_PREFIX_QUERY).use { statement ->
                statement.setString(1, prefix)
                statement.setString(2, prefix.escapeSqlLikePattern())
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.getString("id"))
                        }
                    }
                }
            }
        }

    private fun sessionRow(
        id: String,
        workspaceCwd: String,
        lastActiveAt: String,
    ) = SessionRow(
        id = id,
        workspaceCwd = workspaceCwd,
        lastActiveAt = lastActiveAt,
    )
}

private data class SessionRow(
    val id: String,
    val workspaceCwd: String,
    val lastActiveAt: String,
)

private const val PATH_PREFIX_QUERY =
    """
    SELECT id
    FROM sessions
    WHERE workspace_cwd = ? OR workspace_cwd LIKE ? || '/%' ESCAPE '\'
    ORDER BY last_active_at DESC, created_at DESC
    """
