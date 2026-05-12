# Semantic Overview: Wikimedia Content Moderation Ruleset

The `wikimedia_content_moderation.drl` ruleset simulates a **real-time content moderation system** for Wikipedia. Each pipeline addresses a distinct moderation concern.

---

## 🛡️ 1. Vandalism Detection Pipeline

**Trigger:** `sizeDelta < -100` (large content deletions)

| Stage | Semantic Purpose |
|-------|------------------|
| `Vandalism_Detect` | Identify edits that remove >100 bytes – potential vandalism indicator |
| `Vandalism_Analyze` | Calculate risk score based on deletion magnitude (>500 bytes = 90, >200 = 60, else 30) |
| `Vandalism_Verify` | Classify severity: HIGH (>70 risk), MEDIUM (>40), or LOW |
| `Vandalism_Flag` | Mark HIGH severity cases for human moderator review |
| `Vandalism_Log` | Record flagged incidents for audit trail and pattern analysis |
| `Vandalism_Complete` | Cleanup working memory |

**Real-world use:** Wikipedia uses similar heuristics to detect blanking attacks, spam removal wars, and edit warring.

---

## 🤖 2. Bot Activity Monitoring Pipeline

**Trigger:** `bot == true` (automated edits)

| Stage | Semantic Purpose |
|-------|------------------|
| `Bot_Detect` | Identify bot-originated edits from the event stream |
| `Bot_Categorize` | Classify as REGISTERED (official bots with "Bot" in name) or UNVERIFIED |
| `Bot_HealthCheck` | Verify bot is operating within expected norms |
| `Bot_Metrics` | Update activity counters for rate limiting and quotas |
| `Bot_Report` | Generate activity summary for bot operators |
| `Bot_Complete` | Cleanup working memory |

**Real-world use:** Wikipedia has 2,500+ approved bots handling anti-vandalism, link fixes, and categorization. Monitoring prevents runaway bots.

---

## 📈 3. Content Growth Pipeline

**Trigger:** `sizeDelta > 200` AND `bot == false` (significant human additions)

| Stage | Semantic Purpose |
|-------|------------------|
| `Content_Detect` | Identify substantial content contributions |
| `Content_Review` | Tier assignment: MAJOR (>1000 bytes), SUBSTANTIAL (>500), NOTABLE (>200) |
| `Content_Approve` | Auto-approve non-bot contributions for processing |
| `Content_Index` | Mark article for search index refresh |
| `Content_Cache` | Invalidate CDN cache for fresh content delivery |
| `Content_Complete` | Cleanup working memory |

**Real-world use:** New content triggers re-indexing, cache invalidation, and feeds "Recent Changes" watchlists.

---

## ✏️ 4. Minor Edits Pipeline

**Trigger:** `-50 <= sizeDelta <= 50` AND `bot == false` (small human edits)

| Stage | Semantic Purpose |
|-------|------------------|
| `Minor_Detect` | Identify small corrections, typos, formatting fixes |
| `Minor_Classify` | Categorize: FORMATTING (0 bytes), ADDITION (+), CORRECTION (-) |
| `Minor_Validate` | Quick validation – minor edits assumed good faith |
| `Minor_Accept` | Accept into article revision history |
| `Minor_Track` | Update editor contribution statistics for barn stars/awards |
| `Minor_Complete` | Cleanup working memory |

**Real-world use:** Minor edits are often hidden in watchlists by default since they're low-risk. Tracking helps identify dedicated editors.

---

## 💬 5. Discussion Pipeline

**Trigger:** `title matches "Talk:.*"` (talk page edits)

| Stage | Semantic Purpose |
|-------|------------------|
| `Discussion_Detect` | Identify talk page activity |
| `Discussion_Analyze` | Extract article topic from title |
| `Discussion_Sentiment` | Placeholder for NLP sentiment analysis |
| `Discussion_Route` | Route to appropriate moderation queue if needed |
| `Discussion_Notify` | Alert article watchers of discussion activity |
| `Discussion_Complete` | Cleanup working memory |

**Real-world use:** Talk pages are where edit disputes, policy discussions, and collaboration happen. Monitoring helps with conflict resolution.

---

## Architecture Summary

```
                        WikiEvent Stream
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
    ┌──────────┐        ┌──────────┐        ┌──────────┐
    │ Vandalism│        │   Bot    │        │ Content  │
    │ Pipeline │        │ Pipeline │        │ Pipeline │
    │ (delete) │        │ (auto)   │        │ (grow)   │
    └──────────┘        └──────────┘        └──────────┘
          │                   │                   │
          ▼                   ▼                   ▼
    ┌──────────┐        ┌──────────┐
    │  Minor   │        │Discussion│
    │ Pipeline │        │ Pipeline │
    │ (small)  │        │ (talk)   │
    └──────────┘        └──────────┘
```

Each event flows through **exactly one pipeline** based on its characteristics, ensuring clean cluster separation for process mining analysis.

---

## Related Files

- **Ruleset:** [wikimedia_content_moderation.drl](file:///d:/Projects/Benchmarks/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-cep-wikimedia/src/main/resources/rules/wikimedia_content_moderation.drl)
- **Runner:** [ContentModerationRunner.java](file:///d:/Projects/Benchmarks/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-cep-wikimedia/src/main/java/org/kie/benchmark/cep/wikimedia/ContentModerationRunner.java)
- **Clusters:** [clusters.csv](file:///d:/Projects/Benchmarks/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-cep-wikimedia/src/main/resources/clusters/clusters.csv)
