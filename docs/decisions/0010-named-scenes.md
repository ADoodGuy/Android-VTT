# ADR 0010: Named tabletop scenes

## Decision

The app stores multiple named scenes on-device. A scene is the complete restorable tabletop state rather than only a token collection or map preset.

Each scene owns:

- grid kind, hex orientation, snap setting, and displayed feet per cell,
- camera center and zoom,
- all tokens and their direct-control locks,
- the complete measurement path,
- drawing strokes and current brush color,
- notes,
- the map image URI, geometry, rotation, alignment snap anchor, and map control locks.

The previously single autosave becomes the active scene's autosave. Switching scenes saves the current scene before loading the target scene.

## Library and migration

The scene library is a versioned JSON document in SharedPreferences. Scene records have stable numeric IDs, user-editable names, a versioned tabletop payload, and a map configuration payload.

If a scene library does not yet exist, the existing single tabletop autosave and current persistent map configuration are imported into `Scene 1`. Legacy preferences are left intact as a rollback/migration safety measure.

New scenes start blank with the standard default grid/camera settings. At least one scene must remain in the library.

## Scene operations

The first scene-management UI supports:

- switching scenes,
- creating a new blank scene,
- renaming the active scene,
- duplicating the active scene,
- deleting the active scene after confirmation.

Duplicating a scene duplicates both tabletop state and map configuration. Mutable measurement-path state is deep-copied so editing one scene cannot mutate another scene in memory.

## Map URI ownership

Duplicated scenes can refer to the same Android persisted document URI. Replacing or removing a map therefore releases the old persisted URI permission only when another saved scene does not reference that URI. This prevents changing one duplicate from breaking the map image in another duplicate.

The legacy single-map preferences continue to mirror the currently loaded scene as a compatibility fallback, but the scene library is authoritative once it exists.

## Transient state

Selections, context menus, active manipulation gestures, map alignment UI state, and workspace mode are not scene content. Loading a scene clears those transient states and returns to Tokens mode.

## Deferred work

Scene reordering, thumbnails, folders/tags, import/export, cloud synchronization, sharing, scene templates, and bulk scene operations are deferred.
