## MODIFIED Requirements

### Requirement: HomeScreen supports selection mode for batch delete

The Android `HomeScreen` SHALL support a session selection mode that is entered by long pressing a `SessionCard`. While selection mode is active, the top area SHALL switch from provider filter controls to bulk actions.

#### Scenario: Long press enters selection mode

- **WHEN** the user long-presses a session card on the home screen
- **THEN** the screen enters selection mode
- **AND** the pressed session is marked as selected
- **AND** the top area shows the selected count and bulk actions instead of the provider filter

#### Scenario: Tap toggles selection while in selection mode

- **GIVEN** the home screen is already in selection mode
- **WHEN** the user taps another session card
- **THEN** that card is toggled selected or unselected
- **AND** the selected count updates immediately

#### Scenario: Exit selection mode

- **GIVEN** the home screen is in selection mode
- **WHEN** the user taps "完成" or all selected items are cleared
- **THEN** selection mode exits
- **AND** the provider filter UI is shown again

### Requirement: Batch delete selected sessions

The Android `HomeScreen` SHALL allow deleting all selected sessions from selection mode using the existing single-session delete API.

#### Scenario: Delete multiple selected sessions

- **GIVEN** the user has selected 3 sessions in selection mode
- **WHEN** the user taps the bulk delete action and confirms
- **THEN** Android calls `DELETE /v1/sessions/:id` once for each selected session
- **AND** successfully deleted sessions are removed from the list
- **AND** selection mode exits if no selected sessions remain

#### Scenario: Partial delete failure

- **GIVEN** the user has selected multiple sessions
- **AND** at least one delete request fails
- **WHEN** batch delete finishes
- **THEN** Android shows an error message
- **AND** failed sessions remain selected for retry

#### Scenario: Select all visible sessions

- **GIVEN** the session list is filtered to a subset of sessions
- **WHEN** the user taps "全选" in selection mode
- **THEN** all currently visible sessions are selected
- **AND** hidden sessions outside the current filter are not selected
