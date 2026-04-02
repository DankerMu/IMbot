@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DetailUtilsTest {
    @Test
    fun `filterSkills matches command and label case insensitively`() {
        assertEquals(
            listOf("commit", "compact"),
            filterSkills("com").map(SkillItem::command),
        )
        assertEquals(
            listOf("commit", "compact"),
            filterSkills("COM").map(SkillItem::command),
        )
    }

    @Test
    fun `filterSkills handles blank missing and special character queries`() {
        assertEquals(DEFAULT_SKILLS, filterSkills(""))
        assertTrue(filterSkills("zzz").isEmpty())
        assertTrue(filterSkills("co+").isEmpty())
    }

    @Test
    fun `assembleSlashCommand preserves non blank args and omits blank suffix`() {
        assertEquals("/commit fix typo", assembleSlashCommand("commit", "fix typo"))
        assertEquals("/help", assembleSlashCommand("help", ""))
        assertEquals("/test   spaced  ", assembleSlashCommand("test", "  spaced  "))
    }

    @Test
    fun `isInteractiveToolCall only matches AskUserQuestion`() {
        assertTrue(isInteractiveToolCall("AskUserQuestion"))
        assertTrue(isInteractiveToolCall("askuserquestion"))
        assertFalse(isInteractiveToolCall("Read"))
        assertFalse(isInteractiveToolCall("Bash"))
        assertFalse(isInteractiveToolCall(""))
        assertFalse(isInteractiveToolCall(null))
    }

    @Test
    fun `parseAskUserQuestion handles json variants and invalid payloads`() {
        assertEquals(
            "选哪个?",
            parseAskUserQuestion("""{"question":"选哪个?","options":["A","B"]}""").first,
        )
        assertEquals(
            listOf("A", "B"),
            parseAskUserQuestion("""{"question":"选哪个?","options":["A","B"]}""").second,
        )
        assertEquals(
            "你确定吗?",
            parseAskUserQuestion("""{"question":"你确定吗?"}""").first,
        )
        assertNull(parseAskUserQuestion("""{"question":"你确定吗?"}""").second)
        assertEquals(
            """{"text":"hello"}""",
            parseAskUserQuestion("""{"text":"hello"}""").first,
        )
        assertNull(parseAskUserQuestion("""{"text":"hello"}""").second)
        assertEquals(
            DEFAULT_ASK_USER_QUESTION_MESSAGE,
            parseAskUserQuestion("{}").first,
        )
        assertEquals(
            "{broken",
            parseAskUserQuestion("{broken").first,
        )
        assertNull(parseAskUserQuestion("{broken").second)
        assertEquals(
            "Q",
            parseAskUserQuestion("""{"question":"Q","options":[]}""").first,
        )
        assertNull(parseAskUserQuestion("""{"question":"Q","options":[]}""").second)
    }

    @Test
    fun `parseAskUserQuestionV2 handles simplified json variants and invalid payloads`() {
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = "选哪个?",
                    header = null,
                    options = listOf(ParsedOption("A", null), ParsedOption("B", null)),
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2("""{"question":"选哪个?","options":["A","B"]}"""),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = "你确定吗?",
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2("""{"question":"你确定吗?"}"""),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = DEFAULT_ASK_USER_QUESTION_MESSAGE,
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2("""{"text":"hello"}"""),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = DEFAULT_ASK_USER_QUESTION_MESSAGE,
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2("{}"),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = "{broken",
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2("{broken"),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = DEFAULT_ASK_USER_QUESTION_MESSAGE,
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2(null),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = DEFAULT_ASK_USER_QUESTION_MESSAGE,
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2(""),
        )
    }

    @Test
    fun `parseAskUserQuestion caps large option sets and appends truncation note`() {
        val payload =
            """{"question":"选哪个?","options":["1","2","3","4","5","6","7","8","9","10","11","12"]}"""
        val result = parseAskUserQuestion(payload)

        assertEquals(
            "选哪个?\n\n$ASK_USER_QUESTION_OPTIONS_TRUNCATED_NOTE",
            result.first,
        )
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
            result.second,
        )
    }

    @Test
    fun `parseAskUserQuestionV2 parses standard format and multiple questions`() {
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = "Q?",
                    header = "Approach",
                    options = listOf(ParsedOption("A", "desc")),
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2(
                """
                {"questions":[{"question":"Q?","header":"Approach","options":[{"label":"A","description":"desc"}],"multiSelect":false}]}
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = "Q1?",
                    header = null,
                    options = null,
                    multiSelect = false,
                ),
                ParsedQuestion(
                    question = "Q2?",
                    header = "Mode",
                    options = listOf(ParsedOption("B", null)),
                    multiSelect = false,
                ),
            ),
            parseAskUserQuestionV2(
                """{"questions":[{"question":"Q1?"},{"question":"Q2?","header":"Mode","options":["B"]}]}""",
            ),
        )
    }

    @Test
    fun `parseAskUserQuestionV2 supports multi select mixed options and caps large option sets`() {
        assertEquals(
            listOf(
                ParsedQuestion(
                    question = "Q?",
                    header = null,
                    options = listOf(ParsedOption("A", "desc"), ParsedOption("B", null)),
                    multiSelect = true,
                ),
            ),
            parseAskUserQuestionV2(
                """{"questions":[{"question":"Q?","options":[{"label":"A","description":"desc"},"B"],"multiSelect":true}]}""",
            ),
        )

        val payload =
            """{"questions":[{"question":"选哪个?","options":["1","2","3","4","5","6","7","8","9","10","11","12"]}]}"""
        val result = parseAskUserQuestionV2(payload)

        assertEquals(1, result.size)
        assertEquals(
            listOf(
                ParsedOption("1", null),
                ParsedOption("2", null),
                ParsedOption("3", null),
                ParsedOption("4", null),
                ParsedOption("5", null),
                ParsedOption("6", null),
                ParsedOption("7", null),
                ParsedOption("8", null),
                ParsedOption("9", null),
                ParsedOption("10", null),
            ),
            result.single().options,
        )
    }

    @Test
    fun `latest pending helpers only mark the newest unanswered cards actionable`() {
        val olderInteractive =
            MessageItem.InteractiveToolCall(
                id = "interactive-1",
                toolName = "AskUserQuestion",
                questions =
                    listOf(
                        ParsedQuestion(
                            question = "旧问题",
                            header = null,
                            options = listOf(ParsedOption("A", null)),
                            multiSelect = false,
                        ),
                    ),
                timestamp = DETAIL_UTILS_TIMESTAMP,
            )
        val latestInteractive =
            MessageItem.InteractiveToolCall(
                id = "interactive-2",
                toolName = "AskUserQuestion",
                questions =
                    listOf(
                        ParsedQuestion(
                            question = "新问题",
                            header = null,
                            options = listOf(ParsedOption("B", null)),
                            multiSelect = false,
                        ),
                    ),
                timestamp = DETAIL_UTILS_TIMESTAMP,
            )
        val olderApproval =
            MessageItem.StatusChange(
                id = "status-older",
                status = "running",
                message = "Approval required: old",
                eventType = "approval_required",
                callId = "approval-1",
            )
        val latestApproval =
            MessageItem.StatusChange(
                id = "status-latest",
                status = "running",
                message = "Approval required: new",
                eventType = "approval_required",
                callId = "approval-2",
            )
        val messages =
            listOf(
                olderInteractive,
                latestInteractive,
                olderApproval,
                latestApproval,
            )

        val latestInteractiveId = findLatestPendingInteractiveToolCallId(messages)
        val latestApprovalId = findLatestPendingApprovalCallId(messages)

        assertEquals("interactive-2", latestInteractiveId)
        assertEquals("approval-2", latestApprovalId)
        assertFalse(isLatestPendingInteractiveToolCall(olderInteractive, latestInteractiveId))
        assertTrue(isLatestPendingInteractiveToolCall(latestInteractive, latestInteractiveId))
        assertFalse(isLatestPendingApprovalRequest(olderApproval, latestApprovalId))
        assertTrue(isLatestPendingApprovalRequest(latestApproval, latestApprovalId))
    }

    @Test
    fun `initial scroll state matches spec`() {
        val state = DetailScrollState()

        assertTrue(state.autoScrollEnabled)
        assertEquals(0, state.newMsgCount)
        assertFalse(state.fabVisible)
    }

    @Test
    fun `new message while auto scroll enabled requests scroll`() {
        val mutation = onTimelineChanged(DetailScrollState(), itemCountChanged = true)

        assertTrue(mutation.shouldScrollToBottom)
        assertEquals(DetailScrollState(), mutation.state)
    }

    @Test
    fun `scrolling away from bottom pauses auto scroll`() {
        val state =
            onScrollDistanceChanged(
                current = DetailScrollState(),
                distanceFromBottomDp = 160f,
            )

        assertFalse(state.autoScrollEnabled)
        assertTrue(state.fabVisible)
    }

    @Test
    fun `new message while paused increments unread counter`() {
        val mutation =
            onTimelineChanged(
                current = DetailScrollState(autoScrollEnabled = false, fabVisible = true),
                itemCountChanged = true,
            )

        assertFalse(mutation.shouldScrollToBottom)
        assertEquals(1, mutation.state.newMsgCount)
        assertTrue(mutation.state.fabVisible)
    }

    @Test
    fun `fab tap resumes auto scroll and resets counter`() {
        val mutation =
            resumeAutoScroll(
                DetailScrollState(autoScrollEnabled = false, newMsgCount = 3, fabVisible = true),
            )

        assertTrue(mutation.shouldScrollToBottom)
        assertTrue(mutation.state.autoScrollEnabled)
        assertEquals(0, mutation.state.newMsgCount)
        assertFalse(mutation.state.fabVisible)
    }

    @Test
    fun `scrolling back to bottom resumes auto scroll`() {
        val state =
            onScrollDistanceChanged(
                current = DetailScrollState(autoScrollEnabled = false, newMsgCount = 2, fabVisible = true),
                distanceFromBottomDp = 40f,
            )

        assertTrue(state.autoScrollEnabled)
        assertEquals(0, state.newMsgCount)
        assertFalse(state.fabVisible)
    }

    @Test
    fun `status color mapping matches requirement`() {
        assertEquals(Color(0xFF10B981), detailStatusColor("running"))
        assertEquals(Color(0xFF2196F3), detailStatusColor("idle"))
        assertEquals(Color(0xFF059669), detailStatusColor("completed"))
        assertEquals(Color(0xFFEF4444), detailStatusColor("failed"))
        assertEquals(Color(0xFF6B7280), detailStatusColor("cancelled"))
        assertEquals(Color(0xFF9CA3AF), detailStatusColor("queued"))
    }

    @Test
    fun `input placeholder text follows session status`() {
        assertEquals("AI 正在回复...", inputPlaceholderForStatus("running"))
        assertEquals("继续对话...", inputPlaceholderForStatus("idle"))
        assertEquals("会话已结束，可恢复后继续", inputPlaceholderForStatus("completed"))
        assertEquals("会话已失败，可恢复后继续", inputPlaceholderForStatus("failed"))
        assertEquals("会话已取消，可恢复后继续", inputPlaceholderForStatus("cancelled"))
    }

    @Test
    fun `idle status supports follow up input while running only supports live actions`() {
        assertTrue(canSendToSession("running"))
        assertTrue(canSendToSession("idle"))
        assertFalse(canSendToSession("completed"))

        assertFalse(canInputToSession("running"))
        assertTrue(canInputToSession("idle"))
        assertFalse(canInputToSession("completed"))

        assertTrue(canResumeSession("completed"))
        assertTrue(canResumeSession("failed"))
        assertTrue(canResumeSession("cancelled"))
        assertFalse(canResumeSession("idle"))

        assertTrue(canCancelSession("running"))
        assertFalse(canCancelSession("idle"))

        assertTrue(canCompleteSession("idle"))
        assertFalse(canCompleteSession("running"))
    }

    @Test
    fun `provider and status labels remain unchanged`() {
        assertEquals("Claude Code", providerDisplayName("claude"))
        assertEquals("book", providerDisplayName("book"))
        assertEquals("CC", providerShortLabel("claude"))
        assertEquals("BK", providerShortLabel("book"))
        assertEquals("运行中", statusLabel("running"))
        assertEquals("已完成", statusLabel("completed"))
    }

    @Test
    fun `status labels include idle`() {
        assertEquals("空闲", statusLabel("idle"))
    }

    @Test
    fun `event type maps to message item kind`() {
        assertEquals(MessageItemKind.User, messageItemKindForEventType("user_message"))
        assertEquals(MessageItemKind.Agent, messageItemKindForEventType("assistant_delta"))
        assertEquals(MessageItemKind.Agent, messageItemKindForEventType("assistant_message"))
        assertEquals(MessageItemKind.ToolCall, messageItemKindForEventType("tool_call_started"))
        assertEquals(MessageItemKind.ToolCall, messageItemKindForEventType("tool_call_completed"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_status_changed"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_started"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_idle"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_result"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_error"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("approval_required"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("approval_resolved"))
    }

    @Test
    fun `approval status message stays generic and readable`() {
        assertEquals(
            "Approval required: Run a shell command",
            approvalStatusMessage("approval_required", "Run a shell command", "bash"),
        )
        assertEquals(
            "Approval resolved: bash",
            approvalStatusMessage("approval_resolved", null, "bash"),
        )
        assertEquals(
            "Approval required",
            approvalStatusMessage("approval_required", null, null),
        )
    }

    @Test
    fun `relative timestamp formatting supports now minutes hours and date`() {
        val now = Instant.parse("2026-03-31T12:00:00Z")

        assertEquals("刚刚", formatRelativeTimestamp("2026-03-31T11:59:45Z", now))
        assertEquals("15 分钟前", formatRelativeTimestamp("2026-03-31T11:45:00Z", now))
        assertEquals("2 小时前", formatRelativeTimestamp("2026-03-31T10:00:00Z", now))
        assertEquals("2026-03-29", formatRelativeTimestamp("2026-03-29T12:00:00Z", now))
    }

    @Test
    fun `copyableText returns agent message content`() {
        assertEquals("hello", copyableText(agentMessage(content = "hello")))
    }

    @Test
    fun `copyableText returns user message text`() {
        assertEquals("world", copyableText(userMessage(text = "world")))
    }

    @Test
    fun `copyableText returns tool call summary`() {
        assertEquals(
            "Tool: Read\nInput: file.kt\nOutput: content",
            copyableText(toolCall(args = "file.kt", result = "content")),
        )
    }

    @Test
    fun `copyableText returns interactive tool question`() {
        assertEquals("需要继续吗？", copyableText(interactiveToolCall()))
    }

    @Test
    fun `copyableText returns status change description`() {
        assertEquals(
            "session started",
            copyableText(statusChange(message = "运行中", description = "session started")),
        )
    }

    @Test
    fun `copyableText returns tool call summary without tool name`() {
        assertEquals(
            "Output: content",
            copyableText(toolCall(toolName = "", args = null, result = "content")),
        )
    }

    @Test
    fun `copyableText returns null for blank agent messages`() {
        assertNull(copyableText(agentMessage(content = "")))
        assertNull(copyableText(agentMessage(content = "   ")))
    }

    @Test
    fun `copyableText returns null for blank user message`() {
        assertNull(copyableText(userMessage(text = "")))
    }

    @Test
    fun `copyableText omits missing tool output`() {
        assertEquals(
            "Tool: Read\nInput: file.kt",
            copyableText(toolCall(args = "file.kt", result = null)),
        )
    }

    @Test
    fun `copyableText omits missing tool input and output`() {
        assertEquals(
            "Tool: Read",
            copyableText(toolCall(args = null, result = null)),
        )
    }

    @Test
    fun `copyableText truncates long tool call input and output`() {
        val longArgs = "a".repeat(500)
        val longResult = "b".repeat(500)

        assertEquals(
            "Tool: Read\nInput: ${"a".repeat(200)}...\nOutput: ${"b".repeat(200)}...",
            copyableText(toolCall(args = longArgs, result = longResult)),
        )
    }

    @Test
    fun `copyableText preserves raw markdown newlines and emoji`() {
        assertEquals(
            "**bold** `code`",
            copyableText(agentMessage(content = "**bold** `code`")),
        )
        assertEquals(
            "line1\nline2",
            copyableText(agentMessage(content = "line1\nline2")),
        )
        assertEquals(
            "hello 🎉",
            copyableText(agentMessage(content = "hello 🎉")),
        )
    }

    @Test
    fun `availableActions returns copy and select for normal agent message`() {
        assertEquals(
            listOf(
                MessageAction.CopyMessage("hello"),
                MessageAction.SelectText,
            ),
            availableActions(agentMessage(content = "hello")),
        )
    }

    @Test
    fun `availableActions returns copy and select for user message`() {
        assertEquals(
            listOf(
                MessageAction.CopyMessage("world"),
                MessageAction.SelectText,
            ),
            availableActions(userMessage(text = "world")),
        )
    }

    @Test
    fun `availableActions returns copy only for tool call`() {
        assertEquals(
            listOf(MessageAction.CopyMessage("Tool: Read\nInput: file.kt\nOutput: content")),
            availableActions(toolCall(args = "file.kt", result = "content")),
        )
    }

    @Test
    fun `availableActions returns copy only for interactive tool call`() {
        assertEquals(
            listOf(MessageAction.CopyMessage("需要继续吗？")),
            availableActions(interactiveToolCall()),
        )
    }

    @Test
    fun `availableActions returns copy only for status change`() {
        assertEquals(
            listOf(MessageAction.CopyMessage("运行中")),
            availableActions(statusChange()),
        )
    }

    @Test
    fun `availableActions returns select only for blank agent content`() {
        assertEquals(
            listOf(MessageAction.SelectText),
            availableActions(agentMessage(content = "")),
        )
    }

    @Test
    fun `availableActions returns no actions for streaming agent message`() {
        assertEquals(
            emptyList<MessageAction>(),
            availableActions(agentMessage(content = "streaming", isStreaming = true)),
        )
    }

    @Test
    fun `copyableText returns full content for very long agent message`() {
        val longContent = "a".repeat(10_000)
        assertEquals(longContent, copyableText(agentMessage(content = longContent)))
    }

    @Test
    fun `hasActions uses lightweight eligibility rules`() {
        assertTrue(hasActions(agentMessage(content = "")))
        assertFalse(hasActions(agentMessage(content = "streaming", isStreaming = true)))
        assertTrue(hasActions(interactiveToolCall()))
        assertTrue(hasActions(userMessage(text = "")))
        assertTrue(hasActions(toolCall()))
        assertTrue(hasActions(toolCall(toolName = "")))
        assertTrue(hasActions(statusChange(message = "运行中")))
        assertFalse(hasActions(statusChange(message = null, description = null)))
    }
}

private const val DETAIL_UTILS_TIMESTAMP = "2026-03-31T12:00:00Z"

private fun agentMessage(
    content: String,
    isStreaming: Boolean = false,
) = MessageItem.AgentMessage(
    id = "agent-1",
    content = content,
    isStreaming = isStreaming,
    timestamp = DETAIL_UTILS_TIMESTAMP,
)

private fun userMessage(text: String) =
    MessageItem.UserMessage(
        id = "user-1",
        text = text,
        timestamp = DETAIL_UTILS_TIMESTAMP,
    )

private fun toolCall(
    toolName: String = "Read",
    args: String? = "file.kt",
    result: String? = "content",
) = MessageItem.ToolCall(
    callId = "call-1",
    toolName = toolName,
    title = "Read file",
    args = args,
    result = result,
    isRunning = false,
)

private fun interactiveToolCall(question: String = "需要继续吗？") =
    MessageItem.InteractiveToolCall(
        id = "interactive-1",
        toolName = "AskUserQuestion",
        questions =
            listOf(
                ParsedQuestion(
                    question = question,
                    header = null,
                    options = listOf(ParsedOption("是", null), ParsedOption("否", null)),
                    multiSelect = false,
                ),
            ),
        timestamp = DETAIL_UTILS_TIMESTAMP,
    )

private fun statusChange(
    message: String? = "运行中",
    description: String? = null,
) = MessageItem.StatusChange(
    id = "status-1",
    status = "running",
    message = message,
    description = description,
)
