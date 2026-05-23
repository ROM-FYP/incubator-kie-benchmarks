# Paper Content: Coverage Variation via Controlled Event Neutralization

## Summary of What You Did

Across all three benchmarks, you created **two additional dataset variants** via *controlled event-field neutralization* — modifying source field values (not removing events) to systematically reduce rule coverage while preserving dataset size (~1.6M events) and temporal structure.

| Domain | Baseline | Medium | Low | Method |
|---|---:|---:|---:|---|
| **Wikimedia** | 78.6% | 57.1% | 24.3% | Pipeline starvation (Bot/Discussion/Content/Minor disabled via `bot=false`, `sizeDelta=100`, strip `Talk:`) |
| **Binance** | 63.9% | 56.5% | 44.4% | Stream-type neutralization (`source_stream→"neutral"` for TRADE, MARK, LIQ) |
| **AirTraffic** | 71.8% | ~50% | ~28% | Spatial pipeline starvation (`on_ground=true`, position nulling) |

---

## Where to Add in the Paper

There are **three places** where this content should be integrated:

### 1. Section 3.1 (Rule Derivation Process) — Stage 2b: Validation (around lines 207-210)

Add a brief mention of the coverage-variant creation methodology **after** the validation paragraph. This fits naturally because Stage 2b already discusses rule coverage >60% as a validation criterion.

**Add after the sentence ending with "returned the process to Stage 2a." (line 210):**

> To further validate the relationship between data characteristics and rule-base activation, we constructed two reduced-coverage dataset variants per domain using *controlled event-field neutralization*. Rather than removing events (which would alter dataset size and temporal properties), this method modifies source-level field values—such as setting `on_ground=true` for aviation events or re-mapping stream identifiers for financial events—to systematically prevent events from triggering specific pipeline entry conditions. Each variant preserves the original dataset size (~1.6M events) and inter-arrival timing, enabling controlled study of how input-stream composition affects forward-chain activation depth. The resulting variants target approximately 50\% and 25\% rule coverage per domain.

### 2. Section 4.1 (Analysis Methodology) — After the existing methodology paragraphs (around line 356)

Add a new paragraph describing the coverage-variation methodology:

**Add after the paragraph ending with "the fraction of total activations at each depth." (line 356):**

> **Coverage-variant analysis.** To study how event-stream composition drives rule activation, we created two reduced-coverage datasets per domain via controlled event-field neutralization. For each domain, event fields governing pipeline entry conditions were modified to progressively disable downstream rule chains: in AirTraffic, the `on\_ground` flag and position coordinates control grid-cell assignment, gating the entire pairing and conflict pipeline (depths 2–9); in Binance, the `source\_stream` field determines event-type routing across DEPTH, TRADE, MARK, and LIQ processing chains; in Wikimedia, the `bot` flag, `sizeDelta`, and title prefix govern entry into the five independent processing pipelines. Each variant preserves the original ~1.6M event count and timestamp sequence, isolating the effect of stream composition on rule coverage and forward-chain depth attenuation. Table 7 summarises the coverage levels achieved.

### 3. Section 4 (Cross-Suite Analysis) — New Table 7 and Discussion (add after Table 6, around line 417)

This is the most important addition. Add a **new table** and a short discussion paragraph:

**Add before Section 5 (line 420):**

> #### 4.2 &nbsp; Coverage-Variant Analysis
>
> Table 7 reports rule coverage and key runtime metrics for the baseline and two reduced-coverage dataset variants per domain. All variants share the same rule base and event count; only source-level field values differ.
>
> **Table 7.** Rule coverage and runtime metrics under controlled event-field neutralisation. Each variant preserves ~1.6M events; "Medium" and "Low" denote progressively reduced coverage targets.
>
> | | **AirTraffic** | | | **Binance** | | | **Wikimedia** | | |
> |---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
> | Metric | Base | Med | Low | Base | Med | Low | Base | Med | Low |
> | Coverage (%) | 71.8 | ~50 | ~28 | 63.9 | 56.5 | 44.4 | 78.6 | 57.1 | 24.3 |
> | Rules fired/event | 39.44 | — | — | 15.72 | 3.59 | 3.38 | 3.69 | 2.63 | 0.07 |
> | Peak WM (facts) | 31.7M | — | — | 172 | 172 | ~162 | 13,009 | 12,322 | 2 |
>
> *Note: AirTraffic Medium/Low runtime values are pending EC2 benchmark execution.*
>
> Three observations emerge. First, **coverage reduction is driven by stream diversity, not volume**: in Binance, removing TRADE events (88.5\% of the dataset) reduces coverage by only 7.4 percentage points (pp), whereas removing the additional 1.1\% MARK events drops it by 12.1 pp—confirming that coverage depends on the variety of fact-producing pathways, not raw event counts. Second, **cross-pipeline starvation cascades** are clearly visible: in Wikimedia, disabling the Content pipeline eliminates `ArticleQuality` facts, which are required as join partners by five Vandalism-verification rules at depths 2–3, causing cascading coverage loss beyond the directly disabled pipeline. Similarly, in Binance, the depth-5 compound rule (`M87\_CompoundSystemicRisk`) requires simultaneous outputs from the TRADE and DEPTH forward chains; it fires in the baseline but goes dark when either chain is removed. Third, **irreducible structural cores** define per-domain coverage floors: in Binance, the DEPTH backbone alone sustains 44.4\% coverage (48 of 108 rules), representing the minimum coverage achievable without destroying the benchmark's architectural integrity.

### 4. Section 5 (Conclusion) — Update the existing conclusion (around lines 427-432)

Update the paragraph to reference the coverage-variant analysis:

**Replace the existing sentence (line 429):**
```
Furthermore, high rule coverage (>63%) across domains validates that the real-world streams effectively exercise the rule sets.
```

**With:**
```
High rule coverage (>63%) across all baseline datasets validates that the real-world 
streams effectively exercise the rule sets. Coverage-variant analysis further reveals 
that rule activation is primarily governed by stream diversity rather than event volume, 
with cross-pipeline fact dependencies producing cascading coverage loss at deeper 
forward-chain levels.
```

---

## Key Points for Each Addition

| Location | Purpose | Length |
|---|---|---|
| **§3.1** (Validation) | Introduce the *method* — what you did and why | ~3 sentences |
| **§4.1** (Methodology) | Describe the *methodology* — domain-specific neutralization fields | ~4 sentences |
| **§4.2** (New subsection) | Present *results* — Table 7 + discussion of three key findings | ~1 table + 1 paragraph |
| **§5** (Conclusion) | Summarise the *insight* — stream diversity vs. volume | ~1 sentence update |

## LaTeX-Ready Table 7

```latex
\begin{table}[t]
\caption{Rule coverage and runtime metrics under controlled event-field
neutralisation. Each variant preserves ${\sim}1.6$M events.}
\label{tab:coverage-variants}
\centering
\small
\begin{tabular}{l rrr rrr rrr}
\toprule
 & \multicolumn{3}{c}{\textbf{AirTraffic}} 
 & \multicolumn{3}{c}{\textbf{Binance}} 
 & \multicolumn{3}{c}{\textbf{Wikimedia}} \\
\cmidrule(lr){2-4} \cmidrule(lr){5-7} \cmidrule(lr){8-10}
Metric & Base & Med & Low & Base & Med & Low & Base & Med & Low \\
\midrule
Coverage (\%)       & 71.8 & $\sim$50 & $\sim$28 & 63.9 & 56.5 & 44.4 & 78.6 & 57.1 & 24.3 \\
Rules fired\,/\,evt & 39.4 & ---      & ---      & 15.7 & 3.59 & 3.38 & 3.69 & 2.63 & 0.07 \\
Peak WM (facts)     & 31.7M & ---     & ---      & 172  & 172  & 162  & 13K  & 12.3K & 2   \\
\bottomrule
\end{tabular}
\end{table}
```

## LaTeX-Ready Paragraph (for §4.2)

```latex
\subsubsection{Coverage-Variant Analysis}

To study how event-stream composition drives rule activation,
we created two reduced-coverage datasets per domain via
\emph{controlled event-field neutralisation}: source-level field
values governing pipeline entry conditions are modified to
progressively disable downstream rule chains, while preserving
the original ${\sim}1.6$M event count and timestamp sequence.
Table~\ref{tab:coverage-variants} reports the resulting coverage levels.

Three observations emerge.
First, \textbf{coverage reduction is driven by stream diversity, not volume}:
in Binance, removing TRADE events (88.5\% of the dataset) reduces coverage
by only 7.4\,pp, whereas removing the additional 1.1\% MARK events drops
it by 12.1\,pp.
Second, \textbf{cross-pipeline starvation cascades} are visible: in
Wikimedia, disabling the Content pipeline eliminates \texttt{ArticleQuality}
facts required as join partners by five Vandalism-verification rules at
depths~2--3, causing coverage loss beyond the directly disabled pipeline.
Third, \textbf{irreducible structural cores} define per-domain coverage
floors: in Binance, the DEPTH backbone alone sustains 44.4\% coverage
(48 of 108 rules), representing the minimum achievable without
destroying the benchmark's architectural integrity.
```

> [!IMPORTANT]
> The AirTraffic Medium and Low runtime metrics (rules fired/event, peak WM) are still pending — you need to run the characterization collector on the cov50 and cov25 datasets on the EC2 instance to fill those in. The coverage percentages (~50% and ~28%) were verified from the cov25 run.
