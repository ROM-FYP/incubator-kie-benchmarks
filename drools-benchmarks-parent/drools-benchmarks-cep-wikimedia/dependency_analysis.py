#!/usr/bin/env python3
"""Dependency analysis for Wikimedia CEP clusters from Infomap ftree."""

clusters = {
    "C1_Minor": [
        "Minor_Accept", "Minor_Track", "Minor_Complete",
        "Minor_Classify", "Minor_Validate_Standard", "Minor_Detect",
    ],
    "C2_Bot": [
        "Bot_Categorize_Registered", "Bot_HealthCheck_Registered_RateLimitExceeded",
        "Bot_Detect", "Bot_HealthCheck_Registered_Healthy",
        "Bot_Metrics", "Bot_Report", "Bot_Complete",
        "Bot_Categorize_Unverified", "Bot_HealthCheck_Unverified_NewBot",
        "Bot_HealthCheck_Unverified_Review",
    ],
    "C3_Content_Vandalism": [
        "Content_Approve_NewArticle", "Content_Review_Standard",
        "Content_Detect", "Content_Review_Major_New",
        "Content_Approve_Standard", "Content_Approve_Major",
        "Content_Cache", "Content_Index",
        "Content_Standard_Complete", "Content_Major_Log",
        "Vandalism_Analyze_MassiveDeletion_NewUser", "Vandalism_Detect",
        "Vandalism_Analyze_ModerateDeletion_NewUser", "Vandalism_Verify_LowRisk",
        "Vandalism_Analyze_LargeDeletion_NewUser", "InitializeUserActivity",
        "Vandalism_NoFlag_LowSeverity", "Vandalism_Analyze_ModerateDeletion_RegularUser",
        "Vandalism_Analyze_LargeDeletion_RegularUser",
        "Vandalism_Verify_MediumRisk_OtherArticles",
        "Vandalism_Complete", "Vandalism_Verify_HighRisk_NoArticleData",
        "Vandalism_Flag_HighSeverity_Standard_FirstTime",
        "Vandalism_Logged_Complete",
        "Vandalism_Flag_HighSeverity_Standard_RepeatOffender",
        "Vandalism_Analyze_MassiveDeletion_RegularUser",
        "Vandalism_Verify_HighRisk_StandardArticle",
        "CorrelateHighRiskUser",
    ],
    "C4_Discussion": [
        "Discussion_Analyze", "Discussion_Sentiment", "Discussion_Detect",
        "Discussion_Notify", "Discussion_Route", "Discussion_Complete",
    ],
}

rule_io = {
    "Minor_Detect":             ({"WikiEvent"}, {"MinorEdit"}),
    "Minor_Classify":           ({"MinorEdit"}, {"MinorClassified"}),
    "Minor_Validate_Trusted":   ({"MinorClassified", "UserActivity"}, {"MinorValidated"}),
    "Minor_Validate_Standard":  ({"MinorClassified", "MinorValidated"}, {"MinorValidated"}),
    "Minor_Accept":             ({"MinorValidated"}, {"MinorAccepted"}),
    "Minor_Track":              ({"MinorAccepted"}, {"MinorTracked"}),
    "Minor_Complete":           ({"MinorTracked"}, set()),
    "Bot_Detect":               ({"WikiEvent"}, {"BotActivity"}),
    "Bot_Categorize_Registered":({"BotActivity", "BotProfile"}, {"BotProfile"}),
    "Bot_Categorize_Unverified":({"BotActivity", "BotProfile"}, {"BotProfile"}),
    "Bot_HealthCheck_Registered_RateLimitExceeded": ({"BotProfile", "BotHealthCheck"}, {"BotHealthCheck"}),
    "Bot_HealthCheck_Registered_Healthy":           ({"BotProfile", "BotHealthCheck"}, {"BotHealthCheck"}),
    "Bot_HealthCheck_Unverified_NewBot":            ({"BotProfile", "BotHealthCheck"}, {"BotHealthCheck"}),
    "Bot_HealthCheck_Unverified_Review":            ({"BotProfile", "BotHealthCheck"}, {"BotHealthCheck"}),
    "Bot_Metrics":              ({"BotHealthCheck", "BotMetrics"}, {"BotMetrics"}),
    "Bot_Report":               ({"BotMetrics", "BotReported"}, {"BotReported"}),
    "Bot_Complete":             ({"BotReported"}, set()),
    "Content_Detect":           ({"WikiEvent"}, {"ContentAddition"}),
    "Content_Review_Major_Experienced": ({"ContentAddition", "UserActivity"}, {"ContentReview"}),
    "Content_Review_Major_New": ({"ContentAddition", "UserActivity"}, {"ContentReview"}),
    "Content_Review_Standard":  ({"ContentAddition"}, {"ContentReview"}),
    "Content_Approve_Major":    ({"ContentReview", "ArticleQuality"}, {"ContentApproved"}),
    "Content_Approve_NewArticle":({"ContentReview", "ArticleQuality"}, {"ContentApproved", "ArticleQuality"}),
    "Content_Approve_Standard": ({"ContentReview", "ArticleQuality"}, {"ContentApproved"}),
    "Content_Index":            ({"ContentApproved"}, {"ContentIndexed"}),
    "Content_Cache":            ({"ContentIndexed"}, {"ContentCached"}),
    "Content_Major_Log":        ({"ContentCached"}, set()),
    "Content_Standard_Complete":({"ContentCached"}, set()),
    "Vandalism_Detect":         ({"WikiEvent"}, {"VandalismCandidate"}),
    "InitializeUserActivity":   ({"WikiEvent", "UserActivity"}, {"UserActivity"}),
    "Vandalism_Analyze_MassiveDeletion_NewUser":     ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_MassiveDeletion_TrustedUser": ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_MassiveDeletion_RegularUser": ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_LargeDeletion_NewUser":       ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_LargeDeletion_TrustedUser":   ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_LargeDeletion_RegularUser":   ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_ModerateDeletion_NewUser":     ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_ModerateDeletion_TrustedUser": ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Analyze_ModerateDeletion_RegularUser": ({"VandalismCandidate", "UserActivity"}, {"VandalismAnalysis"}),
    "Vandalism_Verify_HighRisk_HighQualityArticle":  ({"VandalismAnalysis", "ArticleQuality"}, {"VandalismVerified"}),
    "Vandalism_Verify_HighRisk_LowQualityArticle":   ({"VandalismAnalysis", "ArticleQuality"}, {"VandalismVerified"}),
    "Vandalism_Verify_HighRisk_StandardArticle":     ({"VandalismAnalysis", "ArticleQuality"}, {"VandalismVerified"}),
    "Vandalism_Verify_HighRisk_NoArticleData":       ({"VandalismAnalysis", "ArticleQuality"}, {"VandalismVerified"}),
    "Vandalism_Verify_MediumRisk_HighQualityArticle":({"VandalismAnalysis", "ArticleQuality"}, {"VandalismVerified"}),
    "Vandalism_Verify_MediumRisk_OtherArticles":     ({"VandalismAnalysis", "ArticleQuality"}, {"VandalismVerified"}),
    "Vandalism_Verify_LowRisk":  ({"VandalismAnalysis"}, {"VandalismVerified"}),
    "Vandalism_Flag_HighSeverity_HighValue_RepeatOffender": ({"VandalismVerified", "EditPattern"}, {"VandalismFlagged"}),
    "Vandalism_Flag_HighSeverity_HighValue_FirstTime":      ({"VandalismVerified", "EditPattern"}, {"VandalismFlagged", "EditPattern"}),
    "Vandalism_Flag_HighSeverity_Standard_RepeatOffender":  ({"VandalismVerified", "EditPattern"}, {"VandalismFlagged"}),
    "Vandalism_Flag_HighSeverity_Standard_FirstTime":       ({"VandalismVerified", "EditPattern"}, {"VandalismFlagged", "EditPattern"}),
    "Vandalism_Flag_MediumSeverity_HighValue":              ({"VandalismVerified"}, {"VandalismFlagged"}),
    "Vandalism_NoFlag_LowSeverity":  ({"VandalismVerified"}, set()),
    "Vandalism_Complete":        ({"VandalismFlagged"}, {"VandalismLogged"}),
    "Vandalism_Logged_Complete": ({"VandalismLogged"}, set()),
    "Discussion_Detect":         ({"WikiEvent"}, {"DiscussionPost"}),
    "Discussion_Analyze":        ({"DiscussionPost"}, {"DiscussionAnalyzed"}),
    "Discussion_Sentiment":      ({"DiscussionAnalyzed"}, {"DiscussionSentiment"}),
    "Discussion_Route":          ({"DiscussionSentiment"}, {"DiscussionRouted"}),
    "Discussion_Notify":         ({"DiscussionRouted"}, {"DiscussionNotified"}),
    "Discussion_Complete":       ({"DiscussionNotified"}, set()),
    "CorrelateHighRiskUser":     ({"VandalismAnalysis", "BotHealthCheck"}, {"HighRiskUser"}),
    "CorrelateArticleAttack":    ({"VandalismCandidate", "ArticleUnderAttack"}, {"ArticleUnderAttack"}),
    "BoostVandalismPriorityForAttackedArticles": ({"VandalismFlagged", "ArticleUnderAttack"}, set()),
}

all_ftree_rules = set()
for rules in clusters.values():
    all_ftree_rules.update(rules)
all_drl_rules = set(rule_io.keys())
missing_from_ftree = all_drl_rules - all_ftree_rules

print("=" * 60)
print("  RULES NOT IN FTREE (causal dead-ends)")
print("=" * 60)
for r in sorted(missing_from_ftree):
    reads, writes = rule_io[r]
    print(f"  {r}: reads={reads}, writes={writes}")

print("\n" + "=" * 60)
print("  CLUSTER FACT I/O")
print("=" * 60)
for cname, rules in clusters.items():
    reads_all, writes_all = set(), set()
    for rule in rules:
        if rule in rule_io:
            r, w = rule_io[rule]
            reads_all.update(r)
            writes_all.update(w)
    external = reads_all - writes_all - {"WikiEvent"}
    print(f"\n  {cname} ({len(rules)} rules):")
    print(f"    Reads:  {sorted(reads_all)}")
    print(f"    Writes: {sorted(writes_all)}")
    if external:
        print(f"    !! EXTERNAL DEPS: {sorted(external)}")
    else:
        print(f"    OK Self-contained (+ WikiEvent)")

print("\n" + "=" * 60)
print("  CROSS-CLUSTER DEPENDENCIES")
print("=" * 60)
cnames = list(clusters.keys())
found_any = False
for i, c1 in enumerate(cnames):
    w1 = set()
    for rule in clusters[c1]:
        if rule in rule_io:
            _, w = rule_io[rule]
            w1.update(w)
    for j, c2 in enumerate(cnames):
        if i == j: continue
        r2 = set()
        for rule in clusters[c2]:
            if rule in rule_io:
                r, _ = rule_io[rule]
                r2.update(r)
        shared = w1 & r2
        if shared:
            print(f"  {c1} -> {c2} via {sorted(shared)}")
            found_any = True
if not found_any:
    print("  NONE! All clusters are independent.")
