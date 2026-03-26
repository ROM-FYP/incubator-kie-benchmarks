#!/usr/bin/env python3
"""
Construct a Directed Rule Interaction Graph from a Binance CEP
causal-trace JSON-Lines file produced by BinanceCausalTraceListener.

The algorithm mirrors the opensky-airTrafficking build_causal_graph.py:

  1. Fact Provenance  — track which rule last inserted/updated each fact.
  2. Activation Extraction — on ACTIVATION_CREATED, resolve the producer
     of every supporting_fact to derive candidate edges Ri → Rj.
  3. Edge Aggregation — accumulate (Ri, Rj) weights using activation-level
     counting (each producer counted at most once per activation).

Outputs:
  • CSV  — Source,Target,Weight   (for pandas / networkx / Gephi)
  • .net — Pajek format           (for Infomap community detection)
"""

import json
import argparse
import sys
from collections import defaultdict


# ---------------------------------------------------------------------------
# Core algorithm
# ---------------------------------------------------------------------------

def build_graph(log_file, min_weight=1, remove_self_loops=True,
                exclude_rules=None, normalize=False):
    """Stream through the JSONL log and return an edge-weight dict."""
    if exclude_rules is None:
        exclude_rules = set()

    # Fact Provenance: fact_id -> producer_rule
    producer = {}
    # Aggregated Edges: (Ri, Rj) -> accumulated_weight
    weights = defaultdict(int)

    line_count = 0
    with open(log_file, 'r') as f:
        for line in f:
            if not line.strip():
                continue

            line_count += 1
            if line_count % 1_000_000 == 0:
                print(f"  processed {line_count:,} events...", file=sys.stderr)

            event = json.loads(line)
            event_type = event.get('type')

            # Step 1: Fact Provenance
            if event_type in ('FACT_INSERT', 'FACT_UPDATE'):
                producer[event['fact_id']] = event['producer']

            elif event_type == 'FACT_DELETE':
                producer.pop(event['fact_id'], None)

            # Step 2: Extract ACTIVATION_CREATED
            elif event_type == 'ACTIVATION_CREATED':
                rj = event['rule']
                if rj in exclude_rules:
                    continue

                supporting_facts = event.get('supporting_facts', [])

                # Collect all unique Ri for this activation
                unique_producers = set()
                for fact_id in supporting_facts:
                    ri = producer.get(fact_id)
                    if ri and ri != "EXTERNAL":
                        unique_producers.add(ri)

                for ri in unique_producers:
                    if ri in exclude_rules:
                        continue
                    if remove_self_loops and ri == rj:
                        continue

                    # Step 3: Aggregate edge weight
                    weights[(ri, rj)] += 1

    print(f"  finished — {line_count:,} events scanned.", file=sys.stderr)

    # Apply weight filter
    filtered_weights = {edge: w for edge, w in weights.items()
                        if w >= min_weight}

    # Optional normalization (out-degree)
    if normalize:
        out_degree = defaultdict(int)
        for (ri, _), w in filtered_weights.items():
            out_degree[ri] += w

        normalized_weights = {}
        for (ri, rj), w in filtered_weights.items():
            normalized_weights[(ri, rj)] = w / out_degree[ri]
        filtered_weights = normalized_weights

    return filtered_weights


# ---------------------------------------------------------------------------
# Exporters
# ---------------------------------------------------------------------------

def export_csv(weights, output_file):
    """Write edge list as Source,Target,Weight CSV."""
    with open(output_file, 'w') as f:
        f.write("Source,Target,Weight\n")
        for (ri, rj), w in sorted(weights.items()):
            f.write(f"{ri},{rj},{w}\n")


def export_net(weights, output_file):
    """Export to Infomap-compatible Pajek .net format."""
    rules = set()
    for ri, rj in weights.keys():
        rules.add(ri)
        rules.add(rj)

    rule_list = sorted(list(rules))
    rule_ids = {rule: i + 1 for i, rule in enumerate(rule_list)}

    with open(output_file, 'w') as f:
        f.write(f"*Vertices {len(rule_list)}\n")
        for rule in rule_list:
            f.write(f'{rule_ids[rule]} "{rule}"\n')

        f.write("*Arcs\n")
        for (ri, rj), w in sorted(weights.items()):
            f.write(f"{rule_ids[ri]} {rule_ids[rj]} {w}\n")


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

def print_summary(weights):
    """Print basic graph statistics to stdout."""
    if not weights:
        print("Graph is empty — no edges found.")
        return

    rules = set()
    for ri, rj in weights.keys():
        rules.add(ri)
        rules.add(rj)

    total_weight = sum(weights.values())
    sorted_edges = sorted(weights.items(), key=lambda x: x[1], reverse=True)

    print("\n" + "=" * 60)
    print("  Rule Interaction Graph — Summary")
    print("=" * 60)
    print(f"  Nodes (rules):       {len(rules)}")
    print(f"  Edges (directed):    {len(weights)}")
    print(f"  Total edge weight:   {total_weight:,}")
    print()
    print("  Top 15 edges by weight:")
    print("  " + "-" * 56)
    print(f"  {'Source':<30} {'Target':<20} {'Weight':>6}")
    print("  " + "-" * 56)
    for (ri, rj), w in sorted_edges[:15]:
        print(f"  {ri:<30} {rj:<20} {w:>6}")
    print("=" * 60 + "\n")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Construct Directed Rule Interaction Graph "
                    "from Binance CEP causal trace")
    parser.add_argument("log_file",
                        help="Path to JSON-lines trace file "
                             "(e.g. binance_causal_trace.jsonl)")
    parser.add_argument("--min-weight", type=int, default=1,
                        help="Drop edges below this weight (default: 1)")
    parser.add_argument("--keep-self-loops", action="store_true",
                        help="Do not drop self-loops")
    parser.add_argument("--normalize", action="store_true",
                        help="Normalize outgoing edge weights")
    parser.add_argument("--exclude", type=str, nargs="*", default=[],
                        help="Exclude rules by name")
    parser.add_argument("--out-csv", type=str,
                        default="binance_rule_graph.csv",
                        help="Output path for CSV (default: binance_rule_graph.csv)")
    parser.add_argument("--out-net", type=str,
                        default="binance_rule_graph.net",
                        help="Output path for .net/Infomap "
                             "(default: binance_rule_graph.net)")

    args = parser.parse_args()

    print(f"Streaming JSONL log from {args.log_file}...")
    weights = build_graph(
        args.log_file,
        min_weight=args.min_weight,
        remove_self_loops=not args.keep_self_loops,
        exclude_rules=set(args.exclude),
        normalize=args.normalize
    )

    export_csv(weights, args.out_csv)
    export_net(weights, args.out_net)
    print_summary(weights)
    print(f"Exported to {args.out_csv} and {args.out_net}")
