# Capability: auto-scroll-behavior

Intelligent auto-scroll management for the message list. Automatically scrolls to new messages unless the user is reading previous content. Provides a FAB to resume scrolling.

## ADDED Requirements

### Requirement: Auto-Scroll on New Messages

When new messages are added to the list and the user is at (or near) the bottom, the list SHALL automatically scroll to show the new content.

#### Scenario: New message while at bottom -- auto scroll

WHEN the user is viewing the bottom of the message list (within 100dp of the end)
AND a new message or delta arrives
THEN the list automatically scrolls to show the new content

---

### Requirement: Pause Auto-Scroll on Manual Scroll Up

When the user scrolls up more than 100dp from the bottom, auto-scroll SHALL be paused. A floating action button with a down arrow and message counter SHALL appear.

#### Scenario: User scrolls up -- auto scroll paused

WHEN the user scrolls the message list upward more than 100dp from the bottom
THEN auto-scroll is paused
AND new messages arriving do NOT cause the list to scroll

#### Scenario: User scrolls up -- FAB appears

WHEN auto-scroll is paused
THEN a small FAB appears at the bottom-right of the message list
AND the FAB shows a down-arrow icon "↓"

---

### Requirement: New Message Counter on FAB

While auto-scroll is paused, the FAB SHALL display a counter showing how many new messages have arrived since the pause.

#### Scenario: New messages arrive while paused -- FAB counter increments

WHEN auto-scroll is paused
AND 3 new messages arrive
THEN the FAB shows "↓ 3"
AND each additional message increments the counter

---

### Requirement: Resume Auto-Scroll via FAB

Tapping the FAB SHALL smooth-scroll to the bottom of the list and resume auto-scroll. The FAB SHALL disappear after scrolling completes.

#### Scenario: Tap FAB -- smooth scroll to bottom + resume auto

WHEN the user taps the "↓ N" FAB
THEN the list smooth-scrolls to the bottom
AND auto-scroll is resumed
AND the FAB disappears
AND subsequent new messages auto-scroll normally

---

### Requirement: Resume Auto-Scroll via Manual Scroll to Bottom

If the user manually scrolls back to the bottom (within 100dp), auto-scroll SHALL resume automatically without needing to tap the FAB.

#### Scenario: User scrolls back to bottom manually -- resume auto (no FAB needed)

WHEN auto-scroll is paused
AND the user manually scrolls back to within 100dp of the bottom
THEN auto-scroll resumes automatically
AND the FAB disappears
