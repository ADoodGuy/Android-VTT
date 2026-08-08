# ADR 0007: Initial map management

## Decision

The tabletop supports one imported background map image at a time. The image is selected through Android's system document picker rather than through broad storage permissions.

The app requests a persistable read grant for the selected document URI and stores that URI in app preferences. Map geometry is stored alongside it so the configured map can return after an app restart when the document provider continues granting access.

Map geometry is expressed in tabletop/grid coordinates:

- width in cells,
- height in cells,
- center X in cells/world units,
- center Y in cells/world units.

The current grid's feet-per-cell setting continues to define the tabletop's game-unit scale. The map itself therefore does not need a second independent feet-per-cell value.

A newly imported map defaults to 20 cells wide and derives its initial height from the source image aspect ratio. Replacing an existing map preserves the current tabletop size and position.

Large source images are decoded with power-of-two sampling so the longest decoded edge is approximately 4096 pixels or less. This limits prototype memory use while still allowing the map to scale with the tabletop viewport.

The rendering order is:

1. tabletop background,
2. imported map,
3. grid,
4. drawings and measurements,
5. tokens and token manipulation controls.

## Prototype controls

A floating **Map** button sits above the bottom-right grid controls so no additional top-toolbar scrolling is required.

The map settings panel supports:

- choosing or replacing the image,
- editing width and height in cells,
- editing center X and Y,
- resetting the center fields to the origin,
- removing the map.

## Deferred work

Direct drag/resize/rotation handles for the map, cropping, opacity, multiple map layers, map locking, visibility toggles, image tiling, thumbnails, campaign/map libraries, and persistence of the rest of the tabletop state are deferred to later milestones.
