# On-device intelligence — Assay

The constellation's AI posture, as applied to Assay: on-device by default, a catalogue of one-click models sized to the phone, bring-your-own-model from a link or a file, and optional bring-your-own-key cloud providers that are visibly watched. This document is the design and the contract; §7 is the part that is Assay-specific, §8 is the model catalogue.

## 1. Principles (non-negotiable)

1. **On-device is the default; cloud is opt-in per task.** A fresh install must be fully useful with no key and no network beyond the model download the user chose.
2. **No backend.** The catalogue is a signed JSON file shipped in the APK and optionally refreshed from the repo at a pinned ref. Model files come from the publisher (Hugging Face) or the user. Nothing is proxied.
3. **AI is never on the critical path.** For Assay: AI cannot originate a `Finding` (structural: its return types cannot carry one), cannot set `proof_passed`, cannot approve. If no model is configured the audit still completes.
4. **Provenance on every token.** Every AI artifact carries `provenance ∈ {LOCAL, CLOUD(provider), MIXED}`, `model`, `promptVersion`, and, where the runtime exposes it, a confidence summary from per-token logprobs. The UI renders this as a mark + label (◉ radium local · ◌ alien cloud), never colour alone.
5. **Keys and model files are the user's.** Keys in Keystore, sent only to their own provider's host (asserted at call time). Model files in app-private storage, exportable by the user, deletable in one tap.
6. **Honest capability.** If a model does not fit the phone's RAM, the catalogue says so before download. If a task's output fails validation (bad JSON, malformed diff), the UI shows the failure, never a retry disguised as success.

## 2. Runtime decision

Three runtimes exist for Android in late 2026. Assay uses them in this order of preference, behind one `ModelProvider` interface:

| Runtime | Use | Why |
|---|---|---|
| **ASOM** (`127.0.0.1:11435`, OpenAI-compatible, AIDL pairing) | Preferred when paired | One loaded model serves the whole constellation; the daemon owns download, memory and scheduling. Assay must work without it (design constraint). |
| **Embedded llama.cpp** (GGUF, JNI) | Default when ASOM is absent | Broadest model coverage; per-token logprobs for confidence; GBNF grammars for guaranteed JSON/diff shapes; MIT. The same reasoning that made Fonebrew choose llama.cpp over Ollama. |
| **LiteRT-LM** (`.litertlm` / `.task`) | Optional accelerator shelf | NPU/GPU paths for the Gemma 3n family; fewer models; Apache-2.0. Shown in the catalogue as a runtime glyph; not required. |
| **AICore / Gemini Nano** | Zero-download provider on supported Pixels/Samsungs | No file to manage; device-gated; no logprobs. Listed under "On this phone" when the system reports it available; never assumed. |
| **Cloud (BYOK)** | Opt-in per task | OpenAI-compatible (`/v1/chat/completions`) and Anthropic Messages; no vendor SDKs; `java.net.http` / OkHttp only. |

The phone-side adapter and the runner-side adapter (BUILD-SPEC §3.5) share the same interface and prompt resources; the runner can point at ASOM on a self-hosted box, or at a cloud key from a repo secret.

## 3. The catalogue

`assets/models/catalogue.v1.json`, mirrored at `docs/design/2026-11/models-catalogue.v1.json` in the repo; refresh is a tap in the Models room that fetches the file from a pinned GitHub raw URL and verifies its detached signature (Ed25519, public key in the APK). Schema (one entry):

```json
{
  "id": "qwen3-4b-q4km",
  "name": "Qwen3 4B",
  "family": "qwen3",
  "params": "4B",
  "quant": "Q4_K_M",
  "bytes": 2497000000,
  "sha256": "…",
  "url": "https://huggingface.co/<org>/<repo>/resolve/<rev>/<file>.gguf",
  "revision": "<commit-sha>",
  "licence": "Apache-2.0",
  "permissive": true,
  "gated": false,
  "runtime": "gguf",
  "contextLength": 32768,
  "chatTemplate": "qwen3",
  "tasks": ["explain", "triage", "propose", "prove", "digest"],
  "languages": ["en", "hi", "te", "ta", "zh", "…"],
  "minRamMb": 6144,
  "tshirt": "M",
  "notes": "Thinking mode off by default for triage."
}
```

Rules: `url` must be `https`, must point at a host on the allow-list (`huggingface.co`, `hf.co`, publisher CDNs it redirects to), and the `revision` is a commit SHA, never a branch. `sha256` is mandatory. A gated model carries `gated: true` and the Models room asks for a Hugging Face token in place (Keystore; sent only to `huggingface.co`).

## 4. T-shirt sizes

Sizing is by file size on disk and the RAM the loaded model needs with a 4k context; the fit mark compares to `ActivityManager.MemoryInfo.totalMem` minus a 1.5 GB floor for the OS and the app.

| Size | Disk | Needs | Fits phones with | Typical models |
|---|---|---|---|---|
| **XS** | ≤ 0.6 GB | ≤ 1.5 GB | 4 GB+ | 0.3–0.6B models |
| **S** | ≤ 1.3 GB | ≤ 2.5 GB | 6 GB+ | 1–1.7B |
| **M** | ≤ 2.6 GB | ≤ 4 GB | 8 GB+ | 3–4B (the sweet spot) |
| **L** | ≤ 5 GB | ≤ 7 GB | 12 GB+ | 7–8B |
| **XL** | ≤ 9 GB | ≤ 12 GB | 16 GB+ | 12–14B |

One default per size for Assay's task mix is set in §8. A phone always sees its own size highlighted; larger sizes are shown dimmed with "needs N GB".

## 5. Download, integrity, storage

- **Downloader**: OkHttp with `Range` resume, streamed SHA-256, free-space precheck (file size + 10 %), Wi-Fi-only and charging-only conditions (defaults on), foreground service with a notification; no third-party download manager. Progress is the catalogue cell filling.
- **Verification**: the file is renamed into place only after the digest matches; a mismatch deletes the part and shows the expected/actual digest.
- **Storage**: `getExternalFilesDir("models")` (user-visible via SAF, survives app data clears of cache, removed on uninstall). "Export model" copies to a user-picked location. "Delete" is one tap with a hold.
- **Memory**: load on demand, unload on `onTrimMemory`, never two models resident; the Models room shows "loaded" with the radium glow.

## 6. Bring your own model

- **From a link**: an `https` URL. Hugging Face `/resolve/` URLs are recognised and shown with repo, file and revision; other hosts are accepted after a one-line "unknown publisher" note. Redirects are followed only within the same registrable domain or to a known CDN. Gated repos prompt for the token in place.
- **From a file**: SAF picker; the file is copied into the models directory (llama.cpp needs a real path to mmap).
- **Inspection before install** (nothing executes until confirmed): GGUF magic and version; metadata KV read for `general.architecture`, `general.name`, `<arch>.context_length`, quant type, tokenizer presence; architecture checked against the bundled llama.cpp's supported list; size vs RAM → fit mark; SHA-256 computed and pinned. For `.litertlm`/`.task`, the manifest is read the same way. The detected card is shown; **Install** confirms; the entry joins "On this phone" with `source: user` and no catalogue id.
- Anything that fails inspection is refused with the specific reason (unsupported architecture, truncated file, no tokenizer), never a generic error.

## 7. What AI does in Assay, and where

| Task | Runs on | Input | Output type (cannot carry a `Finding`) | Validation | Gate |
|---|---|---|---|---|---|
| `explain` | Phone or runner | One SARIF result + snippet | `Explanation` (markdown ≤ 1200 chars) | Length; no code-fence injection | Shown with provenance; never stored in SARIF |
| `triage` | Runner (phone may re-run for display) | Result + explanation | `Triage{severity ≥ pinned floor, exploitability note}` | JSON via GBNF; severity floor enforced in `Severity.kt` | Advisory; cannot lower severity |
| `propose` | **Runner only** | Result + file context | `PatchProposal{unifiedDiff}` | `git apply --check` on the fix branch | Becomes a candidate via `CandidateCli --actor system:assay-ai` |
| `prove` | **Runner only** | Result + proposal | `ProvingTestSource{path, ext, source}` | Compiles/parses; runs in `prove.yml`, judged by `ProofGate` | Exit codes only |
| `digest` | Phone | `console.json` | One paragraph | Length | Bench header only |
| `translate` | Phone | Explanation | Text | — | On tap only |

Phone-side tasks are small (explain, digest, translate) and run comfortably at **S–M**. Runner-side tasks (propose, prove) want **M–L** and run on ASOM on a self-hosted box or on a cloud key from a repo secret; on hosted Actions with no key, `--ai off` is implied and the run is still `COMPLETE`.

Prompts are versioned resources under `src/main/resources/prompts/<task>/v<n>.md`; the version is recorded in every artifact. Structured outputs use GBNF grammars on llama.cpp and `response_format`/tool-schema on cloud providers.

## 8. Model catalogue (Nov 2026)

*Filled from the research pass in `01-COMPETITIVE-LANDSCAPE.md`'s sibling research; see the table below. Defaults per size are the ones Assay ships selected.*

<!-- CATALOGUE -->

## 9. BYOK cloud providers

Presets ship for Anthropic (Messages API), OpenAI, Gemini (OpenAI-compatible endpoint), DeepSeek, xAI, Mistral, Groq, Cerebras, OpenRouter, Together, and "custom OpenAI-compatible". Each preset carries its base host; the call path asserts `request.host == preset.host`. The Models room shows every cloud provider as a **watched object** with the alien glow and "sent only to `<host>`". A per-provider spend counter uses a user-editable price table shipped in assets — no network call for pricing. Free tiers change monthly; the catalogue notes which presets had one at the time of the research pass and the UI never promises one.

<!-- BYOK -->

## 10. Evaluation and honesty

- `fixtures/ai/<task>/` holds a golden set (inputs + acceptable outputs or validators). `AiEvalTest` runs every task against a fake provider on CI (shape, validation, grammar conformance).
- Real-model quality is recorded per model id in `docs/design/2026-11/model-eval.md` after an owner run on the RedMagic, marked `NEEDS-DEVICE-VALIDATION` until then.
- The confidence summary (mean logprob of the answer tokens, where available) is shown as a mark (◉ confident · ◐ unsure · ○ unknown), never a percentage.
- The Models room's "fits this phone" uses measured RAM, and the first load of any model is timed and shown ("loaded in 3.2 s").
