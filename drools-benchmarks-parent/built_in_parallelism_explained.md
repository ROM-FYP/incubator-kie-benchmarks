# Drools Built-in Parallelism: Intra-Session Parallelism via Phreak Network Partitioning

## Key Architectural Fact

> **Both `PARALLEL_EVALUATION` and `FULLY_PARALLEL` execute within a single `KieSession`.**
> They do not create multiple sessions. Instead, the engine internally partitions its Phreak rule network into independent segments and evaluates them concurrently using a `ForkJoinPool`—all within the same session instance, all coordinated by internal synchronization.

This is a critical distinction: Drools' built-in parallelism is **intra-session** parallelism. The application creates one `KieSession`, configures the `KieBase` with a `ParallelExecutionOption`, and calls `fireAllRules()` as usual. The engine internally manages the threading, partitioning, and synchronization—transparent to the user.

---

## How It Works Internally

The Phreak algorithm (Drools' lazy, goal-oriented successor to ReteOO) separates user actions (`insert`, `update`, `delete`) from internal network evaluation. This separation is what enables the engine to partition the rule network:

1. **Partitioning**: The `PartitionsManager` (in `KnowledgeBaseImpl`) divides the Phreak rule network into independent partitions—groups of rules whose evaluation paths do not share data dependencies.
2. **Parallel Dispatch**: When `fireAllRules()` is called, the engine dispatches each partition's evaluation to worker threads from the internal `ForkJoinPool`.
3. **Synchronization**: A `CompositeDefaultAgenda` coordinates the partitioned agendas, managing thread synchronization barriers and ensuring Working Memory consistency.

All of this happens **inside the single `KieSession`**. The user's application code is unchanged—only the `KieBase` configuration differs.

---

## The Two Modes

### E1: `PARALLEL_EVALUATION` (LHS-only Parallelism)

| Aspect | Behaviour |
|---|---|
| **LHS (pattern matching)** | Evaluated in parallel across Phreak partitions |
| **RHS (rule consequences)** | Executed **sequentially** on a single thread |
| **Salience / Agenda Groups** | **Respected**—sequential RHS preserves ordering semantics |
| **Session model** | Single `KieSession` with internal synchronization |
| **Safety** | Safe for rules that `modify()` / `update()` shared facts—sequential RHS prevents concurrent mutation |

**How it works:** When facts are inserted or modified, the engine evaluates the `when` conditions concurrently across partitions. However, the `then` consequences are collected and fired strictly sequentially. The engine synchronizes the agenda after parallel evaluation is complete, then processes the agenda in a single thread.

**Rationale:** This is a safe middle-ground. By keeping the RHS sequential, Drools prevents race conditions from two threads attempting to `modify()` the same fact simultaneously.

### E2: `FULLY_PARALLEL` (LHS + RHS Parallelism)

| Aspect | Behaviour |
|---|---|
| **LHS (pattern matching)** | Evaluated in parallel across Phreak partitions |
| **RHS (rule consequences)** | Also executed **in parallel** across partitions |
| **Salience / Agenda Groups** | **Not guaranteed**—parallel RHS breaks deterministic ordering |
| **Session model** | Single `KieSession` with internal synchronization |
| **Safety** | Risk of `ConcurrentModificationException` if rules mutate shared facts |

**How it works:** Both evaluation and consequence firing happen concurrently. The engine still uses the same single-session architecture with the `CompositeDefaultAgenda` managing synchronization, but the RHS barrier is relaxed—consequences in independent partitions fire simultaneously.

**Rationale:** This mode targets maximum throughput for **stateless** / CLOUD-mode workloads where rules validate independent facts without modifying shared state.

---

## Configuration

Both modes are configured at the `KieBase` level—**not** at the session level—and apply to all sessions created from that base:

**Programmatic (recommended for benchmarks):**
```java
KieServices ks = KieServices.Factory.get();
KieBaseConfiguration kieBaseConf = ks.newKieBaseConfiguration();
kieBaseConf.setOption(ParallelExecutionOption.PARALLEL_EVALUATION);
// or: kieBaseConf.setOption(ParallelExecutionOption.FULLY_PARALLEL);
KieBase kieBase = kieContainer.newKieBase(kieBaseConf);

// Sessions created from this KieBase inherit the parallel behaviour
KieSession session = kieBase.newKieSession();
session.insert(myFact);
session.fireAllRules();  // Internally parallel, externally identical
```

**System property:**
```
-Ddrools.parallelExecution=parallel_evaluation
-Ddrools.parallelExecution=fully_parallel
```

The `ParallelExecutionOption` enum is defined in `org.kie.internal.conf.ParallelExecutionOption` with three values: `SEQUENTIAL` (default), `PARALLEL_EVALUATION`, and `FULLY_PARALLEL`.

---



## Citable References & Sources

### Official Drools Documentation

| Reference | URL |
|---|---|
| **Drools User Guide — KieBase Configuration** | [docs.drools.org/latest/drools-docs/drools/KIE/index.html](https://docs.drools.org/latest/drools-docs/drools/KIE/index.html) |
| **Drools User Guide — Rule Engine** | [docs.drools.org/latest/drools-docs/drools/rule-engine/index.html](https://docs.drools.org/latest/drools-docs/drools/rule-engine/index.html) |
| **Drools Documentation Portal** | [www.drools.org/learn/documentation.html](https://www.drools.org/learn/documentation.html) |

> The official documentation describes `ParallelExecutionOption` as follows:
> *"Determines if the engine should evaluate rules and execute their consequences sequentially or in parallel. [...] `parallel_evaluation`: Rules are evaluated in parallel, but rule consequences are executed sequentially, respecting salience and agenda-groups. `fully_parallel`: Both evaluation and execution occur in parallel."*
> — Drools Documentation, KieBase Configuration

### Source Code References (Apache Incubator KIE Drools)

| Class / File | Location | Purpose |
|---|---|---|
| `ParallelExecutionOption.java` | [`kie-internal/src/main/java/org/kie/internal/conf/`](https://github.com/apache/incubator-kie-drools/blob/main/kie-internal/src/main/java/org/kie/internal/conf/ParallelExecutionOption.java) | Enum defining `SEQUENTIAL`, `PARALLEL_EVALUATION`, `FULLY_PARALLEL` |
| `CompositeDefaultAgenda` | `drools-core/src/main/java/org/drools/core/common/` | Coordinates partitioned agendas for parallel execution |
| `PartitionsManager` | `drools-core/src/main/java/org/drools/core/impl/KnowledgeBaseImpl.java` | Manages Phreak network partitioning |

Repository: [github.com/apache/incubator-kie-drools](https://github.com/apache/incubator-kie-drools)

### Red Hat / KIE Community Resources

| Resource | Description |
|---|---|
| [Mark Proctor's Blog (blog.athico.com)](http://blog.athico.com/) | Lead architect of Drools; detailed posts on Phreak algorithm and parallelism design |
| [KIE Community Blog (kie.org)](https://www.kie.org/) | Official KIE project blog with technical deep-dives |
| Red Hat Decision Manager Documentation | Enterprise version of Drools with parallel evaluation documentation |

### For LaTeX Citation

```bibtex
@misc{drools-docs-parallel,
  author       = {{Apache KIE Community}},
  title        = {Drools Documentation: KieBase Configuration — Parallel Execution},
  howpublished = {\url{https://docs.drools.org/latest/drools-docs/drools/KIE/index.html}},
  year         = {2024},
  note         = {Accessed: 2026-06-12}
}

@misc{drools-source-parallel,
  author       = {{Apache KIE Community}},
  title        = {ParallelExecutionOption.java — Apache Incubator KIE Drools},
  howpublished = {\url{https://github.com/apache/incubator-kie-drools/blob/main/kie-internal/src/main/java/org/kie/internal/conf/ParallelExecutionOption.java}},
  year         = {2024},
  note         = {Accessed: 2026-06-12}
}
```

> **Note on Academic Citations:** There is no single, formal academic paper dedicated to Drools' parallel execution or the Phreak algorithm. The Phreak algorithm is a proprietary implementation of the Drools project. For academic citation, refer to the official Drools documentation and source code as shown above. The original Rete algorithm can be cited via Forgy (1982) and Doorenbos (1995, Rete/UL thesis).
