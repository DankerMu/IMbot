## MODIFIED Requirements

### Requirement: Long press enters multi-select mode

Long pressing a `SessionCard` SHALL enter selection mode instead of opening the legacy context menu.

#### Scenario: Long press on card

- **WHEN** the user long-presses a session card
- **THEN** the card becomes selected
- **AND** the home screen enters selection mode
- **AND** no popup context menu is shown

### Requirement: SessionCard shows selected visual state

`SessionCard` SHALL expose a visual selected state while the home screen is in selection mode.

#### Scenario: Selected card visual treatment

- **GIVEN** the home screen is in selection mode
- **AND** a session card is selected
- **THEN** the card shows a selected accent treatment
- **AND** the card shows an explicit selection indicator

#### Scenario: Unselected card in selection mode

- **GIVEN** the home screen is in selection mode
- **AND** a session card is not selected
- **THEN** the card remains visible in the list
- **AND** it still responds to tap by toggling selection

### Requirement: Swipe delete disabled in selection mode

Swipe-to-delete SHALL remain available in normal mode, but SHALL be disabled while the home screen is in selection mode.

#### Scenario: Swipe in normal mode

- **WHEN** the home screen is not in selection mode
- **THEN** swiping a session card left reveals the delete action as before

#### Scenario: Swipe in selection mode

- **GIVEN** the home screen is in selection mode
- **WHEN** the user attempts to swipe a session card
- **THEN** the swipe-to-delete gesture does not trigger
