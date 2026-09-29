package com.tfi.tfr12.phase2.dbi.maincontract;

/**
 * The natural (resolved) key of a TF_Main_Contract row.
 *
 * <p>Per {@code R115IMC0-Data} (legacy/TFR12/copybooks/R115IMC0.copy:146-153),
 * the copybook's "data area" key group ({@code R115IMC0-Key}) also carries a
 * Customer-ID element (R115IMC0.copy:150), but Customer-ID is NOT part of the
 * key used to identify a single row: {@code L200-Find-DCL} and
 * {@code L300-Remove-DCL} (legacy/TFR12/src/F115IMC0.src:1203-1244), the two
 * paragraphs that look an occurrence up in the module's own row cache, match
 * only on Table-Version + Fin-Inst-ID + Contract-Type + Contract-No
 * (F115IMC0.src:1208-1215, 1225-1232) — Customer-ID is never compared. The
 * copybook's own header comment agrees: "For Select, Update and Delete, use:
 * Fin-Inst-No or Fin-Inst-ID / Contract-Type / Contract-No"
 * (R115IMC0.copy:98-101); Customer is listed separately as "Optional" and
 * "only relevant for cursor" (R115IMC0.copy:104-106, 124).
 *
 * <p>The Actual-vs-Work table-version switch ({@code R115IMC0-In-Table-Version},
 * R115IMC0.copy:113-115, dispatched by {@code C100-Verify},
 * F115IMC0.src:358-380) is deliberately NOT part of this key: it selects which
 * physical table (TF_Main_Contract vs TF_Work_MC) a call reads/writes, a
 * concern this task was scoped to exclude (that switching logic lives in
 * FTFRL120.src, RULE-026 in analysis/TFR12/BUSINESS_RULES.md, out of scope
 * here).
 *
 * @param finInstId    resolved internal Fin-Inst-ID (R115IMC0-Fin-Inst-ID,
 *                     Pic S9(18) Comp-3, R115IMC0.copy:149)
 * @param contractType R115IMC0-Contract-Type, Pic X(03) (R115IMC0.copy:152)
 * @param contractNo   R115IMC0-Contract-No, Pic S9(07) Comp-3 (R115IMC0.copy:153)
 */
public record MainContractKey(long finInstId, String contractType, long contractNo) {
}
