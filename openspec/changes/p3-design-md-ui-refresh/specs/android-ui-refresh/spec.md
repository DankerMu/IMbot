## ADDED Requirements

### Requirement: Curated warm-neutral design language

Android UI SHALL use a curated warm-neutral palette in both light and dark modes instead of a generic cool-gray mobile app palette.

#### Scenario: Light mode background hierarchy
- **WHEN** the app renders in light mode
- **THEN** the page background uses a warm paper tone
- **AND** cards use a slightly lighter ivory surface
- **AND** outlines use warm gray separators

#### Scenario: Dark mode background hierarchy
- **WHEN** the app renders in dark mode
- **THEN** the app uses warm charcoal backgrounds and surfaces
- **AND** text uses parchment-tinted light neutrals instead of pure white

### Requirement: Home screen uses structured editorial header

The Home screen SHALL present sessions with an editorial header and grouped list structure.

#### Scenario: Home header content
- **WHEN** the Home screen is visible
- **THEN** it shows an eyebrow/meta line, a main title, and session summary pills

#### Scenario: Session grouping
- **WHEN** there are running and non-running sessions
- **THEN** running sessions are displayed under a dedicated section label
- **AND** the remaining sessions are displayed under a separate recent section label

### Requirement: Session cards emphasize prompt and workspace metadata

Session cards SHALL use stronger information hierarchy suitable for a developer tool.

#### Scenario: Session card content order
- **WHEN** a session card is rendered
- **THEN** the provider identity and status appear in the header
- **AND** the prompt summary appears as the main title
- **AND** the workspace path appears as lower-emphasis metadata

#### Scenario: Empty session title
- **WHEN** a session has no initial prompt
- **THEN** the main title renders an explicit empty-session label instead of a blank string

### Requirement: New Session uses staged flow container

The New Session flow SHALL visually read as a guided launch sequence instead of a plain form pager.

#### Scenario: Stage chrome
- **WHEN** the New Session screen is visible
- **THEN** the active step content is presented inside a shared stage container
- **AND** the step indicator communicates progress with stronger active/inactive differentiation

#### Scenario: Bottom action hierarchy
- **WHEN** the user advances through the flow
- **THEN** the bottom actions are rendered inside a dedicated action container
- **AND** primary and secondary actions are visually distinct

### Requirement: Detail top bar prioritizes workspace context

The Session Detail top shell SHALL present provider, workspace identity, status, and usage as a compact developer-oriented header.

#### Scenario: Detail header structure
- **WHEN** the detail screen top bar renders
- **THEN** provider identity is visible in the first header row
- **AND** the session status remains visible in the top-right badge area
- **AND** the second header row renders compact path / usage metadata without a duplicated workspace-name title
- **AND** path / usage metadata may wrap to avoid truncation

#### Scenario: Keyboard-safe detail composer
- **WHEN** the soft keyboard opens while the detail screen input is focused
- **THEN** the top bar remains visible
- **AND** only the middle transcript area compresses
- **AND** the composer stays pinned above the keyboard
- **AND** the soft-input handling remains scoped to the detail experience instead of changing every text-entry screen in the activity
- **AND** transcript-hosted interactive answer inputs remain scrollable into view while focused

### Requirement: Workspace and Settings adopt the same editorial shell

Workspace and Settings SHALL use the same editorial header system as Home instead of generic Material top app bars.

#### Scenario: Workspace summary shell
- **WHEN** the Workspace screen is visible
- **THEN** it shows an eyebrow, a main title, and summary pills for online hosts / total hosts / total roots

#### Scenario: Settings summary shell
- **WHEN** the Settings screen is visible
- **THEN** it shows an eyebrow, a main title, and summary pills for relay status / theme / version

### Requirement: Bottom navigation uses contained surface chrome

Top-level bottom navigation SHALL render as a contained surface with explicit active-state styling rather than default Material navigation chrome.

#### Scenario: Selected destination
- **WHEN** a bottom navigation item is selected
- **THEN** its icon and label use the primary accent color
- **AND** its indicator uses a tinted active container
