with open("src/main/resources/rules/taxonomy.drl", "r") as f:
    lines = f.readlines()

new_lines = []
in_then = False
empty_then = True
rule_name = ""
has_sym = False
count = 0

for line in lines:
    if line.startswith("rule "):
        try:
            rule_name = line.split('"')[1]
        except IndexError:
            rule_name = "UNKNOWN"
        has_sym = False
        empty_then = True
        in_then = False
    
    if "$sym" in line:
        has_sym = True
        
    if in_then:
        if line.strip() == "end":
            if empty_then:
                sym_var = "$sym" if has_sym else '"GLOBAL"'
                new_lines.append(f'  insert(new RiskSignal({sym_var}, "{rule_name}", "INFO", System.currentTimeMillis(), "Fired"));\n')
                count += 1
            in_then = False
        elif line.strip() and not line.strip().startswith("//"):
            empty_then = False
            
    if line.strip() == "then":
        in_then = True
        
    new_lines.append(line)

with open("src/main/resources/rules/taxonomy.drl", "w") as f:
    f.writelines(new_lines)

print(f"Total rules patched: {count}")
