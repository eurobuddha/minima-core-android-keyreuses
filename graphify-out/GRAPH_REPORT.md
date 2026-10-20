# Graph Report - apks/keyreuses  (2026-09-04)

## Corpus Check
- 20 files · ~13,279 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 226 nodes · 549 edges · 17 communities (14 shown, 3 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `2cde396e`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- AuditApi
- org.junit.Test
- .fromPublicKey
- KeyAudit
- Sha3Test
- .card
- Copy
- gradlew
- KeyUses — native Minima key re-use audit
- KeyUses — dapp → APK design map
- User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly.
- install.sh
- pre-commit

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 41 edges
2. `AuditApi` - 19 edges
3. `LocalKey` - 19 edges
4. `KeyAuditTest` - 18 edges
5. `Usage` - 16 edges
6. `NodeApi` - 13 edges
7. `KeyAudit` - 11 edges
8. `Reuse` - 11 edges
9. `MinimaAddress` - 11 edges
10. `Design` - 9 edges

## Surprising Connections (you probably didn't know these)
- `MainActivity` --references--> `AuditApi`  [EXTRACTED]
  apks/keyreuses/app/src/main/java/com/eurobuddha/keyreuses/MainActivity.java → apks/keyreuses/app/src/main/java/com/eurobuddha/keyreuses/AuditApi.java
- `MainActivity` --references--> `NodeApi`  [EXTRACTED]
  apks/keyreuses/app/src/main/java/com/eurobuddha/keyreuses/MainActivity.java → apks/keyreuses/app/src/main/java/com/eurobuddha/keyreuses/NodeApi.java

## Import Cycles
- None detected.

## Communities (17 total, 3 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.18
Nodes (11): android.os.Bundle, android.view.View, android.widget.LinearLayout, android.widget.ProgressBar, android.widget.TextView, androidx.appcompat.app.AppCompatActivity, MainActivity, LayoutParams (+3 more)

### Community 1 - "AuditApi"
Cohesion: 0.10
Nodes (11): android.app.Activity, android.os.Handler, AuditApi, Cb, JSONObject, Cb, NodeApi, org.json.JSONArray (+3 more)

### Community 2 - "org.junit.Test"
Cohesion: 0.28
Nodes (5): LocalKey, Reuse, Usage, KeyAuditTest, org.junit.Test

### Community 3 - ".fromPublicKey"
Cohesion: 0.18
Nodes (3): MinimaAddress, Result, MinimaAddressTest

### Community 4 - "KeyAudit"
Cohesion: 0.16
Nodes (12): KeyAudit, Result, Row, Status, AT_RISK, OK, REUSED, Verdict (+4 more)

### Community 5 - "Sha3Test"
Cohesion: 0.16
Nodes (3): Sha3, Sha3Test, java.nio.charset.Charset

### Community 7 - ".card"
Cohesion: 0.26
Nodes (6): android.content.Context, android.graphics.drawable.GradientDrawable, Design, PairingListener, GradientDrawable, MinimaAPI

### Community 9 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 13 - "KeyUses — native Minima key re-use audit"
Cohesion: 0.14
Nodes (13): Architecture, Build, Files, First run, IPC safety, KeyUses — native Minima key re-use audit, Regenerating address goldens, Scope (+5 more)

### Community 14 - "KeyUses — dapp → APK design map"
Cohesion: 0.25
Nodes (7): 1. Tokens (`index.html:12-16` → `Design.java`), 2. Screen order, 3. Verdict states (`renderVerdict`, `index.html:264-327` → `KeyAudit.Verdict`), 4. Table, 5. Copy, 6. Deliberate differences, KeyUses — dapp → APK design map

### Community 16 - "User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly."
Cohesion: 0.50
Nodes (3): RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions., User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly., Versioning guardrail — every code change ships with a version bump

## Knowledge Gaps
- **26 isolated node(s):** `install.sh script`, `OK`, `RISK_HEURISTIC`, `REUSE_WARN`, `REUSE_RISK` (+21 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `AuditApi`?**
  _High betweenness centrality (0.256) - this node is a cross-community bridge._
- **Why does `Result` connect `KeyAudit` to `MainActivity`, `org.junit.Test`?**
  _High betweenness centrality (0.108) - this node is a cross-community bridge._
- **Why does `AuditApi` connect `AuditApi` to `MainActivity`?**
  _High betweenness centrality (0.105) - this node is a cross-community bridge._
- **What connects `install.sh script`, `OK`, `RISK_HEURISTIC` to the rest of the system?**
  _26 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `AuditApi` be split into smaller, more focused modules?**
  _Cohesion score 0.0953058321479374 - nodes in this community are weakly interconnected._
- **Should `KeyUses — native Minima key re-use audit` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._