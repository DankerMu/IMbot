# Capability: provider-picker-step

Step 1 of the new session flow. Displays provider selection cards with host status awareness.

## ADDED Requirements

### Requirement: Three Provider Cards

The provider picker SHALL display 3 large selectable cards: Claude Code, book, and OpenClaw. Each card shows the provider icon, provider name, and the associated host's online/offline status.

#### Scenario: All providers shown

WHEN step 1 is displayed
THEN 3 provider cards are visible: "Claude Code", "book", "OpenClaw"
AND each card shows the provider's icon and the host status indicator

#### Scenario: Tap to select

WHEN the user taps the "Claude Code" card
THEN the card is visually highlighted as selected (filled background, border)
AND the "下一步" button becomes enabled
AND `provider = CLAUDE` and `hostId = "macbook-1"` are set in the ViewModel state

---

### Requirement: Only One Provider Selectable

At most one provider card SHALL be selected at a time. Tapping a different card deselects the previous one.

#### Scenario: Only one provider selectable at a time

WHEN the user selects "Claude Code" and then taps "book"
THEN "book" becomes selected and "Claude Code" is deselected

---

### Requirement: Disabled Provider When Host Offline

If a provider's associated host is offline, the provider card SHALL be visually disabled and show a "离线" label. Tapping a disabled card SHALL have no effect.

#### Scenario: Offline host -- provider card disabled + "离线" label

WHEN the MacBook host status is `offline`
THEN the "Claude Code" and "book" cards are visually dimmed/grayed
AND each shows a "离线" label
AND tapping either card has no effect
AND the "OpenClaw" card remains selectable (if relay-local is online)

---

### Requirement: Auto-Select host_id from Provider

Selecting a provider SHALL automatically determine the `host_id` based on the provider-to-host mapping: Claude Code/book map to the MacBook host, OpenClaw maps to `relay-local`.

#### Scenario: Selection -- next button enabled

WHEN the user selects any available (online) provider
THEN the "下一步" button is enabled
WHEN no provider is selected
THEN the "下一步" button is disabled
