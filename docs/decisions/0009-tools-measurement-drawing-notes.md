# ADR 0009: Measurement paths, drawing controls, and notes

## Decision

The Tools workspace expands from Pan / Measure / Draw to Pan / Measure / Draw / Notes. The fixed contextual toolbar row remains 52 dp tall and is horizontally scrollable so tool-specific controls do not force the tabletop viewport to resize.

## Measurement

Measurement is waypoint-based rather than drag-to-measure.

- Tapping empty tabletop space adds a snapped measurement marker.
- Each marker after the first is connected to the previous marker, forming an ordered polyline.
- Measurement markers use the same combined grid snapping model as the rest of the tabletop: centers, edge midpoints, and vertices/intersections when Snap is enabled.
- Tapping an existing marker selects it and opens a contextual delete action.
- Deleting a selected marker removes that marker and every marker created after it. Earlier markers remain in order.
- Clear measurement removes the complete path.
- The measurement readout reports the formatted value of each segment in path order.

The selected marker index is transient UI state and is not restored after app restart.

## Drawing

Completed drawing strokes now store their own ARGB color in addition to world-space points and brush width.

- Draw mode exposes the existing spectrum palette plus black and an arbitrary `#RRGGBB` custom color.
- Selecting a color returns the drawing tool to Brush mode.
- Eraser mode uses a screen-sized eraser radius so its physical touch target stays usable at different zoom levels.
- Erasing removes points within the eraser radius and splits affected polylines into surviving runs. Untouched parts of a stroke remain rather than deleting the entire stroke.
- Brush color is included in the tabletop autosave. Eraser/Brush mode itself is transient and restores as Brush.

## Notes

Notes are persistent tabletop objects with a world-space anchor and editable text.

- Selecting Notes and tapping empty tabletop space creates a note at the tapped position, using normal grid snapping when enabled.
- The note body is an editable text field while Notes is active.
- A small note header is the movement handle so text editing and drag movement do not compete for the same gesture.
- Releasing a moved note uses the same position snapping behavior as tokens.
- Notes do not rotate.
- Note cards keep a screen-readable physical size while their anchor remains attached to the tabletop. Their width grows and shrinks with the longest line up to a cap; text then wraps and the card height grows or shrinks automatically.
- Notes remain visible outside Notes mode but are noninteractive there.
- Individual notes can be deleted from their header while Notes is active.

## Persistence

The tabletop autosave schema advances from version 1 to version 2. Version 1 snapshots remain readable:

- a legacy two-point measurement is migrated to a two-marker measurement path,
- legacy drawing strokes receive the previous default drawing color,
- notes default to an empty collection,
- brush color defaults to the previous drawing color.

Version 2 persists measurement paths, per-stroke colors, current brush color, and note IDs/text/positions along with the existing tabletop state.

## Deferred work

Measurement path reordering, per-segment labels drawn directly on the tabletop, measurement marker dragging, configurable brush width, configurable eraser size, note colors/styles, note locking, rich text, note layering, and note attachments are deferred.
