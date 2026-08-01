# Graph Report - keyreuses  (2026-08-01)

## Corpus Check
- 18 files · ~12,908 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 224 nodes · 508 edges · 16 communities (14 shown, 2 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `48c8125d`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity
- AuditApi
- .run
- .fromPublicKey
- KeyAudit
- Sha3Test
- NodeApi
- Design
- Copy
- gradlew
- KeyUses — native Minima key re-use audit
- KeyUses — dapp → APK design map
- JSONArray

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 41 edges
2. `LocalKey` - 19 edges
3. `KeyAuditTest` - 18 edges
4. `AuditApi` - 17 edges
5. `Usage` - 16 edges
6. `KeyAudit` - 11 edges
7. `Reuse` - 11 edges
8. `MinimaAddress` - 11 edges
9. `NodeApi` - 11 edges
10. `Design` - 9 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Import Cycles
- None detected.

## Communities (16 total, 2 thin omitted)

### Community 0 - "MainActivity"
Cohesion: 0.14
Nodes (14): MainActivity, AppCompatActivity, AuditApi, Bundle, JSONArray, LayoutParams, LinearLayout, NodeApi (+6 more)

### Community 1 - "AuditApi"
Cohesion: 0.18
Nodes (6): Activity, AuditApi, Cb, Handler, JSONArray, JSONObject

### Community 2 - ".run"
Cohesion: 0.28
Nodes (5): LocalKey, Reuse, Usage, Test, KeyAuditTest

### Community 3 - ".fromPublicKey"
Cohesion: 0.18
Nodes (5): Charset, MinimaAddress, Result, Test, MinimaAddressTest

### Community 4 - "KeyAudit"
Cohesion: 0.16
Nodes (12): KeyAudit, Result, Row, Status, AT_RISK, OK, REUSED, Verdict (+4 more)

### Community 5 - "Sha3Test"
Cohesion: 0.20
Nodes (4): Sha3, Charset, Test, Sha3Test

### Community 6 - "NodeApi"
Cohesion: 0.21
Nodes (7): Cb, Context, Handler, JSONObject, NodeApi, PairingListener, MinimaAPI

### Community 7 - "Design"
Cohesion: 0.36
Nodes (3): Design, Context, GradientDrawable

### Community 9 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 13 - "KeyUses — native Minima key re-use audit"
Cohesion: 0.14
Nodes (13): Architecture, Build, Files, First run, IPC safety, KeyUses — native Minima key re-use audit, Regenerating address goldens, Scope (+5 more)

### Community 14 - "KeyUses — dapp → APK design map"
Cohesion: 0.25
Nodes (7): 1. Tokens (`index.html:12-16` → `Design.java`), 2. Screen order, 3. Verdict states (`renderVerdict`, `index.html:264-327` → `KeyAudit.Verdict`), 4. Table, 5. Copy, 6. Deliberate differences, KeyUses — dapp → APK design map

## Knowledge Gaps
- **23 isolated node(s):** `1. Tokens (`index.html:12-16` → `Design.java`)`, `2. Screen order`, `3. Verdict states (`renderVerdict`, `index.html:264-327` → `KeyAudit.Verdict`)`, `4. Table`, `5. Copy` (+18 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Usage` connect `.run` to `AuditApi`, `KeyAudit`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **Why does `Reuse` connect `.run` to `AuditApi`, `KeyAudit`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **What connects `1. Tokens (`index.html:12-16` → `Design.java`)`, `2. Screen order`, `3. Verdict states (`renderVerdict`, `index.html:264-327` → `KeyAudit.Verdict`)` to the rest of the system?**
  _23 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.1445578231292517 - nodes in this community are weakly interconnected._
- **Should `KeyUses — native Minima key re-use audit` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._