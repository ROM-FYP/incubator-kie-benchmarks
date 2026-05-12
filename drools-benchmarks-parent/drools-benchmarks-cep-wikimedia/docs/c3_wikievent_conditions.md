# C3 (Content & Vandalism) - WikiEvent Routing Conditions

The C3 cluster contains the heaviest combination of rules, encompassing Vandalism detection, Content addition, complex Correlations, and the intentionally duplicated Bot pipeline. 

Because Drools is a forward-chaining engine, most of the 47 rules in C3 evaluate **derived facts** (like `VandalismCandidate`, `BotActivity`, or `UserActivity`). However, exactly **four rules** serve as the entry points for the cluster, meaning they match directly against the main stream `WikiEvent` object.

Below are the exact conditions and property bindings for those four entry-point rules. This is the reason why C3 requires the entire, unfiltered stream to be routed to it.

---

### 1. InitializeUserActivity
**Purpose:** Ensures that before any other correlation can happen, the engine tracks the editing footprint of every single user. This must fire for every event.
```java
rule "InitializeUserActivity" salience 1000
when
    $e : WikiEvent( $u : user )
```
* **Condition:** None. It accepts every single `WikiEvent`.
* **Bindings:** Extracts `$u : user`. If `not UserActivity(user == $u)` exists, it initializes it.

---

### 2. Vandalism_Detect
**Purpose:** Sniffs for massive, rapid deletions of text which indicate potential page blanking or vandalism.
```java
rule "Vandalism_Detect" salience 500
when
    $e : WikiEvent( sizeDelta < -100, $t : title, $u : user, $size : sizeDelta, $ts : timestamp )
```
* **Condition:** `sizeDelta < -100` (Negative size delta meaning text was deleted from the article).
* **Bindings:** Extracts the `title`, `user`, absolute `sizeDelta`, and `timestamp` to generate a `VandalismCandidate`.

---

### 3. Bot_Detect (Duplicated from C2)
**Purpose:** Specifically imported into C3 to flag events as bot edits so downstream correlation rules (like `CorrelateHighRiskUser`) have local access to bot metric anomalies.
```java
rule "Bot_Detect" salience 400
when
    $e : WikiEvent( bot == true, $u : user, $ts : timestamp )
```
* **Condition:** `bot == true`
* **Bindings:** Extracts `user` and `timestamp` to generate a `BotActivity` instance.

---

### 4. Content_Detect
**Purpose:** Triggers the expensive content-review pipeline whenever substantial new textual content is added by a human to a page.
```java
rule "Content_Detect" salience 300
when
    $e : WikiEvent( sizeDelta > 200, bot == false, $t : title, $u : user, $size : sizeDelta, $ts : timestamp )
```
* **Condition:** `sizeDelta > 200` AND `bot == false` (must be a human producing a large positive addition of text).
* **Bindings:** Extracts the `title`, `user`, `sizeDelta`, and `timestamp` to create a `ContentAddition` entity.

---

### Summary Note
Because `InitializeUserActivity` has no constraint on `sizeDelta` or `bot` status (it fires for literally every event), the Orchestrator's Alpha-Filter routing logic for C3 cannot drop a single event without potentially breaking the user metrics. This confirms why C3's routing condition in `WikimediaClusterOrchestrator` is strictly `eventQueues.get(3).put(event)` with no `if` blocks.
