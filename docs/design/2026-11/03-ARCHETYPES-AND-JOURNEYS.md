# Assay — behavioural archetypes and their journeys

`docs/PERSONAS-AND-ROLES.md` describes *roles* (maintainer, reviewer, operator) and their *authority*. Roles decide who may press Approve. They do not decide what the first screen says, how dense the proof view is, or whether a user will believe a candidate is safe. That is behaviour, and the console has to be designed for it. This document adds the behavioural layer; it does not replace the role contract.

## 1. Three axes, not six personas

Every Assay user sits somewhere on three axes. The archetypes below are the clusters that actually occur; the axes are what the UI adapts to.

| Axis | Low end | High end | What it changes in the UI |
|---|---|---|---|
| **Expertise** | Does not know what an exported component or a SARIF result is | Writes Semgrep rules | Vocabulary, density, whether the diff or the explanation is on top |
| **Trust posture** | *Delegate*: "just fix it" | *Verify*: "show me the exit codes and the tool digest" | How much of the proof ladder is visible by default; whether AI is on |
| **Cadence** | *Reactive*: a scare triggered this | *Gated/ritual*: before every release, every Monday | Whether the origin room is a to-do list or a status board; notification design |

The console exposes one control for this, not a persona picker: a **Guidance dial** with three stops — **Guide / Work / Expert** — set at first run from one question ("How did you build this app?") and changeable in Settings. It changes defaults only. Every surface is reachable at every stop; nothing is hidden, only folded.

## 2. The archetypes

### A1 — The vibe shipper
*Novice · Delegate · Reactive.* Built the app with Fonebrew, Claude Code or Cursor. Shipped it. Has never read a security finding. Arrives because of a scare: a Play policy email, a reviewer saying "this app sends my data somewhere", a thread about leaked API keys in APKs, or Fonebrew's Audit tab turning up something.

- **Expectation:** "Tell me if I'm in trouble. Fix it. Don't make me learn security." Wants one tap and a yes/no.
- **Fears:** being banned from Play; breaking the app; being judged for not knowing.
- **What they will do wrong:** tap Approve as if it were OK on a dialog; assume a green-ish screen means safe; paste a PAT with repo-admin scope because the first one they generated had it.
- **Stellar looks like:** an answer to "Am I exposed?" within five minutes of install, in plain words, with the *proof ladder* (failed 2/2 before · passed 2/2 after) shown as a picture, and an Approve that is physically harder to hit than Reject (long-press, or a second tap on the same control, never a modal "OK"). The offline APK quick-check is their first result — no account, no runner. The "connect a repo" path is offered *after* the first result, not before.
- **Rooms they live in:** Bench (origin), Candidates (zoomed-in proof view at Guide density). They never need Runs or Settings → Scanner policy.
- **What Assay must refuse them:** a findings browser. They would read it as a to-do list of things they cannot fix. Counts + "Open in Fonebrew Audit" is the hand-off.

### A2 — The solopreneur maintainer
*Literate · Delegate with gates · Gated/ritual.* One to five apps live, revenue depends on them, phone-first between other things. Security is a chore between features. Knows what a permission is, could not write a Semgrep rule and does not want to.

- **Expectation:** a *what-needs-me* list, batching, an audit before every release, a weekly line in a digest. Zero noise.
- **Fears:** time sink; false positives; a tool that nags; a tool that touches their repo without asking.
- **What they will do wrong:** ignore the app for a month, then face forty candidates; add every repo and never set an output branch.
- **Stellar looks like:** a release gate that is three taps (Bench → "Run audit" on the repo → come back to a candidate list); candidates reviewable one-handed; a *stale* candidate (source moved on) clearly dimmed with a clock glyph rather than silently failing; hand-off to Orrery as a status cell. Scheduled runs are *their* GitHub Actions cron, visible in the app, never the app polling.
- **Rooms:** Bench, Runs, Candidates. Z-out to Portfolio when they have more than two repos.

### A3 — The security-literate engineer
*Expert · Verify · Ritual.* Reads SARIF. Wants rule IDs, the diff, the exit codes, the tool digests, severity floors, custom Semgrep configs, OSV offline DB freshness. Distrusts AI and may turn it off entirely. Uses the CLI on a laptop and the phone to approve.

- **Expectation:** no hand-holding, density, terminal parity (the phone must show exactly what `assay console-snapshot` produced). "AI off" must be a first-class mode, not a degraded one.
- **Fears:** the tool hiding a finding; the tool "fixing" with LLM output and calling it proven; drift between CLI and app.
- **What they will do wrong:** nothing the UI should prevent. They will find the gaps.
- **Stellar looks like:** every claim on screen has a digest a tap away (`attempts.json`, tool-lock, scanner version); the proof view defaults to the diff; the explanation (if AI is on) is below it with a provenance badge; Settings → Scanner policy exposes the pinned versions and lets them point at their own Semgrep rules; Reject requires a reason that lands in the hash-chained ledger.
- **Rooms:** Candidates (Expert density), Runs, Settings → Scanner policy / Runner.

### A4 — The small-studio lead
*Literate · Verify by delegation · Gated.* Two to six people. Wants to know who approved what, which repos are red, and that a junior cannot approve alone.

- **Expectation:** a portfolio view, a decision log with real identities, assignment by repo.
- **Fears:** an unreviewed approval; a repo nobody owns.
- **Stellar looks like:** Z-out from Bench to a Portfolio of repo-cells, each carrying last-run state and open-candidate count as glyph + number; a Decisions list filtered by actor (`human:<login>`); policy "approval requires N" as a repo setting enforced in `approve.yml`.
- **Rooms:** Portfolio (Z-out), Decisions (a tab inside Candidates), Settings → Policies. v2.

### A5 — The external reviewer
*Expert · Verify · Reactive, once.* A client, publisher, acquirer or auditor who must sign off. Does not run anything. Receives a snapshot.

- **Expectation:** open the snapshot, see the chain — scanner → finding → candidate → proof → human decision — and trust that nothing can be edited.
- **Stellar looks like:** the existing "Open verified audit" path, in a visibly read-only mode (no Approve control rendered, not merely disabled), with the source commit and bus digest in the header.
- **Rooms:** Snapshot (a read-only Candidates room reached from Bench's import action).

### A6 — The operator
*Expert · Verify · Ritual.* Runs the runner — hosted Actions today, a self-hosted Linux box later. Cares about tool pins, container isolation, hardening units, preflight.

- **Expectation:** preflight results and runner health visible on the phone; nothing else.
- **Stellar looks like:** Settings → Runner shows the last preflight as glyph + label per check; a failed pin is a candidate for *their* attention, not the maintainer's.
- **Rooms:** Settings → Runner.

### The anti-archetype — the dashboard tourist
Wants charts of findings over time, a findings table with filters, a "security score". Fonebrew's Audit tab is that surface. The console refuses it on purpose (BUILD-SPEC §3.7 "Findings never get a browser"). The on-ramp must say this in one line so the tourist leaves satisfied rather than confused: "Assay runs the audit and proves the fixes. To browse findings, open the branch in Fonebrew Audit."

## 3. Journeys

Each journey names the archetype it is designed for first; others pass through the same screens at a different density.

### J1 — First launch (all; designed for A1)
1. **Cell-growth loader** → one screen, no carousel: a four-cell diagram — *Four scanners find · AI explains and proposes · Proof is exit codes · You approve* — with a single info icon per cell. Under it, one question: *How did you build this app?* → `With an AI tool` / `By hand` / `I review other people's apps` → sets the Guidance dial.
2. **Pick an entry:** `Scan an APK on this phone` (no setup, result in a minute) · `Connect a repo` (needs a GitHub token; explained behind the info icon) · `Open a sample` (clearly labelled SAMPLE).
3. **First result** rendered in the archetype's density. The compass overlay appears once, here, after the result exists — never on an empty screen.
Exit criteria: user has seen one real or sample result and knows where Candidates and Settings are. Time budget: under two minutes to a result on the APK path.

### J2 — Connect a repo (A2, A3)
Repos room → `+` → `owner/repo`, output branch (default `assay/audit`), token (Keystore; scope checklist shown; the app verifies scope via `GET /user` and refuses an admin-scoped token with an explanation). If the repo has no caller workflow: one tap "Add the caller workflow" opens a PR with the twelve-line file; the app shows the PR link. Nothing runs until the user taps Run.

### J3 — Run and review (A2 first, A3 density)
Bench shows the repo as a cell with `queued → running → complete | partial | failed` (glyph + label). Tap → Runs room → the run; tap the candidate count → Candidates. For each candidate the Guide view shows the proof ladder and the plain explanation; Work adds scanner before/after; Expert opens on the diff. Approve = long-press; Reject = tap + reason. Apply opens a PR; the app never merges.

### J4 — The scare (A1)
Entry from a deep link or a Fonebrew hand-off (`assay://candidate/<id>` or the APK share sheet). Lands directly on the proof view at Guide density, with Back returning to Bench. No tutorial, no onboarding — if it is the first launch, the dial question is asked *after* the result is shown.

### J5 — Stale and failure (A2, A3)
A run goes `partial`: the Bench cell carries a ▲ with "1 scanner failed" and the explanation line from `completeness`. A candidate goes `stale`: dimmed, clock glyph, "source moved on; re-run to refresh" — one tap to re-run. A token expires: the Repos cell shows ✕ "token rejected" and Settings opens on the token field. Never a toast that disappears.

### J6 — Hand-off (A2, A4)
"Open in Fonebrew Audit: Check branch `<name>`" on every run; "Status in Orrery" on every repo. Both are share-sheet actions, not embedded views.

### J7 — Read-only review (A5)
Bench → Import snapshot → file picker → read-only Candidates room with the bus digest in the header. Back returns to Bench. No credentials are ever requested on this path.

## 4. Expectations the console must meet, by archetype

| Need | A1 | A2 | A3 | A4 | A5 | A6 |
|---|---|---|---|---|---|---|
| Result without an account | ● | ● | ○ | ○ | ● | ○ |
| Plain-language explanation on top | ● | ● | ○ | ◐ | ◐ | ○ |
| Diff on top | ○ | ○ | ● | ◐ | ● | ○ |
| Proof ladder visible | ● | ● | ● | ● | ● | ○ |
| Raw `attempts.json` / digests one tap away | ○ | ◐ | ● | ◐ | ● | ● |
| AI off as a first-class mode | ○ | ◐ | ● | ◐ | ● | ● |
| Portfolio view | ○ | ◐ | ○ | ● | ○ | ○ |
| Decision log with identities | ○ | ◐ | ● | ● | ● | ○ |
| Runner preflight on phone | ○ | ○ | ◐ | ○ | ○ | ● |
| Findings browser | ✕ | ✕ | ✕ | ✕ | ✕ | ✕ |

● must · ◐ should · ○ not needed · ✕ refused by design

## 5. Copy rules that fall out of this

- Explanations live behind info icons; surfaces carry a short hint at most.
- Never "Done", "Secure", "Safe", a score, or a colour-only state. Use the ladder words: `proposed · proof passed · approved · rejected · stale · applied`.
- The word *candidate* is kept (it is honest), but the Guide density adds the gloss once: "a proposed fix that has not been approved".
- Sample data says SAMPLE in the surface, not in a footnote.
