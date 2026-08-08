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

Add **Dice** to Tools with an app-level dice editor, persistent app-wide history/presets, and separate modal result/history windows.

### Cluster mode

- Rolls one pool with a common die size.
- Supports d2 through d12.
- Supports up to 500 dice in a pool.
- The result window displays the complete face distribution as a horizontal bar graph with one row for every possible face, including zero-count faces.
- The entire result row is a touch target; users do not need to hit the filled portion of the bar.
- Tapping a result row offers reroll rules for:
  - exactly that result,
  - that result or lower,
  - that result or higher.
- A reroll replaces only matching dice in the current pool and updates the result window.
- Each reroll creates a new history entry containing the complete updated pool.

### Single mode

- Supports up to eight dice sets in one expression.
- Each set supports 1–100 dice and die sizes d2 through d100, with a 500-die expression limit.
- Every dice set has its own optional arithmetic modifier represented by an explicit **+ / −** operation and non-negative whole-number value. The modifier is applied once to that set's rolled subtotal.
- Supports up to eight additional ordered global arithmetic modifier terms.
- Global modifiers are also represented by explicit **+ / −** operation buttons and non-negative whole-number value fields.
- Global modifier terms can be added and removed independently like dice sets.
- Normal rolls evaluate the expression once.
- Advantage and Disadvantage evaluate the complete expression twice, including all set and global modifiers, and retain the higher or lower total respectively while displaying both attempts.
- The final kept total in the result window is tappable and copies the integer result to the Android clipboard.

### Result window

The dice editor is configuration-only. Successful Cluster and Single rolls open a separate modal result window above the editor.

- Cluster results contain the histogram and reroll controls.
- Single results contain the kept total and detailed per-set/global arithmetic.
- Closing the result window returns to the unchanged editor controls.
- Rolling a preset also opens the same result window.

### Presets

Presets are mode-specific and app-wide.

A Cluster preset stores:

- name,
- dice count,
- die size.

A Single preset stores:

- name,
- all dice sets and their set-specific modifiers,
- all ordered global arithmetic modifier terms,
- Normal / Advantage / Disadvantage selection.

Preset selection uses an inline menu card rather than a popup dropdown. Every saved preset occupies one horizontally scrollable line containing its name/configuration and **Roll / Edit / Delete** controls.

Preset behavior supports:

- **Roll** without replacing the current editor controls,
- **Edit**, which loads the preset into the controls,
- **Delete**,
- saving the current controls as a new preset,
- saving edited controls back over the selected preset.

### History

- Keep the five most recent dice operations across both modes.
- History is app-wide and persisted across process restarts.
- History is no longer permanently shown in the editor; a **History** action in the dice header opens a separate modal history window.
- Cluster history records the full face/count distribution.
- Single history records the expression, kept total, and Advantage/Disadvantage comparison when applicable.

### UI ownership

- Selecting the Dice tool activates and opens the dice editor.
- The editor, result window, and history window are modal and prevent underlying tabletop gestures.
- Closing the editor while Dice remains selected exposes a compact reopen control.
- Leaving Tools or selecting another tool deactivates the Dice UI.

## Persistence

Dice state uses its own versioned SharedPreferences JSON store. It is intentionally separate from named scene snapshots. Persisted state includes:

- current roller mode,
- current editor controls,
- Cluster and Single presets,
- the five-entry roll history.

Schema v3 replaced the v1/v2 single signed global modifier field with an ordered global modifier list. Schema v4 adds a set-specific modifier to every Single dice set. Older editor state, Single presets, and Single history migrate with a default set modifier of **+0**, preserving all previously stored dice/global-modifier behavior.

Result-window and history-window visibility are transient and are not restored after process restart.

## Deferred

The initial implementation does not assume system-specific rules beyond the requested mechanics. Possible later extensions include:

- exploding/acing dice,
- success or target-number counting,
- reroll-failure presets,
- keep-highest/lowest subsets within one dice set,
- critical/fumble annotations,
- roll labels or character associations,
- cryptographically auditable or network-synchronized rolls.
