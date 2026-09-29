package com.tfi.tfr12.phase2.dbi.syscoderel;

/**
 * A full {@code TF_Sys_Code_Rel} row: the natural key ({@link SysCodeRelKey})
 * plus the one non-persisted field the legacy interface carries alongside it.
 *
 * <p><b>Field-model note - no representative "data" fields exist for this
 * table.</b> Confirmed directly from {@code legacy/STG/TFR12/copybooks/
 * R115ISR0.copy}: {@code R115ISR0-Data} (copybook lines 100-131) consists of
 * exactly {@code R115ISR0-Key} (the 8 natural-key fields - see
 * {@link SysCodeRelKey}), {@code R115ISR0-Filler.R115ISR0-F-I-No} (the
 * external institution number, copybook line 118), and the two cursor-only
 * accumulators {@code R115ISR0-Out-Row-No} / {@code R115ISR0-Out-Total-Rows}
 * (copybook lines 129-130, populated only during Get-1st/Get-Next, not part
 * of the persisted row). There is no description, flag, amount, or any other
 * non-key column anywhere in the copybook or in the DCL host-variable names
 * referenced from {@code DCLTF-Sys-Code-Rel} throughout F115ISR0.src. This is
 * consistent with RULE-027: {@code TF_Sys_Code_Rel} is a pure junction table
 * expressing "this From- combination maps to that To- combination under this
 * relation type" - the row *is* its key, which is exactly why the table has
 * an Update path at all only as unreachable dead code (H000-Update /
 * H100-Check-Qualification, F115ISR0.src:933-1147): there is nothing to
 * change about a row except which row it is, so a "change" is a Delete of
 * the old key followed by an Insert of the new one.
 *
 * <p>This is the {@code R} type plugged into
 * {@code DbiEngine<SysCodeRelKey, SysCodeRelRow>}.
 *
 * @param finInstId     see {@link SysCodeRelKey#finInstId()}
 * @param fromStType    see {@link SysCodeRelKey#fromStType()}
 * @param fromContrType see {@link SysCodeRelKey#fromContrType()}
 * @param fromScCode    see {@link SysCodeRelKey#fromScCode()}
 * @param toStType      see {@link SysCodeRelKey#toStType()}
 * @param toContrType   see {@link SysCodeRelKey#toContrType()}
 * @param toScCode      see {@link SysCodeRelKey#toScCode()}
 * @param relationType  see {@link SysCodeRelKey#relationType()}
 * @param finInstNo     external financial institution number, PIC X(18)
 *                      (R115ISR0.copy:118, under R115ISR0-Filler - not part
 *                      of the persisted key); used only to resolve
 *                      {@code finInstId} via {@link
 *                      com.tfi.tfr12.phase2.dbi.FinInstResolver}, mirroring
 *                      N100-Fetch-Fin-Inst-ID (F115ISR0.src:1425-1449),
 *                      called from both D100-Prepare-Input
 *                      (F115ISR0.src:459-464, cursor path) and
 *                      F100-Prepare-Qualification (F115ISR0.src:823-835,
 *                      Select/Delete path)
 */
public record SysCodeRelRow(
        long finInstId,
        String fromStType,
        String fromContrType,
        String fromScCode,
        String toStType,
        String toContrType,
        String toScCode,
        String relationType,
        String finInstNo) {

    /** The row's persisted natural key, dropping the non-persisted {@code finInstNo}. */
    public SysCodeRelKey key() {
        return new SysCodeRelKey(finInstId, fromStType, fromContrType, fromScCode,
                toStType, toContrType, toScCode, relationType);
    }
}
