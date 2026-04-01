## MODIFIED Requirements

### Requirement: GFM tables render as structured UI

#### Scenario: Tables render as cells instead of raw pipe text

- **WHEN** the content contains a GFM table with headers, alignments, and rows
- **THEN** Android parses the table into structured cells
- **AND** the rendered output shows a visible grid with a distinct header row
- **AND** the raw `|` separator source is not shown as plain paragraph text

### Requirement: Math renders offline on Android

The Markdown renderer SHALL render inline and display math without requiring network access.

#### Scenario: Inline math renders with bundled assets

- **WHEN** the content contains inline math delimited by `$...$`
- **THEN** Android renders the formula using bundled local assets
- **AND** the formula is styled inline with surrounding Markdown text

#### Scenario: Display math renders with bundled assets

- **WHEN** the content contains a display math block delimited by `$$...$$`
- **THEN** Android renders the formula as a centered block
- **AND** the renderer loads KaTeX assets from inside the APK rather than the network
