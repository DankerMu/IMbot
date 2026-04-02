package com.imbot.android.ui.detail

internal data class SkillItem(
    val command: String,
    val label: String,
    val description: String,
    val category: SkillCategory,
)

internal enum class SkillCategory {
    BUILT_IN,
    AGENT_SKILL,
    SLASH_COMMAND,
}

internal val DEFAULT_SKILLS =
    listOf(
        SkillItem("commit", "Commit", "创建 git commit", SkillCategory.BUILT_IN),
        SkillItem("review", "Review", "代码审查", SkillCategory.BUILT_IN),
        SkillItem("test", "Test", "运行测试", SkillCategory.BUILT_IN),
        SkillItem("help", "Help", "获取帮助", SkillCategory.BUILT_IN),
        SkillItem("compact", "Compact", "压缩上下文", SkillCategory.BUILT_IN),
        SkillItem("clear", "Clear", "清除对话", SkillCategory.BUILT_IN),
        SkillItem("init", "Init", "初始化项目", SkillCategory.BUILT_IN),
        SkillItem("bug", "Bug", "报告问题", SkillCategory.AGENT_SKILL),
        SkillItem("explain", "Explain", "解释代码", SkillCategory.AGENT_SKILL),
    )

internal fun filterSkills(
    query: String,
    skills: List<SkillItem> = DEFAULT_SKILLS,
): List<SkillItem> =
    if (query.isBlank()) {
        skills
    } else {
        skills.filter { skill ->
            skill.command.contains(query, ignoreCase = true) ||
                skill.label.contains(query, ignoreCase = true)
        }
    }

internal fun assembleSlashCommand(
    command: String,
    args: String,
): String {
    val trimmedArgs = args.trim()
    return if (trimmedArgs.isEmpty()) {
        "/$command"
    } else {
        "/$command $args"
    }
}
