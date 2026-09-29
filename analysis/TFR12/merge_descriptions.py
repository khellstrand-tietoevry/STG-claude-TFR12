#!/usr/bin/env python3
"""Merge subagent-written node descriptions into topology.json, after checking
each identifier-like token the description names (module IDs, table names)
actually appears in that node's own packet file. A description that fails the
check is dropped (the node is left without a description) rather than trusted
blind, per the extraction rule: untrusted excerpts, verify before merging.
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
TOPOLOGY_PATH = os.path.join(HERE, "topology.json")
DESCRIPTIONS_PATH = os.path.join(HERE, "descriptions.json")
PACKETS_DIR = os.path.join(HERE, "node_packets")

topology = json.load(open(TOPOLOGY_PATH, encoding="utf-8"))
descriptions = json.load(open(DESCRIPTIONS_PATH, encoding="utf-8"))

leaves = {}
for domain in topology["root"]["children"]:
    for child in domain["children"]:
        leaves[child["id"]] = child

# All valid module/table identifiers in this system, used to know which tokens
# in a description are "identifier-like" and therefore must be checked.
KNOWN_IDS = set(leaves.keys()) | {leaves[i]["name"] for i in leaves}
IDENT_RE = re.compile(r"\b[A-Z][A-Z0-9-]{3,}\b")

accepted, dropped = [], []
for node_id, desc in descriptions.items():
    if node_id not in leaves:
        dropped.append((node_id, "not a known node id"))
        continue
    packet_path = os.path.join(PACKETS_DIR, f"{node_id.replace(':', '_')}.txt")
    packet_text = open(packet_path, encoding="utf-8").read()

    mentioned = {m for m in IDENT_RE.findall(desc) if m in KNOWN_IDS}
    missing = [m for m in mentioned if m not in packet_text]
    if missing:
        dropped.append((node_id, f"identifiers not found in packet: {missing}"))
        continue

    leaves[node_id]["description"] = desc
    accepted.append(node_id)

with open(TOPOLOGY_PATH, "w", encoding="utf-8") as f:
    json.dump(topology, f, indent=2)

print(f"Accepted descriptions: {len(accepted)}/{len(descriptions)}")
if dropped:
    print("Dropped:")
    for node_id, reason in dropped:
        print(f"  - {node_id}: {reason}")
print(f"Wrote {TOPOLOGY_PATH}")
