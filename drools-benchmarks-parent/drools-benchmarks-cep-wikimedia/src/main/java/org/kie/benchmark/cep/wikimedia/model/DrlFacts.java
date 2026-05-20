package org.kie.benchmark.cep.wikimedia.model;

import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Timestamp;
import java.io.Serializable;

/**
 * Consolidated collection of fact models used in the Wikimedia benchmark.
 * Annotated with Drools CEP metadata to ensure consistency across sessions.
 */
public class DrlFacts {

    @Role(Role.Type.EVENT)
    @Expires("24h")  // state fact — must survive between a user's edits (avg gap ~11 min)
    public static class UserActivity implements Serializable {
        public String user;
        public int editCount;
        public String lastEditType;
        public UserActivity() {}
        public UserActivity(String user, int editCount, String lastEditType) {
            this.user = user; this.editCount = editCount; this.lastEditType = lastEditType;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getEditCount() { return editCount; }
        public void setEditCount(int editCount) { this.editCount = editCount; }
        public String getLastEditType() { return lastEditType; }
        public void setLastEditType(String lastEditType) { this.lastEditType = lastEditType; }
    }

    @Role(Role.Type.EVENT)
    @Expires("24h")  // state fact — article quality persists across edits
    public static class ArticleQuality implements Serializable {
        public String title;
        public int qualityScore;
        public int editCount;
        public ArticleQuality() {}
        public ArticleQuality(String title, int qualityScore, int editCount) {
            this.title = title; this.qualityScore = qualityScore; this.editCount = editCount;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getQualityScore() { return qualityScore; }
        public void setQualityScore(int qualityScore) { this.qualityScore = qualityScore; }
        public int getEditCount() { return editCount; }
        public void setEditCount(int editCount) { this.editCount = editCount; }
    }

    @Role(Role.Type.EVENT)
    @Expires("24h")  // state fact — edit patterns accumulate across a user's session
    public static class EditPattern implements Serializable {
        public String user;
        public String title;
        public String pattern;
        public EditPattern() {}
        public EditPattern(String user, String title, String pattern) {
            this.user = user; this.title = title; this.pattern = pattern;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    @Timestamp("timestamp")
    public static class VandalismCandidate implements Serializable {
        public String title;
        public String user;
        public int deletionSize;
        public long timestamp;
        public VandalismCandidate() {}
        public VandalismCandidate(String title, String user, int deletionSize, long timestamp) {
            this.title = title; this.user = user; this.deletionSize = deletionSize; this.timestamp = timestamp;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getDeletionSize() { return deletionSize; }
        public void setDeletionSize(int deletionSize) { this.deletionSize = deletionSize; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class VandalismAnalysis implements Serializable {
        public String title;
        public String user;
        public int riskScore;
        public String userReputation;
        public VandalismAnalysis() {}
        public VandalismAnalysis(String title, String user, int riskScore, String userReputation) {
            this.title = title; this.user = user; this.riskScore = riskScore; this.userReputation = userReputation;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getRiskScore() { return riskScore; }
        public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
        public String getUserReputation() { return userReputation; }
        public void setUserReputation(String userReputation) { this.userReputation = userReputation; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class VandalismVerified implements Serializable {
        public String title;
        public String user;
        public String severity;
        public String articleImpact;
        public VandalismVerified() {}
        public VandalismVerified(String title, String user, String severity, String articleImpact) {
            this.title = title; this.user = user; this.severity = severity; this.articleImpact = articleImpact;
        }
        public VandalismVerified(String title, String severity, String articleImpact) {
            this(title, "UNKNOWN", severity, articleImpact);
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getArticleImpact() { return articleImpact; }
        public void setArticleImpact(String articleImpact) { this.articleImpact = articleImpact; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class VandalismFlagged implements Serializable {
        public String title;
        public String user;
        public int priority;
        public VandalismFlagged() {}
        public VandalismFlagged(String title, String user, int priority) {
            this.title = title; this.user = user; this.priority = priority;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class VandalismLogged implements Serializable {
        public String title;
        public VandalismLogged() {}
        public VandalismLogged(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Role(Role.Type.EVENT)
    @Expires("10m")  // bot state — bots edit rapidly, 10 min window is sufficient
    public static class BotHealthCheck implements Serializable {
        public String user;
        public String status;
        public boolean anomalyDetected;
        public int activityRate;
        public long timestamp;
        public BotHealthCheck() {}
        public BotHealthCheck(String user, String status, boolean anomalyDetected) {
            this(user, status, anomalyDetected, 0, 0L);
        }
        public BotHealthCheck(String user, String status, boolean anomalyDetected, int activityRate) {
            this(user, status, anomalyDetected, activityRate, 0L);
        }
        public BotHealthCheck(String user, String status, boolean anomalyDetected, int activityRate, long timestamp) {
            this.user = user; this.status = status; this.anomalyDetected = anomalyDetected;
            this.activityRate = activityRate; this.timestamp = timestamp;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isAnomalyDetected() { return anomalyDetected; }
        public void setAnomalyDetected(boolean anomalyDetected) { this.anomalyDetected = anomalyDetected; }
        public int getActivityRate() { return activityRate; }
        public void setActivityRate(int activityRate) { this.activityRate = activityRate; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("10m")  // bot state — accumulate window is 60s, 10m gives comfortable buffer
    @Timestamp("timestamp")
    public static class BotActivity implements Serializable {
        public String user;
        public String action;
        public long timestamp;
        public BotActivity() {}
        public BotActivity(String user, String action, long timestamp) {
            this.user = user; this.action = action; this.timestamp = timestamp;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("10m")  // bot state
    public static class BotProfile implements Serializable {
        public String user;
        public String category;
        public int activityRate;
        public long timestamp;
        public BotProfile() {}
        public BotProfile(String user, String category, int activityRate) {
            this(user, category, activityRate, 0L);
        }
        public BotProfile(String user, String category, int activityRate, long timestamp) {
            this.user = user; this.category = category; this.activityRate = activityRate; this.timestamp = timestamp;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getActivityRate() { return activityRate; }
        public void setActivityRate(int activityRate) { this.activityRate = activityRate; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("10m")  // bot state
    public static class BotMetrics implements Serializable {
        public String user;
        public int activityRate;
        public long timestamp;
        public BotMetrics() {}
        public BotMetrics(String user) { this(user, 0, 0L); }
        public BotMetrics(String user, int activityRate) { this(user, activityRate, 0L); }
        public BotMetrics(String user, int activityRate, long timestamp) {
            this.user = user; this.activityRate = activityRate; this.timestamp = timestamp;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getActivityRate() { return activityRate; }
        public void setActivityRate(int activityRate) { this.activityRate = activityRate; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("10m")  // bot state
    public static class BotReported implements Serializable {
        public String user;
        public long timestamp;
        public BotReported() {}
        public BotReported(String user) { this(user, 0L); }
        public BotReported(String user, long timestamp) {
            this.user = user; this.timestamp = timestamp;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    @Timestamp("timestamp")
    public static class ContentAddition implements Serializable {
        public String title;
        public String user;
        public int addedBytes;
        public long timestamp;
        public ContentAddition() {}
        public ContentAddition(String title, String user, int addedBytes, long timestamp) {
            this.title = title; this.user = user; this.addedBytes = addedBytes; this.timestamp = timestamp;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getAddedBytes() { return addedBytes; }
        public void setAddedBytes(int addedBytes) { this.addedBytes = addedBytes; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class ContentReview implements Serializable {
        public String title;
        public String user;
        public String qualityTier;
        public String contributorRating;
        public ContentReview() {}
        public ContentReview(String title, String user, String qualityTier, String contributorRating) {
            this.title = title; this.user = user; this.qualityTier = qualityTier; this.contributorRating = contributorRating;
        }
        public ContentReview(String title, String qualityTier, String contributorRating) { 
            this(title, "UNKNOWN", qualityTier, contributorRating); 
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getQualityTier() { return qualityTier; }
        public void setQualityTier(String qualityTier) { this.qualityTier = qualityTier; }
        public String getContributorRating() { return contributorRating; }
        public void setContributorRating(String contributorRating) { this.contributorRating = contributorRating; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class ContentApproved implements Serializable {
        public String title;
        public String user;
        public int qualityBoost;
        public String tier;
        public ContentApproved() {}
        public ContentApproved(String title, String user, int qualityBoost) {
            this(title, user, qualityBoost, "STANDARD");
        }
        public ContentApproved(String title, String user, int qualityBoost, String tier) {
            this.title = title; this.user = user; this.qualityBoost = qualityBoost; this.tier = tier;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getQualityBoost() { return qualityBoost; }
        public void setQualityBoost(int qualityBoost) { this.qualityBoost = qualityBoost; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class ContentIndexed implements Serializable {
        public String title;
        public String tier;
        public ContentIndexed() {}
        public ContentIndexed(String title) { this(title, "STANDARD"); }
        public ContentIndexed(String title, String tier) { this.title = title; this.tier = tier; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class ContentCached implements Serializable {
        public String title;
        public String tier;
        public ContentCached() {}
        public ContentCached(String title) { this(title, "STANDARD"); }
        public ContentCached(String title, String tier) { this.title = title; this.tier = tier; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    @Timestamp("timestamp")
    public static class MinorEdit implements Serializable {
        public String title;
        public String user;
        public int changeSize;
        public long timestamp;
        public MinorEdit() {}
        public MinorEdit(String title, String user, int changeSize, long timestamp) {
            this.title = title; this.user = user; this.changeSize = changeSize; this.timestamp = timestamp;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public int getChangeSize() { return changeSize; }
        public void setChangeSize(int changeSize) { this.changeSize = changeSize; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class MinorClassified implements Serializable {
        public String title;
        public String user;
        public String editType;
        public int frequency;
        public MinorClassified() {}
        public MinorClassified(String title, String user, String editType, int frequency) {
            this.title = title; this.user = user; this.editType = editType; this.frequency = frequency;
        }
        public MinorClassified(String title, String editType, int frequency) {
            this(title, "UNKNOWN", editType, frequency);
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getEditType() { return editType; }
        public void setEditType(String editType) { this.editType = editType; }
        public int getFrequency() { return frequency; }
        public void setFrequency(int frequency) { this.frequency = frequency; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class MinorValidated implements Serializable {
        public String title;
        public String user;
        public String trustLevel;
        public MinorValidated() {}
        public MinorValidated(String title, String user, String trustLevel) {
            this.title = title; this.user = user; this.trustLevel = trustLevel;
        }
        public MinorValidated(String title, String trustLevel) {
            this(title, "UNKNOWN", trustLevel);
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getTrustLevel() { return trustLevel; }
        public void setTrustLevel(String trustLevel) { this.trustLevel = trustLevel; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class MinorAccepted implements Serializable {
        public String title;
        public MinorAccepted() {}
        public MinorAccepted(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class MinorTracked implements Serializable {
        public String title;
        public MinorTracked() {}
        public MinorTracked(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    @Timestamp("timestamp")
    public static class DiscussionPost implements Serializable {
        public String title;
        public String user;
        public long timestamp;
        public DiscussionPost() {}
        public DiscussionPost(String title, String user, long timestamp) {
            this.title = title; this.user = user; this.timestamp = timestamp;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class DiscussionAnalyzed implements Serializable {
        public String title;
        public String topic;
        public int relatedArticleQuality;
        public DiscussionAnalyzed() {}
        public DiscussionAnalyzed(String title, String topic, int relatedArticleQuality) {
            this.title = title; this.topic = topic; this.relatedArticleQuality = relatedArticleQuality;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public int getRelatedArticleQuality() { return relatedArticleQuality; }
        public void setRelatedArticleQuality(int relatedArticleQuality) { this.relatedArticleQuality = relatedArticleQuality; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class DiscussionSentiment implements Serializable {
        public String title;
        public String mood;
        public String userEngagement;
        public DiscussionSentiment() {}
        public DiscussionSentiment(String title, String mood, String userEngagement) {
            this.title = title; this.mood = mood; this.userEngagement = userEngagement;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getMood() { return mood; }
        public void setMood(String mood) { this.mood = mood; }
        public String getUserEngagement() { return userEngagement; }
        public void setUserEngagement(String userEngagement) { this.userEngagement = userEngagement; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class DiscussionRouted implements Serializable {
        public String title;
        public DiscussionRouted() {}
        public DiscussionRouted(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Role(Role.Type.EVENT)
    @Expires("90s")
    public static class DiscussionNotified implements Serializable {
        public String title;
        public DiscussionNotified() {}
        public DiscussionNotified(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Role(Role.Type.EVENT)
    @Expires("24h")  // state fact — high risk designation should persist across the day
    public static class HighRiskUser implements Serializable {
        public String user;
        public String riskFactors;
        public HighRiskUser() {}
        public HighRiskUser(String user, String riskFactors) {
            this.user = user; this.riskFactors = riskFactors;
        }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getRiskFactors() { return riskFactors; }
        public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }
    }

    @Role(Role.Type.EVENT)
    @Expires("24h")  // state fact — article attack state persists during an attack event
    public static class ArticleUnderAttack implements Serializable {
        public String title;
        public int attackSeverity;
        public ArticleUnderAttack() {}
        public ArticleUnderAttack(String title, int attackSeverity) {
            this.title = title; this.attackSeverity = attackSeverity;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getAttackSeverity() { return attackSeverity; }
        public void setAttackSeverity(int attackSeverity) { this.attackSeverity = attackSeverity; }
    }
}
