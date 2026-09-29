package com.tfi.tfr12.phase2.orgdata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The "extra info" attributes G400-Extract-Attributes
 * (legacy/TFR12/src/F5879010.src:602-664) pulls out of the colon/semicolon
 * delimited {@code BZXOJNA0-ORNA-Data} blob returned by the iHUB Org
 * attribute lookup, plus the validity window that selected this particular
 * version (RULE-039).
 *
 * <p>Only four keys are ever recognized by the {@code Evaluate WT-Data-Key}
 * in G400 (F5879010.src:644-661) — {@code GRP}, {@code SGRP}, {@code UPDC},
 * {@code MRR}; any other key present in the data is silently ignored (no
 * {@code When Other} arm). These map 1:1 onto
 * {@code R5879010-Ut-Xtra-Info} in the copybook
 * (legacy/TFR12/copybooks/R5879010.copy:97-101), which is only populated at
 * all when the caller set {@code R5879010-In-Xtra-Info-Yes}
 * (F5879010.src:519-521).
 *
 * @param orgUnitId    the internal Org-ID these attributes belong to
 *                     ({@code BZXOJNA0-In-FK-OR-Org-ID}, sourced from the
 *                     already-resolved {@code BZXOJOR0-OROR-Org-ID},
 *                     F5879010.src:563)
 * @param runGroup     GRP key, first 3 chars of its value
 *                     ({@code R5879010-Ut-KO-Kjoregruppe}, Pic X(03),
 *                     F5879010.src:645-648)
 * @param subGroup     SGRP key, first 4 chars of its value parsed numeric
 *                     ({@code R5879010-Ut-KO-Delgruppe}, Pic 9(04),
 *                     F5879010.src:649-652)
 * @param updateCode   UPDC key, first 4 chars of its value parsed numeric
 *                     ({@code R5879010-Ut-KO-Oppdat-Kode}, Pic 9(04),
 *                     F5879010.src:653-656)
 * @param interimAccountRate MRR key, first 6 chars of its value
 *                     ({@code R5879010-Ut-KO-Mellomregnrte}, Pic
 *                     S9(02)V9(03), F5879010.src:657-660); the raw value uses
 *                     a comma as the decimal separator per this program's
 *                     {@code Decimal-Point Is Comma} configuration
 *                     (F5879010.src:26), e.g. raw {@code "05,123"} -> 5.123
 * @param validFrom    RULE-039: always equal to {@code validTo} and to the
 *                     run date used to qualify the lookup
 *                     ({@code BZXOJNA0-In-ORNA-Val-From-Dt}, F5879010.src:565)
 * @param validTo      RULE-039: always equal to {@code validFrom}
 *                     ({@code BZXOJNA0-In-ORNA-Val-To-Dt}, F5879010.src:566)
 */
public record OrgUnitAttributes(
        String orgUnitId,
        String runGroup,
        int subGroup,
        int updateCode,
        BigDecimal interimAccountRate,
        LocalDate validFrom,
        LocalDate validTo) {
}
