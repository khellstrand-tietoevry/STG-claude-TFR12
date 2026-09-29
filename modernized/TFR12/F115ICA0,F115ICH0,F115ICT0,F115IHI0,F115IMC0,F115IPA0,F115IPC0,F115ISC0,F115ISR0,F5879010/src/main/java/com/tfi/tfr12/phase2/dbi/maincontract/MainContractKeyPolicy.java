package com.tfi.tfr12.phase2.dbi.maincontract;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * RULE-018 mandatory-key validation for TF_Main_Contract, encoding
 * F115IMC0.src's H100-Check-Qualification section
 * (legacy/TFR12/src/F115IMC0.src:915-987).
 *
 * <p><b>Scope note (matches the pattern already confirmed independently in
 * F115ICA0, F115ICH0, F115IPC0, F115IPA0 and F115ICT0):</b>
 * H100-Check-Qualification is invoked from exactly one place in F115IMC0 —
 * H000-Update (F115IMC0.src:903-905, specifically the
 * {@code Perform H100-Check-Qualification} at line 905). F000-Select
 * (F115IMC0.src:669-671) and I000-Delete (F115IMC0.src:1017-1019) both instead
 * call F100-Prepare-Qualification (F115IMC0.src:715-734), which copies
 * whatever qualification fields were supplied (resolving Fin-Inst-No to
 * Fin-Inst-ID if both are given, F115IMC0.src:726-732) but performs no
 * mandatory-field check and raises no TF-*-MISSING error — a genuinely
 * missing key on Select simply surfaces later as 'DB-FOREKOMST-NF-Q' (row not
 * found, F115IMC0.src:693-711) once F200-Select-Row queries DB2, and a
 * missing key on Delete is never rejected at all before I200-Delete-Row
 * issues its DELETE. This contradicts the copybook's own documentation
 * comment ("Contract-Type and -No is mandatory for Select/Update/Delete",
 * legacy/TFR12/copybooks/R115IMC0.copy:98-101,129-130) — the comment claims a
 * check the control flow does not actually perform outside Update. The
 * generic {@link com.tfi.tfr12.phase2.dbi.DbiEngine} nonetheless applies this
 * policy uniformly before Select, Update AND Delete (see its own javadoc);
 * that is a deliberate hardening decision made for the modernized system, not
 * a faithful reproduction of F115IMC0's literal Select/Delete behavior. See
 * {@code MainContractServiceTest} for the pinned characterization of both the
 * Update-only legacy behavior and the engine's broader modern behavior.
 *
 * <p><b>Only two mandatory elements, not more:</b> unlike
 * TF_Contract_Amount (which additionally requires Currency) or
 * TF_Main_Charge/TF_Part_Charge (which additionally require role/charge
 * elements), TF_Main_Contract's H100-Check-Qualification only ever guards
 * three copybook fields — Fin-Inst, Contract-Type, Contract-No
 * (F115IMC0.src:917-947, 949-966, 968-985) — with no fourth table-specific
 * mandatory element. Customer-ID is explicitly documented as optional
 * (R115IMC0.copy:104-106,124) and is never checked by H100-Check-Qualification
 * at all.
 *
 * <p><b>Four When-branches collapse to the same two-branch shape used
 * elsewhere:</b> F115IMC0.src:918-938 checks, in order, the data-area
 * Fin-Inst-ID, the data-area Fin-Inst-No (resolved via
 * N100-Fetch-Fin-Inst-ID, F115IMC0.src:1246-1270), the qualification-area
 * In-Fin-Inst-ID, and the qualification-area In-Fin-Inst-No (also resolved) —
 * because in the legacy copybook these are four distinct fields
 * (R115IMC0.copy:118-120 vs. :149,156). {@link MainContractRow} already
 * collapses each area-pair into one field per its own javadoc, so here this
 * reduces to: Fin-Inst-ID present -> continue; else Fin-Inst-No present ->
 * resolve; else TF-FIN-INST-MISSING.
 *
 * <p><b>"Last failure wins", not fail-fast:</b> F115IMC0.src:917-985 runs its
 * three mandatory-field checks (Fin-Inst, Contract-Type, Contract-No) as
 * three independent {@code Evaluate True ... End-Evaluate} blocks in strict
 * sequence with no early exit. A failing check calls Z000-Error-Message,
 * which overwrites the single scalar {@code R7919999-Message} error-text
 * field and sets Return-Error, but execution falls through into the next
 * check regardless. So when more than one field is missing, the mnemonic
 * that is actually visible to the caller is whichever check failed LAST —
 * Contract-No, then Contract-Type, then Fin-Inst, in that order of survival —
 * not the first field found missing. This implementation reproduces that
 * ordering rather than throwing on the first violation. (Contract-Type and
 * Contract-No share the same mnemonic, TF-CONTRACT-MISSING, so this ordering
 * is only observable between Fin-Inst and {Contract-Type, Contract-No}.)
 *
 * <p>Insert (G000-Insert / G100-Move-Input-To-DCL, F115IMC0.src:764-873) is
 * deliberately NOT covered by this policy — it takes the key wholly from
 * input with no equivalent check (RULE-016's documented asymmetry), matching
 * {@link KeyPolicy}'s own contract.
 */
public final class MainContractKeyPolicy implements KeyPolicy<MainContractKey, MainContractRow> {

    /** F115IMC0.src:939-946. */
    public static final String FIN_INST_MISSING = "TF-FIN-INST-MISSING";

    /** F115IMC0.src:958-965 (Contract-Type) and :977-984 (Contract-No) — same mnemonic for both. */
    public static final String CONTRACT_MISSING = "TF-CONTRACT-MISSING";

    @Override
    public MainContractKey resolveKey(MainContractRow record, FinInstResolver resolver) {
        String pendingFailure = null;

        // F115IMC0.src:917-947 - Fin-Inst-ID present -> continue; else resolve
        // Fin-Inst-No via F5879010 (N100-Fetch-Fin-Inst-ID, F115IMC0.src:1246-
        // 1270); if neither is present, TF-FIN-INST-MISSING (F115IMC0.src:939-
        // 946) — deferred like the other two checks below, since the legacy
        // 'When other' arm does not stop the paragraph either.
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0) {
            if (record.finInstNo() != null && !record.finInstNo().isBlank()) {
                // A resolver that cannot resolve the number mirrors the legacy
                // N100-Fetch-Fin-Inst-ID abending when F5879010 fails
                // (F115IMC0.src:1260-1263) rather than a soft missing-field
                // case, so we let it propagate immediately instead of
                // deferring it with the other checks.
                resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
            } else {
                pendingFailure = FIN_INST_MISSING;
            }
        }

        // F115IMC0.src:949-966
        if (record.contractType() == null || record.contractType().isBlank()) {
            pendingFailure = CONTRACT_MISSING;
        }

        // F115IMC0.src:968-985 (same mnemonic as Contract-Type, above)
        if (record.contractNo() == 0) {
            pendingFailure = CONTRACT_MISSING;
        }

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new MainContractKey(resolvedFinInstId, record.contractType(), record.contractNo());
    }
}
