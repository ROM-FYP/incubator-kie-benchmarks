import json
import argparse
from collections import defaultdict

def build_graph(log_file, min_weight=1, remove_self_loops=True, exclude_rules=None, normalize=False):
    if exclude_rules is None:
        exclude_rules = set()

    # Data Structures
    producer = {} # Fact Provenance: fact_id -> producer_rule
    weights = defaultdict(int) # Aggregated Edges: (Ri, Rj) -> accumulated_weight

    with open(log_file, 'r') as f:
        for line in f:
            if not line.strip():
                continue
                
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
                
                # Collect all unique Ri for this activation (activation-level counting)
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

    # Apply Weights Filter
    filtered_weights = {edge: w for edge, w in weights.items() if w >= min_weight}

    # Apply Optional Normalization
    if normalize:
        out_degree = defaultdict(int)
        for (ri, _), w in filtered_weights.items():
            out_degree[ri] += w
            
        normalized_weights = {}
        for (ri, rj), w in filtered_weights.items():
            normalized_weights[(ri, rj)] = w / out_degree[ri]
        filtered_weights = normalized_weights

    return filtered_weights

def export_csv(weights, output_file):
    with open(output_file, 'w') as f:
        f.write("Source,Target,Weight\n")
        for (ri, rj), w in sorted(weights.items()):
            f.write(f"{ri},{rj},{w}\n")

def export_net(weights, output_file):
    """Export to Infomap-compatible Pajek .net format"""
    rules = set()
    for ri, rj in weights.keys():
        rules.add(ri)
        rules.add(rj)
        
    rule_list = sorted(list(rules))
    rule_ids = {rule: i+1 for i, rule in enumerate(rule_list)}
    
    with open(output_file, 'w') as f:
        f.write(f"*Vertices {len(rule_list)}\n")
        for rule in rule_list:
            f.write(f'{rule_ids[rule]} "{rule}"\n')
            
        f.write("*Arcs\n")
        for (ri, rj), w in sorted(weights.items()):
            f.write(f"{rule_ids[ri]} {rule_ids[rj]} {w}\n")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Construct Directed Rule Interaction Graph")
    parser.add_argument("log_file", help="Path to JSON-lines trace file")
    parser.add_argument("--min-weight", type=int, default=1, help="Drop edges below this weight")
    parser.add_argument("--keep-self-loops", action="store_true", help="Do not drop self-loops")
    parser.add_argument("--normalize", action="store_true", help="Normalize outgoing edge weights")
    parser.add_argument("--exclude", type=str, nargs="*", default=[], help="Exclude rules by name")
    parser.add_argument("--out-csv", type=str, default="rule_graph.csv", help="Output path for CSV")
    parser.add_argument("--out-net", type=str, default="rule_graph.net", help="Output path for .net/Infomap")
    
    args = parser.parse_args()
    
    print(f"Streaming jsonl log from {args.log_file}...")
    weights = build_graph(
        args.log_file,
        min_weight=args.min_weight,
        remove_self_loops=not args.keep_self_loops,
        exclude_rules=set(args.exclude),
        normalize=args.normalize
    )
    
    export_csv(weights, args.out_csv)
    export_net(weights, args.out_net)
    print(f"Exported to {args.out_csv} and {args.out_net}")
