package com.tfi.tfr12.phase3.pricing;

import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.IncomSwift;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;
import com.tfi.tfr12.phase2.dbi.syschargetype.SysChargeTypeRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link PopPricingServiceImpl}, hand-traced against
 * {@code legacy/STG/TFR12/src/F115L150.src} (dual-execution against a live GnuCOBOL run is
 * explicitly out of scope for this phase, per instruction). Every test cites the exact
 * source lines it pins so a reviewer can re-derive the expected value independently.
 *
 * <p>Rule coverage (see {@code analysis/TFR12/BUSINESS_RULES.md}):
 * <ul>
 *   <li>{@link Rule001ValidDateForwardedToProductPriceLookup} - RULE-001 (P0, pricing-side half)</li>
 *   <li>{@link Rule003GraduatedTieredPricing} - RULE-003 (P0)</li>
 *   <li>{@link Rule011PercentBasisIsOriginalAmount} - RULE-011 (P1)</li>
 *   <li>{@link Rule012CurrencyConversion} - RULE-012 (P1)</li>
 *   <li>{@link Rule021FlatAmountXorPercentage} - RULE-021 (P1)</li>
 * </ul>
 */
class PopPricingServiceTest {

    // ------------------------------------------------------------------
    // RULE-001 (P0, F115L150.src:264-276): the caller's valid-date must reach the
    // PP_Prod_Tjen_Pris qualification key unchanged, so today's negotiated price is
    // selected rather than the contract's original date. The complementary
    // F115L060-side half (fetching today's date at all) is pinned in
    // ChargeListingServiceTest.Rule001PopPricedTodayBoundary.
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("RULE-001: caller's valid-date reaches the product-price lookup key")
    class Rule001ValidDateForwardedToProductPriceLookup {

        @Test
        @DisplayName("rule001_validDateOnRequest_isForwardedVerbatimToProductPriceQuery")
        void rule001_validDateOnRequest_isForwardedVerbatimToProductPriceQuery() {
            PricingTier tier = tier(zero(), pct("1.000"), zero(), zero(), zero(), zero(), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);

            ContractAmountRow contractAmount = contractAmount("NOK", amt("100000"), amt("100000"));
            SysChargeTypeRow chargeType = chargeType("S");
            PricingRequest request = new PricingRequest(0, "00010000", chargeType, contractAmount, null, 20260315);

            service.computeDefaultCharge(request);

            assertEquals(1, priceGateway.calls().size());
            assertEquals(20260315, priceGateway.calls().get(0).validDate());
        }
    }

    // ------------------------------------------------------------------
    // RULE-003 (P0, F115L150.src:463-519): graduated/tiered percentage pricing.
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("RULE-003: graduated/tiered percentage pricing across amount bands")
    class Rule003GraduatedTieredPricing {

        @Test
        @DisplayName("rule003_twoTiers_amountWithinSecondTier_sumsIncrementsPerBand")
        void rule003_twoTiers_amountWithinSecondTier_sumsIncrementsPerBand() {
            // Worked example from analysis/TFR12/BUSINESS_RULES.md RULE-003, re-verified
            // directly against F115L150.src:463-519:
            //   Tier 1: valid up to 10,000 at 1.0%; Tier 2: valid up to 25,000 at 0.5%;
            //   amount (in the bank's basic currency) = 20,000.
            //   Increment 1 = (10,000 - 0) * 1.0% = 100.00
            //   Increment 2 = (20,000 - 10,000) * 0.5% = 50.00
            //   total = 150.00
            PricingTier tier1 = tier(zero(), pct("1.000"), zero(), zero(), zero(), zero(), amt("10000"));
            PricingTier tier2 = tier(zero(), pct("0.500"), amt("999"), amt("111"), zero(), zero(), amt("25000"));

            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier1, tier2);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);

            // No part amount supplied -> F115L150.src:158-159: the tiered base is the
            // contract's REMAINING amount (deliberately 20,000, distinct from the RULE-011
            // "original amount" case to show the two rules use different bases).
            PricingRequest request = requestWithoutPartAmount("USD", amt("20000"), amt("999999"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("150.000"), result.chargeAmount());
            // F115L150.src:514-517: max/min come from the LAST tier actually read (tier 2),
            // not tier 1.
            assertEquals(dec("999.000"), result.maximumCharge());
            assertEquals(dec("111.000"), result.minimumCharge());
            assertEquals("USD", result.currency());
        }

        @Test
        @DisplayName("rule003_amountExceedsLastTierBand_chargesOnlyUpToLastTierThreshold")
        void rule003_amountExceedsLastTierBand_chargesOnlyUpToLastTierThreshold() {
            // Same two tiers as above but the amount (30,000) exceeds tier 2's own
            // threshold (25,000). F115L150.src:483-512: the Perform loop stops the moment
            // Get-Next fails (no third tier) even though 30,000 > 25,000 still holds -
            // the schedule simply does not charge for the amount above the last band.
            PricingTier tier1 = tier(zero(), pct("1.000"), zero(), zero(), zero(), zero(), amt("10000"));
            PricingTier tier2 = tier(zero(), pct("0.500"), zero(), zero(), zero(), zero(), amt("25000"));

            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier1, tier2);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("USD", amt("30000"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            // Increment 1 = (10,000-0)*1.0% = 100.00; Increment 2 = (25,000-10,000)*0.5% = 75.00
            // (tier 2's own Gyldig-Til-Belop is used as the upper bound of the slice, per the
            // "If R349IPP0-Gyldig-Til-Belop < WS-PA-or-CA-Amount-Ex" branch, F115L150.src:486-489).
            assertEquals(dec("175.000"), result.chargeAmount());
        }

        @Test
        @DisplayName("rule003_amountNotAboveFirstThreshold_zeroIterations_maxMinComeFromFirstTier")
        void rule003_amountNotAboveFirstThreshold_zeroIterations_maxMinComeFromFirstTier() {
            // F115L150.src:483: "Perform Until ... Or WS-PA-or-CA-Amount-Ex <= WS-Stored-Gyldig-Til-Bel"
            // is tested BEFORE the first iteration. WS-Stored-Gyldig-Til-Bel starts at 0
            // (B000-Initialize), so an amount of exactly 0 means zero iterations run, and
            // Max/Min are read from whichever row C400-Read-1st-Prod-Tjen-Pris already loaded
            // (tier 1), never advancing to tier 2.
            PricingTier tier1 = tier(zero(), pct("1.000"), amt("50"), amt("5"), zero(), zero(), amt("10000"));
            PricingTier tier2 = tier(zero(), pct("0.500"), amt("999"), amt("111"), zero(), zero(), amt("25000"));

            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier1, tier2);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("USD", amt("0"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("0.000"), result.chargeAmount());
            assertEquals(dec("50.000"), result.maximumCharge());
            assertEquals(dec("5.000"), result.minimumCharge());
        }

        @Test
        @DisplayName("rule003_tieredAmountBase_isConvertedToBasicCurrencyBeforeTiering")
        void rule003_tieredAmountBase_isConvertedToBasicCurrencyBeforeTiering() {
            // F115L150.src:471-480: before any tier comparison happens, the contract's
            // amount is converted FROM the contract currency INTO the bank's basic
            // currency; the tier thresholds (Gyldig-Til-Belop) are compared against that
            // converted amount, not the raw contract-currency amount.
            //
            // A second (inert) tier is required here purely so R349IPP0-Ut-Ak-Rader-Totalt
            // is > 1 and C000-Interface dispatches to E000 at all (F115L150.src:152-160);
            // per F115L150.src:505-511, C430-Call-DBI-PP-PP (Get-Next) always runs once at
            // the end of the loop's first iteration regardless of whether a further
            // iteration is needed, so tier 2 becomes "the currently loaded row" (and its
            // Maksimumpris/Minimumpris feed the result) even though tier 1 alone already
            // covers the whole converted amount.
            PricingTier tier1 = tier(zero(), pct("2.000"), zero(), zero(), zero(), zero(), amt("100000"));
            PricingTier tier2 = tier(zero(), zero(), zero(), zero(), zero(), zero(), amt("999999999"));

            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier1, tier2);
            // 1 contract-currency unit = 2 basic-currency units, and vice versa halves it back.
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.withRates(dec("2"), dec("0.5"));
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            // 10,000 contract-currency units -> 20,000 basic-currency units for tiering.
            PricingRequest request = requestWithoutPartAmount("EUR", amt("10000"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            // Increment = (20,000 - 0) * 2.0% = 400.00 basic-currency, then converted back:
            // 400.00 * 0.5 = 200.00 contract-currency.
            assertEquals(dec("200.000"), result.chargeAmount());
        }
    }

    // ------------------------------------------------------------------
    // RULE-011 (P1, F115L150.src:441-461): percent-of-contract-amount basis.
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("RULE-011: percent-of-contract-amount uses the ORIGINAL amount, not outstanding")
    class Rule011PercentBasisIsOriginalAmount {

        @Test
        @DisplayName("rule011_noPartAmount_usesOriginalAmount_notRemainingAmount")
        void rule011_noPartAmount_usesOriginalAmount_notRemainingAmount() {
            // Worked example from analysis/TFR12/BUSINESS_RULES.md RULE-011, re-verified
            // against D200-Compute-Charge-Amount (F115L150.src:452-461): original amount
            // 200,000 at 0.5% -> charge 1,000.00, using R115ICA0-Curr-O-Amount
            // (F115L150.src:458). The commented-out alternative at F115L150.src:457
            // (R115ICA0-Remaining-Amount) is dead code - remainingAmount is deliberately
            // set to a different value (150,000) here so a regression that resurrects that
            // line would be caught (750.00 instead of 1,000.00).
            PricingTier tier = tier(zero(), pct("0.500"), zero(), zero(), zero(), zero(), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);

            ContractAmountRow contractAmount = contractAmount("NOK", amt("150000"), amt("200000"));
            SysChargeTypeRow chargeType = chargeType("S"); // Settlement, i.e. NOT Pct-Of-PA ('Q')
            PricingRequest request = new PricingRequest(0, "00010000", chargeType, contractAmount, null, 20260928);

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("1000.000"), result.chargeAmount());
            // Percentage-derived charge amounts are already in contract currency and are
            // NOT run through FX (RULE-012's "else" branch, F115L150.src:557-561); confirmed
            // by no exchange calls having been recorded (max/min are both 0 here too).
            assertTrue(fx.calls().isEmpty());
        }

        @Test
        @DisplayName("rule011_partAmountAndPctOfPaMethod_usesPartAmountInstead")
        void rule011_partAmountAndPctOfPaMethod_usesPartAmountInstead() {
            // The contrasting branch (D100-Compute-Charge-Amount, F115L150.src:441-450,
            // gated by F115L150.src:430-431): when a part-amount is supplied AND the charge
            // type's Method-of-Calc is Pct-Of-PA ('Q'), the PART amount is the base instead
            // of the contract's original amount - which is deliberately set to a very
            // different value here (999,999) to prove it is ignored on this path.
            PricingTier tier = tier(zero(), pct("2.000"), zero(), zero(), zero(), zero(), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);

            ContractAmountRow contractAmount = contractAmount("NOK", amt("999999"), amt("999999"));
            SysChargeTypeRow chargeType = chargeType("Q"); // R115ICT0-MethCalc-Pct-Of-PA
            PartAmountRow partAmount = partAmount("NOK", amt("50000"));
            PricingRequest request = new PricingRequest(7, "00010000", chargeType, contractAmount, partAmount, 20260928);

            PricingResult result = service.computeDefaultCharge(request);

            // 50,000 * 2.0% = 1,000.00
            assertEquals(dec("1000.000"), result.chargeAmount());
        }

        @Test
        @DisplayName("rule011_partAmountButMethodIsNotPctOfPa_stillUsesOriginalContractAmount")
        void rule011_partAmountButMethodIsNotPctOfPa_stillUsesOriginalContractAmount() {
            // F115L150.src:430-431: BOTH conditions (Part-Amount-No > 0 AND
            // MethCalc-Pct-Of-PA) are required to take the part-amount branch; a part
            // amount alone is not enough.
            PricingTier tier = tier(zero(), pct("1.000"), zero(), zero(), zero(), zero(), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);

            ContractAmountRow contractAmount = contractAmount("NOK", amt("30000"), amt("80000"));
            SysChargeTypeRow chargeType = chargeType("P"); // MethCalc-Pct-Of-CA, not Pct-Of-PA
            PartAmountRow partAmount = partAmount("NOK", amt("12345"));
            PricingRequest request = new PricingRequest(3, "00010000", chargeType, contractAmount, partAmount, 20260928);

            PricingResult result = service.computeDefaultCharge(request);

            // 80,000 (Curr-O-Amount) * 1.0% = 800.00, NOT 12,345 * 1.0% = 123.45.
            assertEquals(dec("800.000"), result.chargeAmount());
        }
    }

    // ------------------------------------------------------------------
    // RULE-012 (P1, F115L150.src:539-579): base-currency -> contract-currency conversion.
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("RULE-012: POP pricing amounts are converted from base currency to contract currency")
    class Rule012CurrencyConversion {

        @Test
        @DisplayName("rule012_flatAmountAndCaps_areEachConvertedFromBaseToContractCurrency")
        void rule012_flatAmountAndCaps_areEachConvertedFromBaseToContractCurrency() {
            // F115L150.src:412-419 (flat-amount branch) + F115L150.src:549-579
            // (L100-Convert-Amounts): the flat charge and its Ovre-/Nedre-Prisbelop caps are
            // all expressed in the bank's base currency and each individually converted.
            PricingTier tier = tier(amt("200.00"), zero(), zero(), zero(), amt("500.00"), amt("100.00"), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            // Base currency -> contract currency (EUR) at a 0.1 multiplier, chosen so a
            // caller that forgot to convert would be caught immediately (200 != 20).
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.withRates(dec("10"), dec("0.1"));
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("EUR", amt("0"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("20.000"), result.chargeAmount());
            assertEquals(dec("50.000"), result.maximumCharge());
            assertEquals(dec("10.000"), result.minimumCharge());
            assertEquals("EUR", result.currency());
            // Three independent conversions: charge, max, min (F115L150.src:549,564,572).
            assertEquals(3, fx.calls().size());
            fx.calls().forEach(call -> {
                assertTrue(call.fromBasicCurrency());
                assertEquals("EUR", call.toCurrency());
                assertEquals("0001", call.bankNo());
            });
        }

        @Test
        @DisplayName("rule012_zeroMaxOrMinCharge_isLeftAtZeroWithoutCallingExchange")
        void rule012_zeroMaxOrMinCharge_isLeftAtZeroWithoutCallingExchange() {
            // F115L150.src:564,572 ("If WS-POP-Max-Charge not = +0" / "...Min-Charge..."):
            // a zero cap short-circuits to the Else branch and is never sent through FX.
            PricingTier tier = tier(amt("50.00"), zero(), zero(), zero(), zero(), amt("25.00"), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.withRates(dec("10"), dec("0.1"));
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("EUR", amt("0"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("0.000"), result.maximumCharge());
            assertEquals(dec("2.500"), result.minimumCharge());
            // Only 2 conversions: the charge amount and the (non-zero) min charge.
            assertEquals(2, fx.calls().size());
        }

        @Test
        @DisplayName("rule012_zeroFlatChargeWithZeroPercent_isCopiedThroughWithoutConversion")
        void rule012_zeroFlatChargeWithZeroPercent_isCopiedThroughWithoutConversion() {
            // F115L150.src:554-561 (the "Feilretting"/bug-fix branch): when the POP charge
            // amount is zero AND there is no percentage in play either, the zero is simply
            // copied through - it is not sent to F116L030 (avoiding a spurious FX call for
            // a charge that will never be non-zero).
            PricingTier tier = tier(zero(), zero(), amt("40"), amt("4"), zero(), zero(), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.withRates(dec("10"), dec("0.1"));
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("EUR", amt("0"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("0.000"), result.chargeAmount());
            // Only the (non-zero) max/min go through FX - not the charge amount.
            assertEquals(2, fx.calls().size());
        }
    }

    // ------------------------------------------------------------------
    // RULE-021 (P1, F115L150.src:390-438): flat amount XOR percentage.
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("RULE-021: a POP price row must define a flat amount or a percentage, not both")
    class Rule021FlatAmountXorPercentage {

        @Test
        @DisplayName("rule021_bothAmountAndPercentageNonZero_throwsInvalidConfiguration")
        void rule021_bothAmountAndPercentageNonZero_throwsInvalidConfiguration() {
            // Worked example from analysis/TFR12/BUSINESS_RULES.md RULE-021, re-verified
            // against F115L150.src:401-410: Prisbelop = 100.00 and Prisprosent = 1.500
            // (both non-zero) raises TF-SY-INVALID-CONTENT-TAB-VAR1 via Z700-Error-AE -
            // no charge is computed.
            PricingTier tier = tier(amt("100.00"), pct("1.500"), zero(), zero(), zero(), zero(), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("NOK", amt("0"), amt("0"));

            assertThrows(InvalidProductPricingConfigurationException.class,
                    () -> service.computeDefaultCharge(request));
        }

        @Test
        @DisplayName("rule021_bothZero_leavesChargeAtZeroAndCarriesMaksimumMinimumThrough")
        void rule021_bothZero_leavesChargeAtZeroAndCarriesMaksimumMinimumThrough() {
            // F115L150.src:392-399: both zero is a valid (if degenerate) configuration -
            // only Maksimumpris/Minimumpris are carried through, converted per RULE-012.
            PricingTier tier = tier(zero(), zero(), amt("30"), amt("3"), amt("999"), amt("999"), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("NOK", amt("0"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("0.000"), result.chargeAmount());
            assertEquals(dec("0.000"), result.percentComm());
            // Maksimumpris/Minimumpris (30/3), NOT Ovre-/Nedre-Prisbelop (999/999).
            assertEquals(dec("30.000"), result.maximumCharge());
            assertEquals(dec("3.000"), result.minimumCharge());
        }

        @Test
        @DisplayName("rule021_flatAmountOnly_usesOvreNedrePrisbelopAsCapsNotMaksimumMinimum")
        void rule021_flatAmountOnly_usesOvreNedrePrisbelopAsCapsNotMaksimumMinimum() {
            // F115L150.src:412-419: the flat-amount branch draws its max/min from
            // Ovre-/Nedre-Prisbelop, a DIFFERENT field pair from Maksimumpris/Minimumpris
            // (used by the other two branches) - populated with clashing values here to
            // prove the correct pair is picked.
            PricingTier tier = tier(amt("75.00"), zero(), amt("777"), amt("777"), amt("500.00"), amt("10.00"), zero());
            FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning(tier);
            FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
            PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
            PricingRequest request = requestWithoutPartAmount("NOK", amt("0"), amt("0"));

            PricingResult result = service.computeDefaultCharge(request);

            assertEquals(dec("75.000"), result.chargeAmount());
            assertEquals(dec("500.000"), result.maximumCharge());
            assertEquals(dec("10.000"), result.minimumCharge());
        }
    }

    // ------------------------------------------------------------------
    // Supplementary coverage of C000-Interface's dispatch (F115L150.src:145-164), not tied
    // to one of the four pinned rules but needed so the "zero POP rows" branch isn't left
    // completely untested.
    // ------------------------------------------------------------------
    @Test
    @DisplayName("zeroPriceRows_returnsBlankResultWithoutInvokingCurrencyExchange")
    void zeroPriceRows_returnsBlankResultWithoutInvokingCurrencyExchange() {
        // F115L150.src:145-148: When +0, Set Return-OK to True - J000-Output-Fields never
        // runs, so the result is exactly the Initialize default (F115L150.src:169-170):
        // zero amounts, blank currency.
        FakeProductPriceGateway priceGateway = FakeProductPriceGateway.returning();
        FakeCurrencyExchangeGateway fx = FakeCurrencyExchangeGateway.identity();
        PopPricingService service = new PopPricingServiceImpl(priceGateway, fx);
        PricingRequest request = requestWithoutPartAmount("NOK", amt("0"), amt("0"));

        PricingResult result = service.computeDefaultCharge(request);

        assertEquals(dec("0.000"), result.chargeAmount());
        assertEquals("", result.currency());
        assertTrue(fx.calls().isEmpty());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static PricingRequest requestWithoutPartAmount(String contractCurrency,
                                                             BigDecimal remainingAmount,
                                                             BigDecimal currOriginalAmount) {
        ContractAmountRow contractAmount = contractAmount(contractCurrency, remainingAmount, currOriginalAmount);
        SysChargeTypeRow chargeType = chargeType("S");
        return new PricingRequest(0, "00010000", chargeType, contractAmount, null, 20260928);
    }

    private static ContractAmountRow contractAmount(String currency, BigDecimal remainingAmount, BigDecimal currOriginalAmount) {
        return new ContractAmountRow(
                1L, "00010000", "ILC", 100L, currency,
                currOriginalAmount, remainingAmount, currOriginalAmount, 0, 0);
    }

    private static SysChargeTypeRow chargeType(String methodCalc) {
        return new SysChargeTypeRow("HANDLING", 1L, "00010000", 0, "Handling fee", methodCalc, "W", 0L, BigDecimal.ZERO);
    }

    private static PartAmountRow partAmount(String currency, BigDecimal amount) {
        return new PartAmountRow(1L, "00010000", "ILC", 100L, currency, 1, false,
                IncomSwift.UNSPECIFIED, amount, "OPEN", "TESTUSER", false);
    }

    private static PricingTier tier(BigDecimal prisbelop, BigDecimal prisprosent, BigDecimal maksimumpris,
                                     BigDecimal minimumpris, BigDecimal ovrePrisbelop, BigDecimal nedrePrisbelop,
                                     BigDecimal gyldigTilBelop) {
        return new PricingTier(prisbelop, prisprosent, maksimumpris, minimumpris, ovrePrisbelop, nedrePrisbelop, gyldigTilBelop);
    }

    private static BigDecimal amt(String value) {
        return new BigDecimal(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(String value) {
        return new BigDecimal(value).setScale(3);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO;
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------
    // Test doubles for the two ports genuinely external to this repo slice.
    // ------------------------------------------------------------------

    private static final class FakeProductPriceGateway implements ProductPriceGateway {
        private final List<PricingTier> tiers;
        private final List<ProductPriceQuery> calls = new ArrayList<>();

        private FakeProductPriceGateway(List<PricingTier> tiers) {
            this.tiers = tiers;
        }

        static FakeProductPriceGateway returning(PricingTier... tiers) {
            return new FakeProductPriceGateway(List.of(tiers));
        }

        @Override
        public List<PricingTier> findPriceTiers(ProductPriceQuery query) {
            calls.add(query);
            return tiers;
        }

        List<ProductPriceQuery> calls() {
            return calls;
        }
    }

    private static final class FakeCurrencyExchangeGateway implements CurrencyExchangeGateway {
        private final List<CurrencyExchangeRequest> calls = new ArrayList<>();
        private final BigDecimal toBasicMultiplier;
        private final BigDecimal fromBasicMultiplier;

        private FakeCurrencyExchangeGateway(BigDecimal toBasicMultiplier, BigDecimal fromBasicMultiplier) {
            this.toBasicMultiplier = toBasicMultiplier;
            this.fromBasicMultiplier = fromBasicMultiplier;
        }

        static FakeCurrencyExchangeGateway identity() {
            return new FakeCurrencyExchangeGateway(BigDecimal.ONE, BigDecimal.ONE);
        }

        static FakeCurrencyExchangeGateway withRates(BigDecimal toBasicMultiplier, BigDecimal fromBasicMultiplier) {
            return new FakeCurrencyExchangeGateway(toBasicMultiplier, fromBasicMultiplier);
        }

        @Override
        public BigDecimal exchange(CurrencyExchangeRequest request) {
            calls.add(request);
            BigDecimal multiplier = request.toBasicCurrency() ? toBasicMultiplier : fromBasicMultiplier;
            return request.fromAmount().multiply(multiplier).setScale(3, RoundingMode.HALF_UP);
        }

        List<CurrencyExchangeRequest> calls() {
            return calls;
        }
    }
}
