# Capability: prompt-input-step

Step 3 of the new session flow. Displays a summary of selections, prompt input, model dropdown, and the submit button.

## ADDED Requirements

### Requirement: Summary of Selections

The top of step 3 SHALL display a summary showing the selected provider (with icon) and the selected directory path.

#### Scenario: Summary shows correct provider + path

WHEN the user has selected "Claude Code" and directory `/Users/danker/Desktop/AI-vault/IMbot`
THEN the summary shows: provider icon + "Claude Code" and path "AI-vault/IMbot" (last 2 segments)

---

### Requirement: Multi-Line Prompt Input (Required)

A multi-line text input SHALL allow the user to enter the initial prompt. The prompt is required -- the "开始" button SHALL be disabled when the prompt is empty.

#### Scenario: Empty prompt -- button disabled

WHEN the prompt input field is empty
THEN the "开始" button is disabled (grayed out)

#### Scenario: Type prompt -- button enabled

WHEN the user types "帮我分析这个项目的架构"
THEN the "开始" button becomes enabled

---

### Requirement: Optional Model Dropdown

A dropdown selector SHALL allow the user to optionally choose a model. The options SHALL be: sonnet (default), opus, haiku. The selected model is included in the `POST /v1/sessions` request.

#### Scenario: Select model -- saved

WHEN the user selects "opus" from the model dropdown
THEN `model = "opus"` is saved in the ViewModel state
AND the dropdown label updates to "opus"

---

### Requirement: Submit Session

Tapping the "开始" button SHALL call `POST /v1/sessions` with the provider, host_id, cwd, prompt, and model. On success, navigate to the session detail screen. On failure, show a Snackbar error.

#### Scenario: Tap "开始" -- loading -- success -- navigate

WHEN the user taps "开始"
THEN the button shows a loading indicator
AND `POST /v1/sessions` is called
AND on success (201), the app navigates to `SessionDetailScreen`

#### Scenario: Tap "开始" -- loading -- error -- Snackbar

WHEN the user taps "开始"
AND `POST /v1/sessions` returns an error
THEN a Snackbar is shown with the error message
AND the user remains on step 3
AND the "开始" button returns to its normal state
