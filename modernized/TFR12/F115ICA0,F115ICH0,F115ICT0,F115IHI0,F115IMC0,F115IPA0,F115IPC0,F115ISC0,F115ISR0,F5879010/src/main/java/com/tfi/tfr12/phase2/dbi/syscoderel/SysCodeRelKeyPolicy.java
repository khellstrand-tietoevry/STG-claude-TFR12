package com.tfi.tfr12.phase2.dbi.syscoderel;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;

/**
 * Key validation for {@code TF_Sys_Code_Rel} (legacy F115ISR0).
 *
 * <h2>RULE-027 - confirmed directly in the source, not from the summary alone</h2>
 *
 * <p>{@code legacy/STG/TFR12/src/F115ISR0.src}, {@code C000-Interface}
 * (lines 326-362) dispatches on {@code R115ISR0-In-Action}:
 * <pre>
 * 328   Evaluate True
 * 329     When R115ISR0-In-Get-1st   ... Perform D000-Get-1st / E000-Get-Next
 * 334     When R115ISR0-In-Get-Next  ... Perform E000-Get-Next
 * 338     When R115ISR0-In-Select    ... Perform F000-Select
 * 342     When R115ISR0-In-Insert    ... Perform G000-Insert
 * 345 *   When R115ISR0-In-Update           &lt;- COMMENTED OUT
 * 346 *     Perform H000-Update             &lt;- COMMENTED OUT
 * 348     When R115ISR0-In-Delete    ... Perform I000-Delete
 * 351     When Other
 * 352-358   ... 'GL-IN-AKSJON-UV' ... Perform Z900-Abend
 * 360   End-Evaluate
 * </pre>
 * and the copybook condition-name it would need is itself commented out too,
 * {@code legacy/STG/TFR12/copybooks/R115ISR0.copy:69}:
 * <pre>
 *   68     88  R115ISR0-In-Insert                Value 'INSERT'.
 *   69 *   88  R115ISR0-In-Update                Value 'UPDATE'.
 *   70     88  R115ISR0-In-Delete                Value 'DELETE'.
 * </pre>
 * So a caller that sets {@code R115ISR0-In-Action} to {@code 'UPDATE'} falls
 * to {@code C000-Interface}'s {@code When Other} (line 351), which raises
 * message {@code GL-IN-AKSJON-UV} and calls {@code Z900-Abend} (line 358) -
 * the request abends, it does not return a business error. {@code H000-Update}
 * (F115ISR0.src:933-943), {@code H100-Check-Qualification}
 * (F115ISR0.src:945-1146) and {@code H200-Update-Row} (F115ISR0.src:1148-1172)
 * remain in the source as unreachable dead code: nothing in
 * {@code C000-Interface} ever performs {@code H000-Update}. This is exactly
 * what {@link com.tfi.tfr12.phase2.dbi.Capabilities#CREATE_DELETE_ONLY}
 * models ({@code update=false}, everything else {@code true}) - confirmed
 * matching: Get-1st/Get-Next (cursor), Select, Insert and Delete are all live
 * dispatch arms above, only Update is disabled.
 *
 * <h2>Select / cursor / Delete qualification - no active requirement modeled here</h2>
 *
 * <p>Per this task's instruction not to model {@code H100}'s dead
 * Update-only validation logic, this policy instead reproduces what the
 * table's *reachable* paths actually require:
 * <ul>
 *   <li><b>Select</b> ({@code F000-Select}, F115ISR0.src:741-796) and
 *       <b>Delete</b> ({@code I000-Delete}, F115ISR0.src:1174-1181) both
 *       qualify through the same {@code F100-Prepare-Qualification}
 *       (F115ISR0.src:798-837). That paragraph only moves fields (with an
 *       institution-number-to-id fallback lookup at 823-835) - it contains no
 *       {@code Evaluate}/error-message/abend for a blank or zero key field,
 *       unlike {@code H100-Check-Qualification}. A Select or Delete with a
 *       wholly blank qualification is not rejected by F115ISR0 itself; it is
 *       simply likely to under-qualify (or fail to match) the underlying SQL,
 *       which is out of scope for this DBI-contract-level characterization.
 *       <b>Confirmed: no active qualification requirement for Select/Delete.</b></li>
 *   <li><b>Cursor</b> (Get-1st, {@code D000-Get-1st}, F115ISR0.src:370-380)
 *       is different: {@code D300-Select-Cursor} (F115ISR0.src:468-596) picks
 *       a physical cursor from the pattern of which qualification fields are
 *       set, and only four patterns are wired up (all requiring the
 *       Fin-Inst-Id switch): {@code SW-Use-Fi-ST-Asc} ('FS   '),
 *       {@code -Fi-ST-Re-Asc} ('FSR  '), {@code -Fi-ST-SC-Asc} ('FS S '),
 *       {@code -Fi-ST-Re-SC-Asc} ('FSRS '). Any other combination - in
 *       particular Fin-Inst-Id unset, or Fin-Inst-Id set but From-ST-Type
 *       unset - falls to {@code When other} (F115ISR0.src:585-592), which
 *       raises {@code GL-CURSOR-UV} and calls {@code Z900-Abend}. <b>So the
 *       cursor path DOES have an active qualification requirement: Fin-Inst-Id
 *       non-zero and From-ST-Type non-blank are both mandatory.</b> This is
 *       NOT modeled by this class: {@code DbiEngine.getRange(K)} does not
 *       call {@link KeyPolicy#validate} at all (by design - see
 *       {@code DbiEngine} javadoc, which documents key validation as running
 *       "before Select, Update and Delete", cursor is deliberately excluded),
 *       so there is currently no hook through which this contract enforces
 *       the D300-Select-Cursor precondition. <b>Flagging this as a gap</b>:
 *       a caller of {@code DbiEngine.getRange} with Fin-Inst-Id unset or
 *       From-ST-Type blank will not be told (by this Java layer) what legacy
 *       would abend on. See {@code SysCodeRelServiceTest} for a disabled test
 *       pinning this pending decision.</li>
 * </ul>
 *
 * <p>Because Update is unreachable and Select/Delete have no active
 * requirement, {@link #validate} is a deliberate no-op: it never throws.
 * That is the faithful characterization of this table's reachable paths, not
 * an oversight.
 */
public final class SysCodeRelKeyPolicy implements KeyPolicy<SysCodeRelKey, SysCodeRelRow> {

    @Override
    public SysCodeRelKey resolveKey(SysCodeRelRow record, FinInstResolver resolver) {
        // No mandatory-field validation (see class javadoc: F100-Prepare-
        // Qualification never raises a business error or abend for a blank/
        // zero key field). It DOES still resolve Fin-Inst-No to an id when
        // only the external number is supplied (F115ISR0.src:823-835), so
        // this method reproduces that resolution without validating anything.
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0 && record.finInstNo() != null && !record.finInstNo().isBlank()) {
            resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
        }
        return new SysCodeRelKey(resolvedFinInstId, record.fromStType(), record.fromContrType(),
                record.fromScCode(), record.toStType(), record.toContrType(), record.toScCode(),
                record.relationType());
    }
}
