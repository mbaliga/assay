# Assay — competitive landscape, 4 Nov 2026

Best-in-class across three surfaces, then the positions nobody occupies, then what Assay must match and must not copy. Prices are from official pages unless marked **(3P)** (third-party aggregator, indicative). "Proves fix?" asks whether the tool *runs something* to show a fix works, beyond an LLM review. **No product on the market gates a fix on a new test that fails before and passes after** — that is Assay's `ProofGate`, and it is unoccupied ground.

## 1. Desktop and CI

| Tool | Best at | Indie price | Autofix | Proves fix? | Android | Offline/local | AI posture |
|---|---|---|---|---|---|---|---|
| **GitHub Advanced Security** (CodeQL + Copilot Autofix) | Native PR flow; CodeQL depth | Free on public repos. Private: Code Security $30 + Secret Protection $19 per committer/month | Yes; agentic autofix GA 15 Oct 2026 | Partly — reruns CodeQL until the alert clears, then opens a draft PR. No tests | Kotlin/Java via CodeQL | CodeQL CLI local; autofix cloud | Deterministic finding, AI fix |
| **Semgrep AppSec Platform** | Rules engine + "Multimodal" hybrid AI (Mar 2026) | Free: 10 contributors, 10 repos, SAST + SCA + autofix (60 credits). Secrets $15/contributor extra | Yes (autofix public beta Mar 2026) | Not documented | Kotlin/Java rules; no APK | Community CLI fully local | Rules find; LLM triages auth logic |
| **Snyk** | Developer-first SCA, IDE plugins | Free: 5 projects, 100 Code tests/month. Team from $25/month | Agent Fix (agentic since Apr 2026) | Partly — engine rescans candidates, rejects insecure ones | Gradle/Maven SCA | CLI; analysis cloud | Engine filters AI fixes |
| **SonarQube** | Quality + security; free self-hosted Community Build | Cloud free ≤50k LOC; Team $34/month | Remediation Agent (Enterprise) | No | Kotlin/Java rules | Community Build self-hosted | AI fix as add-on |
| **Aikido** | All-in-one for small teams | Free: 2 users, 10 repos, 10 AutoFixes/month. Basic $300/month | Yes, with confidence rating | No | Mobile pentest of uploaded APK + source | On-prem scanner from Pro | Fixes on Bedrock; snippets only |
| **Socket** | Malicious-package detection | Free: 1 seat, 1,000 scans/month | Limited | No | Not named | CLI | Risk analysis |
| **Endor Labs** | Reachability-based SCA | Free developer tier, local agent | Agentic remediation | Not documented | JVM SCA | Free tier local | AI SAST + agents |
| **MobSF** | Free static + dynamic APK analysis | Free (GPL-3.0); v4.4.6 Mar 2026 | No | No | **The reference tool** | Yes | None |
| **Ostorlab** | Agentic mobile DAST with replayable exploits | Community free; AppSec Mobile from $499/app/month | AI autofix + paid "fix validation" | Partly, as a paid credit | **Yes** | OXO engine Apache-2.0; platform SaaS | BYOK on some plans |
| **NowSecure** | Enterprise device testing | No free; custom (3P) | No | No | **Yes** | No | Unspecified |
| **Appknox** | Mobile SAST/DAST/API + store monitoring | First scan free; quote | Guidance only | No | **Yes** | No | Prioritisation |
| **Checkmarx One** | Enterprise SAST + MCP agent | Trial, then enterprise | Yes | Partly — rescans | Kotlin/Java | Unclear | Agentic, MCP-first |
| **Veracode** | Compliance-grade SAST | ~$15k/year (3P) | Veracode Fix | No | Mobile binaries | No | AI fix |
| **Trivy** | Free OSS all-in-one | Free | No | No | Limited | Yes | None. **Supply chain compromised Mar 2026** (malicious v0.69.4, hijacked action tags) |
| **GitLab Ultimate** | Single-platform DevSecOps | Enterprise; AI review $0.25/MR (18.10) | Agentic SAST MR with confidence score (18.11) | No | Generic | Self-managed | Credit-billed |

**Best in class: GitHub Advanced Security** for an indie on a public repo — CodeQL, push-protection secrets, Dependabot and agentic autofix for nothing, and since Oct 2026 the autofix reruns CodeQL until the alert closes before opening a draft PR. It proves against the scanner, not with a test. **Semgrep** is the runner-up and the closest cousin to Assay's design: rule-first, free local CLI, free cloud tier with autofix. For the APK itself, **MobSF** has no rival.

## 2. Mobile (phone-first)

| App | Best at | Price | Vuln analysis? | Fix loop? | Offline |
|---|---|---|---|---|---|
| **App Manager** (F-Droid v4.1.1, Sep 2026) | Trackers/libraries, manifest, components, permissions, signing on installed apps | Free | No | No | Yes |
| **ClassyShark3xodus** (last release Jan 2024) | Tracker signatures, cert/hash checks | Free | No | No | Yes |
| **Exodus Privacy** (Jul 2024) | Tracker/permission reports | Free | No | No | No (server reports) |
| **Apk Analyzer** (Martin Styk) | Manifest, size, dex | Free | No | No | Yes |
| **Acode / Android IDE – Phone AS / Code Studio** | On-device editing, Gradle builds; Acode hosts Claude Code/Codex in its terminal | Free/freemium | No security feature listed | No | Yes |
| **Codex / Claude Code mobile clients** | Steering cloud agents | Plan-based | Via cloud agent | No | No |

**No phone app audits a repository or APK with an agent loop.** On-device tools stop at trackers and permissions; mobile IDEs list no security feature; the Codex and Claude phone apps are remote controls for cloud agents. **Best in class (phone): App Manager** — the only actively maintained, offline, FOSS deep APK inspector. It is a neighbour, not a competitor: it finds no vulnerabilities and has no fix loop. Assay's offline APK quick-check should be at least as deep as App Manager on components, permissions and signing, or it will look thin next to a free F-Droid app.

## 3. Agentic

| Tool | What it does | Originates findings? | Proves fix? | Human gate | Indie price |
|---|---|---|---|---|---|
| **Claude Security** (Anthropic) | Managed: continuous scans, threat model, attack paths, MCP (21 Oct 2026). **Plugin**: local multi-agent scan → Markdown/JSONL/**SARIF 2.1.0**, commit-stamped | Yes (LLM); independent verifier agents; docs admit nondeterminism | Partly — a reviewer agent runs the project's *existing* tests; writes none | Never auto-applies; user runs `git apply`; one PR per patch | Managed: Enterprise ($25k/month credits). Plugin: any paid plan |
| **Codex Security** (OpenAI) | Editable threat model, impact ranking, sandbox PoC validation, patches; local CLI `@openai/codex-security` | Yes | Validates the *finding* with a PoC; patch validation undocumented | Review implied | Pro/Business/Enterprise; token-billed from 1 Oct 2026 |
| **Copilot Autofix / coding agent** | Agentic autofix GA 15 Oct 2026; agent self-checks with CodeQL; `/security-review` in the Copilot app (Jul 2026, incl. Free) | Consumes CodeQL; app review is LLM | CodeQL rerun | Draft PR + admin policy | Free on public repos |
| **Cursor Bugbot** | PR review; `/review-security` (Jun 2026) | Yes (LLM) | No | PR comments | Add-on |
| **Google CodeMender / Jules** | Builds a PoC exploit in a customer sandbox, then patches (preview Jul 2026); Jules has a critic step | Yes | Exploit confirms finding; LLM judge checks patch | Manual approval before repo change | Gemini Enterprise; Jules free tier |
| **Semgrep Assistant / Multimodal** | Triage, Memories, autofix PRs | Hybrid | No | PR | In free tier |
| **Snyk Agent Fix** | Fixes with retrieved examples; engine filters | No | Engine rescan | IDE apply | Not on plans page |
| **Aikido AutoFix** | SAST/SCA/IaC/container fixes | No | No | PR | 10/month free |
| **CodeRabbit** | PR review + Deep Scan with verifier (Oct 2026) | Yes | No | Developer responsible | $24–72/user (3P); free for OSS |
| **Amazon Q / Kiro / AWS Security Agent** | IDE scanning; agent opens fix PRs | Yes | No | Expert review | Free tier |
| **DryRun** | Contextual PR review (logic/authz) | Yes | No | PR comments | Trial only (3P) |
| **Corgea** | AI SAST + autofix | Yes | Undocumented | PR | Free: 2 members, 10 repos, 10 fixes; $39/dev |
| **Pixee** | Fixes findings from 10+ scanners; evaluation agent; 76 % merge rate | No | Evaluator agent + your CI | PR; CI as gate | Per resolution; quote |
| **ZeroPath** | AI SAST with exploitability reasoning | Yes | "Verification records", method undescribed | PR | $1,000/month + $60/dev |
| **Mobb** | Fixes third-party findings; Vibe Shield MCP | Mostly no | Undocumented | PR/IDE | Free for public repos; IDE $20/dev |

**Best in class (agentic): the Claude Security plugin.** The only agentic tool that runs locally, emits commit-stamped SARIF, verifies both findings and patches with independent agents, runs the project's tests against each patch, refuses to deliver a patch it cannot vouch for, and never auto-applies. Codex Security and CodeMender prove *findings* harder (sandbox exploits) but document no test gate on the *fix* and are cloud/enterprise-gated. Claude Security's own docs say its scans are nondeterministic and complement deterministic scanners — which is exactly the split Assay makes architectural.

## 4. Where nobody is

1. **Phone-first security auditing.** No Play or F-Droid app does vulnerability analysis of a repo or APK, with or without an agent. GitHub's Android agent (Sep 2026) runs in Codespaces behind a Copilot licence.
2. **Sovereign, no backend, AI optional.** Every agentic entrant needs a cloud model and usually a vendor backend; local tools (MobSF, Semgrep CE, Trivy) have no AI loop; only Ostorlab offers BYOK, inside a SaaS. Nobody combines on-device model + BYOK + zero telemetry.
3. **Exit-code proof gating with a new test.** The best anyone does: rerun the scanner (GitHub, Snyk, Checkmarx); run *existing* tests (Claude Security plugin); prove the *finding* with a PoC (Codex, CodeMender, Ostorlab). Nobody writes a fail-before/pass-after test as a hard gate before human approval.
4. **Deterministic-only findings by policy.** Every agentic entrant lets the LLM originate findings and admits nondeterminism. "Only scanners create findings" is unique and makes results reproducible.
5. **Vibe-coded Android apps.** Vibe-coding security tools (Replit Security Agent, VibeEval, Vercel Deepsec, Mobb Vibe Shield) target web stacks. Mobile vendors target enterprises at $499/app/month and up.
6. **Gitleaks + Semgrep + OSV + MobSF under one triage-and-fix loop, source to APK, for an indie.** Aikido comes closest and is SaaS with a capped free tier.

## 5. What Assay must match to be credible — with P0/P1 for v1.2

The build prompt (`05-ULTRACODE-PROMPT.md`, WP-A7) implements every **P0** below that earlier WPs have not already covered.

| Table stakes in 2026 | Who sets the bar | Assay status | Priority |
|---|---|---|---|
| SARIF 2.1.0 with CWE tags; report stamped with commit/APK hash | Claude Security, CodeQL, mobsfscan | Have SARIF + fingerprints; **add CWE taxonomy tags and the stamp to the ratified index** | **P0** |
| Secrets + deps + SAST + APK binary in one run | Semgrep, Aikido | Have (four scanners) via `assay audit` (WP-A1) | — |
| SCA prioritised by reachability or EPSS | Endor, Semgrep | **Add EPSS score to OSV results** (offline EPSS CSV pinned by hash; reachability is v2) | **P0** |
| Autofix as a reviewable diff, one finding per patch, applied by a person, with confidence + explanation | GitHub, Snyk, Claude Security | Candidate model has it; WP-A5/A6 deliver the diff + explanation | — |
| **Scanner rescan after each fix**, in addition to the proving test | GitHub, Snyk, Checkmarx | `prove.yml` already re-runs the originating scanner on both commits — **surface it in the proof view as "scanner: before ◆ / after ○"** | **P0** |
| Triage that remembers: persistent dismissals and false-positive memory | Semgrep Memories, Claude Security | **Add a `dismissals.json` in the internal bus keyed by fingerprint, with reason + actor; the ratified index carries `dismissed: true`** | **P0** |
| Diff and branch scans, not only full scans | all | `audit --since <sha>` filtering results by changed files for Semgrep/Gitleaks; OSV/MobSF always full | P1 |
| A path out: git-applyable patch, GitHub Action, SARIF upload to code scanning | all | Actions in WP-A2; **add `upload-sarif` step (optional, user-enabled) to `actions/audit.yml`** | **P0** |
| MCP server / agent integration | Claude Security, Checkmarx, Socket, Mobb, Endor | **`assay mcp` read-only server exposing index, findings, candidates, proof** — lets Fonebrew/Claude Code read Assay without a dashboard | P1 |
| History: open / fixed / reopened across scans | Claude Security (Oct 2026) | Internal bus has runs; **compute per-fingerprint lifecycle in `console-snapshot` and show counts (not a browser) on the Bench cell** | **P0** |
| Supply-chain hygiene for itself: pin scanner binaries by hash, never "latest" | the Trivy compromise | Have (tool-lock + digests) | — |
| Android depth comparable to MobSF: exported components, deep links, WebView JS bridges, intent handling, network security config | GitHub's Sep 2026 Android research | **Extend `LocalApkAudit.kt` with deep links, `addJavascriptInterface`/`setJavaScriptEnabled` detection, NSC parse, `debuggable`** — the offline quick-check must beat App Manager on security specifics | **P0** |

## 6. What Assay should not copy

- LLM-originated findings (competitors admit noise; GitHub's own Android research says an expert must review every result).
- Paid "fix validation" credits (Ostorlab) or per-resolution pricing (Pixee). Proof is free and mandatory.
- "An LLM judge said it's fine" as validation (CodeMender's patch check, Pixee's evaluator). Exit codes or nothing.
- Enterprise gating and sales-led pricing. The indie is the unserved buyer.
- DAST/pentest bundles needing disabled pinning and test accounts. Out of scope for v1.
- Auto-opening PRs on a schedule (Aikido's daily PRs). Keep the human moment explicit.
- Pulling scanners by mutable tag.
- Cloud dashboards as the core surface. A local report folder + SARIF + the Fonebrew hand-off keeps it sovereign.

## 7. Positioning sentence

*Assay is the only security auditor that runs from a phone, needs no vendor backend, lets only deterministic scanners say what is wrong, and will not let a fix reach a human's Approve until a test that failed on the old code passes on the new one.*

## Sources

Anthropic: code.claude.com/docs/en/claude-security.md · claude.com/resources/articles/claude-security-now-scans-continuously-and-tracks-fixes · cybersecuritynews.com/claude-security-public-beta · smartscope.blog (plans/pricing, 2026)
OpenAI: thehackernews.com/2026/03/openai-codex-security-scanned-12.html · smartscope.blog codex-security pricing (Oct 2026)
GitHub: github.blog/changelog/2026-10-15-agentic-autofix-for-code-scanning-alerts-general-availability · 2025-10-28 coding-agent validation · 2026-07-14 security reviews in Copilot app · github.com/security/plans · github.blog/security/how-we-found-24-android-vulnerabilities-using-our-open-source-ai-security-agent
Vendors: semgrep.dev/pricing · semgrep.dev/blog/2026 (multimodal, autofix beta) · snyk.io/plans · snyk.io/blog/snyk-agent-fix-agentic-architecture · aikido.dev/pricing · aikido.dev/attack/mobile-pentesting · help.aikido.dev autofix overview · sonarsource.com/pricing · endorlabs.com/pricing · socket.dev/pricing · checkmarx.com/checkmarx-one-developer-assist · aicodereview.cc/blog/veracode-pricing (3P) · aquasec.com/blog/trivy-supply-chain-attack-what-you-need-to-know · businesswire.com (GitLab 18.11, Apr 2026) · pulse.bot (GitLab 18.10)
Mobile: appsecsanta.com/mobsf · ostorlab.co/plans · blog.ostorlab.co/ostorlab-vs-mobsf · appknox.com/pricing · ethicalhacking.ai/pricing/nowsecure-pricing (3P) · oversecured.com/blog/best-android-security-tools-in-2026
Agentic: helpnetsecurity.com/2026/07/24/google-codemender-ai-agent-code-security · itpro.com (Jules) · cursor.com/changelog/bugbot-updates-june-2026 · coderabbit.ai/blog/one-million-security-scans-and-fixes · stackpick.net (CodeRabbit, DryRun, 3P) · aws.amazon.com/blogs/security (Kiro/Q) · corgea.com/pricing · pixee.ai/pricing · zeropath.com/faq · mobb.ai/pricing
Phone apps: f-droid.org (App Manager, ClassyShark3xodus, Exodus) · apkmirror.com (Apk Analyzer) · play.google.com (Acode, Android IDE, Code Studio)
Vibe-coding: lushbinary.com (Replit Security Agent) · vibe-eval.com/updates/lovable-security-report-may-2026
