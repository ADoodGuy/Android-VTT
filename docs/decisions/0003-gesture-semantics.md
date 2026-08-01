# ADR 0003: Prototype gesture semantics

## Status

Accepted.

## Decision

- Tap a token: select it and open its context menu.
- Long-press and drag a token: pick it up and move it.
- Two-pointer transform: pan and zoom in every tool.
- One-pointer drag with Pan selected: pan the tabletop.
- One-pointer drag with Measure selected: create or update a measurement.
- One-pointer drag with Draw selected: create a freehand stroke.
- Tap empty tabletop with Pan selected: clear selection and dismiss the token menu.

## Consequences

Tokens are separate hit targets above the tabletop canvas. Navigation remains available without changing tools because two-pointer transforms are global.
