package com.tfi.tfr12.phase3.listing;

import com.tfi.tfr12.phase1.datetime.MachineTimestamp;
import com.tfi.tfr12.phase1.datetime.MachineTimestampService;
import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.IncomSwift;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;
import com.tfi.tfr12.phase2.dbi.syschargetype.SysChargeTypeRow;
import com.tfi.tfr12.phase3.pricing.PopPricingService;
import com.tfi.tfr12.phase3.pricing.PricingRequest;
import com.tfi.tfr12.phase3.pricing.PricingResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins 7 business rules from {@code analysis/TFR12/BUSINESS_RULES.md} for legacy
 * program {@code F115L060} ("Listing/Computation of sum total of charges",
 * {@code legacy/TFR12/src/F115L060.src}), hand-traced against the source, per explicit
 * instruction for this phase (dual-execution against GnuCOBOL is out of scope). The
 * file is ISO-8859-1/Latin-1; column 7 '*' marks a comment, columns 8-72 are code.
 * Every test cites the exact source line range it pins. See
 * {@link ChargeListingService} for the paragraph-to-method mapping and the class-level
 * scope notes (in particular the RULE-001 boundary note).
 *
 * <p>One {@code @Nested} class per rule:
 * <ul>
 *   <li>{@link Rule019ContractTypeClassification} - RULE-019 (P1)</li>
 *   <li>{@link Rule033RoleTypeResolution} - RULE-033 (P1)</li>
 *   <li>{@link Rule038ChargeTypeConfigFallback} - RULE-038 (P2)</li>
 *   <li>{@link Rule029SettledPartAmountSuppression} - RULE-029 (P1)</li>
 *   <li>{@link Rule006CurrencyRelabelOnly} - RULE-006 (P1)</li>
 *   <li>{@link Rule007PercentBasedChargeAmount} - RULE-007 (P1)</li>
 *   <li>{@link Rule001PopPricedTodayBoundary} - RULE-001 (P0)</li>
 * </ul>
 *
 * <p>No credential-like values appear in F115L060.src; nothing here needed
 * substitution. Bank numbers/currencies/amounts below are all synthetic.
 */
class ChargeListingServiceTest {

    // =====================================================================
    // RULE-019 (P1, F115L060.src:253-269): contract-type classification.
    // =====================================================================
    @Nested
    @DisplayName("RULE-019: contract-type classification for GL account and applicability rules")
    class Rule019ContractTypeClassification {

        private final ChargeListingService service = newService();

        @Test
        @DisplayName("rule019_collectionContractTypes_classifyAsCollection")
        void rule019_collectionContractTypes_classifyAsCollection() {
            // F115L060.src:256-259.
            for (String type : List.of("IDP", "IDA", "ICP", "ICA", "EDP", "EDA", "ECP", "ECA")) {
                assertEquals(ContractFamily.COLLECTION, service.classifyContractType(type), type);
            }
        }

        @Test
        @DisplayName("rule019_importExportLcAndGuaranteeContractTypes_classifyCorrectly")
        void rule019_importExportLcAndGuaranteeContractTypes_classifyCorrectly() {
            assertEquals(ContractFamily.IMPORT_LC, service.classifyContractType("ILC")); // F115L060.src:260-261
            assertEquals(ContractFamily.IMPORT_LC, service.classifyContractType("ILU"));
            assertEquals(ContractFamily.EXPORT_LC, service.classifyContractType("ELC")); // F115L060.src:262-263
            assertEquals(ContractFamily.EXPORT_LC, service.classifyContractType("ECC"));
            assertEquals(ContractFamily.GUARANTEE, service.classifyContractType("ILG")); // F115L060.src:264-265
            assertEquals(ContractFamily.GUARANTEE, service.classifyContractType("ELG"));
        }

        @Test
        @DisplayName("rule019_unrecognizedContractType_aborts")
        void rule019_unrecognizedContractType_aborts() {
            // F115L060.src:266-268: When Other -> Z900-Abend (paragraph C301).
            assertThrows(UnrecognizedContractTypeException.class,
                    () -> service.classifyContractType("XYZ"));
        }

        @Test
        @DisplayName("rule019_glAccountSelection_followsContractFamily")
        void rule019_glAccountSelection_followsContractFamily() {
            // F115L060.src:1001-1017 (J100-Iter-Output): the family picks which of the
            // charge type's 4 GL-account fields is used.
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "P",
                    222L, 333L, 444L, 111L, true);
            assertEquals(111L, ContractFamily.COLLECTION.glAccount(config));
            assertEquals(222L, ContractFamily.IMPORT_LC.glAccount(config));
            assertEquals(333L, ContractFamily.EXPORT_LC.glAccount(config));
            assertEquals(444L, ContractFamily.GUARANTEE.glAccount(config));
        }
    }

    // =====================================================================
    // RULE-033 (P1, F115L060.src:499-570): fee-payer role resolution.
    // =====================================================================
    @Nested
    @DisplayName("RULE-033: fee-payer role determined by contract type and charge role category")
    class Rule033RoleTypeResolution {

        private final ChargeListingService service = newService();

        @Test
        @DisplayName("rule033_collection_allFiveRoleCategories")
        void rule033_collection_allFiveRoleCategories() {
            assertEquals(List.of("GLACC"),
                    service.resolveRoleTypes(ChargeRoleCategory.GLACC, ContractFamily.COLLECTION)); // :500-502
            assertEquals(List.of(""),
                    service.resolveRoleTypes(ChargeRoleCategory.AGENT_PTY, ContractFamily.COLLECTION)); // :504-506
            assertEquals(List.of("DRWEE", "DRWER"),
                    service.resolveRoleTypes(ChargeRoleCategory.ANY, ContractFamily.COLLECTION)); // :508-513
            assertEquals(List.of("DRWEE"),
                    service.resolveRoleTypes(ChargeRoleCategory.BUYER, ContractFamily.COLLECTION)); // :534-536
            assertEquals(List.of("DRWER"),
                    service.resolveRoleTypes(ChargeRoleCategory.SELLER, ContractFamily.COLLECTION)); // :552-554
        }

        @Test
        @DisplayName("rule033_importLc_customerAndBeneficiary")
        void rule033_importLc_customerAndBeneficiary() {
            assertEquals(List.of("CUSTM", "BENEF"),
                    service.resolveRoleTypes(ChargeRoleCategory.ANY, ContractFamily.IMPORT_LC)); // :514-519
            assertEquals(List.of("CUSTM"),
                    service.resolveRoleTypes(ChargeRoleCategory.BUYER, ContractFamily.IMPORT_LC)); // :538-540
            assertEquals(List.of("BENEF"),
                    service.resolveRoleTypes(ChargeRoleCategory.SELLER, ContractFamily.IMPORT_LC)); // :556-558
        }

        @Test
        @DisplayName("rule033_exportLc_issuerAndBeneficiary")
        void rule033_exportLc_issuerAndBeneficiary() {
            assertEquals(List.of("ISSUI", "BENEF"),
                    service.resolveRoleTypes(ChargeRoleCategory.ANY, ContractFamily.EXPORT_LC)); // :520-525
            assertEquals(List.of("ISSUI"),
                    service.resolveRoleTypes(ChargeRoleCategory.BUYER, ContractFamily.EXPORT_LC)); // :542-544
            assertEquals(List.of("BENEF"),
                    service.resolveRoleTypes(ChargeRoleCategory.SELLER, ContractFamily.EXPORT_LC)); // :560-562
        }

        @Test
        @DisplayName("rule033_guarantee_customerAndBeneficiary_ndlGarPatch")
        void rule033_guarantee_customerAndBeneficiary_ndlGarPatch() {
            // The 2013 "ndl"/"gar" patch lines (F115L060.src:527-532, 547-551, 565-568).
            assertEquals(List.of("CUSTM", "BENEF"),
                    service.resolveRoleTypes(ChargeRoleCategory.ANY, ContractFamily.GUARANTEE));
            assertEquals(List.of("CUSTM"),
                    service.resolveRoleTypes(ChargeRoleCategory.BUYER, ContractFamily.GUARANTEE));
            assertEquals(List.of("BENEF"),
                    service.resolveRoleTypes(ChargeRoleCategory.SELLER, ContractFamily.GUARANTEE));
        }

        @Test
        @DisplayName("rule033_unmatchedCombination_readsNoCharges")
        void rule033_unmatchedCombination_readsNoCharges() {
            // F115L060.src:499-570 has no "When Other": GLACC/AGENT_PTY charge types only
            // ever apply to Collection contracts; on Import LC (or Export LC / Guarantee)
            // the Evaluate falls through with no match, so no role type - and hence no
            // charge - is produced for that combination.
            assertEquals(List.of(), service.resolveRoleTypes(ChargeRoleCategory.GLACC, ContractFamily.IMPORT_LC));
            assertEquals(List.of(), service.resolveRoleTypes(ChargeRoleCategory.AGENT_PTY, ContractFamily.EXPORT_LC));
            assertEquals(List.of(), service.resolveRoleTypes(ChargeRoleCategory.GLACC, ContractFamily.GUARANTEE));
        }
    }

    // =====================================================================
    // RULE-038 (P2, F115L060.src:1104-1156): institution-then-regno fallback.
    // =====================================================================
    @Nested
    @DisplayName("RULE-038: charge-type configuration falls back from institution to registration number")
    class Rule038ChargeTypeConfigFallback {

        @Test
        @DisplayName("rule038_institutionScopedLookupHits_regnoLookupNeverCalled")
        void rule038_institutionScopedLookupHits_regnoLookupNeverCalled() {
            SysChargeTypeDetails found = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "T", 0, 0, 0, 0, true);
            FakeSysChargeTypeGateway gateway = FakeSysChargeTypeGateway.hitByFinInst(found);
            ChargeListingService service = newService(gateway, new FakePopPricingService(), new FakeMachineTimestampService());

            SysChargeTypeDetails result = service.resolveChargeTypeConfig("CT1", 42L, 7);

            assertEquals(found, result);
            assertEquals(0, gateway.selectByRegnoCalls); // F115L060.src:1153-1154: When Return-OK, Continue.
        }

        @Test
        @DisplayName("rule038_institutionScopedLookupMisses_fallsBackToRegno")
        void rule038_institutionScopedLookupMisses_fallsBackToRegno() {
            SysChargeTypeDetails found = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "T", 0, 0, 0, 0, true);
            FakeSysChargeTypeGateway gateway = FakeSysChargeTypeGateway.missByFinInst_hitByRegno(found);
            ChargeListingService service = newService(gateway, new FakePopPricingService(), new FakeMachineTimestampService());

            SysChargeTypeDetails result = service.resolveChargeTypeConfig("CT1", 42L, 7);

            assertEquals(found, result);
            assertEquals(1, gateway.selectByRegnoCalls);
            assertEquals(7, gateway.lastRegNoArg); // F115L060.src:1135-1136: Move In-Reg-No To In-Regno.
        }

        @Test
        @DisplayName("rule038_bothLookupsMiss_rejectsWithL103")
        void rule038_bothLookupsMiss_rejectsWithL103() {
            FakeSysChargeTypeGateway gateway = FakeSysChargeTypeGateway.missBoth();
            ChargeListingService service = newService(gateway, new FakePopPricingService(), new FakeMachineTimestampService());

            // F115L060.src:1147-1149: Z700-Error-AE 'L103'.
            assertThrows(ChargeTypeNotConfiguredException.class,
                    () -> service.resolveChargeTypeConfig("CT1", 42L, 7));
        }
    }

    // =====================================================================
    // RULE-029 (P1, F115L060.src:604-628): settled part-amount suppresses recalc.
    // =====================================================================
    @Nested
    @DisplayName("RULE-029: settled part-amount suppresses recalculation of percentage-based charge")
    class Rule029SettledPartAmountSuppression {

        private final ChargeListingService service = newService();

        @Test
        @DisplayName("rule029_notPercentOfPartAmountMethod_alwaysReadsMainCharge")
        void rule029_notPercentOfPartAmountMethod_alwaysReadsMainCharge() {
            // F115L060.src:626-628 (Else of the outer If): unconditional.
            assertTrue(service.shouldReadMainCharge("P", 5, false, true));
            assertTrue(service.shouldReadMainCharge("P", 5, true, false));
        }

        @Test
        @DisplayName("rule029_percentOfPartAmountButNoCheckRequested_alwaysReadsMainCharge")
        void rule029_percentOfPartAmountButNoCheckRequested_alwaysReadsMainCharge() {
            // F115L060.src:617 (In-Check-PA > +0 is false) -> Else -> unconditional.
            assertTrue(service.shouldReadMainCharge("Q", 0, false, true));
            assertTrue(service.shouldReadMainCharge("Q", -1, false, true));
        }

        @Test
        @DisplayName("rule029_checkErrored_stillReadsMainCharge")
        void rule029_checkErrored_stillReadsMainCharge() {
            // F115L060.src:622-625: R7919999-Return-Error -> still Perform E000.
            assertTrue(service.shouldReadMainCharge("Q", 3, true, true));
            assertTrue(service.shouldReadMainCharge("Q", 3, true, false));
        }

        @Test
        @DisplayName("rule029_settledWithNoError_suppressesMainChargeRead")
        void rule029_settledWithNoError_suppressesMainChargeRead() {
            // F115L060.src:622-625: no error AND Has-Been-Settled -> the If is false ->
            // E000-Read-Main-Charge is NOT performed.
            assertFalse(service.shouldReadMainCharge("Q", 3, false, true));
        }

        @Test
        @DisplayName("rule029_notSettledWithNoError_stillReadsMainCharge")
        void rule029_notSettledWithNoError_stillReadsMainCharge() {
            assertTrue(service.shouldReadMainCharge("Q", 3, false, false));
        }
    }

    // =====================================================================
    // RULE-006 (P1, F115L060.src:1050-1101): currency relabel without conversion.
    // =====================================================================
    @Nested
    @DisplayName("RULE-006: charge currency is re-labelled to contract currency without recalculating the amount")
    class Rule006CurrencyRelabelOnly {

        @Test
        @DisplayName("rule006_currencyMatchesContract_untouched")
        void rule006_currencyMatchesContract_untouched() {
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "DRWEE", dec("100.00"), "USD", dec("0.000"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("100000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals("USD", presented.currency());
            assertEquals(dec("100.000"), presented.convertedAmount());
        }

        @Test
        @DisplayName("rule006_currencyDiffersFromContract_relabeledButAmountNotConverted")
        void rule006_currencyDiffersFromContract_relabeledButAmountNotConverted() {
            // F115L060.src:1076-1079: Move WS-Charge-Amount To WS-Converted-Amount FIRST
            // (unconditionally, no FX), THEN relabel the currency if it differs.
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "DRWEE", dec("100.00"), "USD", dec("0.000"), "text");
            ContractAmountRow ca = contractAmount("EUR", dec("100000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals("EUR", presented.currency());
            // The suspected defect the RULE-006 card names: 100 USD becomes "100 EUR", not
            // an FX-converted EUR amount. Pinning the CURRENT (relabel-only) behavior as
            // correct, per explicit instruction.
            assertEquals(dec("100.000"), presented.convertedAmount());
        }
    }

    // =====================================================================
    // RULE-007 (P1, F115L060.src:1341-1381): percent-based charge amount.
    // =====================================================================
    @Nested
    @DisplayName("RULE-007: percentage-based charge amount derived from Contract or Part Amount")
    class Rule007PercentBasedChargeAmount {

        @Test
        @DisplayName("rule007_pctOfContractAmount_computesFromCurrOriginalAmount")
        void rule007_pctOfContractAmount_computesFromCurrOriginalAmount() {
            ChargeListingService service = newService();
            // 100,000.00 * 2.500 / 100 = 2,500.00 (RULE-007 card's own worked example).
            ChargeLine raw = new ChargeLine("CT1", "DRWEE", ZERO_2DP, "USD", dec("2.500"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("100000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_PCT_CA, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(dec2("2500.00"), presented.chargeAmount());
        }

        @Test
        @DisplayName("rule007_roundingIsHalfUp_notTruncated")
        void rule007_roundingIsHalfUp_notTruncated() {
            // 100.000 * 33.335 / 100 = 33.335 exactly -> ROUNDED (half away from zero) to
            // 2 decimals = 33.34. A truncating (DOWN) implementation would wrongly give
            // 33.33 - this is the case that proves HALF_UP, not just "some rounding".
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "DRWEE", ZERO_2DP, "USD", dec("33.335"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("100.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_PCT_CA, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(dec2("33.34"), presented.chargeAmount());
        }

        @Test
        @DisplayName("rule007_pctOfPartAmount_withPartAmountSeqSupplied_usesPartAmount")
        void rule007_pctOfPartAmount_withPartAmountSeqSupplied_usesPartAmount() {
            // F115L060.src:1353-1357: PA-Seq-No != 0 -> use the Part Amount, not the
            // contract amount (deliberately different values below to prove which base
            // was actually used).
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "CUSTM", ZERO_2DP, "USD", dec("10.000"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("999999.000"));
            PartAmountRow pa = partAmountRow(dec("5000.000"), false);
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_PCT_PA, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 3, pa, "0001");

            assertEquals(dec2("500.00"), presented.chargeAmount()); // 5000 * 10% = 500, not 99999.9
        }

        @Test
        @DisplayName("rule007_pctOfPartAmount_noPartAmountSeqSupplied_fallsBackToContractAmount")
        void rule007_pctOfPartAmount_noPartAmountSeqSupplied_fallsBackToContractAmount() {
            // F115L060.src:1357-1362 (Else): PA-Seq-No = 0 -> Curr-O-Amount.
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "CUSTM", ZERO_2DP, "USD", dec("10.000"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("5000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_PCT_PA, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(dec2("500.00"), presented.chargeAmount());
        }

        @Test
        @DisplayName("rule007_baseAmountZero_chargeAmountForcedZero_noDivideByZero")
        void rule007_baseAmountZero_chargeAmountForcedZero_noDivideByZero() {
            // F115L060.src:1375-1378 (Else).
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "DRWEE", ZERO_2DP, "USD", dec("2.500"), "text");
            ContractAmountRow ca = contractAmount("USD", BigDecimal.ZERO.setScale(3));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_PCT_CA, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(dec2("0.00"), presented.chargeAmount());
        }

        @Test
        @DisplayName("rule007_nonZeroStoredChargeAmount_neverRecomputedFromPercent")
        void rule007_nonZeroStoredChargeAmount_neverRecomputedFromPercent() {
            // F115L060.src:1059-1062: recompute is gated on WS-Charge-Amount = Zero. A
            // charge already carrying a stored amount is left exactly as read, even though
            // a (deliberately wildly different) percentage is also present.
            ChargeListingService service = newService();
            ChargeLine raw = new ChargeLine("CT1", "DRWEE", dec2("17.50"), "USD", dec("99.999"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("100000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_PCT_CA, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(dec2("17.50"), presented.chargeAmount());
        }
    }

    // =====================================================================
    // RULE-001 (P0, F115L060.src:1257-1280, 1096-1101): POP priced as of today.
    // =====================================================================
    @Nested
    @DisplayName("RULE-001: party-specific charge price override (POP) - the F115L060-owned boundary")
    class Rule001PopPricedTodayBoundary {

        @Test
        @DisplayName("rule001_readPopEnabled_fetchesMachineDateAndCallsPopExactlyOnce")
        void rule001_readPopEnabled_fetchesMachineDateAndCallsPopExactlyOnce() {
            // F115L060.src:1096, 1100 (Read-POP-No is false) -> L400-Read-POP performed,
            // which (F115L060.src:1278) forwards the machine date obtained once per run in
            // B000-Initialize (F115L060.src:178-188) as PricingRequest.validDate().
            FakePopPricingService pop = new FakePopPricingService();
            pop.nextResult = new PricingResult(dec("0"), dec("0"), "USD", dec("50.000"), dec("5.000"));
            FakeMachineTimestampService timestamps = new FakeMachineTimestampService();
            ChargeListingService service = newService(FakeSysChargeTypeGateway.missBoth(), pop, timestamps);

            ChargeLine raw = new ChargeLine("CT1", "DRWEE", dec2("10.00"), "USD", dec("0.000"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("100000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "T", 0, 0, 0, 0, true);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(1, timestamps.callCount);
            assertEquals(1, pop.callCount);
            assertEquals(dec("50.000"), presented.maximumCharge());
            assertEquals(dec("5.000"), presented.minimumCharge());
            // F115L150.src linkage: what F115L060 supplies as PricingRequest.
            assertEquals("0001", pop.lastRequest.bankNo());
            assertEquals(config.row(), pop.lastRequest.chargeTypeConfig());
            assertEquals(ca, pop.lastRequest.contractAmount());
            // RULE-001: the fetched machine date (date8 = 20260929) reaches the pricing
            // request unchanged, closing the F115L060 <-> F115L150 boundary end to end.
            assertEquals(20260929, pop.lastRequest.validDate());
        }

        @Test
        @DisplayName("rule001_readPopDisabled_skipsPopCallAndMachineDateFetch_zeroMinMax")
        void rule001_readPopDisabled_skipsPopCallAndMachineDateFetch_zeroMinMax() {
            // F115L060.src:1096-1098: R115ICT0-Read-POP-No -> Initialize R115L150-TF-POP,
            // L400-Read-POP (and hence the date fetch it depends on) never runs at all.
            FakePopPricingService pop = new FakePopPricingService();
            FakeMachineTimestampService timestamps = new FakeMachineTimestampService();
            ChargeListingService service = newService(FakeSysChargeTypeGateway.missBoth(), pop, timestamps);

            ChargeLine raw = new ChargeLine("CT1", "DRWEE", dec2("10.00"), "USD", dec("0.000"), "text");
            ContractAmountRow ca = contractAmount("USD", dec("100000.000"));
            SysChargeTypeDetails config = chargeTypeDetails("CT1", METHOD_CALC_FLAT, "T", 0, 0, 0, 0, false);

            PresentedCharge presented = service.applyAdditionalInfo(raw, ca, config, 0, null, "0001");

            assertEquals(0, timestamps.callCount);
            assertEquals(0, pop.callCount);
            assertEquals(dec("0.000"), presented.maximumCharge());
            assertEquals(dec("0.000"), presented.minimumCharge());
        }
    }

    // =====================================================================
    // Fixture helpers
    // =====================================================================

    private static final String METHOD_CALC_FLAT = "S"; // R115ICT0-MethCalc-Settlement, not P or Q.
    private static final String METHOD_CALC_PCT_CA = "P"; // R115ICT0-MethCalc-Pct-Of-CA.
    private static final String METHOD_CALC_PCT_PA = "Q"; // R115ICT0-MethCalc-Pct-Of-PA.
    private static final BigDecimal ZERO_2DP = BigDecimal.ZERO.setScale(2);

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal dec2(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private static ChargeListingService newService() {
        return newService(FakeSysChargeTypeGateway.missBoth(), new FakePopPricingService(), new FakeMachineTimestampService());
    }

    private static ChargeListingService newService(SysChargeTypeGateway gateway, PopPricingService pop,
                                                    MachineTimestampService timestamps) {
        return new ChargeListingServiceImpl(gateway, pop, timestamps, Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC));
    }

    private static ContractAmountRow contractAmount(String currency, BigDecimal currOriginalAmount) {
        return new ContractAmountRow(1L, "00000001", "IDP", 100L, currency,
                currOriginalAmount, currOriginalAmount, currOriginalAmount, 0, 0);
    }

    private static PartAmountRow partAmountRow(BigDecimal amount, boolean hasBeenSettled) {
        return new PartAmountRow(1L, "00000001", "IDP", 100L, "USD", 3,
                hasBeenSettled, IncomSwift.UNSPECIFIED, amount, "OPEN", "TESTUSER", false);
    }

    private static SysChargeTypeDetails chargeTypeDetails(String ctType, String methodCalc, String credit,
                                                            long glIlcAcc, long glElcAcc, long glGarAcc,
                                                            long glCollAcc, boolean readPopEnabled) {
        SysChargeTypeRow row = new SysChargeTypeRow(ctType, 1L, "00000001", 0,
                "description", methodCalc, credit, glCollAcc, BigDecimal.ZERO.setScale(2));
        return new SysChargeTypeDetails(row, ChargeRoleCategory.BUYER, readPopEnabled, glIlcAcc, glElcAcc, glGarAcc);
    }

    // =====================================================================
    // Test doubles
    // =====================================================================

    private static final class FakeSysChargeTypeGateway implements SysChargeTypeGateway {
        private final Optional<SysChargeTypeDetails> byFinInstResult;
        private final Optional<SysChargeTypeDetails> byRegnoResult;
        int selectByRegnoCalls = 0;
        int lastRegNoArg = -1;

        private FakeSysChargeTypeGateway(Optional<SysChargeTypeDetails> byFinInstResult,
                                          Optional<SysChargeTypeDetails> byRegnoResult) {
            this.byFinInstResult = byFinInstResult;
            this.byRegnoResult = byRegnoResult;
        }

        static FakeSysChargeTypeGateway hitByFinInst(SysChargeTypeDetails result) {
            return new FakeSysChargeTypeGateway(Optional.of(result), Optional.empty());
        }

        static FakeSysChargeTypeGateway missByFinInst_hitByRegno(SysChargeTypeDetails result) {
            return new FakeSysChargeTypeGateway(Optional.empty(), Optional.of(result));
        }

        static FakeSysChargeTypeGateway missBoth() {
            return new FakeSysChargeTypeGateway(Optional.empty(), Optional.empty());
        }

        @Override
        public Optional<SysChargeTypeDetails> selectByFinInst(String chargeType, long finInstId) {
            return byFinInstResult;
        }

        @Override
        public Optional<SysChargeTypeDetails> selectByRegno(String chargeType, int regNo) {
            selectByRegnoCalls++;
            lastRegNoArg = regNo;
            return byRegnoResult;
        }
    }

    private static final class FakePopPricingService implements PopPricingService {
        PricingResult nextResult = new PricingResult(
                BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), "", BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3));
        int callCount = 0;
        PricingRequest lastRequest;
        final List<PricingRequest> allRequests = new ArrayList<>();

        @Override
        public PricingResult computeDefaultCharge(PricingRequest request) {
            callCount++;
            lastRequest = request;
            allRequests.add(request);
            return nextResult;
        }
    }

    private static final class FakeMachineTimestampService implements MachineTimestampService {
        int callCount = 0;

        @Override
        public MachineTimestamp fetchTimestamp(Clock clock) {
            callCount++;
            return new MachineTimestamp(2026, 9, 29, 0, 0, 0, 0,
                    "2026-09-29-00.00.00.000000", "20260929000000000000", 2026092900000000L, 20260929, 0);
        }
    }
}
