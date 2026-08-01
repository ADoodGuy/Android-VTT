# ADR 0002: Hex size is flat-to-flat distance

## Status

Accepted.

## Decision

A hex grid's public size is the perpendicular distance between two opposing parallel edges (`flatToFlat`).

A circular token whose world-space diameter equals `flatToFlat` is inscribed in the hex. Internally:

```text
apothem       = flatToFlat / 2
circumradius  = flatToFlat / sqrt(3)
cornerToCorner = 2 * circumradius
```

Both pointy-top and flat-top orientations use this same definition.

## Consequences

Token sizing and grid scale use the same intuitive value. The implementation must not use the ambiguous property name `size` for hex geometry.
