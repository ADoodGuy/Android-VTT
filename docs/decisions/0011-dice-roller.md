# ADR 0011: App-wide dice roller with Cluster and Single modes

## Status

Proposed for device validation.

## Context

Android VTT is primarily a system-neutral tabletop, but the dice utility should efficiently support the game families currently targeted by the app owner:

- large pools of small dice such as d6-based systems,
- d20-style checks,
- mixed polyhedral expressions used by systems such as Savage Worlds,
- D&D-style Advantage and Disadvantage.

Dice configuration is useful across maps and scenes, so it should not be coupled to a particular saved tabletop.

## Decision

Add **Dice** to Tools with an app-level dice panel and persistent app-wide history/presets.

### Cluster mode

- Rolls one pool with a common die size.
- Supports d2 through d12.
- Supports up to 500 dice in a pool.
- Displays a result histogram as face/count buckets.
- Tapping a result bucket offers reroll rules for:
  - exactly that result,
  - that result or lower,
  - that result or higher.
- A reroll replaces only matching dice in the current pool.
- Each reroll creates a new history entry containing the complete updated pool.

### Single mode

- Supports up to eight dice sets in one expression.
- Each set supports 1–100 dice and die sizes d2 through d100, with a 500-die expression limit.
- Supports one signed integer arithmetic modifier applied to the complete expression.
- Normal rolls evaluate the expression once.
- Advantage and Disadvantage evaluate the complete expression twice and retain the higher or lower total respectively, while displaying both attempts.

### Presets

Presets are mode-specific and app-wide.

A Cluster preset stores:

- name,
- dice count,
- die size.

A Single preset stores:

- name,
- all dice sets,
- arithmetic modifier,
- Normal / Advantage / Disadvantage selection.

The preset menu supports:

- **Roll** without replacing the current editor controls,
- **Edit**, which loads the preset into the controls,
- **Delete**,
- saving the current controls as a new preset,
- saving edited controls back over the selected preset.

### History

- Keep the five most recent dice operations across both modes.
- History is app-wide and persisted across process restarts.
- Cluster history records the full face/count distribution.
- Single history records the expression, kept total, and Advantage/Disadvantage comparison when applicable.

### UI ownership

- Selecting the Dice tool activates and opens the dice panel.
- The panel is modal and prevents underlying tabletop gestures.
- Closing the panel while Dice remains selected exposes a compact reopen control.
- Leaving Tools or selecting another tool deactivates the Dice UI.

## Persistence

Dice state uses its own versioned SharedPreferences JSON store. It is intentionally separate from named scene snapshots. Persisted state includes:

- current roller mode,
- current editor controls,
- Cluster and Single presets,
- the five-entry roll history.

## Deferred

The initial implementation does not assume system-specific rules beyond the requested mechanics. Possible later extensions include:

- exploding/acing dice,
- success or target-number counting,
- reroll-failure presets,
- keep-highest/lowest subsets within one dice set,
- critical/fumble annotations,
- roll labels or character associations,
- cryptographically auditable or network-synchronized rolls.
