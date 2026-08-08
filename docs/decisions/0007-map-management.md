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
- a normalized image-local persistent snap anchor,
- independent movement/scaling/rotation lock flags.

The current grid's feet-per-cell setting continues to define the tabletop's game-unit scale. The map itself therefore does not need a second independent feet-per-cell value.

A newly imported map defaults to 24 cells wide and derives its initial height from the source image aspect ratio. This corresponds to a common 24-inch-wide physical mat when each grid cell represents one inch. Replacing an existing map preserves its current tabletop size, position, rotation, snap anchor, and locks.

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

Normal selected-map manipulation uses the same compact controller proven by the alignment assistant rather than map-edge handles.

- A single tap on the map selects it and places a temporary controller anchor at the app-grid snap point nearest the tap. The current grid's combined center/edge-midpoint/vertex anchor model is used.
- The temporary controller anchor is separate from the persistent alignment snap anchor. Choosing a convenient place for controls therefore does not overwrite the map-grid phase established by the alignment assistant.
- A double tap deselects the map.
- A stationary long press opens the map settings panel.
- When movement is unlocked, the controller anchor is shown as a yellow crosshair. Dragging the crosshair translates the whole map. With Snap enabled, releasing snaps that temporary control point to the nearest grid anchor, producing a grid-aligned translation that preserves the persistent map alignment.
- An orange square sits about 96 dp from the controller anchor. Dragging it toward or away from the anchor scales both map axes proportionally around that fixed point.
- A purple handle runs on a roughly 60 dp ring around the controller anchor and rotates the map about that point.
- The compact controller remains a consistent physical screen size regardless of map dimensions or zoom, so maps can be manipulated without zooming out to reach their edges.
- Direct scaling retains 0.5-cell magnetic anchors with a 0.1-cell magnetic window. Rotation retains 15-degree anchors with a 3-degree magnetic window. Turning **Snap to grid** off makes ordinary movement, scaling, and rotation continuous.
- Live size/rotation text appears while scale or rotation is active.

Movement, scaling, and rotation can be locked independently in Map settings. The movement lock removes the draggable normal crosshair and rejects map translation. The scale lock removes the orange scale stem/handle and rejects direct scaling. The rotation lock removes the purple ring/handle and rejects direct rotation. If movement is locked while another controller remains available, a small noninteractive anchor dot may be shown as the pivot reference. Locks are persisted with the map configuration.

Locks govern direct tabletop manipulation. Numeric map geometry fields remain available as an intentional administrative override.

The map settings panel supports replacing the image, numeric width/height, numeric center X/Y, numeric rotation, direct-control locks, launching the alignment assistant, resetting position/rotation, and removing the map. Numeric width and height remain independently editable so intentionally distorted or pre-corrected source images can be configured; subsequent direct scale manipulation preserves whatever proportion is currently configured.

## Alignment assistant

The alignment assistant is intended for imported maps whose printed grid does not initially line up with the app grid.

The assistant shows a yellow image-local crosshair, a high-contrast example of the current app grid, and horizontal/vertical rulers covering multiple cells. The crosshair becomes the map's persistent snap anchor when the alignment is accepted.

Alignment interaction uses the same compact scale/rotation controller as normal map selection, but the crosshair itself edits the persistent image-local alignment point:

- one-finger dragging anywhere on the tabletop moves the image-local crosshair rather than panning or moving the map,
- releasing a crosshair drag translates the map so the chosen image point lands on the nearest app-grid anchor,
- two-finger gestures continue to pan and zoom the tabletop, allowing precise anchor placement at high zoom,
- crosshair dragging is handled by the full tabletop interaction layer rather than the map's screen-space hit box, so it continues to work when a zoomed map is much larger than the viewport,
- the orange square controller scales the map proportionally around the fixed alignment crosshair,
- the purple circular controller rotates the map about that fixed alignment crosshair,
- the existing live size/rotation indicators remain available,
- direct-control locks apply in the assistant as well,
- **Done** persists the alignment geometry and snap anchor,
- **Cancel** restores the geometry and anchor captured when the assistant opened.

The persistent snap anchor is stored as normalized horizontal and vertical offsets from the image center. Because those offsets are image-local, the saved point remains attached to the same visual location when the map is resized or rotated. Future snapped map movement preserves this established grid phase.

Alignment placement deliberately uses the current grid even if ordinary **Snap to grid** is disabled, because placing the chosen image point on the app grid is the purpose of the assistant. Scale and rotation magnetic snapping continue to respect the normal Snap toggle.

## Deferred work

Multiple map layers, map z-order, cropping, opacity, visibility toggles, image tiling, thumbnails, campaign/map libraries, and persistence of the rest of the tabletop state are deferred to later milestones.
