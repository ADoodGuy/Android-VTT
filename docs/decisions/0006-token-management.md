# ADR 0006: Prototype token management

## Decision

The prototype stores tokens as a collection of immutable `TabletopToken` values. Each token has a stable numeric ID, name, world-space position, grid-relative width and height, ARGB color, rotation angle, and orientation-marker axis.

Token edits replace the matching value in Compose's observable state list. The selected token is referenced by ID rather than by list position so deletion and reordering do not invalidate selection.

Token dimensions are measured in grid cells. Equal width and height render as circular tokens, while unequal dimensions render as ovals. Quick presets remain available for 0.5 × 0.5, 1 × 1, 1 × 2, 2 × 2, and 2 × 4 cells, and custom width and height values from 0.1 to 100 cells can be entered directly.

The quick color palette is ordered by visible-spectrum hue: red, orange, yellow, green, cyan, blue, and purple. Tokens can also use a custom `#RRGGBB` color.

Rotation is stored in degrees and normalized to the range from 0 through less than 360. Circular tokens show a radial orientation line. Oval tokens can show the orientation line toward either a major-axis endpoint or a minor-axis endpoint. Zero degrees points upward and positive rotation is clockwise on screen.

Grid scales retain the 1, 5, and 10 feet-per-cell presets and also accept a custom positive feet-per-cell value.

## Prototype controls

- **Add token** creates and selects a token at the current camera center without opening the settings card.
- A single tap selects a token and displays four scale handles plus one rotation handle.
- A double tap selects the token and opens its full settings card.
- Long-press and drag moves the token.
- The four scale handles sit at the token endpoints at 90-degree intervals. Dragging either handle on an axis changes that dimension symmetrically around the fixed token center.
- Direct scale manipulation snaps to 0.5-cell increments and clamps to 0.5–100 cells.
- The rotation handle remains 28 dp beyond the endpoint of the visible orientation indicator and rotates with that indicator.
- Direct rotation manipulation snaps to 15-degree increments.
- The settings card supports renaming, preset and custom size, preset and custom color, numeric rotation, oval marker-axis selection, resetting position, and deletion.
- Grid style, snap state, preset scale, and custom scale are grouped under a bottom-right **Grid** menu to reduce top-toolbar scrolling.

## Deferred work

Image-backed tokens, arbitrary property schemas, persistence, z-order controls, configurable manipulation snap increments, rotation/scale undo, and general undo/redo are intentionally deferred to later milestones.
