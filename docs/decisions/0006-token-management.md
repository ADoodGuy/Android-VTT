# ADR 0006: Prototype token management

## Decision

The prototype stores tokens as a collection of immutable `TabletopToken` values. Each token has a stable numeric ID, name, world-space position, world-space diameter, and a palette color.

Token edits replace the matching value in Compose's observable state list. The selected token is referenced by ID rather than by list position so deletion and reordering do not invalidate selection.

## Prototype controls

- **Add token** creates a token at the current camera center and applies the active snap rule.
- A tap selects one token and opens its settings panel.
- Long-press and drag moves that token.
- The settings panel supports renaming, cycling size, cycling color, resetting position, and deletion.

## Deferred work

Image-backed tokens, arbitrary property schemas, rotation, non-circular footprints, persistence, z-order controls, and undo/redo are intentionally deferred to later milestones.
