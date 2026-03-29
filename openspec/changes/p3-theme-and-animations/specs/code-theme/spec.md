# Capability: code-theme

Syntax highlighting with GitHub-style color palettes for light and dark themes, themed code block backgrounds, and copy functionality.

## ADDED Requirements

### Requirement: Light Theme Syntax Highlighting Palette

Code blocks in light theme SHALL use the following GitHub-inspired palette: keyword `#D73A49`, string `#032F62`, comment `#6A737D`, number `#005CC5`, type/function `#6F42C1`, background `#F6F8FA`. Text SHALL be rendered in a monospace font (JetBrains Mono preferred, Fira Code fallback, system monospace last resort) at 14sp.

#### Scenario: Code block in light theme -- correct colors

WHEN a code block is displayed in light theme
THEN the background color is `#F6F8FA`
AND keywords are colored `#D73A49`
AND string literals are colored `#032F62`
AND comments are colored `#6A737D`
AND the text font is monospace at 14sp

### Requirement: Dark Theme Syntax Highlighting Palette

Code blocks in dark theme SHALL use: keyword `#FF7B72`, string `#A5D6FF`, comment `#8B949E`, number `#79C0FF`, type/function `#D2A8FF`, background `#161B22`.

#### Scenario: Code block in dark theme -- correct colors

WHEN a code block is displayed in dark theme
THEN the background color is `#161B22`
AND keywords are colored `#FF7B72`
AND string literals are colored `#A5D6FF`
AND comments are colored `#8B949E`

### Requirement: Language-Aware Syntax Highlighting

When a code block includes a language hint (e.g., ` ```kotlin `), the renderer SHALL apply language-specific tokenization and coloring. When no language hint is present, the block SHALL render as plain monospace text with the themed background.

#### Scenario: Language hint present -- syntax highlighted

WHEN a code block has a language hint like "kotlin" or "typescript"
THEN the code is tokenized according to that language's grammar
AND tokens are colored per the active theme palette

#### Scenario: No language hint -- plain monospace

WHEN a code block has no language hint
THEN the text is rendered in monospace font
AND the themed background color is applied
AND no syntax coloring is applied (all text uses default foreground color)

### Requirement: Code Block Copy Button

Each code block SHALL display a copy button. On mobile, the button SHALL appear as a small icon button (copy icon) at the top-right corner of the code block. Tapping the button SHALL copy the code content to the clipboard and show a Snackbar "已复制".

#### Scenario: Copy button tap -- copies code

WHEN the user taps the copy button on a code block
THEN the entire code block content is copied to the system clipboard
AND a Snackbar appears showing "已复制"
AND the Snackbar auto-dismisses after 2 seconds

#### Scenario: Copy button visibility

WHEN a code block is rendered
THEN a small copy icon button is visible at the top-right corner of the block
AND the button does not overlap with code text (positioned with padding)

### Requirement: Long Code Block Horizontal Scroll

Code blocks with lines exceeding the screen width SHALL be horizontally scrollable. The code block container SHALL NOT wrap text; instead, it SHALL enable horizontal scroll within the block while the page scrolls vertically.

#### Scenario: Long code block -- horizontal scroll

WHEN a code block contains lines wider than the screen
THEN the user can scroll horizontally within the code block
AND vertical page scrolling is not affected by the horizontal scroll gesture
AND the code block background extends to cover the full content width
