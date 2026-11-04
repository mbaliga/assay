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

Defaults per size are the ones Assay ships pre-selected; the user can change them per task in the Models room.

Two releases in 2026 decide most of this table: **Gemma 4** (2 Apr 2026; E2B/E4B are Apache-2.0, with official LiteRT-LM builds, and are the basis of Gemini Nano 4) and **Qwen3.5 Small** (2 Mar 2026; 0.8B/2B/4B/9B, Apache-2.0, 201 languages, strong tool calling). Gemma 3n, Qwen3, Qwen2.5-Coder and Phi-4-mini are now fallbacks. Sizes are GGUF Q4_K_M unless marked LRT (Google's prebuilt LiteRT-LM file). Speeds are decode tokens/s on a Snapdragon 8 Elite-class phone; "est." is scaled from generic llama.cpp benchmarks, not measured — **benchmark on the RedMagic before shipping defaults**. `sha256` values are pinned at build time from `GET /api/models/{repo}?blobs=true` (`siblings[].lfs.sha256`), never typed by hand.

| Catalogue id | Model (HF repo) | Params | Disk | Licence | Runtime | Min RAM | Speed | Assay tasks | Indic | Size | Default? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `qwen3.5-0.8b` | `Qwen/Qwen3.5-0.8B` | 0.8B | ~0.5 GB | Apache-2.0 | gguf | 4 GB | ~45 est. | `digest` (weak) | weak | XS | — (no XS default for Assay) |
| `qwen2.5-coder-1.5b` | `Qwen/Qwen2.5-Coder-1.5B-Instruct` | 1.5B | ~1.0 GB | Apache-2.0 | gguf | 4–6 GB | — | `explain` | poor | S | **S default** (explain only) |
| `qwen3.5-2b` | `Qwen/Qwen3.5-2B` | 2B | ~1.2 GB | Apache-2.0 | gguf | 4–6 GB | ~35 est. | `explain`, `digest`, `translate` | fair | S | — |
| `qwen3.5-4b` | `Qwen/Qwen3.5-4B` | 4B | 2.78 GB | Apache-2.0 | gguf (needs a recent llama.cpp: hybrid Gated-DeltaNet) | 6–8 GB | ~18–25 est. | all phone tasks; `propose`/`prove` on a runner | fair–good | M | **M default**; **L default with thinking on** |
| `gemma4-e2b` | `google/gemma-4-E2B-it` · LRT `litert-community/gemma-4-E2B-it-litert-lm` | 2.3B eff. / 5.1B | LRT 2.59 GB · GGUF 3.1 GB · QAT 2.62 GB | Apache-2.0 | litert-lm, gguf, aicore (Nano 4 "Fast") | 6 GB | 47 CPU / 52 GPU (LiteRT, S26 Ultra) | `explain`, `digest`, `translate` | good (140+ langs) | M | alternate M (LiteRT speed path) |
| `gemma4-e4b` | `google/gemma-4-E4B-it` | 4.5B eff. / ~8B | LRT 3.65 GB · QAT GGUF 4.22 GB | Apache-2.0 | litert-lm, gguf, aicore (Nano 4 "Full") | 8 GB | 18 CPU / 22 GPU | all phone tasks | good | L | alternate L |
| `qwen3.5-9b` | `Qwen/Qwen3.5-9B` | 9B | ~5.5 GB | Apache-2.0 | gguf | 12 GB | ~8–12 est. | best quality for `propose`/`prove` (HumanEval ~72 % in one blog test) | good | XL | **XL default** |
| `granite-4.0-micro` | `ibm-granite/granite-4.0-micro` | 3B | 2.1 GB | Apache-2.0 | gguf | 6 GB | ~20 est. | `triage`, `explain` (strong instruction following; OpenAI-schema tools) | none | M | — |
| `phi-4-mini` | `microsoft/Phi-4-mini-instruct` | 3.8B | ~2.5 GB · LRT 3.9 GB | MIT | gguf, litert-lm | 6–8 GB | 7 CPU / 10 GPU (LiteRT, S24 Ultra) | `explain`, `propose` (fallback) | weak | M | — |
| `qwen2.5-coder-7b` | `Qwen/Qwen2.5-Coder-7B-Instruct` | 7B | ~4.7 GB | Apache-2.0 | gguf | 8–12 GB | — | `propose` (best diff writer of the older set) | poor | L | — |
| `smollm3-3b` | `HuggingFaceTB/SmolLM3-3B` | 3B | ~1.9 GB | Apache-2.0 | gguf | 6 GB | ~20 est. | general fallback | weak | M | — |
| `gemini-nano` | AICore (Nano 4 = Gemma 4 E2B/E4B) | — | no download | ML Kit GenAI terms | aicore | 12 GB, flagships only (~1–3 % of devices) | hardware | `explain`, `digest` | good | — | shown only when the system reports it |

**Not shipped in the catalogue, and why:** Qwen2.5-Coder-3B (Qwen Research licence, not permissive); LFM2.5-1.2B (licence free only under US$10M revenue — a trap for the user, not just the owner); Gemma 3 / 3n (Gemma Terms, gated); Llama 3.2 (Llama Community licence, gated); DeepSeek-R1-Distill-1.5B (reasoning loops, poor instruction following). Users may still bring any of these themselves via §6; the inspector shows the declared licence before first use.

**Official LiteRT-LM builds exist for:** Gemma 4 E2B/E4B, Gemma 3n, Gemma 3 1B, Qwen3-0.6B, Qwen2.5-0.5B/1.5B, Phi-4-mini, FunctionGemma (289 MB). Not for Qwen3 4B/8B or Qwen3.5 — those are llama.cpp only.

**Runtime facts that shaped §2:** llama.cpp (MIT) is the only runtime exposing per-token probabilities (`llama_get_logits`; n-best in its server), and the only one running every model above; it has CPU (i8mm), Vulkan GPU and an experimental Hexagon NPU backend. LiteRT-LM has GPU/NPU prefill 2–7× faster (Gemma 4 E2B ~3,800 tok/s prefill on GPU), constrained decoding and a Kotlin API, but ~10 models and no documented logprobs — output from that path is marked "no token confidence". AICore is zero-download but device-gated, carries non-removable safety filters, and Firebase's hybrid API falls back to the cloud (which the no-backend rule forbids; use the on-device-only path).

**Indic:** no small model publishes Konkani scores — treat Konkani as unverified with a low-confidence mark and a back-translation check. For translation alone, a dedicated translation model (IndicTrans2-class; Sarvam Edge's ~150 M translation model, 334 MB, if it becomes licensable) beats any 2–4B general model; a `translate`-only slot in the catalogue is reserved for one.

## 9. BYOK cloud providers

Presets ship for Anthropic (Messages API), OpenAI, Gemini (OpenAI-compatible endpoint), DeepSeek, xAI, Mistral, Groq, Cerebras, OpenRouter, Together, and "custom OpenAI-compatible". Each preset carries its base host; the call path asserts `request.host == preset.host`. The Models room shows every cloud provider as a **watched object** with the alien glow and "sent only to `<host>`". A per-provider spend counter uses a user-editable price table shipped in assets — no network call for pricing. Free tiers change monthly; the catalogue notes which presets had one at the time of the research pass and the UI never promises one.

As of the research pass (4 Nov 2026; items marked * are from secondary sources and must be re-checked when the preset ships):

| Preset | Base host | Free tier for individuals | Prompt caching | Structured output |
|---|---|---|---|---|
| Anthropic | `api.anthropic.com` (Messages API; OpenAI-compat endpoint exists but drops caching/logprobs) | No | Native API only | `json_schema` strict via tools |
| OpenAI | `api.openai.com` | Free usage tier in allowed countries | Automatic* | Yes, strict |
| Gemini | `generativelanguage.googleapis.com/v1beta/openai/` | Yes, no billing (~10 rpm / 1,500 rpd on Flash, indicative) | Implicit + explicit* | Yes* |
| DeepSeek | `api.deepseek.com` | None published | Automatic disk cache* | JSON mode* |
| xAI | `api.x.ai` | No | Automatic* | Yes* |
| Mistral | `api.mistral.ai` | Free mode (console limits) | — | JSON/schema* |
| Groq | `api.groq.com/openai/v1` | Free plan | Automatic, gpt-oss only | Yes |
| Cerebras | `api.cerebras.ai` | **Card required**; $5 credit, 30-day expiry | — | Yes* |
| OpenRouter | `openrouter.ai/api/v1` | `:free` models, 20 rpm / 50 rpd (1,000 rpd after $10 credit) | Pass-through* | Per model |
| Together | `api.together.xyz` | No ($15 minimum) | — | JSON mode* |
| GitHub Models | — | **Retired 30 Jul 2026** — do not ship a preset | — | — |

The app never promises a free tier in UI copy; the preset carries a `freeTierNotedOn: "2026-11-04"` field and the Models room shows "had a free tier when last checked" with that date.

## 10. Evaluation and honesty

- `fixtures/ai/<task>/` holds a golden set (inputs + acceptable outputs or validators). `AiEvalTest` runs every task against a fake provider on CI (shape, validation, grammar conformance).
- Real-model quality is recorded per model id in `docs/design/2026-11/model-eval.md` after an owner run on the RedMagic, marked `NEEDS-DEVICE-VALIDATION` until then.
- The confidence summary (mean logprob of the answer tokens, where available) is shown as a mark (◉ confident · ◐ unsure · ○ unknown), never a percentage.
- The Models room's "fits this phone" uses measured RAM, and the first load of any model is timed and shown ("loaded in 3.2 s").
