# Assay console — the rooms

The console is redrawn on Fonebrew's room grammar so that moving between Fonebrew, Assay, CSApp and Orrery feels like moving between rooms of one house. This document is the spatial model, the room-by-room content, the interaction contract, and the screen inventory a build session can implement without inventing layout.

Hard constraints carried over: BUILD-SPEC §3.7 (console = control plane + offline quick check; findings never get a browser), §6 (Hyle tokens, no submodule), CLAUDE.md rule 7 (colour never carries state alone), and the owner's taste notes: generous whitespace, no boxy card grids, explanatory copy behind info icons, no decorative connectors, organic cell marks composed like plant cells (not wobbly), Cohere as the nearest reference, state conveyed through how the UI manifests.

## 1. Room grammar (shared with Fonebrew)

- One **origin room** at the centre. System **Back** from any cardinal room returns to the origin. **Up** (header chevron) is hierarchical inside a room.
- **Planar swipes** move to the four cardinal rooms. A **compass** (bottom bar, four marks + centre) gives tap parity; every room is also reachable by TalkBack custom action and by hardware keyboard (`Alt+←/→/↑/↓`, `Esc` = origin).
- **Z axis**: pinch out = zoom *out* to the overview of the current room; pinch in on an item = zoom *in* to its detail. Z has `+/−` and zoom-to-fit controls for non-gesture parity. Zoom in from the origin is the most common move; zoom out from the origin is the portfolio.
- **Immersive mode** (chrome hidden, gestures only, status in the margins) and **regular mode** (app bars, compass, buttons) with full parity. The dial in Settings; default regular.
- **Predictive back**, process-death restoration of room + Z position + scroll + open sheet, RTL mirroring of the whole spatial model.

## 2. Room map

```
                      ▲ North: RUNS
                        dispatch · progress · history

   ◀ West: REPOS       ● Origin: BENCH        East: CANDIDATES ▶
     targets · tokens    what needs you          review · prove
     caller workflow     (a to-do list)          approve · reject

                      ▼ South: SETTINGS
                        models · providers · runner · policy
```

- **Z-out from Bench** → **Portfolio**: every repo as a cell; last-run state and open-candidate count carried by glyph + number. Empty for one repo (the Bench *is* the portfolio then; zoom-out is a no-op with a short hint).
- **Z-in on a candidate** → **Proof view**: the ladder, diff, scanner before/after, explanation with provenance, decision controls.
- **Z-in on a run** → **Run detail**: per-scanner status, artifact digests, the Fonebrew hand-off line.
- **Pocket actions** (bottom-sheet from Bench, not rooms): `Scan an APK on this phone`, `Import snapshot`, `Open a sample`.

The hand-offs are share-sheet intents, not rooms: *Open in Fonebrew Audit* (on a run), *Status in Orrery* (on a repo).

## 3. Room by room

### Bench (origin)
A to-do list, in the sense Fonebrew's Audit tab is: the things that need the human. Ordered by urgency the engine can compute, never by AI.

- Sections (headers only when non-empty): **Needs you** (candidates at `proof_passed`, runs at `partial`/`failed`, stale candidates, expired tokens) · **Running** (live runs with a slow under-the-surface motion, no spinner) · **Recent** (last three completed runs).
- Each item is one line + a mark; tap zooms in. Swipe right on a candidate = open proof; swipe left = snooze until next run (recorded locally only).
- Empty Bench (fresh install, nothing connected): the three pocket actions as large cells and one hint line. No illustration of an empty inbox; the cell-growth loader has just played and the cells it grew *are* the three actions.
- Digest line at the top when AI is configured: one sentence, provenance badge, "Write this week's digest" on tap. Off → nothing shown (not "AI is off").

### Repos (west)
List of targets. Cell per repo: `owner/repo`, output branch, token state (✓ verified scope · ✕ rejected · ◌ unchecked), caller-workflow state (present · missing → one-tap PR). `+` adds a repo; the form validates the token via `GET /user`, shows the scope checklist, and refuses admin-scoped tokens with a one-line reason. Removing a repo removes the token binding; the token itself is in Settings.

### Runs (north)
Per selected repo (selector in the header, remembered). Each run one line: time · commit short-sha · state mark · counts (findings by severity glyph, candidates). **Run audit now** is the room's primary control — a single cell-button, long-press to choose scanners/`--apk`/AI on-off for this run. **Refresh** is a pull. No background polling, ever.

### Candidates (east)
Per selected repo. Tabs: **Open** · **Decided** (the decision log, filterable by `human:` actor) · **Stale**. Each candidate one line: finding rule id · severity mark · state mark (dashed outline / solid + check / filled / strikethrough / dimmed + clock) · proving status as a ladder glyph (`✗✗ → ✓✓`).
Zoom in → **Proof view**, density by the Guidance dial:
- **Guide**: proof ladder as a picture on top; plain explanation (if AI) with provenance badge; "what this changes" in two lines; Approve (long-press) / Reject (tap → reason sheet). Diff behind a disclosure.
- **Work**: ladder + scanner before/after + explanation; diff half-open.
- **Expert**: diff on top; ladder as a compact row; explanation below; digests (`attempts.json`, tool-lock, scanner version, candidate id) as a monospace block.
Apply → opens a PR; the PR link replaces the control. Never merges.

### Settings (south)
Sections as cells, not a list of toggles:
- **Models** — the shared Models room (§5).
- **GitHub** — token (Keystore), scope, login resolved (`human:<login>` shown; this is the actor identity).
- **Runner** — hosted Actions (default) · self-hosted (preflight results as glyph + label per check, from the last `runner-preflight`).
- **Scanner policy** — pinned versions and digests (read-only, from tool-lock), Semgrep config path, OSV offline toggle, severity floor (read-only; the engine owns it).
- **Appearance** — Guidance dial, immersive/regular, density, theme, sounds and haptics (Hyle presets), chart colours information-rich/monochrome.
- **About** — version, licence, "no telemetry" statement with the exact list of hosts the app can contact.

## 4. Interaction contract

| Gesture | Regular-mode parity | Meaning |
|---|---|---|
| Swipe ←/→/↑/↓ (from origin) | Compass taps | Move to cardinal room |
| Back | Back | Return to origin |
| Pinch out | `−` / zoom-to-fit | Overview of the room |
| Pinch in on item | Tap | Detail |
| Long-press Approve | Hold ≥600 ms with a growing cell ring | Approve (irreversible in the ledger) |
| Tap Reject | Tap | Opens reason sheet; empty reason is refused |
| Pull down (Runs, Candidates) | Refresh button | Explicit refresh — the only network call that is not a tap on a named action |
| Double-tap a candidate | Bookmark mark | Mark for later (local) |
| Pull item up / down (Bench) | Overflow menu | Snooze / un-snooze |

Every network call originates from a tap or a pull on a named control. `NetworkPolicyTest` enforces that no call is reachable from a lifecycle callback or a worker.

## 5. The Models room (shared pattern across the constellation)

Reached from Settings, and by deep link from any provenance badge. Three shelves, no tabs:

1. **On this phone** — installed models as cells carrying: name, t-shirt size (XS–XL), size on disk, last used, a radium glow when loaded. Tap → load/unload, set as default for a task (`explain`, `propose`, `prove`), delete.
2. **Catalogue** — one-click downloads. Each cell: name, size, licence (permissive or not, as a glyph), what it is good at (task glyphs), "fits this phone" as a fit mark computed from RAM. Tap → download with SHA-256 pin, resumable; progress is the cell filling, not a bar. Gated models say so and ask for the token in place.
3. **Bring your own** — a URL field (HF `resolve/` URLs, any `https` GGUF/`.task`) and a file picker (SAF). The app reads the header, shows the detected architecture/quant/context, pins the hash, then installs. Nothing executes until the user confirms the detected card.

Below the shelves: **Cloud (BYOK)** — provider cells with an alien glow and the watched-object glyph; key in Keystore; "sent only to `<host>`" shown on every cell. **ASOM** appears as a local provider at `127.0.0.1:11435` when its pairing is verified.

Provenance appears everywhere AI text appears: ◉ On-device (radium) · ◌ Cloud `<provider · model>` (alien) · ◎ Mixed · ○ Unknown — glyph + label always.

Full catalogue, sizing and integrity rules: `02-ON-DEVICE-INTELLIGENCE.md`.

## 6. Marks and states (Hyle)

- Severity: SEV1 ◆ · SEV2 ◇ · SEV3 ▲ · SEV4 ○, label always, luminance 100/80/60/40 %.
- Run state: queued ◌ · running ◐ (with under-the-surface motion) · complete ● · partial ◐▲ · failed ✕.
- Candidate state: proposed (dashed) · proof passed (solid + ✓) · approved (filled) · rejected (strikethrough ✕) · stale (dimmed + clock) · applied (filled + PR glyph).
- Token/connection state: verified ✓ · rejected ✕ · unchecked ◌.
- Marks are composed cell shapes (plant-cell geometry, straight-ish walls, rounded junctions), never filled coloured circles; the Voronoi generator in the ASOC logo builder is the reference for assembly. Severity and state are never red/green.
- Ground `#07070A`; violet `#8E7BFF` primary; radium `#CCEC75` local glow; alien `#3ED4FF` cloud glow. Light theme for print only.

## 7. Motion, sound, haptics

- Loader: cell growth — one cell with a visible nucleus divides into the cells that become the screen's elements; the cells settle and the UI manifests. Under 900 ms; skippable; reduced-motion = fade.
- Running: slow under-the-surface movement in the cell, no spinner. Failure: the cell's wall dims and contracts (cellular death), then the ✕ mark.
- Earcons and haptics from the Hyle preset in HAfx Studio: a soft tick on compass moves, a two-step confirm on Approve, a single low pulse on Reject. All off by default in immersive mode; on in regular mode; a global toggle.
- Nothing heavy. If an animation takes longer than the action it decorates, cut it.

## 8. Accessibility

- Every control has a content description naming its *effect*, not its shape ("Approve candidate; hold to confirm").
- 200 % font scale without truncation in every room (Roborazzi row in CI).
- Compass and `+/−` give full gesture parity; TalkBack custom actions for room moves and Z.
- No state by colour alone; every mark has a label in the same node.
- Both themes meet AA; the dark theme is primary.

## 9. Screen inventory (for the build session)

| # | Screen | Reached from | States to design |
|---|---|---|---|
| 1 | First launch | cold start | loader · four-cell diagram + dial question · entry pick |
| 2 | Bench | origin | empty · needs-you · running · recent · digest on/off |
| 3 | Portfolio | Z-out Bench | 1 repo (no-op hint) · n repos · a repo in failed |
| 4 | Repos | west | empty · list · add form (token scope check pass/fail) · missing caller workflow |
| 5 | Runs | north | no runs · list · dispatching · running · partial · failed · refreshing |
| 6 | Run detail | Z-in run | per-scanner table · hand-off line · digests |
| 7 | Candidates | east | Open/Decided/Stale · empty each |
| 8 | Proof view | Z-in candidate | Guide/Work/Expert · approve hold · reject sheet · applied (PR link) · stale |
| 9 | Settings | south | the six cells |
| 10 | Models room | Settings / badge | three shelves · download progress · gated · BYOM header card · BYOK provider |
| 11 | APK quick check | pocket | picker · result · "Local quick check" trust label |
| 12 | Snapshot (read-only) | pocket | import · read-only Candidates with digest header |
| 13 | Sample | pocket | SAMPLE label on every surface |
