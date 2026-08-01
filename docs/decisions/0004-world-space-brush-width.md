# ADR 0004: Drawing width uses world space

## Status

Accepted.

## Decision

Each drawing stroke stores its brush width in world units. Rendered pixel width is calculated as:

```text
pixelWidth = worldWidth * pixelsPerWorldUnit
```

## Consequences

A stroke scales with the map when zooming and retains a stable physical relationship to tokens and grid cells. A future UI may optionally offer screen-space annotation tools, but those will be a separate drawing mode.
