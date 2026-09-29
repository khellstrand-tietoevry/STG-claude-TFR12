package com.tfi.tfr12.phase2.orgdata;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Stands in for the two iHUB Org DBI programs F5879010 calls dynamically
 * (F5879010.src:44-45, {@code Call BZXOJOR0} / {@code Call BZXOJNA0}). Those
 * two programs are external to this repository (F5879010's own header calls
 * itself a "temporary clutch" that redirects legacy {@code OA_Konsern}
 * lookups to them) — this port exists so
 * {@link OrgMasterDataClientImpl}, which characterizes F5879010's OWN logic
 * (the Fin-Inst-No resolution orchestration and the RULE-039 as-of-today
 * filtering), can be tested against a fake instead of BZXOJOR0/BZXOJNA0's
 * unknown internals.
 */
public interface IHubOrgGateway {

    /**
     * Mirrors an OR_Organization lookup by external organization reference
     * (F5879010's F100-Prepare-OROR-Qual / F300-Select-On-DB2, qualifying
     * {@code BZXOJOR0-In-OROR-Ext-Org-Ref} with {@code FK-TY-Int-Code =
     * 'FIIN'}, F5879010.src:260,351-355). This is the path
     * {@link OrgMasterDataClientImpl#resolveFinInstId(String)} uses to
     * satisfy RULE-018's documented edge case ("If only Fin-Inst-No is
     * given, N100-Fetch-Fin-Inst-ID calls F5879010 to resolve it").
     *
     * @param externalOrgRef the external institution/organization number
     *                       (R5879010-In-Konsernnr / Fin-Inst-No)
     * @return the resolved internal Org-ID ({@code BZXOJOR0-OROR-Org-ID}),
     *         or empty if no matching organization exists (the legacy call
     *         returns {@code R791ERRX-State-Error}, F5879010.src:436-439)
     */
    Optional<Long> resolveOrgIdByExternalRef(String externalOrgRef);

    /**
     * Mirrors G200-Get-1st-Attribute's call to BZXOJNA0
     * (F5879010.src:557-574): looks up the ORORH-type org-unit attribute
     * version for {@code orgId} whose validity window covers {@code asOfDate}
     * ({@code BZXOJNA0-In-ORNA-Val-From-Dt} = {@code In-ORNA-Val-To-Dt} =
     * {@code asOfDate}, F5879010.src:565-566 — RULE-039).
     *
     * @param orgId    the internal Org-ID ({@code BZXOJOR0-OROR-Org-ID})
     * @param asOfDate the run date to qualify the lookup by (both bounds)
     * @return the raw colon/semicolon-delimited {@code BZXOJNA0-ORNA-Data}
     *         string for the one matching version, or empty if no version's
     *         validity window covers {@code asOfDate} (RULE-039's documented
     *         edge case: "No attribute version is valid for today's date")
     */
    Optional<String> fetchOrgUnitAttributeData(long orgId, LocalDate asOfDate);
}
