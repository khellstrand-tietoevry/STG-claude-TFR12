package com.tfi.tfr12.phase2.orgdata;

import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Real implementation of F5879010's own logic, wrapping {@link IHubOrgGateway}
 * (which stands in for the external BZXOJOR0/BZXOJNA0 programs). Only the two
 * behaviors this legacy "clutch" module contributes on top of the raw iHUB
 * calls are reproduced here: the Fin-Inst-No -> internal-ID resolution
 * (RULE-018's documented edge case) and RULE-039's as-of-today attribute
 * filtering. Everything else BZXOJOR0/BZXOJNA0 themselves might do is out of
 * scope — their internals are not in this repository.
 */
public final class OrgMasterDataClientImpl implements OrgMasterDataClient {

    /** F5879010.src:436-439 - Select-by-No finding nothing (R791ERRX-State-Error). */
    public static final String FIN_INST_MISSING = "TF-FIN-INST-MISSING";

    private final IHubOrgGateway gateway;

    public OrgMasterDataClientImpl(IHubOrgGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * F5879010.src:339-356 (F100-Prepare-OROR-Qual, select-by-No branch) plus
     * F300-Select-On-DB2 -> F320-Call-DBI-OROR-Get-1st: qualifies BZXOJOR0 on
     * the external organization reference (Fin-Inst-No) and returns the
     * resolved internal Org-ID (F900-Populate-Output,
     * {@code R5879010-Ut-KO-Konsern-Id}, F5879010.src:486).
     *
     * <p>This is the paragraph RULE-018 refers to when it says "If only
     * Fin-Inst-No is given, N100-Fetch-Fin-Inst-ID calls F5879010 to resolve
     * it" — every sibling DBI table's {@code KeyPolicy} depends on this
     * method via the shared {@code FinInstResolver} interface.
     */
    @Override
    public long resolveFinInstId(String finInstNo) {
        return gateway.resolveOrgIdByExternalRef(finInstNo)
                .orElseThrow(() -> new MissingKeyFieldException(FIN_INST_MISSING));
    }

    /**
     * RULE-039: G000-Get-Attributes (F5879010.src:525-535) — fetches the
     * today-qualified attribute version via {@link IHubOrgGateway} and, only
     * if one was found (G200-Get-1st-Attribute succeeding,
     * F5879010.src:529), extracts it (G400-Extract-Attributes,
     * F5879010.src:602-664). No version valid for today -> empty
     * (F5879010.src:529-533).
     */
    @Override
    public Optional<OrgUnitAttributes> lookupAttributes(String orgUnitId, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        long orgId = Long.parseLong(orgUnitId);

        // G100-Get-Search-Date (F5879010.src:537-553) + G200-Get-1st-Attribute
        // (F5879010.src:557-574): both the valid-from and valid-to
        // qualification are the SAME run date (RULE-039).
        return gateway.fetchOrgUnitAttributeData(orgId, today)
                .map(rawData -> extractAttributes(orgUnitId, rawData, today));
    }

    /**
     * G400-Extract-Attributes (F5879010.src:602-664): unstrings
     * {@code BZXOJNA0-ORNA-Data} on ':' or ';' into key/value pairs and picks
     * out the four recognized keys (GRP/SGRP/UPDC/MRR); any other key is
     * silently ignored (the {@code Evaluate WT-Data-Key} has no
     * {@code When Other} arm, F5879010.src:644-661).
     *
     * <p>Simplification vs. the legacy Unstring: the legacy table caps at 10
     * elements (5 pairs) and abends on overflow (F5879010.src:630-638); this
     * port simply parses whatever pairs are present with no cap, since the
     * overflow-abend is an internal parsing safety net, not part of RULE-039
     * or the Fin-Inst resolution this class characterizes.
     */
    private static OrgUnitAttributes extractAttributes(String orgUnitId, String rawData, LocalDate asOfDate) {
        String runGroup = "";
        int subGroup = 0;
        int updateCode = 0;
        BigDecimal interimAccountRate = BigDecimal.ZERO;

        String[] tokens = rawData.split("[:;]", -1);
        for (int i = 0; i + 1 < tokens.length; i += 2) {
            String key = tokens[i].trim();
            String value = tokens[i + 1];
            switch (key) {
                case "GRP" -> // F5879010.src:645-648, Pic X(03)
                        runGroup = truncate(value, 3);
                case "SGRP" -> // F5879010.src:649-652, Pic 9(04)
                        subGroup = parseNumeric(truncate(value, 4));
                case "UPDC" -> // F5879010.src:653-656, Pic 9(04)
                        updateCode = parseNumeric(truncate(value, 4));
                case "MRR" -> // F5879010.src:657-660, Pic S9(02)V9(03)
                        interimAccountRate = parseCommaDecimal(truncate(value, 6));
                default -> {
                    // no When-Other arm in the legacy Evaluate - ignored
                }
            }
        }

        return new OrgUnitAttributes(orgUnitId, runGroup, subGroup, updateCode,
                interimAccountRate, asOfDate, asOfDate);
    }

    private static String truncate(String value, int maxLen) {
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private static int parseNumeric(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? 0 : Integer.parseInt(trimmed);
    }

    /**
     * F5879010.src:26 sets {@code Decimal-Point Is Comma}, so the program's
     * own edited numeric fields (e.g. {@code W-Ext-MRR Pic 99,999}) use a
     * comma as the decimal separator, e.g. raw {@code "05,123"} -> 5.123.
     */
    private static BigDecimal parseCommaDecimal(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(trimmed.replace(',', '.'));
    }
}
