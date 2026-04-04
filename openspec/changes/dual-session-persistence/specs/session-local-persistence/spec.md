# Spec: session-local-persistence

确保 companion 正确识别每个 provider 的 session 存储路径，session 在 CLI 原生存储中持久化，且本地可通过 `--resume` 恢复。扩展 SessionIndex 元数据和 CompanionProviderConfig 以支持 per-provider 路径。

## ADDED Requirements

### Requirement: CompanionProviderConfig includes config_dir for provider-specific paths

每个 provider 的配置 SHALL 支持可选的 `config_dir` 字段，指定该 provider 的 Claude Code 配置目录。Session JSONL 存储路径为 `${config_dir}/projects/`。

#### Scenario: Explicit config_dir in companion.json

- **WHEN** companion.json 中 `providers.book` 配置了 `"config_dir": "~/.claudebook"`
- **THEN** companion SHALL 将 book 的 session projects 目录解析为 `~/.claudebook/projects/`

#### Scenario: Default config_dir for claude provider

- **WHEN** companion.json 中 `providers.claude` 没有配置 `config_dir`
- **THEN** companion SHALL 默认使用 `~/.claude` 作为 claude 的 config_dir
- **AND** session projects 目录 SHALL 为 `~/.claude/projects/`

#### Scenario: Auto-detect config_dir from book binary wrapper

- **WHEN** companion.json 中 `providers.book` 没有配置 `config_dir`
- **AND** book binary 是一个 shell script，其中包含 `CLAUDE_CONFIG_DIR="$HOME/.claudebook"` 或 `export CLAUDE_CONFIG_DIR=...`
- **THEN** companion SHALL 从 binary 的前 10 行中提取 `CLAUDE_CONFIG_DIR` 的值作为 config_dir

#### Scenario: Auto-detect fallback to default

- **WHEN** companion.json 中 `providers.book` 没有配置 `config_dir`
- **AND** book binary 不是 shell script 或不含 `CLAUDE_CONFIG_DIR`
- **THEN** companion SHALL 回退到 `~/.claude` 作为默认 config_dir

#### Scenario: config_dir path expansion

- **WHEN** `config_dir` 值为 `"~/.claudebook"`
- **THEN** companion SHALL 将 `~` 展开为 `$HOME`（与现有 binary path 展开逻辑一致）

---

### Requirement: discoverSessions uses provider-specific projects directory

`discoverSessions()` 的调用方 SHALL 根据 provider 传入正确的 `claudeProjectsDir` 参数，而不是使用硬编码默认值。

#### Scenario: list_sessions command uses correct projects dir for claude

- **WHEN** relay 发送 `list_sessions` 命令，`provider=claude`
- **THEN** companion SHALL 调用 `discoverSessions(cwd, "claude", { claudeProjectsDir: "<claude_config_dir>/projects" })`

#### Scenario: list_sessions command uses correct projects dir for book

- **WHEN** relay 发送 `list_sessions` 命令，`provider=book`
- **THEN** companion SHALL 调用 `discoverSessions(cwd, "book", { claudeProjectsDir: "<book_config_dir>/projects" })`
- **AND** 对于默认配置，book 的 projects dir SHALL 为 `~/.claudebook/projects/`

#### Scenario: Book sessions discoverable after fix

- **WHEN** companion 配置了 book provider（config_dir 为 `~/.claudebook`），且 `~/.claudebook/projects/-Users-danker-Desktop-novel/` 下存在 JSONL 文件
- **THEN** `discoverSessions("/Users/danker/Desktop/novel", "book", { claudeProjectsDir: "~/.claudebook/projects" })` SHALL 返回这些 session

---

### Requirement: CLI session JSONL files persist after stream-json mode exit

Companion 通过 `--input-format stream-json --output-format stream-json` 启动的 CLI 进程退出后，对应的 `.jsonl` session 文件 SHALL 存在于该 provider 对应的 projects 目录中。

**注意**：当前 Claude Code CLI 已经在 stream-json 模式下持久化 session。此 requirement 是验证性的 — 确保这个行为在测试中被显式覆盖。

#### Scenario: Claude session JSONL persisted

- **WHEN** companion 通过 `createSession()` 启动一个 provider=claude 的 CLI 进程，进程正常退出（exit code 0）
- **THEN** `~/.claude/projects/<encoded-cwd>/<provider_session_id>.jsonl` 文件 SHALL 存在且非空

#### Scenario: Book session JSONL persisted in book config dir

- **WHEN** companion 通过 `createSession()` 启动一个 provider=book 的 CLI 进程（binary 设置 `CLAUDE_CONFIG_DIR=~/.claudebook`），进程正常退出
- **THEN** `~/.claudebook/projects/<encoded-cwd>/<provider_session_id>.jsonl` 文件 SHALL 存在且非空

#### Scenario: Resumed session preserves existing JSONL

- **WHEN** companion 通过 `resumeSession()` 使用 `-r <provider_session_id>` 恢复一个已有 session，进程正常退出
- **THEN** 原有的 JSONL 文件 SHALL 仍然存在，且文件大小不减小

---

### Requirement: SessionIndex entry records session source

每个 `SessionIndexEntry` SHALL 包含一个 `source` 字段，标识 session 的创建来源。

#### Scenario: Remote session source is recorded on creation

- **WHEN** companion 通过 relay 的 `create_session` 命令创建一个新 session
- **THEN** `SessionIndex.set()` 写入的 entry SHALL 包含 `source: "remote"`

#### Scenario: Local session source is recorded during reconciliation

- **WHEN** session reconciler 发现一个本地 JSONL 文件没有对应的 SessionIndex entry
- **THEN** 创建的 SessionIndex entry SHALL 包含 `source: "local"`

#### Scenario: Source field defaults to remote for existing entries without source

- **WHEN** SessionIndex 从 `~/.imbot/sessions.json` 加载一个不含 `source` 字段的旧 entry
- **THEN** 该 entry 的 `source` SHALL 被默认为 `"remote"`（向后兼容）

#### Scenario: Source field is preserved across persist/load cycles

- **WHEN** SessionIndex 写入一个 `source: "local"` 的 entry，然后重新从文件加载
- **THEN** 该 entry 的 `source` SHALL 仍然是 `"local"`

---

### Requirement: SessionIndex entry includes initial_prompt summary

每个 `SessionIndexEntry` MAY 包含一个 `initial_prompt` 字段（可选），存储 session 的初始 prompt 摘要（前 200 字符）。

#### Scenario: Remote session records initial_prompt from create command

- **WHEN** companion 处理 `create_session` 命令（包含 `prompt` 字段）
- **THEN** `SessionIndex.set()` 写入的 entry SHALL 包含 `initial_prompt` 字段，值为 prompt 的前 200 字符

#### Scenario: Local session initial_prompt is null

- **WHEN** session reconciler 从本地 JSONL 文件创建 SessionIndex entry
- **THEN** `initial_prompt` SHALL 为 `null`（不从 JSONL 文件中提取）

#### Scenario: Missing initial_prompt defaults to null on load

- **WHEN** SessionIndex 加载一个不含 `initial_prompt` 字段的旧 entry
- **THEN** `initial_prompt` SHALL 默认为 `null`

---

### Requirement: Relay sessions table includes local_available column

`sessions` 表 SHALL 包含一个 `local_available` 列（BOOLEAN，默认 `false`），标识该 session 是否在 companion 主机上有本地 CLI 持久化数据。

#### Scenario: Companion-created session is marked local_available

- **WHEN** relay 收到 companion 的 `session_started` 事件（companion 创建的 session）
- **THEN** 该 session 的 `local_available` SHALL 为 `true`（companion 创建意味着 CLI 在本地运行）

#### Scenario: Shadow session from reconciliation is marked local_available

- **WHEN** relay 收到 `report_local_sessions` 消息并创建 shadow record
- **THEN** 新创建的 shadow session 的 `local_available` SHALL 为 `true`

#### Scenario: OpenClaw session is not local_available

- **WHEN** relay 创建一个 `provider=openclaw` 的 session
- **THEN** `local_available` SHALL 为 `false`（OpenClaw 在 relay 本地运行，不在 companion 主机上）

#### Scenario: GET /sessions returns local_available field

- **WHEN** client 请求 `GET /sessions`
- **THEN** 每个 session 对象 SHALL 包含 `local_available` boolean 字段

#### Scenario: Database migration adds local_available column

- **WHEN** relay 启动并且 `sessions` 表缺少 `local_available` 列
- **THEN** migration SHALL 添加该列，默认值为 `false`
- **AND** 已存在的 companion-created session（`provider IN ('claude', 'book') AND provider_session_id IS NOT NULL`）的 `local_available` SHALL 被设为 `true`
