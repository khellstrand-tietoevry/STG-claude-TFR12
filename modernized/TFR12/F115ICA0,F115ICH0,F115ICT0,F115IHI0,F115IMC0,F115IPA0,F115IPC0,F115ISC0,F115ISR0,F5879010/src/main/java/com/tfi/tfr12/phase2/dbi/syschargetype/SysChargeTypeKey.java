package com.tfi.tfr12.phase2.dbi.syschargetype;

/**
 * The natural key of a {@code TF_Sys_Charge_Type} row, as actually used to
 * qualify SQL access in legacy F115ICT0 — mirrors {@code W-DB-Key}
 * (F115ICT0.src:125-128):
 * <pre>
 *   05  W-DB-Key.
 *       10  W-DB-Type             Pic  X(20).
 *       10  W-DB-Fin-Inst-Id      Pic S9(18)              Comp-3.
 *       10  W-DB-Regno            Pic S9(04)              Comp.
 * </pre>
 * and the row-identification comparison in L200-Find-DCL / L300-Remove-DCL
 * (F115ICT0.src:954-959, 969-974), which matches on Type + Regno + Fin-Inst-Id
 * together.
 *
 * <p><b>Only 2 of these 3 fields are independently mandatory per
 * H100-Check-Qualification</b> (F115ICT0.src:738-787, performed by
 * H000-Update alone — see {@link SysChargeTypeKeyPolicy}):
 * <ul>
 *   <li>{@code ctType} — required, error {@code TF-TYPE-MISSING}
 *       (F115ICT0.src:740-754)</li>
 *   <li>{@code finInstId} — required (resolvable from an external
 *       {@code finInstNo}), error {@code TF-FININST-MISSING}
 *       (F115ICT0.src:756-787)</li>
 * </ul>
 * {@code regno} is part of the physical row-identification key (it is always
 * included in the qualification per the comment at F115ICT0.src:394-397,
 * "Allways qualify on regno") but H100-Check-Qualification never tests it for
 * blank/zero and never raises an error mnemonic for it on its own — unlike
 * {@code ctType} and {@code finInstId}, a caller can leave {@code regno} at
 * zero and H100 raises nothing. It is included here anyway because it is a
 * required part of physical row identity (repository lookups must
 * disambiguate on it), not because H100 mandates it — this is the same
 * "physical key vs. independently-validated key" distinction drawn by
 * {@code SysCodeKey}'s javadoc for the sibling {@code TF_Sys_Code} table.
 *
 * @param ctType    system charge type code, PIC X(20)
 *                  (R115ICT0.copy:97, "R115ICT0-CT-Type")
 * @param finInstId internal financial institution id, PIC S9(18) COMP-3
 *                  (R115ICT0.copy:96, "R115ICT0-Fin-Inst-Id")
 * @param regno     registration number, PIC S9(04) COMP
 *                  (R115ICT0.copy:98, "R115ICT0-Regno")
 */
public record SysChargeTypeKey(String ctType, long finInstId, int regno) {
}
