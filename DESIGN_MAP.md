# KeyUses — dapp → APK design map

Source of truth: `mds/keyuses/minidapp/index.html` **v0.1.51**. The dapp is a single vertically
scrolling column, so this map is short — but every token, string and state below exists in both.

---

## 1. Tokens (`index.html:12-16` → `Design.java`)

These are **not** the utxo/history palette; `ok`/`warn`/`risk` differ. The dapp's translucent
`--card` / `--border` are flattened to their opaque equivalents over `#0A0A0F`.

| Dapp | Value | `Design` |
|---|---|---|
| `--bg` | `#0a0a0f` | `BG` |
| `--card` | `rgba(255,255,255,0.03)` | `CARD` `#15151F` |
| `--border` | `rgba(255,255,255,0.07)` | `BORDER` `#2A2A38` |
| `--text` | `#e8e6e3` | `TEXT` |
| `--text-dim` | `rgba(232,230,227,0.55)` | `DIM` `#8D8B89` |
| `--accent` | `#f7931a` | `ACCENT` |
| gradient end | `#ff5e3a` | `ACCENT_2` |
| `--ok` | `#4ade80` | `OK` |
| `--warn` | `#fbbf24` | `WARN` |
| `--risk` | `#f87171` | `RISK` |

Radii: card 16, button/inset 10, verdict 14, chip pill 20, badge 4 — same numbers, as dp.
Font: the dapp uses Manrope; the APK uses the platform sans, with `Typeface.MONOSPACE` for
addresses and the recommendation command, matching the dapp's monospace usage.

## 2. Screen order

| # | Dapp | APK |
|---|---|---|
| 1 | `.badge` + `h1` + `.subtitle` | `badge()` / `title()` / `subtitle()` |
| 2 | — | **pairing banner** (`pairingBanner()`) — new; the dapp runs inside the node so it never needs one |
| 3 | `#controls` card | `controlsCard()` — Run Audit, privacy note, status box. **No mode switcher**: hosted only. |
| 4 | `#verdict` | `verdictCard()` |
| 5 | results table + `#coverage` | `tableCard()` |
| 6 | explainer card | `explainerCard()` |
| 7 | `.footer` | `footer()` |

## 3. Verdict states (`renderVerdict`, `index.html:264-327` → `KeyAudit.Verdict`)

| Condition | Verdict | Colour | Extra blocks |
|---|---|---|---|
| `anyReuse && worst > 3` | `REUSE_RISK` | risk | "why this happened" list — **no** reco box |
| `anyReuse && worst <= 3` | `REUSE_WARN` | warn | "why this happened" list — **no** reco box |
| `anyRisk` | `RISK_HEURISTIC` | risk | "how this happens" + "do this now" + reco box |
| else | `OK` | ok | reco box |

The missing reco box on the two confirmed-reuse states is deliberate (`index.html:288`, `:297`):
the advice there is *migrate*, not *resync better*.

Reco box content: `archive action:import file:<your-backup> phrase:"<your 24 words>" keyuses:N`
where `N = max over keys of max(sigs, local) + 256` (`KEYUSES_MARGIN`). Tap to copy.

## 4. Table

Columns and alignment are the dapp's: `Address (tap to copy)` left, the rest right.

| Column | Width | Source |
|---|---|---|
| Address | 190dp, mono, wraps | locally derived Mx, `#N` prefix above it |
| Node uses | 66dp | `keys action:list` → `uses` |
| On-chain signatures | 82dp | `/keyaudit` → `spend_blocks` |
| Spent coins | 66dp | `/keyaudit` → `spent_coins` |
| Status | 90dp | chip |

Total exceeds a phone's width, so the table sits in a `HorizontalScrollView` — the same behaviour as
the dapp's `.tablewrap { overflow-x: auto }`.

Chip (`renderRows`, `:332-335`): `RE-USED ×N` amber when `N <= 3` / red when `N > 3`; `AT RISK` red;
`OK` green.

Tap an address → clipboard + the address flashes accent for 1300 ms (the dapp's `copied ✓` timing).

## 5. Copy

Every user-facing string is transcribed verbatim into `Copy.java`, with the dapp's `<strong>`/`<em>`
emphasis preserved as `<b>`/`<i>` and rendered via `HtmlCompat`. Diff `Copy.java` against
`index.html` to verify.

## 6. Deliberate differences

| Change | Why |
|---|---|
| No "My own archive" mode | Pasting CoinScanner JSON is a desktop workflow; unusable on a phone |
| No mode switcher | Follows from the above — hosted only |
| Addresses derived on-device | `keys action:list` returns no address; also lets the two HTTP calls run in parallel |
| Requests chunked at 64 | The dapp doesn't chunk and breaks on a node with >256 keys (`KeyUsesServer.java:46`); URL length binds before the server cap does |
| **Derivation cross-check** banner | `/keyaudit` echoes the server's address; a silent mismatch would mean we queried the wrong address and got a reassuring answer |
| **Exhaustion** banner | `maxuses` is returned by the node and ignored by the dapp; on exhaustion minima-core resets `uses` to 0 and keeps signing (`security-review.md`, `CORE-VM-2`) — guaranteed re-use |
| Pairing banner | The dapp runs inside the node; a companion APK must be enabled first |
| `FLAG_SECURE`, `allowBackup=false` | The screen shows the node's full public-key and address set |
| Coverage note adds the default-address caveat | Stated plainly in `KeyAudit.java:36-38` but never surfaced in the dapp's UI |
