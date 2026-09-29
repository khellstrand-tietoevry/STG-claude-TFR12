package com.tfi.tfr12.phase2.dbi.maincontract;

/**
 * Row DTO for TF_Main_Contract, the most heavily-used table in the system
 * (per this task's own framing) — mirroring the mandatory-key-relevant part
 * of {@code R115IMC0-Data} (legacy/TFR12/copybooks/R115IMC0.copy:146-171) plus
 * a representative sample of the data fields, not the full column list
 * (R115IMC0.copy also has KFAV-Seq-No, Register-Date, Upd-User-ID/-Timestamp/
 * -Date/-Time, Issued-Date, Confirmed, Incom-Swift, Silent-Confrm, and the
 * Out-Row-No/Out-Total-Rows cursor accumulators, which are omitted here as
 * non-representative / cursor-paging concerns).
 *
 * <p><b>Simplification vs. the legacy copybook (same decision already made for
 * the sibling tables, e.g. {@code ContractAmountRow}):</b> R115IMC0 carries
 * each key element twice — once in the "data area"
 * ({@code R115IMC0-Fin-Inst-ID}, {@code -Contract-Type}, {@code -Contract-No})
 * and once in the parallel "qualification area" ({@code R115IMC0-In-Fin-Inst-ID}
 * / {@code -In-Fin-Inst-No}, {@code -In-Contract-Type}, {@code -In-Contract-No},
 * R115IMC0.copy:111-136 vs. :148-153) — purely as a wire-format artifact of the
 * old single-copybook request/response transport. This row collapses each such
 * pair into one field: whichever value the caller supplied ends up here, and
 * {@link MainContractKeyPolicy} validates this single set, matching the
 * fallback the legacy also performs (F115IMC0.src:917-947, 949-966, 968-985)
 * when only the qualification-area field was given. Unlike the sibling tables
 * checked so far, F115IMC0's own H100-Check-Qualification actually has FOUR
 * When-branches for Fin-Inst alone (data-ID, data-No, qualification-ID,
 * qualification-No — F115IMC0.src:918-938) rather than two, precisely because
 * the two areas are distinct copybook fields there; once collapsed onto one
 * {@code finInstId}/{@code finInstNo} pair here, the observable behavior
 * reduces to the same two-branch shape used elsewhere (ID present -> continue,
 * else No present -> resolve via F5879010, else missing) — see
 * {@link MainContractKeyPolicy}'s own javadoc for the detailed trace.
 *
 * <p><b>In-Progress</b> ({@code R115IMC0-In-Progress}, Pic X(01), 88-levels
 * {@code R115IMC0-In-Progress-Yes} = {@code 'Y'} and
 * {@code R115IMC0-In-Progress-No} = {@code 'N'}, R115IMC0.copy:169-171) is
 * included here as a meaningfully distinctive field for this table: elsewhere
 * in the system (FTFRL120.src:233-246, RULE-026 in
 * analysis/TFR12/BUSINESS_RULES.md, "confirmed" per
 * analysis/TFR12/RULE_REVIEWS.json) it drives whether a whole downstream
 * request reads the live TF_Main_Contract/TF_Contract_Amount/... tables or
 * their TF_Work_* draft counterparts. That Actual-vs-Work switching logic
 * itself is out of scope for this module's own DBI contract and is not
 * exercised by {@link MainContractKeyPolicy} or its tests — only the raw flag
 * value, as F115IMC0 itself reads and writes it
 * (F115IMC0.src:809, 1083), is modeled here.
 *
 * @param finInstId    resolved internal Fin-Inst-ID, or 0 if not yet resolved
 *                     (R115IMC0-Fin-Inst-ID / -In-Fin-Inst-ID, Comp-3,
 *                     R115IMC0.copy:119-120,149)
 * @param finInstNo    external Fin-Inst-No, used to resolve {@code finInstId}
 *                     via {@link com.tfi.tfr12.phase2.dbi.FinInstResolver} when
 *                     it is not already known (R115IMC0-Fin-Inst-No /
 *                     -In-Fin-Inst-No, Pic X(18), R115IMC0.copy:119,156)
 * @param contractType R115IMC0-Contract-Type / -In-Contract-Type, Pic X(03)
 *                     (R115IMC0.copy:132,152)
 * @param contractNo   R115IMC0-Contract-No / -In-Contract-No, Pic S9(07) Comp-3
 *                     (R115IMC0.copy:133,153)
 * @param customerId   resolved internal Customer-ID, or 0 if not applicable
 *                     (R115IMC0-Customer-ID, Pic S9(11) Comp-3,
 *                     R115IMC0.copy:150) — optional, never part of the
 *                     mandatory key (see {@link MainContractKey}'s javadoc)
 * @param status       free-form contract status (R115IMC0-Status, Pic X(12),
 *                     R115IMC0.copy:166)
 * @param availability single-char availability code (R115IMC0-Availability,
 *                     Pic X(01), R115IMC0.copy:160-164): 88-levels
 *                     {@code S} = At-Sight, {@code D} = Deferred,
 *                     {@code A} = Accept, {@code M} = Mixed
 * @param inProgress   raw In-Progress flag value: {@code "Y"} or {@code "N"}
 *                     per the 88-levels above, or any other single character /
 *                     blank as the field carries no other validation
 *                     (R115IMC0-In-Progress, Pic X(01), R115IMC0.copy:169-171)
 */
public record MainContractRow(
        long finInstId,
        String finInstNo,
        String contractType,
        long contractNo,
        long customerId,
        String status,
        String availability,
        String inProgress) {
}
