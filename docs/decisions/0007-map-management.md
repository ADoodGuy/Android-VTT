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
- a normalized image-local snap anchor.

The current grid's feet-per-cell setting continues to define the tabletop's game-unit scale. The map itself therefore does not need a second independent feet-per-cell value.

A newly imported map defaults to 24 cells wide and derives its initial height from the source image aspect ratio. This corresponds to a common 24-inch-wide physical mat when each grid cell represents one inch. Replacing an existing map preserves its current tabletop size, position, rotation, and snap anchor.

Large source images are decoded with power-of-two sampling so the longest decoded edge is approximately 4096 pixels or less. This limits prototype memory use while still allowing the map to scale with the tabletop viewport.

The rendering order is:

1. tabletop background,
2. imported map,
3. grid,
4. drawings and measurements,
5. tokens and manipulation controls.

## Workspace modes

The top toolbar is split into three top-level modes:

- **Tokens** owns token adding, selection, movement, direct scaling/rotation, and token context settings.
- **Maps** owns map importing/replacing, selection, movement, direct scaling/rotation, alignment, and map context settings.
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

Map movement uses the configured image-local snap anchor rather than assuming the image center is the snap point. Direct scaling and rotation use magnetic snapping: the controlling map dimension has 0.5-cell scale anchors with a 0.1-cell magnetic window, and rotation has 15-degree anchors with a 3-degree magnetic window. When **Snap to grid** is disabled, ordinary map movement, scaling, and rotation are all free. Direct proportional scaling allows map dimensions down to the same 0.1-cell minimum accepted by the numeric map settings.

The map settings panel supports replacing the image, numeric width/height, numeric center X/Y, numeric rotation, launching the alignment assistant, resetting position/rotation, and removing the map. Numeric width and height remain independently editable so intentionally distorted or pre-corrected source images can be configured; subsequent direct handle scaling preserves whatever proportion is currently configured.

## Alignment assistant

The alignment assistant is intended for imported maps whose printed grid does not initially line up with the app grid.

The assistant shows a yellow image-local crosshair, a high-contrast example of the current app grid, and horizontal/vertical rulers covering multiple cells. The crosshair becomes the map's persistent snap anchor when the alignment is accepted.

Alignment interaction is intentionally different from ordinary map manipulation:

- one-finger dragging anywhere on the tabletop moves the image-local crosshair rather than panning or moving the map,
- releasing a crosshair drag translates the map so the chosen image point lands on the nearest app-grid anchor,
- two-finger gestures continue to pan and zoom the tabletop, allowing precise anchor placement at high zoom,
- crosshair dragging is handled by the full tabletop interaction layer rather than the map's screen-space hit box, so it continues to work when a zoomed map is much larger than the viewport,
- ordinary map-edge scale and rotation handles are hidden while alignment is active,
- a compact screen-sized controller is drawn around the crosshair instead,
- the orange square controller scales the map proportionally around the fixed crosshair; its physical control radius is independent of map size and zoom,
- the purple circular controller rotates around a small ring centered on the crosshair, rotating the map about that fixed anchor,
- the existing live size/rotation indicators remain available during alignment,
- **Done** persists the alignment geometry and snap anchor,
- **Cancel** restores the geometry and anchor captured when the assistant opened.

The snap anchor is stored as normalized horizontal and vertical offsets from the image center. Because those offsets are image-local, the saved point remains attached to the same visual location when the map is resized or rotated. Future snapped map movement translates the map so this saved point reaches the nearest app-grid anchor, preserving the established grid phase.

Alignment placement deliberately uses the current grid even if ordinary **Snap to grid** is disabled, because placing the chosen image point on the app grid is the purpose of the assistant. Scale and rotation magnetic snapping continue to respect the normal Snap toggle.

## Deferred work

Multiple map layers, map z-order, cropping, opacity, map locking, visibility toggles, image tiling, thumbnails, campaign/map libraries, and persistence of the rest of the tabletop state are deferred to later milestones.
