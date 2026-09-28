# TFR12 — Legacy COBOL (STG_TRADEFINANCE)

Standalone copy of the legacy COBOL source for the mainframe **TFR12** function
(trade-finance charge read/processing), extracted for modernization analysis
with the `code-modernization` plugin.

## Layout

```
legacy/STG/TFR12/
├── src/         21 COBOL programs (.src) — the online/batch modules that make up TFR12
└── copybooks/   66 copybooks (.copy) — shared record layouts and includes
```

This mirrors the `legacy/$system` convention expected by the `code-modernization`
plugin's commands (`modernize-preflight`, `modernize-assess`, `modernize-map`, ...):
the system name is `STG/TFR12`, so its code lives at `legacy/STG/TFR12/`.

Nothing under `legacy/` should be edited — it is a frozen copy of the original
mainframe source, not a working copy.

## Provenance

Copied from `cob2jav/STG/TFR12/{src,copybooks}` (commit `120341cd8be45cc9119125f9c075a6248b0837e4`,
"Re-gather STG/TFR12 dependencies with full call-graph closure") in the
`tfi-workspace` monorepo container. That repo also carries `cob2jav`'s own
in-progress COBOL→Java translation pipeline output (`STG/TFR12/java`,
`runs/`, `validation/`) — deliberately excluded here since this repo holds
only the legacy COBOL source.
