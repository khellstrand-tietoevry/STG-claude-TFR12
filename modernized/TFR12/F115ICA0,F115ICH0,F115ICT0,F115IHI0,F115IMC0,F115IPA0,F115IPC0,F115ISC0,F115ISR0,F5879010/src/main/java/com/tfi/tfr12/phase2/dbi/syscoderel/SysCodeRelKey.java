package com.tfi.tfr12.phase2.dbi.syscoderel;

/**
 * The natural key of a {@code TF_Sys_Code_Rel} row, as actually used to
 * qualify DB2 access in legacy F115ISR0 - confirmed directly from
 * {@code legacy/STG/TFR12/src/F115ISR0.src}:
 * <ul>
 *   <li>{@code G100-Move-Input-To-DCL} (F115ISR0.src:875-905), which builds
 *       the row for Insert (G000-Insert, F115ISR0.src:865-873) wholly from
 *       {@code R115ISR0-Data} - these same 8 fields, nothing else;</li>
 *   <li>{@code F100-Prepare-Qualification} (F115ISR0.src:798-837), which
 *       qualifies both Select (F000-Select, F115ISR0.src:741-796) and Delete
 *       (I000-Delete, F115ISR0.src:1174-1181) from {@code R115ISR0-In-*}
 *       - the same 8 fields again, populated from the caller-supplied
 *       qualification instead of the persisted-row fields; and</li>
 *   <li>the in-memory dedup cache's row-identity comparison in
 *       {@code L200-Find-DCL} (F115ISR0.src:1328-1370) and
 *       {@code L300-Remove-DCL} (F115ISR0.src:1372-1423), whose active
 *       ({@code KUTT}-free) {@code Perform Varying ... Until} test compares
 *       exactly: {@code WS-DB2-Fin-Inst-Id}, {@code WS-DB2-From-Type},
 *       {@code WS-DB2-To-Type}, {@code WS-DB2-Rel-Type},
 *       {@code WS-DB2-From-Code}, {@code WS-DB2-To-Code},
 *       {@code WS-DB2-From-Contr-Type}, {@code WS-DB2-To-Contr-Type}.</li>
 * </ul>
 *
 * <p>Note the 2014-02-01 tuning change recorded at the top of both the
 * source (F115ISR0.src:53-56) and the copybook (R115ISR0.copy) header:
 * "From- / To-F-I-ID removed, and replaced by Fin-Inst-ID" - there is a
 * single {@code Fin-Inst-Id}, used for both the From- and To- side of the
 * relationship (confirmed: {@code F100-Prepare-Qualification} moves the
 * *same* {@code R115ISR0-In-F-I-Id} into both {@code FK-OAKO-FINI-FROM} and
 * {@code FK-OAKO-FINI-To}, F115ISR0.src:806-807/816). This mirrors
 * {@code R115ISR0-Key} in {@code R115ISR0.copy:102-115}, which likewise has
 * only one {@code R115ISR0-F-I-Id}, not a From/To pair.
 *
 * <p>Finding worth flagging: unlike the sibling {@code TF_Sys_Code} table,
 * there are no non-key data columns anywhere in this record layout or in the
 * DCL host-variable names referenced from {@code DCLTF-Sys-Code-Rel}
 * ({@code FK-OAKO-FINI-*}, {@code FK-TFST-Type-*}, {@code FK-TFSC-COTY-*},
 * {@code FK-TFSC-Code-*}, {@code TFSR-Type}) - {@code TF_Sys_Code_Rel} is a
 * pure junction/mapping table between two {@code TF_Sys_Code} rows. That is
 * exactly consistent with RULE-027's business description (a relationship,
 * e.g. Operation-Type-to-Charge-Type, either exists or it doesn't - there is
 * nothing about it to "update" in place other than deleting the old
 * relationship and inserting a new one). See {@link SysCodeRelRow} for how
 * this affects that record's shape.
 *
 * @param finInstId    internal financial institution id, PIC S9(18) COMP-3
 *                     (R115ISR0.copy:103); used for both From- and To-
 * @param fromStType   From-side system type, PIC X(20) (R115ISR0.copy:106)
 * @param fromContrType From-side contract type, PIC X(03) (R115ISR0.copy:107)
 * @param fromScCode   From-side system code, PIC X(20) (R115ISR0.copy:108)
 * @param toStType     To-side system type, PIC X(20) (R115ISR0.copy:111)
 * @param toContrType  To-side contract type, PIC X(03) (R115ISR0.copy:112)
 * @param toScCode     To-side system code, PIC X(20) (R115ISR0.copy:113)
 * @param relationType relation type, PIC X(20) (R115ISR0.copy:115)
 */
public record SysCodeRelKey(
        long finInstId,
        String fromStType,
        String fromContrType,
        String fromScCode,
        String toStType,
        String toContrType,
        String toScCode,
        String relationType) {
}
