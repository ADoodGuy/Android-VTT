# ADR 0012: Automated Android validation and portable backups

## Status

Proposed for device validation.

## Context

Android VTT now persists multiple named scenes, map references, and app-wide dice configuration/history. Continued feature work increases the cost of compile regressions and of losing device-local state. Existing map references may point at Android document-provider URIs that are not portable to another device.

## Decision

Add pull-request/main-branch Android validation and a versioned portable backup package.

### Automated validation

GitHub Actions runs on pull requests and pushes to `main`.

The job:

- checks out the repository,
- configures Java 17,
- runs the repository's verified Gradle-wrapper bootstrap script,
- configures Gradle with wrapper validation,
- runs `:core:geometry:test`,
- runs `:app:testDebugUnitTest`,
- assembles the debug APK, and
- uploads the debug APK as a workflow artifact when validation succeeds.

The repository intentionally bootstraps `gradle-wrapper.jar` rather than committing it, so CI follows the same supported bootstrap path.

### Backup format

Portable backups use a single `.avtt` ZIP archive. Backup schema v1 contains:

- `manifest.json`,
- the complete named-scene library payload,
- the complete app-wide dice payload, and
- one binary map entry for every unique map URI referenced by the scene library.

The backup manifest records which archive map entry corresponds to each original URI. Multiple scenes that share the same original map URI therefore store only one copy of that image.

### Export

Export first autosaves the active scene and dice state. If any referenced map cannot be read, export fails rather than silently producing an incomplete portable backup.

### Import

Import is a full replacement operation, not a merge. The UI requires an explicit confirmation before opening the Android document picker.

Before applying an imported archive:

- the archive format/version is checked,
- the scene library remains subject to the existing versioned scene decoder,
- dice data is validated against the currently supported backup dice schema, and
- every map referenced by the scene library must have a corresponding archive entry.

Restored map images are copied into app-private `filesDir/imported_maps` storage. Scene map URIs are rewritten to those private files before the scene library is installed. This removes dependence on the original device's document-provider URI.

After a successful full restore, obsolete app-private imported-map copies are removed and no-longer-referenced persisted document-provider permissions are released when possible.

### Ownership

Backups include both ownership domains already present in the app:

- scene-owned tabletop/map state, and
- app-wide dice editor state, presets, and five-entry history.

Backup import does not make dice data scene-specific.

## Consequences

- Pull requests can catch compile/test failures before physical-device testing.
- Backups can move maps and persistent tabletop state to another device without requiring the original map URI.
- Backup restore intentionally replaces local scene/dice libraries, so it is guarded by confirmation.
- Map image data can make `.avtt` files substantially larger than the scene JSON alone.
- Backup schema evolution must remain explicit as scene and dice persistence schemas change.

## Deferred

This milestone does not add cloud sync, incremental backups, automatic scheduled backups, backup merging, individual-scene export, encryption/password protection, or remote multiplayer synchronization.
