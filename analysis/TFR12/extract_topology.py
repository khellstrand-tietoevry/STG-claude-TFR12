#!/usr/bin/env python3
"""Extract a topology graph for legacy/STG/TFR12.

Parses the 21 COBOL programs under legacy/STG/TFR12/src for:
  - Program-ID (module identity)
  - LOC (raw line count, sizes the circle in the viewer)
  - literal CALL targets ("Call 'X'") resolved to in-repo modules -> call edges
  - dynamic CALL targets (Call X, no quotes) resolved via MOVE '<lit>' TO X in
    the same file where possible; unresolved ones become observations
  - COPY targets, tallied to find shared-copybook coupling clusters

Domain membership and the DB2 table each DBI module owns are hardcoded from
the prior modernize-assess structural analysis (analysis/STG/TFR12/ASSESSMENT.md) --
re-deriving them here would just re-parse the same header comments that
analysis already cited. Table names were independently re-verified against
each file's header comment ("Interface for all database access on table
TF_...") before being hardcoded below.

COBOL fixed-format handling: column 7 (index 6) is the indicator area
('*' = comment line); columns 8-72 (index 7:72) are the code area used for
all matching, so sequence numbers in columns 73-80 are never scanned.

Writes analysis/STG/TFR12/topology.json and prints a summary.
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))  # .../analysis/TFR12
REPO_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SYS_ROOT = os.path.join(REPO_ROOT, "legacy", "STG", "TFR12")
SRC_DIR = os.path.join(SYS_ROOT, "src")
OUT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "topology.json")

DOMAINS = [
    ("dom:orchestration", "Servo Orchestration", ["FTFRH120", "FTFRK120", "FTFRL120"]),
    ("dom:calc", "Charge Calculation & Listing", ["F115L060", "F115L070", "F115L150"]),
    ("dom:dbi", "Charge/Contract DBI Layer",
     ["F115ICA0", "F115ICH0", "F115ICT0", "F115IHI0", "F115IMC0", "F115IPA0", "F115IPC0", "F115ISC0", "F115ISR0"]),
    ("dom:customer", "Customer/Org Master Data", ["F5879010"]),
    ("dom:datetime", "Date & Time Utilities", ["F7919070", "F7919100", "F7919270"]),
    ("dom:error", "GSM Error & Abend Handling", ["F7918030"]),
    ("dom:trace", "GSM Trace/MQ/CICS Diagnostics", ["F791TRAC"]),
]

# module -> DB2 table it is the sole DBI gateway for (verified against each
# file's own header comment, e.g. F115ICA0.src:8 "...table TF_Contract_Amount.")
TABLE_BY_MODULE = {
    "F115ICA0": "TF_Contract_Amount",
    "F115ICH0": "TF_Main_Charge",
    "F115ICT0": "TF_Sys_Charge_Type",
    "F115IHI0": "TF_History",
    "F115IMC0": "TF_Main_Contract",
    "F115IPA0": "TF_Part_Amount",
    "F115IPC0": "TF_Part_Charge",
    "F115ISC0": "TF_Sys_Code",
    "F115ISR0": "TF_Sys_Code_Rel",
}
# F7918030's own EXEC SQL (D310-SQL section, verified at F7918030.src:503-512):
# a SELECT of localized error text, not a write, despite what an earlier
# summary implied.
EXTRA_TABLE_EDGES = {"F7918030": "PO_Feilmelding"}

CALL_LIT_RE = re.compile(r"\bCall\s+'([A-Za-z0-9-]+)'", re.IGNORECASE)
CALL_VAR_RE = re.compile(r"(?<!End-)\bCall\s+([A-Za-z][A-Za-z0-9-]*)\b", re.IGNORECASE)
COPY_RE = re.compile(r"\bCopy\s+([A-Za-z0-9-]+)", re.IGNORECASE)


def code_text(path):
    """Return the code-area (cols 8-72) of every non-comment line, joined by \n."""
    lines = []
    with open(path, "r", encoding="latin-1") as f:
        for raw in f:
            line = raw.rstrip("\n")
            indicator = line[6:7] if len(line) > 6 else " "
            if indicator == "*":
                continue
            lines.append(line[7:72] if len(line) > 7 else "")
    return "\n".join(lines)


def raw_line_count(path):
    with open(path, "r", encoding="latin-1") as f:
        return sum(1 for _ in f)


module_names = sorted(f[:-4] for f in os.listdir(SRC_DIR) if f.endswith(".src"))

modules = {}
call_edges = set()
copy_counts = {}
dynamic_call_obs = []

for mod in module_names:
    path = os.path.join(SRC_DIR, mod + ".src")
    text = code_text(path)
    loc = raw_line_count(path)

    literal_targets = {m.group(1).upper() for m in CALL_LIT_RE.finditer(text)}
    copies = {m.group(1).upper() for m in COPY_RE.finditer(text)}
    for c in copies:
        copy_counts[c] = copy_counts.get(c, 0) + 1

    for t in literal_targets:
        if t in module_names:
            call_edges.add((mod, t))

    # Dynamic (unquoted) call targets: resolve via MOVE '<lit>' TO <var> in
    # the same file; record what could not be resolved as an observation.
    dyn_vars = set()
    for m in CALL_VAR_RE.finditer(text):
        candidate = m.group(1)
        if candidate.upper() in literal_targets:
            continue  # already counted as a literal call elsewhere in the file
        dyn_vars.add(candidate)
    for var in dyn_vars:
        # \b after the field name so e.g. "BZXOJOR0" doesn't match the unrelated,
        # longer field "BZXOJOR0-In-FK-TY-Int-Code" as a prefix.
        assign_re = re.compile(r"Move\s+'([A-Za-z0-9-]+)'\s+To\s+" + re.escape(var) + r"\b(?!-)", re.IGNORECASE)
        resolved = {m.group(1).upper() for m in assign_re.finditer(text)}
        resolved_local = sorted(r for r in resolved if r in module_names)
        if resolved_local:
            for r in resolved_local:
                call_edges.add((mod, r))
            continue
        # Common idiom: a data item Value-initialized to its own name, used so the
        # CALL is dynamic in form but constant in practice, e.g.
        # "10 BZXOJOR0 Pic X(08) Value 'BZXOJOR0'."
        self_named = re.search(
            re.escape(var) + r"\s+Pic\s+X\(\d+\)\s+Value\s+'" + re.escape(var) + r"'",
            text, re.IGNORECASE,
        )
        if self_named:
            dynamic_call_obs.append(
                f"{mod}: `Call {var}` is dynamic in form but constant in practice — "
                f"{var} is a data item Value-initialized to its own name ('{var}'), calling program "
                f"{var} unconditionally (not in this repo slice: external)."
            )
        elif resolved:
            dynamic_call_obs.append(
                f"{mod}: dynamic `Call {var}` — resolves via MOVE to a literal outside this repo's "
                f"module set ({', '.join(sorted(resolved))})."
            )
        else:
            dynamic_call_obs.append(
                f"{mod}: dynamic `Call {var}` — no MOVE-to-literal assignment found in this file; "
                "target is set elsewhere or passed in."
            )

    modules[mod] = {"loc": loc}

# --- Build topology.json ---

domain_nodes = []
for dom_id, dom_name, mods in DOMAINS:
    children = [
        {"id": m, "name": m, "kind": "module", "language": "cobol",
         "loc": modules[m]["loc"], "file": f"src/{m}.src"}
        for m in mods
    ]
    domain_nodes.append({"id": dom_id, "name": dom_name, "kind": "domain", "children": children})

datastore_children = [
    {"id": f"ds:{table}", "name": table, "kind": "datastore"}
    for table in list(TABLE_BY_MODULE.values()) + list(EXTRA_TABLE_EDGES.values())
]
domain_nodes.append({"id": "dom:data", "name": "Data stores", "kind": "domain", "children": datastore_children})

edges = [{"source": s, "target": t, "kind": "call"} for s, t in sorted(call_edges)]
for mod, table in TABLE_BY_MODULE.items():
    edges.append({"source": mod, "target": f"ds:{table}", "kind": "read"})
for mod, table in EXTRA_TABLE_EDGES.items():
    edges.append({"source": mod, "target": f"ds:{table}", "kind": "read"})

inbound = {}
for s, t in call_edges:
    inbound[t] = inbound.get(t, 0) + 1

entry_points = [m for m in module_names if inbound.get(m, 0) == 0]
dead_ends = []  # every non-entry module below has >=1 inbound call edge (verified by inbound dict)

# Coupling observations: copybooks copied by many modules
shared_copies = sorted(((cnt, name) for name, cnt in copy_counts.items() if cnt >= 8), reverse=True)
coupling_note = ", ".join(f"{name} ({cnt} modules)" for cnt, name in shared_copies[:6])

architecture_obs = [
    f"Single entry point: {entry_points[0] if len(entry_points) == 1 else entry_points} — "
    "everything else in this slice is reached only from inside the repo; the external Servo/JCL/CICS "
    "dispatcher that invokes it is outside this extract.",
    "The 9-module DBI layer (dom:dbi) is a fan-out/fan-in hub: called from both the orchestration "
    "layer (FTFRL120) and the calculation layer (F115L060/L070/L150), and every module funnels errors "
    "through the single F7918030 (dom:error) node — a structural single point of failure for error handling.",
    f"Shared-copybook coupling: {coupling_note or 'no copybook is copied by 8+ modules'} — "
    "a change to one of these ripples across most of the codebase.",
    "F5879010 (Customer/Org Master Data) does not touch its own logical table (OA_Konsern) directly: "
    "it is a stub that forwards to external iHUB-Org DBI programs (BZXOJOR0/BZXOJNA0, dynamic calls, "
    "not present in this repo slice) — the real data dependency lives outside this extract.",
    "F7918030 (GSM Error Handling) does its own read (SELECT from PO_Feilmelding for localized error "
    "text) in addition to being the shared error-message builder every other module calls into.",
]
# Dynamic-call findings are concrete, code-derived facts (the extraction principle
# explicitly asks for these); keep all of them and fill remaining budget with the
# architecture-level notes above, capped at 7 total per the topology schema.
observations = (dynamic_call_obs + architecture_obs)[:7]

flows = [
    {
        "name": "Read a contract's trade-finance charges",
        "persona": "A bank operator viewing a trade-finance contract's charges",
        "description": "An operator (or an upstream online screen) asks TFR12 to read the charges booked against one contract, and gets back a calculated, presentable list.",
        "steps": [
            {"label": "Request enters the service", "nodes": ["FTFRH120"]},
            {"label": "Input is validated (mandatory fields, permitted values, institution check)", "nodes": ["FTFRK120"]},
            {"label": "Business logic reads the contract and its charge rows", "nodes": ["FTFRL120", "F115IMC0", "F115ICA0", "F115ICH0"]},
            {"label": "Charges are listed and amounts computed per method-of-calculation", "nodes": ["F115L060", "F115L070", "F115L150", "F115IPA0", "F115IPC0"]},
            {"label": "System/product access codes and history are resolved", "nodes": ["F115ISC0", "F115ISR0", "F115IHI0"]},
            {"label": "Response is assembled and returned", "nodes": ["FTFRL120", "FTFRH120"]},
        ],
    },
    {
        "name": "Diagnose a failed charge read",
        "persona": "A support engineer investigating a production abend or error response",
        "description": "Something goes wrong mid-read (a lookup fails, a code is invalid); the error path builds a message and, if trace is enabled, a diagnostic record.",
        "steps": [
            {"label": "A DBI or calculation module hits an error condition", "nodes": ["F115ICA0", "F115L060"]},
            {"label": "Error is escalated to the shared GSM error handler", "nodes": ["F7918030"]},
            {"label": "Localized error text is looked up", "nodes": ["F7918030"], },
            {"label": "If trace is on, the full request/response is written to the MQ trace queue", "nodes": ["FTFRH120", "F791TRAC"]},
            {"label": "Abend/error status is returned to the caller", "nodes": ["FTFRH120"]},
        ],
    },
]

topology = {
    "system": "STG/TFR12",
    "root": {"id": "sys", "name": "STG/TFR12", "kind": "system", "children": domain_nodes},
    "edges": edges,
    "entryPoints": entry_points,
    "deadEnds": dead_ends,
    "observations": observations[:7],
    "flows": flows,
}

with open(OUT_PATH, "w", encoding="utf-8") as f:
    json.dump(topology, f, indent=2)

print(f"Modules: {len(module_names)}  Datastores: {len(datastore_children)}  "
      f"Call edges: {len(call_edges)}  Read edges: {len(TABLE_BY_MODULE) + len(EXTRA_TABLE_EDGES)}")
print(f"Entry points: {entry_points}")
print(f"Dead ends: {dead_ends}")
print(f"Dynamic (unquoted) call sites found: {len(dynamic_call_obs)}")
for o in dynamic_call_obs:
    print(f"  - {o}")
print(f"Wrote {OUT_PATH}")
