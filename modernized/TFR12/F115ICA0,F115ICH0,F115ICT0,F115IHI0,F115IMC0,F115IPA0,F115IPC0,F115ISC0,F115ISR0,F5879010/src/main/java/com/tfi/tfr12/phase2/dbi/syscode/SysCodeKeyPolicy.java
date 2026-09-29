package com.tfi.tfr12.phase2.dbi.syscode;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * Key validation for {@code TF_Sys_Code}, ported from legacy
 * H100-Check-Qualification (F115ISC0.src:739-815), only ever invoked from
 * H000-Update (F115ISC0.src:729) - i.e. only wired ahead of Update in
 * {@code DbiEngine}, exactly matching the legacy call graph (Select/Delete
 * use the unchecked F100-Prepare-Qualification instead, F115ISC0.src:621-640
 * / 845).
 *
 * <h2>RULE-017 - confirmed-preserve (do not "fix")</h2>
 *
 * <p>Re-read directly from {@code legacy/TFR12/src/F115ISC0.src:739-815} for
 * this port (not just taken from the rule card summary):
 *
 * <pre>
 * 741   Evaluate True                                  (ST-Type)
 * 742     When R115ISC0-ST-Type       Not = Space
 * 743       Continue
 * 745     When R115ISC0-In-ST-Type    Not = Space
 * 746       Move R115ISC0-In-ST-Type  To    R115ISC0-ST-Type
 * 748     When other
 * 749-753   ... 'TF-TYPE-MISSING' ... Set Return-Error
 * 755   End-Evaluate                                   &lt;- closes its OWN evaluate
 *
 * 757   Evaluate True                                  (Institution)
 * 758-772 ... four branches (current Id / In-Id / current No+resolve / In-No+resolve)
 * 774     When other
 * 775-779   ... 'TF-FIN-INST-MISSING' ... Set Return-Error
 * 781   End-Evaluate                                   &lt;- closes its OWN evaluate
 *
 * 783   Evaluate True                                  (Contract-Type)
 * 784     When R115ISC0-Contr-Type    Not = Space
 * 785       Continue
 * 787     When R115ISC0-In-Contr-Type Not = Space
 * 788       Move R115ISC0-In-Contr-Type To  R115ISC0-Contr-Type
 * 790     When other
 * 791-796   ... 'TF-CONTRTYP-MISSING' ... Set Return-Error
 *           -- NO End-Evaluate HERE --
 * 799   Evaluate True                                  (SC-Code)   &lt;-- starts
 *                                                       immediately after 796,
 *                                                       still inside the
 *                                                       Contract-Type "When
 *                                                       other" arm
 * 800     When R115ISC0-SC-Code       Not = Space
 * 801       Continue
 * 803     When R115ISC0-In-SC-Code    Not = Space
 * 804       Move R115ISC0-In-SC-Code  To    R115ISC0-SC-Code
 * 806     When other
 * 807-811   ... 'TF-CODE-MISSING' ... Set Return-Error
 * 813   End-Evaluate                                   &lt;- closes the SC-Code
 *                                                       evaluate (799), the
 *                                                       INNERMOST open one
 * 815   .                                               &lt;- the paragraph's
 *                                                       period; this is what
 *                                                       finally, implicitly,
 *                                                       closes the still-open
 *                                                       Contract-Type evaluate
 *                                                       (783) - COBOL closes
 *                                                       every still-open
 *                                                       scope at a period.
 * </pre>
 *
 * <p>This confirms the rule card's summary exactly: the Contract-Type
 * Evaluate (783) is missing its own {@code End-Evaluate} before the next
 * {@code Evaluate True} for SC-Code begins (799), so the SC-Code check
 * (799-813) is syntactically nested inside the Contract-Type block's "When
 * other" arm (790-796) rather than being its own independent, always-run
 * check like ST-Type (741-755) and Institution (757-781) are.
 *
 * <p>Consequence: whenever Contract-Type resolves through either non-error
 * branch (784-785 or 787-788 - the normal case), control never reaches the
 * nested SC-Code Evaluate at all, so SC-Code is <b>never validated or
 * defaulted</b> in that path, even if it is blank. Only when Contract-Type
 * itself falls through to its own "When other" (791-796, i.e. Contract-Type
 * is missing entirely) does the nested SC-Code check run.
 *
 * <p>A secondary, easy-to-miss effect of the same nesting: when Contract-Type
 * fails AND the nested SC-Code check also fails, the SC-Code branch's
 * Z000-Error-Message call runs <i>after</i> the Contract-Type branch's, and
 * both write into the same {@code R7919999-Message}/{@code
 * R7918030-Out-MessageText} field - so the later call wins. The error
 * actually surfaced to the caller in that case is {@code TF-CODE-MISSING},
 * not {@code TF-CONTRTYP-MISSING} - even though Contract-Type is what
 * failed first.
 *
 * <p>The system owner was asked whether to fix this or preserve it, and
 * explicitly chose to <b>preserve</b> the gap. This method reproduces it
 * exactly: it does not add the missing check, and it reproduces the
 * last-write-wins error-code precedence when both Contract-Type and SC-Code
 * are blank.
 */
public final class SysCodeKeyPolicy implements KeyPolicy<SysCodeKey, SysCodeRow> {

    @Override
    public SysCodeKey resolveKey(SysCodeRow record, FinInstResolver resolver) {
        // "Last failure wins" (standardized across every table's KeyPolicy -
        // see KeyPolicy's class javadoc): ST-Type, Institution and
        // Contract-Type(+nested SC-Code) are three independent Evaluate
        // blocks in source order with no short-circuit between them, so if
        // more than one is missing, whichever is checked LAST is what
        // actually surfaces (here, that's always the Contract-Type/SC-Code
        // block, since it's the final one in H100-Check-Qualification).
        String pendingFailure = null;

        // ST-Type: F115ISC0.src:741-755. Own End-Evaluate at 755 - always runs.
        if (isBlank(record.stType())) {
            pendingFailure = "TF-TYPE-MISSING";
        }

        // Institution: F115ISC0.src:757-781. Own End-Evaluate at 781 - always
        // runs. Collapsed single-value model (see SysCodeRow javadoc): the
        // legacy current-Id/In-Id/current-No/In-No four-way fallback becomes
        // "id already resolved, or resolvable from the external number".
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0) {
            if (isBlank(record.finInstNo())) {
                pendingFailure = "TF-FIN-INST-MISSING";
            } else {
                // Resolver itself throws MissingKeyFieldException("TF-FIN-INST-MISSING")
                // if the external number cannot be resolved (N100-Fetch-Fin-Inst-ID's
                // Z900-Abend path is out of scope here - unresolvable external
                // numbers surface as this same mandatory-field failure).
                resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
            }
        }

        // Contract-Type + RULE-017 gap: F115ISC0.src:783-815 (see class
        // javadoc for the full trace of the missing End-Evaluate at 796/799).
        if (isBlank(record.contrType())) {
            // Contract-Type's own "When other" (790-796) is reached, which is
            // exactly the branch the SC-Code Evaluate (799-813) is nested
            // inside of - so, and ONLY so, SC-Code gets checked too.
            if (isBlank(record.scCode())) {
                // Both missing: SC-Code's Z000-Error-Message (807-811) runs
                // after Contract-Type's (791-794), so its message overwrites
                // the other's - TF-CODE-MISSING is what is actually surfaced.
                pendingFailure = "TF-CODE-MISSING";
            } else {
                pendingFailure = "TF-CONTRTYP-MISSING";
            }
        }
        // RULE-017 (confirmed-preserved defect, not a bug in this port):
        // Contract-Type resolved normally, so the nested SC-Code Evaluate
        // (799-813) is UNREACHABLE here. SC-Code is NOT validated or
        // defaulted in this path, no matter how blank it is.

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new SysCodeKey(record.stType(), resolvedFinInstId, record.contrType(), record.scCode());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
