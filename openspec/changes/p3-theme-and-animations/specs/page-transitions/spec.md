# Capability: page-transitions

Smooth navigation transitions between screens using shared elements, standard animations, and bottom sheet slides.

## ADDED Requirements

### Requirement: Shared Element Transition -- SessionCard to DetailScreen

Tapping a SessionCard SHALL trigger a shared element transition to SessionDetailScreen. The card's provider icon and title text SHALL morph into the DetailScreen's TopAppBar. The transition duration SHALL be 400ms with `Emphasized` easing. The reverse (back from detail) SHALL play the inverse animation.

#### Scenario: Tap session card -- shared element animation to detail

WHEN the user taps a SessionCard on the home screen
THEN the card's provider icon and title animate (scale, position, opacity) into the DetailScreen TopAppBar
AND the transition takes 400ms with Emphasized easing curve
AND the background content fades during the transition

#### Scenario: Back from detail -- reverse animation

WHEN the user presses back on SessionDetailScreen
THEN the TopAppBar provider icon and title animate back to the card's position in the list
AND the transition is 400ms with Emphasized easing (reverse)

### Requirement: Standard Forward/Back Navigation Animations

Standard page navigation (not shared element) SHALL use: enter animation of 300ms with `EmphasizedDecelerate` easing (slide-in from right + fade-in), exit animation of 250ms with `EmphasizedAccelerate` easing (slide-out to left + fade-out). Back navigation plays the reverse.

#### Scenario: Navigate forward to a new screen

WHEN the user navigates to a new screen (e.g., RootDetailScreen)
THEN the new screen slides in from the right with 300ms EmphasizedDecelerate
AND the current screen slides out to the left

#### Scenario: Navigate back

WHEN the user presses the back button
THEN the current screen slides out to the right with 250ms EmphasizedAccelerate
AND the previous screen slides in from the left

### Requirement: Bottom Nav Tab Cross-Fade

Switching between bottom navigation tabs SHALL use a cross-fade transition (no slide). The outgoing tab fades out while the incoming tab fades in simultaneously.

#### Scenario: Navigate between bottom nav tabs

WHEN the user taps a different bottom nav tab
THEN the current tab content fades out and the new tab content fades in simultaneously
AND there is no horizontal slide animation

### Requirement: Bottom Sheet Slide-Up Animation

Bottom sheets (e.g., AddRootBottomSheet) SHALL slide up from the bottom edge with the standard Material 3 bottom sheet animation. Dismissal SHALL slide down to the bottom edge.

#### Scenario: Open bottom sheet -- slide up

WHEN a bottom sheet is triggered (e.g., "+" button on WorkspaceScreen)
THEN the sheet slides up from the bottom of the screen
AND a scrim (semi-transparent overlay) fades in behind the sheet

#### Scenario: Close bottom sheet -- slide down

WHEN the user dismisses the bottom sheet (swipe down or tap scrim)
THEN the sheet slides down and out of view
AND the scrim fades out
