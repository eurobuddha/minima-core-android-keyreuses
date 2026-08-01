# KeyUses — native Minima key re-use audit

`com.eurobuddha.keyreuses` · slug `minima-keyuses` · companion APK for the Minima Core node app.

Native Android port of the KeyUses MiniDapp (`mds/keyuses/minidapp/index.html` v0.1.51).

## What it does

Minima signatures are **stateful**. Each of a node's 64 keys is a tree of 262,144 *one-time*
(Winternitz) leaves, and the node tracks consumption with a per-key `uses` counter. Signing twice
with the same leaf leaks the secret — progressively, roughly exponentially with each reuse.

That almost always comes from operator error: two nodes on one seed, a re-sync with `keyuses` set
below the key's true prior usage, or a wallet rollback past signatures already broadcast.

KeyUses reads your node's keys, works out what each key has *actually* signed on-chain, and compares
the two. It reports:

- **AT RISK** (heuristic) — the chain shows more signatures than your node's counter, so the node is
  re-issuing leaves it already spent.
- **RE-USED ×N** (definitive) — the address appears in the witness-confirmed reuse database, where
  one leaf public key was seen signing more than one distinct transaction.
- the safe `keyuses` value to pass on your next seed re-sync.

The two signals are deliberately kept apart: the heuristic is derived from spend-blocks, which
under-counts (two transactions from one key in the same block collapse to one), so it errs toward
false negatives. Treat a key sitting exactly at `on-chain == node-uses` as *verify*, not clear.

Only each key's **default** address is audited. Uses via custom-script or multisig addresses
controlled by the same key are not counted.

## Architecture

Companion APK — it does **not** embed a node. Java + classic Views, talking to the Minima Core node
app (`org.minimarex.minimacore`) over broadcast-Intent IPC via `app/libs/minimaapi.aar`.

```
keys action:list  ──►  node (IPC)      64 x {publickey, uses, maxuses}
        │
        ├─ MinimaAddress.fromPublicKey()  →  0x + Mx address, on-device
        │
        ├─ GET /keyaudit?keys=…  ┐  in parallel (possible because we derive our own addresses)
        └─ GET /reuse?addrs=…    ┘
                     │
                     └─ KeyAudit.join()  →  rows + verdict + recommended keyuses
```

### Files

| File | Role |
|---|---|
| `NodeApi.java` | IPC wrapper, copied verbatim from `apks/utxo` (package line only) |
| `Sha3.java` | FIPS-202 SHA3-256, self-contained |
| `MinimaAddress.java` | public key → default address (`0x` + `Mx`) |
| `AuditApi.java` | bounded HTTPS client for the two audit endpoints |
| `KeyAudit.java` | the join + verdict engine — pure Java, fully unit-tested |
| `Copy.java` | user-facing text, transcribed verbatim from the dapp |
| `Design.java` | design tokens from the dapp |
| `MainActivity.java` | the single screen, built programmatically |

### Why the network call

The reuse check needs a scan of the chain **archive**, which cannot run on a phone: the archive is
millions of blocks and the `coins` convenience table is unreliable. The spend index therefore lives
on a server (`https://eurobuddha.com/keyaudit` + `/reuse`, see `mds/keyuses/backend/`).

The app sends your 64 **public** keys and their derived addresses. Public keys cannot move funds,
but they do reveal your addresses to that server. This is disclosed in the app, as in the dapp.

### Why addresses are derived on-device

`keys action:list` returns only `size, depth, uses, maxuses, modifier, publickey` — no address (see
`core/minima-core/src/org/minima/database/wallet/KeyRow.java:69-81`). Deriving locally means the app
can show addresses without trusting the server, and lets both HTTP calls run in parallel instead of
chaining the second behind the first as the dapp must.

Every run also **cross-checks** the derivation: `/keyaudit` echoes the server's own address for each
key, and any disagreement raises a red banner. A silent mismatch would mean the `/reuse` lookup
asked about the wrong address and got back a reassuring "not reused".

### Screenshots

This screen is **not** `FLAG_SECURE`. It shows public keys and addresses only — nothing that can
move funds — and the audit already transmits exactly that data. Capturing and sharing a verdict (to
an exchange, to support, in a disclosure) is a first-class use of the app. `FLAG_SECURE` belongs in
the node app, which renders your seed phrase.

### IPC safety

`keys action:list` is the app's entire node surface: one READ command, 64 rows, roughly 13 KB — far
under the app-side 256 KB `MAX_MESSAGE_LEN` and the ~1 MB Android Binder ceiling. **No paging is
needed and none is implemented**; the adaptive-halving machinery from `apks/history` would be dead
weight here.

The family's transaction rules (`txndelete` on every error path, `istransaction:false` is async
mining not failure) do not apply: **this app builds no transactions**. Their absence is intentional,
not an oversight.

## Build

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the debug keystore, matching the node app. IPC is authorised by the
`MINIMA_ID` token rather than the signature, so this is sufficient for sideloading.

## Test

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test
```

28 JVM tests, no device or node required: FIPS-202 known-answer vectors, address goldens, and the
full verdict table.

### Regenerating address goldens

`MinimaAddressTest` pins the derivation against `mds/keyuses/phrase/derive.js` — the validated JS
port of minima-core's own derivation:

```sh
cd /Users/eurobuddha/Projects/minima/mds/keyuses/phrase
node -e '
const lib = require("./sha3.js");
global.sha3_256 = lib.sha3_256 || lib;       // derive.js looks for a GLOBAL sha3_256 first
const d = require("./derive.js");
d.setSha3(global.sha3_256);
const pk = "0x3A985DA74FE225B2045C172D6BD390BD855F086E3E9D525B46BFE24511431532";
console.log(d._addressFromPublicKey(d._fromHex(pk.slice(2))));
'
```

Independently confirmed against the live backend, which derives with real minima-core Java:

```sh
curl -s "https://eurobuddha.com/keyaudit?keys=0x3A985DA74FE225B2045C172D6BD390BD855F086E3E9D525B46BFE24511431532"
```

Both agree with the goldens in the test.

## First run

1. Install and start **Minima Core**.
2. Install this APK and open it. It will show a banner until you enable it.
3. Minima Core → Apps → enable **KeyUses**.
4. Back in KeyUses, tap **Run Audit**.

## Scope

v0.1.0 ports the node-audit dapp only. Not included: the `KeyUses Phrase` variant (seed-phrase entry
with on-device WOTS/MMR derivation), the "check any address" screen, and the paste-your-own-scan
archive mode.
