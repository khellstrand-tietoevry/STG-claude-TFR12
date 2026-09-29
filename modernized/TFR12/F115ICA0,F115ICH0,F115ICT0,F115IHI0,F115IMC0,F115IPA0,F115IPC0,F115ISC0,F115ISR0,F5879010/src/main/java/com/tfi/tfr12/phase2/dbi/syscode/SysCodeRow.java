package com.tfi.tfr12.phase2.dbi.syscode;

/**
 * A full {@code TF_Sys_Code} row: the natural key plus a representative
 * subset of the persisted data columns, mirroring {@code R115ISC0-Data} in
 * {@code R115ISC0.copy:90-149} and {@code DCLTF-Sys-Code} (via {@code Copy
 * R115TFSC}, F115ISC0.src:181).
 *
 * <p>This is the {@code R} type plugged into
 * {@code DbiEngine<SysCodeKey, SysCodeRow>}: {@link
 * com.tfi.tfr12.phase2.dbi.KeyPolicy#validate(Object,
 * com.tfi.tfr12.phase2.dbi.FinInstResolver)} is called with an instance of
 * this record for Select/Update/Delete, mirroring legacy
 * H100-Check-Qualification (F115ISC0.src:739-815, only actually invoked from
 * H000-Update at F115ISC0.src:729 - Select and Delete key qualification in
 * F100-Prepare-Qualification, F115ISC0.src:621-640, does not run the same
 * mandatory-field check, it silently defaults to a wide-open query instead).
 *
 * <p>Field-model note: legacy keeps two parallel copies of each key value -
 * the record's own persisted field (e.g. {@code R115ISC0-ST-Type}) and the
 * caller-supplied qualification field (e.g. {@code R115ISC0-In-ST-Type},
 * R115ISC0.copy:76-80) - and H100-Check-Qualification falls back from the
 * first to the second when the first is blank. This row collapses that into
 * a single value per field: callers of this modernized contract are expected
 * to have already merged "already on the row" and "supplied on this call"
 * into one value before constructing a {@code SysCodeRow}, exactly as if the
 * legacy record-field had already had the In-field moved into it. See
 * {@link SysCodeKeyPolicy} for exactly how each field is still validated.
 *
 * @param stType       system type, PIC X(20) (R115ISC0.copy:93)
 * @param finInstId    internal financial institution id, PIC S9(18) COMP-3
 *                     (R115ISC0.copy:94); {@code 0} means "not yet resolved"
 * @param finInstNo    external financial institution number, PIC X(18)
 *                     (R115ISC0.copy:99, under R115ISC0-Filler - not part of
 *                     the persisted key, used only to resolve
 *                     {@code finInstId} via {@link
 *                     com.tfi.tfr12.phase2.dbi.FinInstResolver}, mirroring
 *                     N100-Fetch-Fin-Inst-ID, F115ISC0.src:1009-1038)
 * @param contrType    contract type, PIC X(03) (R115ISC0.copy:95)
 * @param scCode       system code, PIC X(20) (R115ISC0.copy:96)
 * @param description  free-text description, PIC X(150) (R115ISC0.copy:101,
 *                      TFSC-Descr)
 * @param property     the raw, undifferentiated property blob, PIC X(80)
 *                      (R115ISC0.copy:103, TFSC-Property) - legacy redefines
 *                      this differently depending on Contract-Type/context
 *                      (contract-name/imp-exp/range fields, a numeric range,
 *                      a letter-receiver code, a default-add-days count, or a
 *                      currency margin percentage - R115ISC0.copy:104-149);
 *                      this DBI-layer contract keeps the raw bytes and does
 *                      not interpret them
 * @param contractName representative parsed view of one such redefine, PIC
 *                     X(10) (R115ISC0.copy:106, R115ISC0-Contract-Name) -
 *                     only meaningful when the caller knows this row uses
 *                     that particular redefine; included here purely as a
 *                     representative data field, not exercised by the
 *                     RULE-017 key-validation tests
 */
public record SysCodeRow(
        String stType,
        long finInstId,
        String finInstNo,
        String contrType,
        String scCode,
        String description,
        String property,
        String contractName) {
}
