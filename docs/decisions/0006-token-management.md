# ADR 0006: Prototype token management

## Decision

The prototype stores tokens as a collection of immutable `TabletopToken` values. Each token has a stable numeric ID, name, world-space position, grid-relative footprint, and palette color.

Token edits replace the matching value in Compose's observable state list. The selected token is referenced by ID rather than by list position so deletion and reordering do not invalidate selection.

Token footprints are explicit presets measured in grid cells. Square presets render as circular tokens, while rectangular presets render as ovals. The initial presets are 0.5 × 0.5, 1 × 1, 1 × 2, 2 × 2, and 2 × 4 cells.

The palette is ordered by visible-spectrum hue: red, orange, yellow, green, cyan, blue, and purple. Size and color use dropdown menus so every available value is visible before selection.

## Prototype controls

- **Add token** creates a token at the current camera center and applies the active snap rule.
- A tap selects one token and opens its settings panel.
- Long-press and drag moves that token.
- The settings panel supports renaming, direct size and color selection, resetting position, and deletion.
- Grid style, snap state, and scale are grouped under a bottom-right **Grid** menu to reduce top-toolbar scrolling.

## Deferred work

Image-backed tokens, arbitrary property schemas, rotation, custom footprints, persistence, z-order controls, and undo/redo are intentionally deferred to later milestones.
