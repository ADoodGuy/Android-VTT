# Android VTT

A system-neutral Virtual Tabletop prototype for Android. This first milestone validates the geometry and interaction foundations before persistence, networking, and game-system modules are added.

## Prototype features

- Infinite world-space tabletop with pan and pinch zoom.
- Square, pointy-top hex, and flat-top hex grids.
- Hex size defined as **flat-to-flat distance**.
- Configurable scale presets expressed as displayed units per grid cell.
- Grid-center snapping.
- Euclidean measurement plus square/hex grid-step readouts.
- A circular prototype token whose diameter equals one grid cell.
- Tap a token to select it and open its context menu.
- Long-press and drag a token to move it.
- Freehand drawing with brush width stored in world-space units.
- Pure Kotlin geometry code with unit tests.

## Toolchain

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Kotlin 2.4.10
- Compile/target SDK 37
- Minimum SDK 23
- Compose BOM 2026.06.00

JDK 17 is required by AGP 9.3. Android Studio's bundled JDK is recommended.

## First-time setup

The generated patch cannot carry the binary Gradle wrapper JAR. Run one bootstrap script after applying the patch; it downloads the official Gradle 9.5.0 wrapper JAR and verifies its SHA-256 checksum.

### Windows PowerShell

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\bootstrap-wrapper.ps1
```

### macOS or Linux

```bash
./scripts/bootstrap-wrapper.sh
```

Then open the repository root in Android Studio and allow Gradle sync to finish.

## Build and test

```bash
./gradlew test
./gradlew assembleDebug
```

On Windows, use `gradlew.bat`.

## Controls

- **Pan tool:** drag with one pointer.
- **Any tool:** pan and zoom with two pointers.
- **Measure tool:** drag to create a measurement.
- **Draw tool:** drag to draw a world-space stroke.
- **Token:** tap for selection/context menu; long-press and drag to move.
- **Empty tabletop tap while using Pan:** clear token selection and dismiss the menu.

## Project structure

```text
app/                 Compose UI and interaction prototype
core/geometry/       Pure Kotlin world, grid, viewport, and measurement math
docs/decisions/      Recorded architecture decisions
scripts/             Gradle wrapper bootstrap helpers
```

## Current scope

This is intentionally an engine prototype. It has no database, map image import, campaign management, multiplayer, fog of war, or game-specific statistics yet.
