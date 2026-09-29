package com.tfi.tfr12.phase2.dbi.syschargetype;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * RULE-018 pattern for TF_Sys_Charge_Type: H100-Check-Qualification
 * (F115ICT0.src:738-787). No RULE-NNN id was independently extracted for this
 * table's own field set in {@code analysis/TFR12/BUSINESS_RULES.md} — this
 * table was not flagged as distinctive during rule mining — but the
 * card for RULE-018 explicitly documents that "the same pattern... is
 * repeated independently in every F115I*0 table-interface module, so a fix
 * or change to key validation must be replicated across all of them"
 * (BUSINESS_RULES.md:291), and F115ICT0 is one of those modules. This class
 * is that per-table replication, read and verified directly against
 * F115ICT0.src rather than assumed from a sibling.
 *
 * <p><b>What the legacy source actually gates:</b> H100-Check-Qualification
 * is performed by H000-Update alone (F115ICT0.src:726-736, "Perform
 * H100-Check-Qualification" at line 728) immediately before
 * G100-Move-Input-To-DCL and H200-Update-Row. G000-Insert
 * (F115ICT0.src:655-663) calls G100-Move-Input-To-DCL directly and performs
 * no equivalent check at all — the same RULE-016/018 insert/update asymmetry
 * documented for the sibling tables.
 *
 * <p><b>Divergence confirmed, not assumed:</b> F000-Select
 * (F115ICT0.src:562-607) and I000-Delete (F115ICT0.src:817-824) both call
 * F100-Prepare-Qualification (F115ICT0.src:609-627) instead of
 * H100-Check-Qualification. F100-Prepare-Qualification moves the {@code In-}
 * qualification fields straight into the DCL host variables (resolving
 * Fin-Inst-No to Fin-Inst-ID via N100-Fetch-Fin-Inst-ID if needed) and
 * performs no blank/zero check and raises none of the {@code TF-*-MISSING}
 * errors. So in the real F115ICT0, Select and Delete can run — and simply not
 * find a row (error {@code DB-FOREKOMST-NF-Q}, F115ICT0.src:586-604) — with
 * an incomplete key; only Update is actually protected. This matches the
 * pattern already independently confirmed for F115ICA0, F115ICH0 and
 * F115IPC0, so it is not a coincidence specific to any one table. This is
 * narrower than {@link com.tfi.tfr12.phase2.dbi.DbiEngine}'s own documented
 * contract ("Key validation... runs before Select, Update and Delete"), which
 * applies this policy uniformly to Select/Update/Delete for every table wired
 * through it — a deliberate engine-level design choice (out of this module's
 * scope to change), flagged here rather than silently assumed to match
 * F115ICT0's own Select/Delete paragraphs.
 *
 * <p>Exact source mapping (mandatory field -&gt; legacy error mnemonic):
 * <ul>
 *   <li>CT-Type -&gt; {@code TF-TYPE-MISSING} (F115ICT0.src:740-754)</li>
 *   <li>Fin-Inst-Id / Fin-Inst-No -&gt; {@code TF-FININST-MISSING}
 *       (F115ICT0.src:756-787)</li>
 * </ul>
 * Note the exact spelling: {@code TF-FININST-MISSING} (one word, no hyphen
 * between FIN and INST), which is literally what F115ICT0.src:781 moves to
 * R7918030-In-MessageCode — it is NOT the same string as the sibling
 * modules' {@code TF-FIN-INST-MISSING} (MainCharge/PartCharge/ContractAmount).
 * This is exactly the kind of divergence RULE-018's card warns about: each
 * module reimplements the pattern independently, so even the error mnemonic
 * text is not guaranteed consistent across tables.
 *
 * <p><b>Regno is deliberately not validated here</b> — see
 * {@link SysChargeTypeKey}'s javadoc. H100-Check-Qualification contains
 * exactly 2 {@code Evaluate True} blocks (CT-Type at F115ICT0.src:740-754,
 * Fin-Inst at F115ICT0.src:756-787); there is no third block for Regno, and
 * no {@code TF-REGNO-MISSING}-shaped mnemonic exists anywhere in F115ICT0.src.
 *
 * <p><b>Flagged COBOL comparison quirk (F115ICT0.src:765), not modeled
 * literally:</b> the third branch of the Fin-Inst Evaluate reads
 * {@code When R115ICT0-Fin-Inst-No Not = +0}, comparing the PIC X(18)
 * alphanumeric field {@code R115ICT0-Fin-Inst-No} to the numeric literal
 * {@code +0}. Per COBOL's nonnumeric-comparison rule (one operand
 * alphanumeric forces a character-by-character comparison, with the numeric
 * literal's unedited digit string "0" padded with spaces to 18 bytes), this
 * condition is literally true not just when the field holds a real
 * institution number, but also when it holds blank/spaces (spaces are not
 * equal to {@code "0" + 17 spaces}) — i.e. the literal COBOL behavior does
 * not obviously treat "blank" as "absent" here the way a modern reader would
 * expect. This implementation instead treats blank/null {@code finInstNo} as
 * absent (via {@code isBlank}), matching the same modeling choice already
 * made for {@code MainChargeKeyPolicy}/{@code PartChargeKeyPolicy}'s
 * equivalent fallback branch. This is flagged rather than silently assumed:
 * it has not been proven byte-for-byte equivalent to the literal legacy
 * comparison for a genuinely blank {@code Fin-Inst-No}, only that it produces
 * the sane, intended-looking behavior once a resolver is asked to resolve an
 * empty string (which {@link FinInstResolver} implementations are expected to
 * reject as unresolvable, i.e. {@code TF-FININST-MISSING}, matching the net
 * effect either way).
 *
 * <p><b>"Last failure wins", not fail-fast</b> (standardized across every
 * table's {@code KeyPolicy} — see {@link KeyPolicy}'s class javadoc): the 2
 * checks above are each their own independent {@code Evaluate True}, and the
 * source does not short-circuit between them — both run regardless of
 * whether the first already failed. Z000-Error-Message
 * (F115ICT0.src:1103-1121, shared by every F115I*0 module) overwrites a
 * single R7919999-Message field on every call, so when both mandatory fields
 * are missing at once, only the LAST failing check's mnemonic
 * ({@code TF-FININST-MISSING}) survives to the caller. This implementation
 * reproduces that ordering.
 *
 * <p>{@code regno} rides along on the returned key (required for physical row
 * identity, per {@link SysChargeTypeKey}'s javadoc) but is not itself
 * validated — H100 never checks it.
 */
public final class SysChargeTypeKeyPolicy implements KeyPolicy<SysChargeTypeKey, SysChargeTypeRow> {

    @Override
    public SysChargeTypeKey resolveKey(SysChargeTypeRow record, FinInstResolver resolver) {
        String pendingFailure = null;

        // F115ICT0.src:740-754 — TF-TYPE-MISSING
        if (isBlank(record.ctType())) {
            pendingFailure = "TF-TYPE-MISSING";
        }

        // F115ICT0.src:756-787 — TF-FININST-MISSING
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0L) {
            if (isBlank(record.finInstNo())) {
                pendingFailure = "TF-FININST-MISSING";
            } else {
                // Resolver itself throws MissingKeyFieldException("TF-FIN-INST-MISSING")
                // if the external number cannot be resolved (N100-Fetch-Fin-Inst-ID,
                // F115ICT0.src:988-1012); the legacy mnemonic string it raises
                // (shared FinInstResolver contract) differs from this policy's own
                // TF-FININST-MISSING — see class javadoc.
                resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
            }
        }

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new SysChargeTypeKey(record.ctType(), resolvedFinInstId, record.regno());
    }

    /** COBOL "Not = Space": blank means null or all-blank/whitespace. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
