export type InteractiveProvider = "claude" | "book";

export type CreateSessionCommand = {
  cmd: "create_session";
  req_id: string;
  session_id: string;
  provider: InteractiveProvider;
  cwd: string;
  prompt: string;
  model?: string;
  permission_mode: string;
};

export type ResumeSessionCommand = {
  cmd: "resume_session";
  req_id: string;
  session_id: string;
  provider_session_id: string;
  cwd: string;
};

export type SendMessageCommand = {
  cmd: "send_message";
  req_id: string;
  session_id: string;
  text: string;
};

export type CancelSessionCommand = {
  cmd: "cancel_session";
  req_id: string;
  session_id: string;
};

export type CompleteSessionCommand = {
  cmd: "complete_session";
  req_id: string;
  session_id: string;
};

export type AnswerInteractiveToolCommand = {
  cmd: "answer_interactive_tool";
  req_id: string;
  session_id: string;
  call_id: string;
  answer: string;
  question_index?: number;
};

export type ListSessionsCommand = {
  cmd: "list_sessions";
  req_id: string;
  cwd: string;
  provider: InteractiveProvider;
};

export type LocalSessionInfo = {
  provider_session_id: string;
  cwd: string;
  created_at: string;
  status: "completed" | "unknown";
};

export type BrowseDirectoryCommand = {
  cmd: "browse_directory";
  req_id: string;
  path: string;
  roots?: readonly string[];
};

export type AddRootCommand = {
  cmd: "add_root";
  req_id: string;
  provider: InteractiveProvider;
  path: string;
  label?: string;
};

export type RemoveRootCommand = {
  cmd: "remove_root";
  req_id: string;
  provider: InteractiveProvider;
  path: string;
};

export type CompanionCommand =
  | CreateSessionCommand
  | ResumeSessionCommand
  | SendMessageCommand
  | CancelSessionCommand
  | CompleteSessionCommand
  | AnswerInteractiveToolCommand
  | ListSessionsCommand
  | BrowseDirectoryCommand
  | AddRootCommand
  | RemoveRootCommand;
