# ADR 0007: Initial map management

## Decision

The tabletop supports one imported background map image at a time. The image is selected through Android's system document picker rather than through broad storage permissions.

The app requests a persistable read grant for the selected document URI and stores that URI in app preferences. Map geometry is stored alongside it so the configured map can return after an app restart when the document provider continues granting access.

Map geometry is expressed in tabletop/grid coordinates:

- width in cells,
- height in cells,
- center X in cells/world units,
- center Y in cells/world units,
- clockwise rotation in degrees,
- a persistent image-local snap anchor stored as normalized X/Y offsets from the map center.

The current grid's feet-per-cell setting continues to define the tabletop's game-unit scale. The map itself therefore does not need a second independent feet-per-cell value.

A newly imported map defaults to 24 cells wide and derives its initial height from the source image aspect ratio. This corresponds to a common 24-inch-wide physical mat when each grid cell represents one inch. Replacing an existing map preserves its current tabletop size, position, rotation, and snap anchor.

Large source images are decoded with power-of-two sampling so the longest decoded edge is approximately 4096 pixels or less. This limits prototype memory use while still allowing the map to scale with the tabletop viewport.

The rendering order is:

1. tabletop background,
2. imported map and any active alignment guides,
3. grid,
4. drawings and measurements,
5. tokens and manipulation controls.

## Workspace modes

The top toolbar is split into three top-level modes:

- **Tokens** owns token adding, selection, movement, direct scaling/rotation, and token context settings.
- **Maps** owns map importing/replacing, selection, movement, direct scaling/rotation, map alignment, and map context settings.
- **Tools** owns the Pan, Measure, and Draw utilities plus clearing utility output.

The second toolbar row is always 52 dp tall. Its controls change with the selected mode, but its height does not, preventing the tabletop viewport from shifting when modes change.

While Maps or Tools mode is active, a full-screen interaction layer sits above token pointer targets. This prevents accidental token manipulation outside Tokens mode.

## Map interaction

In Maps mode:

- tapping the map toggles selection,
- double-tapping deselects it,
- pressing and dragging moves it immediately,
- long-pressing opens the full map settings panel,
- four endpoint handles scale the whole map uniformly around its fixed center,
- direct scaling preserves the width-to-height proportion captured when the handle drag begins,
- dragging a horizontal handle uses map width as the controlling dimension while dragging a vertical handle uses map height,
- a rotation handle sits beyond the top edge and rotates the map around its center,
- live size/rotation text appears while a manipulation handle is active.

Normal map movement snaps the configured image-local snap anchor rather than always snapping the image center. The anchor is stored proportionally on the source image, so it stays attached to the same visual point as the map is resized and rotated. If no custom alignment has been performed, the anchor defaults to the map center.

Direct scaling and rotation use the same magnetic snapping model as token manipulation: the controlling map dimension has 0.5-cell scale anchors with a 0.1-cell magnetic window, and rotation has 15-degree anchors with a 3-degree magnetic window. When **Snap to grid** is disabled, ordinary map movement, scaling, and rotation are all free. Direct proportional scaling allows map dimensions down to the same 0.1-cell minimum accepted by the numeric map settings, which keeps the aspect-ratio constraint valid even for unusual custom proportions.

The map settings panel supports replacing the image, numeric width/height, numeric center X/Y, numeric rotation, resetting position/rotation, opening the alignment assistant, and removing the map. Numeric width and height remain independently editable so intentionally distorted or pre-corrected source images can be configured; subsequent direct handle scaling preserves whatever proportion is currently configured.

## Alignment assistant

The alignment assistant is intended for imported maps whose printed or embedded grid spacing/phase is not known in advance. Its interaction is inspired by drafting-table-style visual alignment workflows: choose a visible map-grid point, compare several cells against a ruler/grid guide, and refine scale until accumulated drift is removed.

While the assistant is active:

- a high-contrast sample of the current app grid is drawn around the map snap anchor,
- horizontal and vertical four-cell rulers with one-cell tick marks start at that anchor,
- a yellow crosshair marks the persistent image-local snap point,
- dragging the map body moves the crosshair across the source image instead of moving the whole map,
- releasing the body drag translates the map so the chosen crosshair point lands on the nearest current grid anchor,
- proportional scale handles remain available and scale around the crosshair rather than around the map center, keeping the chosen alignment point fixed while grid spacing is tuned,
- rotation also keeps the crosshair fixed while the assistant is active,
- **Done** persists the alignment, while **Cancel** restores the geometry and snap anchor captured when the assistant opened.

Because later map movement snaps the saved crosshair rather than the image center, a successfully aligned map does not acquire a new phase offset merely because it was moved and snapped again.

## Deferred work

Multiple map layers, map z-order, cropping, opacity, map locking, visibility toggles, image tiling, thumbnails, campaign/map libraries, automatic grid detection, separate-axis image warping for imperfect source grids, and persistence of the rest of the tabletop state are deferred to later milestones.
