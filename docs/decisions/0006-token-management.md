# ADR 0006: Prototype token management

## Decision

The prototype stores tokens as a collection of immutable `TabletopToken` values. Each token has a stable numeric ID, name, world-space position, grid-relative width and height, ARGB color, rotation angle, orientation-marker axis, and independent movement/scaling/rotation lock flags.

Token edits replace the matching value in Compose's observable state list. The selected token is referenced by ID rather than by list position so deletion and reordering do not invalidate selection.

Token dimensions are measured in grid cells. Equal width and height render as circular tokens, while unequal dimensions render as ovals. Quick presets remain available for 0.5 × 0.5, 1 × 1, 1 × 2, 2 × 2, and 2 × 4 cells, and custom width and height values from 0.1 to 100 cells can be entered directly.

The quick color palette is ordered by visible-spectrum hue: red, orange, yellow, green, cyan, blue, and purple. Tokens can also use a custom `#RRGGBB` color.

Rotation is stored in degrees and normalized to the range from 0 through less than 360. Circular tokens show a radial orientation line. Oval tokens can show the orientation line toward either a major-axis endpoint or a minor-axis endpoint. Zero degrees points upward and positive rotation is clockwise on screen.

Grid scales retain the 1, 5, and 10 feet-per-cell presets and also accept a custom positive feet-per-cell value.

## Prototype controls

- **Add token** creates and selects a token at the current camera center without opening the settings card.
- A single tap selects an unselected token. A single tap on the selected token deselects it.
- A double tap deselects the token.
- A normal press-and-drag moves the token immediately; no long-press delay is required.
- A stationary long press selects the token and opens its full settings card.
- Selecting a token displays four scale handles plus one rotation handle unless the corresponding direct control is locked.
- The four scale handles sit at the token endpoints at 90-degree intervals. Dragging either handle on an axis changes that dimension symmetrically around the fixed token center.
- Direct scaling is continuous. When **Snap to grid** is enabled, scale values within 0.1 cell of a 0.5-cell preset magnetically snap to that preset. Outside the magnetic window, scaling remains free. Handle scaling is clamped to 0.5–100 cells.
- The rotation handle remains 28 dp beyond the endpoint of the visible orientation indicator and rotates with that indicator.
- Direct rotation is continuous. When **Snap to grid** is enabled, angles within 3 degrees of a 15-degree preset magnetically snap to that preset. Outside the magnetic window, rotation remains free.
- Turning **Snap to grid** off disables position snapping, scale magnetic snapping, and rotation magnetic snapping together.
- Movement, scaling, and rotation can be locked independently from the token settings card. A movement lock blocks drag movement. A scale lock removes all four scale handles and rejects handle scaling. A rotation lock removes the rotation handle/extension and rejects handle rotation.
- Locks govern direct tabletop manipulation. Numeric/preset settings remain available as an intentional administrative override.
- While a manipulation handle is being dragged, a screen-aligned label appears beneath the token. It displays the current width and height for scaling or the current angle for rotation, then disappears when the drag ends or is cancelled.
- The settings card supports renaming, direct-control locks, preset and custom size, preset and custom color, numeric rotation, oval marker-axis selection, resetting position, and deletion.
- Grid style, snap state, preset scale, and custom scale are grouped under a bottom-right **Grid** menu to reduce top-toolbar scrolling.

## Deferred work

Image-backed tokens, arbitrary property schemas, persistence, z-order controls, configurable manipulation snap increments and magnetic-window sizes, rotation/scale undo, and general undo/redo are intentionally deferred to later milestones.
