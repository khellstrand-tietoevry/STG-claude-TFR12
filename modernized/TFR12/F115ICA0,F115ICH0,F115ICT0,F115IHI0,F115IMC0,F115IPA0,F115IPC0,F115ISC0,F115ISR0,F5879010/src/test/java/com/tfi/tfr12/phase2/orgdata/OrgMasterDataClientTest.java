package com.tfi.tfr12.phase2.orgdata;

import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins {@link OrgMasterDataClientImpl}'s reproduction of legacy program
 * F5879010 ("Customer/Org Master Data", legacy/TFR12/src/F5879010.src), read
 * byte-safe as ISO-8859-1/Latin-1 (column 7 {@code *} = comment, columns 8-72
 * = code). F5879010 is documented in its own header as a "temporary clutch"
 * that redirects legacy {@code OA_Konsern} lookups to the newer iHUB-Org DBI
 * programs BZXOJOR0/BZXOJNA0 (F5879010.src:6-11) — those two programs are
 * external to this repository and are stood in for here by the
 * {@link FakeIHubOrgGateway} test double, never characterized directly.
 * Every expected value below was hand-traced from the COBOL source, not
 * observed from a running program (F5879010 needs iHUB Org DBIs this
 * environment does not have).
 *
 * <p>Covers two behaviors:
 * <ul>
 *   <li>RULE-039 (analysis/TFR12/BUSINESS_RULES.md) — organization unit
 *   attributes are looked up as-of today's system run date only
 *   (G200-Get-1st-Attribute / G400-Extract-Attributes,
 *   F5879010.src:537-566).</li>
 *   <li>RULE-018's documented edge case — F5879010 resolving an external
 *   Fin-Inst-No to an internal Fin-Inst-ID (F5879010.src:339-356, select-by-No
 *   qualifying BZXOJOR0 on the external organization reference), which is
 *   why {@link OrgMasterDataClient} extends the shared {@code FinInstResolver}
 *   every sibling DBI table's {@code KeyPolicy} depends on.</li>
 * </ul>
 *
 * <p>No credential-like literal appears in F5879010.src; nothing here needed
 * substitution.
 */
class OrgMasterDataClientTest {

    // =====================================================================
    // Test double for the external iHUB Org DBIs (BZXOJOR0 / BZXOJNA0)
    // =====================================================================

    /** Configurable stand-in for BZXOJOR0 (org resolution) and BZXOJNA0 (attribute versions). */
    private static final class FakeIHubOrgGateway implements IHubOrgGateway {
        private final Map<String, Long> orgIdsByExternalRef = new HashMap<>();
        // orgId -> list of (validFrom, validTo, rawData) attribute versions
        private final Map<Long, java.util.List<AttrVersion>> attributeVersions = new HashMap<>();
        int resolveCalls = 0;
        int fetchAttributeCalls = 0;

        record AttrVersion(LocalDate validFrom, LocalDate validTo, String rawData) {
        }

        FakeIHubOrgGateway withOrg(String externalRef, long orgId) {
            orgIdsByExternalRef.put(externalRef, orgId);
            return this;
        }

        FakeIHubOrgGateway withAttributeVersion(long orgId, LocalDate validFrom, LocalDate validTo, String rawData) {
            attributeVersions.computeIfAbsent(orgId, k -> new java.util.ArrayList<>())
                    .add(new AttrVersion(validFrom, validTo, rawData));
            return this;
        }

        @Override
        public Optional<Long> resolveOrgIdByExternalRef(String externalOrgRef) {
            resolveCalls++;
            return Optional.ofNullable(orgIdsByExternalRef.get(externalOrgRef));
        }

        @Override
        public Optional<String> fetchOrgUnitAttributeData(long orgId, LocalDate asOfDate) {
            fetchAttributeCalls++;
            // Mirrors BZXOJNA0-In-ORNA-Val-From-Dt/-Val-To-Dt both being asOfDate
            // (F5879010.src:565-566): only a version whose own window covers
            // asOfDate on both ends is returned - RULE-039.
            return attributeVersions.getOrDefault(orgId, java.util.List.of()).stream()
                    .filter(v -> !asOfDate.isBefore(v.validFrom()) && !asOfDate.isAfter(v.validTo()))
                    .findFirst()
                    .map(AttrVersion::rawData);
        }
    }

    private static Clock fixedClockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    // =====================================================================
    // RULE-039: as-of-today attribute version filtering
    // =====================================================================
    @Nested
    @DisplayName("RULE-039: organization unit attributes are looked up as of today's system run date "
            + "(F5879010.src:537-566)")
    class Rule039AsOfTodayFiltering {

        @Test
        @DisplayName("rule039_singleValidVersionCoveringToday_isReturned")
        void rule039_singleValidVersionCoveringToday_isReturned() {
            long orgId = 4500001L;
            LocalDate today = LocalDate.of(2026, 9, 29);
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway()
                    .withAttributeVersion(orgId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                            "GRP:B02;SGRP:0007;UPDC:0001;MRR:05,123");
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            Optional<OrgUnitAttributes> result = client.lookupAttributes(String.valueOf(orgId), fixedClockOn(today));

            assertTrue(result.isPresent());
            OrgUnitAttributes attrs = result.get();
            assertEquals("B02", attrs.runGroup());
            assertEquals(7, attrs.subGroup());
            assertEquals(1, attrs.updateCode());
            assertEquals(new BigDecimal("5.123"), attrs.interimAccountRate());
            assertEquals(today, attrs.validFrom(), "RULE-039: valid-from is forced to today's run date");
            assertEquals(today, attrs.validTo(), "RULE-039: valid-to is forced to today's run date");
        }

        @Test
        @DisplayName("rule039_multipleVersionsWithDifferentWindows_onlyTheOneCoveringTodayIsReturned")
        void rule039_multipleVersionsWithDifferentWindows_onlyTheOneCoveringTodayIsReturned() {
            long orgId = 4500002L;
            LocalDate today = LocalDate.of(2026, 9, 29);
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway()
                    // past-dated version - must be ignored
                    .withAttributeVersion(orgId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31),
                            "GRP:OLD;SGRP:0001;UPDC:0001;MRR:01,000")
                    // the version actually covering today
                    .withAttributeVersion(orgId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                            "GRP:CUR;SGRP:0002;UPDC:0002;MRR:02,500")
                    // future-dated version - must be ignored
                    .withAttributeVersion(orgId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31),
                            "GRP:NEW;SGRP:0003;UPDC:0003;MRR:03,000");
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            Optional<OrgUnitAttributes> result = client.lookupAttributes(String.valueOf(orgId), fixedClockOn(today));

            assertTrue(result.isPresent());
            assertEquals("CUR", result.get().runGroup(),
                    "only the version whose validity window covers today may be returned, "
                            + "not a past- or future-dated version");
            assertEquals(2, result.get().subGroup());
            assertEquals(new BigDecimal("2.500"), result.get().interimAccountRate());
        }

        @Test
        @DisplayName("rule039_noVersionValidForToday_returnsEmpty")
        void rule039_noVersionValidForToday_returnsEmpty() {
            // F5879010.src:529-533: G200-Get-1st-Attribute not OK -> G400-Extract-Attributes
            // is skipped entirely and the error state is quietly reset - no error
            // surfaces to the caller, the attributes are simply absent.
            long orgId = 4500003L;
            LocalDate today = LocalDate.of(2026, 9, 29);
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway()
                    .withAttributeVersion(orgId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31),
                            "GRP:OLD;SGRP:0001;UPDC:0001;MRR:01,000");
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            Optional<OrgUnitAttributes> result = client.lookupAttributes(String.valueOf(orgId), fixedClockOn(today));

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("rule039_noAttributeVersionsAtAll_returnsEmpty")
        void rule039_noAttributeVersionsAtAll_returnsEmpty() {
            long orgId = 4500004L;
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway(); // no versions configured
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            Optional<OrgUnitAttributes> result =
                    client.lookupAttributes(String.valueOf(orgId), fixedClockOn(LocalDate.of(2026, 9, 29)));

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("rule039_differentRunDates_selectDifferentVersions_deterministicWithFixedClock")
        void rule039_differentRunDates_selectDifferentVersions_deterministicWithFixedClock() {
            // Same org, same two stored versions, two different "todays" via two
            // different fixed clocks - proves the as-of-today filter, not a
            // hardcoded/lucky match.
            long orgId = 4500005L;
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway()
                    .withAttributeVersion(orgId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                            "GRP:Y25;SGRP:0001;UPDC:0001;MRR:01,000")
                    .withAttributeVersion(orgId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                            "GRP:Y26;SGRP:0002;UPDC:0002;MRR:02,000");
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            Optional<OrgUnitAttributes> as2025 =
                    client.lookupAttributes(String.valueOf(orgId), fixedClockOn(LocalDate.of(2025, 6, 15)));
            Optional<OrgUnitAttributes> as2026 =
                    client.lookupAttributes(String.valueOf(orgId), fixedClockOn(LocalDate.of(2026, 6, 15)));

            assertEquals("Y25", as2025.orElseThrow().runGroup());
            assertEquals("Y26", as2026.orElseThrow().runGroup());
        }

        @Test
        @DisplayName("rule039_unrecognizedAttributeKey_isSilentlyIgnored")
        void rule039_unrecognizedAttributeKey_isSilentlyIgnored() {
            // F5879010.src:644-661 - the Evaluate WT-Data-Key has no When-Other
            // arm, so a key outside GRP/SGRP/UPDC/MRR is simply skipped.
            long orgId = 4500006L;
            LocalDate today = LocalDate.of(2026, 9, 29);
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway()
                    .withAttributeVersion(orgId, today, today, "FOO:BAR;GRP:B02;SGRP:0007;UPDC:0001;MRR:05,123");
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            OrgUnitAttributes attrs =
                    client.lookupAttributes(String.valueOf(orgId), fixedClockOn(today)).orElseThrow();

            assertEquals("B02", attrs.runGroup());
        }
    }

    // =====================================================================
    // RULE-018's documented edge case: Fin-Inst-No -> internal ID resolution
    // =====================================================================
    @Nested
    @DisplayName("RULE-018 edge case: F5879010 resolves Fin-Inst-No to an internal Fin-Inst-ID "
            + "(F5879010.src:339-356)")
    class FinInstResolutionTests {

        @Test
        @DisplayName("rule018_resolvableFinInstNo_returnsInternalId")
        void rule018_resolvableFinInstNo_returnsInternalId() {
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway().withOrg("0815", 4500001L);
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            long resolved = client.resolveFinInstId("0815");

            assertEquals(4500001L, resolved);
            assertEquals(1, gateway.resolveCalls);
        }

        @Test
        @DisplayName("rule018_unresolvableFinInstNo_throwsMissingKeyFieldException")
        void rule018_unresolvableFinInstNo_throwsMissingKeyFieldException() {
            // F5879010.src:436-439 - the DB2/BZXOJOR0 lookup returning
            // R791ERRX-State-Error (not found) for a select-by-No qualification.
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway(); // knows no organizations
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> client.resolveFinInstId("9999"));

            assertEquals(OrgMasterDataClientImpl.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_distinctFinInstNos_resolveToDistinctIds")
        void rule018_distinctFinInstNos_resolveToDistinctIds() {
            FakeIHubOrgGateway gateway = new FakeIHubOrgGateway()
                    .withOrg("0815", 4500001L)
                    .withOrg("4711", 4500002L);
            OrgMasterDataClient client = new OrgMasterDataClientImpl(gateway);

            assertEquals(4500001L, client.resolveFinInstId("0815"));
            assertEquals(4500002L, client.resolveFinInstId("4711"));
        }
    }
}
