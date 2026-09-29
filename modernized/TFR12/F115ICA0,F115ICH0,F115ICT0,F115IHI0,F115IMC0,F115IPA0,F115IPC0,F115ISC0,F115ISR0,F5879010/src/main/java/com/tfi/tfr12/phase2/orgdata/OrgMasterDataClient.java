package com.tfi.tfr12.phase2.orgdata;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;

import java.time.Clock;
import java.util.Optional;

/**
 * Java contract for legacy program F5879010 ("Customer/Org Master Data"), a
 * "temporary clutch" (F5879010's own header comment) that redirects legacy
 * {@code OA_Konsern} lookups to the newer iHUB-Org DBI programs BZXOJOR0 /
 * BZXOJNA0 rather than accessing DB2 directly.
 *
 * <p>Extends {@link FinInstResolver} because RULE-018's documented edge case
 * ("If only Fin-Inst-No is given, N100-Fetch-Fin-Inst-ID calls F5879010 to
 * resolve it") makes F5879010 the real implementation every sibling DBI
 * table's {@code KeyPolicy} depends on for that resolution
 * (F5879010.src:339-356, select-by-No qualifying {@code BZXOJOR0} on the
 * external organization reference).
 */
public interface OrgMasterDataClient extends FinInstResolver {

    /**
     * RULE-039: fetches an organization unit's "extra info" attributes
     * (G000-Get-Attributes, F5879010.src:525-535), filtered to the single
     * version whose validity window covers {@code clock}'s current date —
     * both the valid-from and valid-to qualification are set to that same
     * date (F5879010.src:565-566). If no version is valid as of that date,
     * returns {@link Optional#empty()} (F5879010.src:529-533: when the
     * BZXOJNA0 lookup does not return OK, G400-Extract-Attributes is simply
     * skipped and the error state is reset without surfacing an error to the
     * caller).
     *
     * @param orgUnitId the internal Org-ID to fetch attributes for
     * @param clock     injected clock standing in for F7918110's run date
     *                  (F5879010.src:537-553, G100-Get-Search-Date)
     */
    Optional<OrgUnitAttributes> lookupAttributes(String orgUnitId, Clock clock);
}
