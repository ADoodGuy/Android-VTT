# ADR 0006: Prototype token management

## Decision

The prototype stores tokens as a collection of immutable `TabletopToken` values. Each token has a stable numeric ID, name, world-space position, grid-relative width and height, ARGB color, rotation angle, and orientation-marker axis.

Token edits replace the matching value in Compose's observable state list. The selected token is referenced by ID rather than by list position so deletion and reordering do not invalidate selection.

Token dimensions are measured in grid cells. Equal width and height render as circular tokens, while unequal dimensions render as ovals. Quick presets remain available for 0.5 × 0.5, 1 × 1, 1 × 2, 2 × 2, and 2 × 4 cells, and custom width and height values from 0.1 to 100 cells can be entered directly.

The quick color palette is ordered by visible-spectrum hue: red, orange, yellow, green, cyan, blue, and purple. Tokens can also use a custom `#RRGGBB` color.

Rotation is stored in degrees and normalized to the range from 0 through less than 360. Circular tokens show a radial orientation line. Oval tokens can show the orientation line toward either a major-axis endpoint or a minor-axis endpoint. Zero degrees points upward and positive rotation is clockwise on screen.

Grid scales retain the 1, 5, and 10 feet-per-cell presets and also accept a custom positive feet-per-cell value.

## Prototype controls

- **Add token** creates a token at the current camera center and applies the active snap rule.
- A tap selects one token and opens its settings panel.
- Long-press and drag moves that token.
- The settings panel supports renaming, preset and custom size, preset and custom color, rotation, oval marker-axis selection, resetting position, and deletion.
- Grid style, snap state, preset scale, and custom scale are grouped under a bottom-right **Grid** menu to reduce top-toolbar scrolling.

## Deferred work

Image-backed tokens, arbitrary property schemas, persistence, z-order controls, rotation gestures, and undo/redo are intentionally deferred to later milestones.
