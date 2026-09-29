package com.tfi.tfr12.phase2.dbi.syscode;

/**
 * The natural key of a {@code TF_Sys_Code} row, as actually used to qualify
 * SQL access in legacy F115ISC0 (not the full linkage-area key, which also
 * carries the external {@code Fin-Inst-No} as an alternate way to supply the
 * institution - see {@link SysCodeRow#finInstNo()}).
 *
 * <p>Mirrors {@code W-DB-Key} in F115ISC0.src:122-126 (used to qualify every
 * cursor/select/fetch) and the persisted key group {@code R115ISC0-Key} in
 * {@code R115ISC0.copy:92-96}:
 * <pre>
 *   10  R115ISC0-Key.
 *     15  R115ISC0-ST-Type       Pic X(20).
 *     15  R115ISC0-Fin-Inst-Id   Pic S9(18) Comp-3.
 *     15  R115ISC0-Contr-Type    Pic X(03).
 *     15  R115ISC0-SC-Code       Pic X(20).
 * </pre>
 *
 * @param stType    system type, PIC X(20) (R115ISC0.copy:93)
 * @param finInstId internal financial institution id, PIC S9(18) COMP-3
 *                  (R115ISC0.copy:94)
 * @param contrType contract type, PIC X(03) (R115ISC0.copy:95)
 * @param scCode    system code, PIC X(20) (R115ISC0.copy:96)
 */
public record SysCodeKey(String stType, long finInstId, String contrType, String scCode) {
}
