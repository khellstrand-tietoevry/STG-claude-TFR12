#!/usr/bin/env python3
"""Build one description packet per topology.json leaf node.

Each packet is ~150 lines of the module's own source (its header comment plus
the start of the Data Division, which is almost always where the file's
purpose is stated), plus its connections from topology.json. Datastore nodes
get no source, only their connections. Packets are written to
analysis/STG/TFR12/node_packets/<id>.txt so a description subagent can be
pointed at a single file to read, rather than the content being inlined into
the orchestrator's own context.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))  # .../analysis/TFR12
REPO_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SYS_ROOT = os.path.join(REPO_ROOT, "legacy", "STG", "TFR12")
TOPOLOGY_PATH = os.path.join(HERE, "topology.json")
OUT_DIR = os.path.join(HERE, "node_packets")

os.makedirs(OUT_DIR, exist_ok=True)

topology = json.load(open(TOPOLOGY_PATH, encoding="utf-8"))

leaves = {}
for domain in topology["root"]["children"]:
    for child in domain["children"]:
        leaves[child["id"]] = {**child, "domain": domain["name"]}

edges_out = {}
edges_in = {}
for e in topology["edges"]:
    edges_out.setdefault(e["source"], []).append(e)
    edges_in.setdefault(e["target"], []).append(e)

written = []
for node_id, node in leaves.items():
    lines = [f"# Node packet: {node_id}", f"Kind: {node['kind']}", f"Domain: {node['domain']}"]
    if node["kind"] == "module":
        lines.append(f"Entry point: {node_id in topology['entryPoints']}")
        src_path = os.path.join(SYS_ROOT, node["file"])
        with open(src_path, "r", encoding="latin-1") as f:
            src_lines = [next(f, "") for _ in range(150)]
        lines.append(f"LOC (full file): {node['loc']}")
        lines.append("")
        lines.append(f"## First 150 lines of {node['file']} (header + start of Data Division)")
        lines.append("".join(src_lines).rstrip("\n"))
    else:
        lines.append("This is a logical DB2 table (datastore), not a source file in this repo — "
                      "no source packet applies.")

    lines.append("")
    lines.append("## Outbound connections (this node calls/reads/writes)")
    for e in edges_out.get(node_id, []):
        lines.append(f"- {e['kind']} -> {e['target']}")
    if node_id not in edges_out:
        lines.append("- (none)")

    lines.append("")
    lines.append("## Inbound connections (called/read/written by)")
    for e in edges_in.get(node_id, []):
        lines.append(f"- {e['source']} -> {e['kind']}")
    if node_id not in edges_in:
        lines.append("- (none)")

    out_path = os.path.join(OUT_DIR, f"{node_id.replace(':', '_')}.txt")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    written.append(out_path)

print(f"Wrote {len(written)} packets to {OUT_DIR}")
