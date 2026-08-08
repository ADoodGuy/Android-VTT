# ADR 0008: Tabletop persistence foundation

## Decision

The first persistence milestone introduces one versioned local autosave slot for the non-map tabletop state.

The autosave stores:

- grid kind and hex orientation,
- the Snap to grid setting,
- camera center and zoom,
- displayed feet-per-cell scale,
- all tokens and their IDs, names, positions, dimensions, colors, rotations, marker axes, and movement/scaling/rotation locks,
- the current measurement line,
- completed drawing strokes and their world-space brush widths.

The autosave intentionally does not restore transient interaction state such as selected objects, open context menus, active manipulation handles, or a drawing stroke that was still in progress when the activity stopped.

The existing map store remains responsible for the imported image URI, map geometry, alignment anchor, and map-control locks. This avoids destabilizing the already-tested Android document-permission flow. A later named-scene layer can coordinate both stores under a shared scene identity.

## Storage format

The tabletop autosave is encoded as versioned JSON and stored in app-private `SharedPreferences`. Schema version 1 is validated on load. Invalid enum values, non-finite coordinates, invalid token dimensions, and malformed strokes are rejected or replaced with safe defaults rather than being allowed to corrupt runtime state.

Token IDs are persisted. After restore, the next generated token ID is derived from the highest restored ID so newly added tokens cannot collide with restored tokens.

## Save lifecycle

`TabletopSceneStore` is initialized before Compose creates `TabletopState`. When the state is created it attaches to the store and restores the latest valid autosave, if one exists.

The current snapshot is saved from `MainActivity.onStop()`. This captures normal app backgrounding and exit without writing preferences continuously during high-frequency pan, zoom, token drag, scale, rotation, drawing, or measurement gestures.

## Next step

After this single-slot autosave is proven on a physical device, the next persistence slice can add named scenes/tabletops, scene creation/duplication/deletion, and explicit switching between saved scenes. At that point the map store and tabletop store should be namespaced by scene ID so a scene becomes a complete portable tabletop configuration.
