# ADR 0001: World coordinate system

## Status

Accepted for the geometry prototype.

## Decision

Persistent scene geometry uses double-precision world coordinates. The positive X axis points right and the positive Y axis points down, matching Android canvas coordinates. Screen pixels are never stored as token, drawing, or measurement positions.

The camera is represented by:

- the world point at the viewport center;
- pixels per world unit;
- viewport dimensions in pixels.

Every tool converts pointer positions through the shared `ViewportTransform`.

## Consequences

Zoom does not change scene data. Drawings, measurements, and token positions remain stable across devices and display densities. Compose rendering converts to `Float` only at the final draw boundary.
