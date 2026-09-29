package com.tfi.tfr12.phase2.dbi.contractamount;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * RULE-018 mandatory-key validation for TF_Contract_Amount, encoding
 * F115ICA0.src's H100-Check-Qualification section
 * (legacy/TFR12/src/F115ICA0.src:806-894).
 *
 * <p><b>Scope note (read before replicating this pattern to the other 8
 * tables):</b> H100-Check-Qualification is invoked from exactly one place in
 * F115ICA0 — H000-Update (F115ICA0.src:794-804, specifically the
 * {@code Perform H100-Check-Qualification} at line 796). F000-Select
 * (F115ICA0.src:625-693) and I000-Delete (F115ICA0.src:924-932) both instead
 * call F100-Prepare-Qualification (F115ICA0.src:673-693), which copies
 * whatever qualification fields were supplied (resolving Fin-Inst-No to
 * Fin-Inst-ID if both are given, F115ICA0.src:685-691) but performs no
 * mandatory-field check and raises no TF-*-MISSING error — a genuinely
 * missing key on Select simply surfaces later as 'DB-FOREKOMST-NF-Q' (row not
 * found, F115ICA0.src:649-667) once F200-Select-Row queries DB2, and a
 * missing key on Delete is never rejected at all before I200-Delete-Row
 * issues its DELETE. This contradicts the copybook's own documentation
 * comment ("Contract-Type and -No is mandatory for Select/Update/Delete",
 * "Currency is mandatory for Select/Update/Delete",
 * legacy/TFR12/copybooks/R115ICA0.copy:89-93,112-113,123) — comments claim a
 * check that the control flow does not actually perform outside Update. The
 * generic {@link com.tfi.tfr12.phase2.dbi.DbiEngine} nonetheless applies this
 * policy uniformly before Select, Update AND Delete (see its own javadoc);
 * that is a deliberate hardening decision made for the modernized system, not
 * a faithful reproduction of F115ICA0's literal Select/Delete behavior. See
 * {@code ContractAmountServiceTest} for the pinned characterization of both
 * the Update-only legacy behavior and the engine's broader modern behavior.
 *
 * <p><b>"Last failure wins", not fail-fast:</b> F115ICA0.src:808-892 runs its
 * four mandatory-field checks (Fin-Inst, Contract-Type, Contract-No,
 * Currency) as four independent {@code Evaluate True ... End-Evaluate}
 * blocks in strict sequence with no early exit. A failing check calls
 * Z000-Error-Message, which overwrites the single scalar
 * {@code R7919999-Message} error-text field (F115ICA0.src:1241-1242) and sets
 * Return-Error, but execution falls through into the next check regardless.
 * So when more than one field is missing, the mnemonic that is actually
 * visible to the caller is whichever check failed LAST — Currency, then
 * Contract-No, then Contract-Type, then Fin-Inst, in that order of survival —
 * not the first field found missing. This implementation reproduces that
 * ordering rather than throwing on the first violation.
 *
 * <p>Insert (G000-Insert / G100-Move-Input-To-DCL, F115ICA0.src:723-764) is
 * deliberately NOT covered by this policy — it takes the key wholly from
 * input with no equivalent check (RULE-016's documented asymmetry), matching
 * {@link KeyPolicy}'s own contract.
 */
public final class ContractAmountKeyPolicy implements KeyPolicy<ContractAmountKey, ContractAmountRow> {

    /** F115ICA0.src:829-836. */
    public static final String FIN_INST_MISSING = "TF-FIN-INST-MISSING";

    /** F115ICA0.src:848-854 (Contract-Type) and :867-873 (Contract-No) — same mnemonic for both. */
    public static final String CONTRACT_MISSING = "TF-CONTRACT-MISSING";

    /** F115ICA0.src:884-890. */
    public static final String CURRENCY_MISSING = "TF-CURRENCY-MISSING";

    @Override
    public ContractAmountKey resolveKey(ContractAmountRow record, FinInstResolver resolver) {
        String pendingFailure = null;

        // F115ICA0.src:808-837 — Fin-Inst-ID present -> continue; else resolve
        // Fin-Inst-No via F5879010 (N100-Fetch-Fin-Inst-ID, F115ICA0.src:1111-
        // 1135); if neither is present, TF-FIN-INST-MISSING (F115ICA0.src:829-
        // 836) — deferred like the other three checks below, since the legacy
        // 'When other' arm does not stop the paragraph either. The RESOLVED id
        // is what the returned key carries — never the raw external number —
        // per DbiEngine's contract (fixed after architecture review found the
        // resolved id was previously discarded instead of reaching the
        // repository lookup).
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0) {
            if (record.finInstNo() != null && !record.finInstNo().isBlank()) {
                // A resolver that cannot resolve the number mirrors the legacy
                // N100-Fetch-Fin-Inst-ID abending when F5879010 fails
                // (F115ICA0.src:1125-1128) rather than a soft missing-field
                // case, so we let it propagate immediately instead of
                // deferring it with the other checks.
                resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
            } else {
                pendingFailure = FIN_INST_MISSING;
            }
        }

        // F115ICA0.src:839-856
        if (record.contractType() == null || record.contractType().isBlank()) {
            pendingFailure = CONTRACT_MISSING;
        }

        // F115ICA0.src:858-875 (same mnemonic as Contract-Type, above)
        if (record.contractNo() == 0) {
            pendingFailure = CONTRACT_MISSING;
        }

        // F115ICA0.src:877-892
        if (record.currency() == null || record.currency().isBlank()) {
            pendingFailure = CURRENCY_MISSING;
        }

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new ContractAmountKey(resolvedFinInstId, record.contractType(), record.contractNo(), record.currency());
    }
}
