package com.tfi.tfr12.phase2.dbi.partamount;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * Mandatory-key validation for TF_Part_Amount, encoding F115IPA0.src's
 * H100-Check-Qualification section (legacy/TFR12/src/F115IPA0.src:902-1010).
 *
 * <p><b>Scope note (verified independently for THIS table, not assumed by
 * analogy):</b> H100-Check-Qualification is invoked from exactly one place in
 * F115IPA0 — H000-Update (F115IPA0.src:890-900, specifically the
 * {@code Perform H100-Check-Qualification} at line 892). F000-Select
 * (F115IPA0.src:682-730) and I000-Delete (F115IPA0.src:1040-1047) both instead
 * call F100-Prepare-Qualification (F115IPA0.src:732-754), which copies
 * whatever qualification fields were supplied (resolving Fin-Inst-No to
 * Fin-Inst-ID if both are given, F115IPA0.src:746-752) but performs no
 * mandatory-field check and raises no TF-*-MISSING error — a genuinely
 * missing key on Select simply surfaces later as 'DB-FOREKOMST-NF-Q' (row not
 * found, F115IPA0.src:706-728) once F200-Select-Row queries DB2, and a missing
 * key on Delete is never rejected at all before I200-Delete-Row issues its
 * DELETE. This matches the same Update-only pattern already confirmed
 * independently for F115ICA0/F115ICH0/F115IPC0 — it is not simply assumed
 * here by analogy. It also contradicts the copybook's own documentation
 * comment ("For Select, Update and Delete, use: ... Fin-Inst-No or
 * Fin-Inst-ID, Contract-Type, Contract-No, Currency, Part Amount Sequence
 * Number", copybooks/R115IPA0.copy:102-107) — comments claim a check that the
 * control flow does not actually perform outside Update. The generic
 * {@link com.tfi.tfr12.phase2.dbi.DbiEngine} nonetheless applies this policy
 * uniformly before Select, Update AND Delete (see its own javadoc); that is a
 * deliberate hardening decision made for the modernized system, not a
 * faithful reproduction of F115IPA0's literal Select/Delete behavior. See
 * {@code PartAmountServiceTest} for the pinned characterization of both the
 * Update-only legacy behavior and the engine's broader modern behavior.
 *
 * <p><b>Also NOT enforced even within H000-Update itself:</b> unlike a
 * fail-fast validator, H100-Check-Qualification's failure (setting
 * Return-Error, F115IPA0.src:930-931 etc.) does not stop H000-Update
 * (F115IPA0.src:890-899) from going on to call G100-Move-Input-To-DCL and
 * H200-Update-Row regardless — there is no {@code If R7919999-Return-Error}
 * guard between the perform statements. In the real legacy module, whatever
 * H200-Update-Row's own call to F115IPAU subsequently returns then overwrites
 * H100's error code, so a missing-key error can be masked by the DB call's own
 * (likely also-erroring, since the key is incomplete) result. This
 * modernized {@link com.tfi.tfr12.phase2.dbi.DbiEngine}, by contrast, always
 * short-circuits the repository call when this policy throws — again a
 * deliberate hardening, not a literal translation.
 *
 * <p><b>"Last failure wins", not fail-fast:</b> F115IPA0.src:904-1008 runs its
 * five mandatory-field checks (Fin-Inst, Contract-Type, Contract-No,
 * Currency, PA-Seq-No) as five independent {@code Evaluate True ...
 * End-Evaluate} blocks in strict sequence with no early exit. A failing check
 * calls Z000-Error-Message, which overwrites the single scalar
 * {@code R7919999-Message} error-text field, but execution falls through into
 * the next check regardless. So when more than one field is missing, the
 * mnemonic that is actually visible to the caller is whichever check failed
 * LAST — PA-Seq-No, then Currency, then Contract-No, then Contract-Type, then
 * Fin-Inst, in that order of survival — not the first field found missing.
 * This implementation reproduces that ordering rather than throwing on the
 * first violation.
 *
 * <p><b>Shared error mnemonic across two field pairs:</b> unlike
 * ContractAmountKeyPolicy (which has a dedicated CURRENCY_MISSING mnemonic),
 * F115IPA0 reuses {@code TF-CONTRACT-MISSING} for BOTH Contract-Type
 * (F115IPA0.src:944-950) and Contract-No (F115IPA0.src:963-969), and reuses
 * {@code TF-PART-AMOUNT-MISSING} for BOTH Currency (F115IPA0.src:982-988) and
 * PA-Seq-No (F115IPA0.src:1000-1006). So when e.g. Currency is missing but
 * PA-Seq-No is present, the error code is indistinguishable from PA-Seq-No
 * being missing but Currency present — the legacy mnemonic alone cannot tell
 * the two apart.
 *
 * <p>Insert (G000-Insert / G100-Move-Input-To-DCL, F115IPA0.src:784-860) is
 * deliberately NOT covered by this policy — it takes the key wholly from
 * input with no equivalent check (RULE-016's documented asymmetry), matching
 * {@link KeyPolicy}'s own contract.
 */
public final class PartAmountKeyPolicy implements KeyPolicy<PartAmountKey, PartAmountRow> {

    /** F115IPA0.src:925-931. */
    public static final String FIN_INST_MISSING = "TF-FIN-INST-MISSING";

    /** F115IPA0.src:944-950 (Contract-Type) and :963-969 (Contract-No) — same mnemonic for both. */
    public static final String CONTRACT_MISSING = "TF-CONTRACT-MISSING";

    /** F115IPA0.src:982-988 (Currency) and :1000-1006 (PA-Seq-No) — same mnemonic for both. */
    public static final String PART_AMOUNT_MISSING = "TF-PART-AMOUNT-MISSING";

    @Override
    public PartAmountKey resolveKey(PartAmountRow record, FinInstResolver resolver) {
        String pendingFailure = null;

        // F115IPA0.src:904-933 - "When R115IPA0-Fin-Inst-ID Not = +0 -> Continue";
        // else resolve Fin-Inst-No via F5879010 (N100-Fetch-Fin-Inst-ID,
        // F115IPA0.src:1262-1286); if neither is present, TF-FIN-INST-MISSING
        // (F115IPA0.src:925-931) - deferred like the other four checks below,
        // since the legacy 'When other' arm does not stop the paragraph either.
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0) {
            if (record.finInstNo() != null && !record.finInstNo().isBlank()) {
                // A resolver that cannot resolve the number mirrors the legacy
                // N100-Fetch-Fin-Inst-ID abending when F5879010 fails
                // (F115IPA0.src:1276-1279) rather than a soft missing-field
                // case, so we let it propagate immediately instead of
                // deferring it with the other checks.
                resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
            } else {
                pendingFailure = FIN_INST_MISSING;
            }
        }

        // F115IPA0.src:935-952
        if (record.contractType() == null || record.contractType().isBlank()) {
            pendingFailure = CONTRACT_MISSING;
        }

        // F115IPA0.src:954-971 (same mnemonic as Contract-Type, above)
        if (record.contractNo() == 0) {
            pendingFailure = CONTRACT_MISSING;
        }

        // F115IPA0.src:973-990
        if (record.currency() == null || record.currency().isBlank()) {
            pendingFailure = PART_AMOUNT_MISSING;
        }

        // F115IPA0.src:992-1008 (same mnemonic as Currency, above)
        if (record.paSeqNo() == 0) {
            pendingFailure = PART_AMOUNT_MISSING;
        }

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new PartAmountKey(resolvedFinInstId, record.contractType(), record.contractNo(),
                record.currency(), record.paSeqNo());
    }
}
