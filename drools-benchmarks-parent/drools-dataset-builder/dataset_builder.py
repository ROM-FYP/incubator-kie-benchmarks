import argparse
import json
import re
import csv
from collections import defaultdict

def parse_drl(drl_file):
    """
    Parse DRL file to extract fact types and their relevant attributes.
    Builds a schema: fact_type -> list of attributes used in conditions.
    """
    schema = defaultdict(set)
    
    try:
        with open(drl_file, 'r', encoding='utf-8') as f:
            content = f.read()
    except FileNotFoundError:
        print(f"Warning: DRL file not found at {drl_file}, skipping schema extraction.")
        return {}
        
    # Heuristic regex to find patterns like FactType( attr1 == val, attr2 > val )
    pattern = re.compile(r'\b([A-Z]\w+)\s*\((.*?)\)', re.DOTALL)
    
    for match in pattern.finditer(content):
        fact_type = match.group(1)
        conditions = match.group(2)
        
        if not conditions.strip():
            continue
            
        # 1. Match constraints: field ==, >, <, in, etc.
        for m in re.finditer(r'\b([a-zA-Z_]\w*)\s*(?:==|!=|>|<|>=|<=|in)\b', conditions):
            schema[fact_type].add(m.group(1))
            
        # 2. Match bindings: $var : field
        for m in re.finditer(r':\s*([a-zA-Z_]\w*)\b', conditions):
            schema[fact_type].add(m.group(1))

    return {k: list(v) for k, v in schema.items()}

def parse_ftree(ftree_file):
    """
    Parse Infomap .ftree format mapping rule_name -> cluster_id
    """
    rule_to_cluster = {}
    try:
        with open(ftree_file, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#') or line.startswith('*'):
                    continue
                    
                parts = line.split(' ')
                if len(parts) >= 3:
                    path = parts[0]
                    cluster_id = path.split(':')[0]
                    rule_name = parts[2].strip('"')
                    rule_to_cluster[rule_name] = cluster_id
    except FileNotFoundError:
        print(f"Warning: ftree file not found at {ftree_file}, clustering will be empty.")
                    
    return rule_to_cluster

def parse_traces(trace_file):
    """
    Parse execution traces.
    Track fact insertions (fact_id -> object features).
    Track activations (rule_name -> supporting_fact_ids).
    Returns (facts, activations)
    """
    facts = {}
    activations = []
    
    with open(trace_file, 'r', encoding='utf-8') as f:
        for line in f:
            if not line.strip(): continue
            event = json.loads(line)
            event_type = event.get('type')
            
            if event_type in ('FACT_INSERT', 'FACT_UPDATE'):
                fact_id = event['fact_id']
                fact_type = event['fact_type']
                fact_data = event.get('fact_data', {})
                facts[fact_id] = {
                    'fact_type': fact_type,
                    'attributes': fact_data
                }
            elif event_type == 'ACTIVATION_CREATED':
                activations.append({
                    'rule_name': event['rule'],
                    'supporting_fact_ids': event.get('supporting_facts', [])
                })
                
    return facts, activations

def build_fact_rule_mapping(facts, activations, rule_to_cluster):
    """
    Build mapping:
    - fact_id -> rules triggered
    - fact_id -> clusters
    """
    fact_to_rules = defaultdict(set)
    fact_to_clusters = defaultdict(set)
    
    for act in activations:
        rule_name = act['rule_name']
        cluster_id = rule_to_cluster.get(rule_name)
        
        for fact_id in act['supporting_fact_ids']:
            fact_to_rules[fact_id].add(rule_name)
            if cluster_id:
                fact_to_clusters[fact_id].add(cluster_id)
                
    return fact_to_clusters

def build_dataset(facts, fact_to_clusters, schema, output_csv, filter_fact_type=None):
    """
    Generate the final multi-label csv dataset.
    Features: fact_type + flattened attributes
    Labels: binary cluster labels
    """
    # Identify unique clusters
    all_clusters = set()
    for clusters in fact_to_clusters.values():
        all_clusters.update(clusters)
    all_clusters = sorted(list(all_clusters), key=lambda x: int(x) if x.isdigit() else x)
    
    # Identify unique attributes from the DRL schema
    if filter_fact_type:
        all_attributes = sorted(list(schema.get(filter_fact_type, [])))
    else:
        all_attributes = set()
        for attrs in schema.values():
            all_attributes.update(attrs)
        all_attributes = sorted(list(all_attributes))
    
    headers = ['fact_id', 'fact_type'] + [f"attr_{a}" for a in all_attributes] + [f"cluster_{c}" for c in all_clusters]
    
    with open(output_csv, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(headers)
        
        valid_facts_count = 0
        for fact_id, fact_data in facts.items():
            if filter_fact_type and fact_data['fact_type'] != filter_fact_type:
                continue
                
            clusters = fact_to_clusters.get(fact_id, set())
            
            # Filter noise: if no clusters, skip
            if not clusters:
                continue
                
            valid_facts_count += 1
            row = [fact_id, fact_data['fact_type']]
            
            # Add features
            attrs = fact_data['attributes']
            for a in all_attributes:
                val = attrs.get(a, '')
                row.append(val)
                
            # Add multi-label targets
            for c in all_clusters:
                row.append(1 if c in clusters else 0)
                
            writer.writerow(row)
            
    print(f"Constructed dataset with {valid_facts_count} facts, {len(all_attributes)} attributes, {len(all_clusters)} clusters.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Dataset Builder for Benchmark Traces")
    parser.add_argument('--trace', required=True, help="Path to causal JSONl trace file")
    parser.add_argument('--drl', required=True, help="Path to DRL rules file")
    parser.add_argument('--clusters', required=True, help="Path to ftree clusters file")
    parser.add_argument('--out-csv', required=True, help="Path to output dataset CSV")
    parser.add_argument('--fact-type', default=None, help="Filter output to a specific fact type (e.g. OpenSkyStateVector)")
    
    args = parser.parse_args()
    
    print(f"Parsing clusters from {args.clusters}...")
    rule_to_cluster = parse_ftree(args.clusters)
    print(f"Found {len(rule_to_cluster)} rule-cluster mappings.")
    
    print(f"Parsing DRL from {args.drl}...")
    schema = parse_drl(args.drl)
    
    print(f"Parsing traces from {args.trace}...")
    facts, activations = parse_traces(args.trace)
    print(f"Found {len(facts)} facts and {len(activations)} activations.")
    
    print("Building fact-to-cluster mappings...")
    fact_to_clusters = build_fact_rule_mapping(facts, activations, rule_to_cluster)
    
    print(f"Building final dataset to {args.out_csv}...")
    build_dataset(facts, fact_to_clusters, schema, args.out_csv, args.fact_type)
    print("Dataset construction complete.")
