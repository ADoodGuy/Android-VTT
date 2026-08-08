# ADR 0007: Initial map management

## Decision

The tabletop supports one imported background map image at a time. The image is selected through Android's system document picker rather than through broad storage permissions.

The app requests a persistable read grant for the selected document URI and stores that URI in app preferences. Map geometry is stored alongside it so the configured map can return after an app restart when the document provider continues granting access.

Map geometry is expressed in tabletop/grid coordinates:

- width in cells,
- height in cells,
- center X in cells/world units,
- center Y in cells/world units,
- clockwise rotation in degrees.

The current grid's feet-per-cell setting continues to define the tabletop's game-unit scale. The map itself therefore does not need a second independent feet-per-cell value.

A newly imported map defaults to 24 cells wide and derives its initial height from the source image aspect ratio. This corresponds to a common 24-inch-wide physical mat when each grid cell represents one inch. Replacing an existing map preserves its current tabletop size, position, and rotation.

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
- **Maps** owns map importing/replacing, selection, movement, direct scaling/rotation, and map context settings.
- **Tools** owns the Pan, Measure, and Draw utilities plus clearing utility output.

The second toolbar row is always 52 dp tall. Its controls change with the selected mode, but its height does not, preventing the tabletop viewport from shifting when modes change.

While Maps or Tools mode is active, a full-screen interaction layer sits above token pointer targets. This prevents accidental token manipulation outside Tokens mode.

## Map interaction

In Maps mode:

- tapping the map toggles selection,
- double-tapping deselects it,
- pressing and dragging moves it immediately,
- long-pressing opens the full map settings panel,
- four endpoint handles resize width or height symmetrically around the fixed map center,
- a rotation handle sits beyond the top edge and rotates the map around its center,
- live size/rotation text appears while a manipulation handle is active.

Map movement uses the same grid-position snapping as tokens. Direct scaling and rotation use the same magnetic snapping model as token manipulation: 0.5-cell scale anchors with a 0.1-cell magnetic window and 15-degree rotation anchors with a 3-degree magnetic window. When **Snap to grid** is disabled, map movement, scaling, and rotation are all free.

The map settings panel supports replacing the image, numeric width/height, numeric center X/Y, numeric rotation, resetting position/rotation, and removing the map.

## Deferred work

Multiple map layers, map z-order, cropping, opacity, map locking, visibility toggles, image tiling, thumbnails, campaign/map libraries, and persistence of the rest of the tabletop state are deferred to later milestones.
