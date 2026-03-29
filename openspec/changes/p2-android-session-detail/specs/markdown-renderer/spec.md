# Capability: markdown-renderer

Full GitHub Flavored Markdown (GFM) rendering with syntax highlighting, code block copy, and incremental delta rendering for streaming messages.

## ADDED Requirements

### Requirement: GFM Feature Support

The Markdown renderer SHALL support the full GitHub Flavored Markdown specification: headings (h1-h6), bold, italic, strikethrough, inline code, code blocks with language hints, links (clickable), unordered and ordered lists, nested lists, tables, and blockquotes.

#### Scenario: Bold/italic/strikethrough render correctly

WHEN the content contains `**bold**`, `*italic*`, and `~~strikethrough~~`
THEN bold text is rendered in bold weight
AND italic text is rendered in italic style
AND strikethrough text is rendered with a line-through decoration

#### Scenario: Headings render with correct sizes

WHEN the content contains `# H1`, `## H2`, through `###### H6`
THEN each heading renders with a progressively smaller font size
AND headings have appropriate vertical spacing

#### Scenario: Nested lists render

WHEN the content contains a nested list (e.g., `- Item\n  - Sub-item\n    - Sub-sub-item`)
THEN the list renders with proper indentation at each level

#### Scenario: Tables render

WHEN the content contains a GFM table with headers and rows
THEN the table renders with visible cell borders/separators
AND the header row is visually distinct from data rows

#### Scenario: Links are clickable

WHEN the content contains `[text](url)`
THEN the link text is styled as a link (underline, link color)
AND tapping the link opens the URL in the system browser

---

### Requirement: Syntax Highlighting for Code Blocks

Code blocks with a language hint (e.g., `` ```kotlin ``) SHALL be rendered with syntax highlighting appropriate for the specified language. Code blocks without a language hint SHALL render in plain monospace.

#### Scenario: Code block with language hint -- syntax highlighted

WHEN the content contains a code block with `` ```typescript ``
THEN the code is rendered with syntax highlighting (keywords, strings, comments in distinct colors)
AND the code uses a monospace font
AND the code block has a rounded background

#### Scenario: Code block without language -- plain monospace

WHEN the content contains a code block with `` ``` `` (no language)
THEN the code is rendered in monospace without syntax highlighting

#### Scenario: Inline code renders

WHEN the content contains `` `inline code` ``
THEN the text is rendered in monospace with a subtle background color

---

### Requirement: Copy Button on Code Blocks

Each fenced code block SHALL display a copy button (icon) in its top-right corner. Tapping the button copies the code block content to the clipboard.

#### Scenario: Tap copy -- code copied to clipboard

WHEN the user taps the copy button on a code block
THEN the code block content (without the language hint or fences) is copied to the system clipboard
AND a brief visual feedback is shown (e.g., icon changes to checkmark for 1 second)

---

### Requirement: Incremental Rendering for Streaming

When `assistant_delta` events arrive during streaming, the renderer SHALL append the new text to the existing rendered output without re-rendering the entire message from scratch.

#### Scenario: Incremental append doesn't flicker

WHEN 20 `assistant_delta` events arrive in rapid succession (every 50ms)
THEN each delta is appended to the visible content
AND no visible flicker or full re-render occurs
AND the frame rate remains at 60fps

---

### Requirement: Performance for Large Messages

Messages exceeding 10,000 characters SHALL render within frame budget (< 16ms per frame) during both initial render and scroll.

#### Scenario: 10K char message renders < 16ms per frame

WHEN an agent message contains 10,000 characters of mixed Markdown
THEN the initial render completes without blocking the main thread for more than 16ms
AND scrolling through the rendered content maintains 60fps
