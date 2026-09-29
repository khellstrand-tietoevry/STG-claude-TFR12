package com.tfi.tfr12.phase3.merge;

import com.tfi.tfr12.phase1.errormessage.ErrorMessageRequest;
import com.tfi.tfr12.phase1.errormessage.ErrorMessageResult;
import com.tfi.tfr12.phase1.errormessage.ErrorMessageService;
import com.tfi.tfr12.phase1.errormessage.MessageKey;
import com.tfi.tfr12.phase1.errormessage.MessageRepository;
import com.tfi.tfr12.phase1.errormessage.MessageRow;
import com.tfi.tfr12.phase1.errormessage.MessageVariable;
import com.tfi.tfr12.phase1.errormessage.VariableSubstitutionResult;
import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.IncomSwift;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins 9 business rules from {@code analysis/TFR12/BUSINESS_RULES.md} for legacy
 * program {@code F115L070} ("Lists charges - merges/presents charge data",
 * {@code legacy/TFR12/src/F115L070.src}), hand-traced against the source per explicit
 * instruction for this phase (dual-execution against GnuCOBOL is out of scope for this
 * phase). The file is ISO-8859-1/Latin-1; column 7 '*' marks a comment, columns 8-72
 * are code. Every test cites the exact source line range it pins.
 *
 * <p>One {@code @Nested} class per rule:
 * <ul>
 *   <li>{@link Rule002MinMaxClamp} - RULE-002 (P0)</li>
 *   <li>{@link Rule005ScopeFollowsPartAmount} - RULE-005 (P1)</li>
 *   <li>{@link Rule008SettlementSummed} - RULE-008 (P1)</li>
 *   <li>{@link Rule009TotalsByCreditRouting} - RULE-009 (P1)</li>
 *   <li>{@link Rule010ReverseFloorsAtZero} - RULE-010 (P1)</li>
 *   <li>{@link Rule014DuplicateChargeDetection} - RULE-014 (P0)</li>
 *   <li>{@link Rule020MainPartConsistency} - RULE-020 (P1)</li>
 *   <li>{@link Rule034ScopeFollowsPartAmountData} - RULE-034 (P1)</li>
 *   <li>{@link Rule035PayingPartyOverride} - RULE-035 (P1)</li>
 * </ul>
 *
 * <p>No credential-like values appear in F115L070.src; nothing here needed
 * substitution. Fin-Inst/contract identifiers and amounts below are all synthetic.
 */
class ChargeMergeServiceTest {

    private static final long FIN_INST_ID = 1001L;
    private static final String CONTRACT_TYPE = "ILC";
    private static final long CONTRACT_NO = 4500001L;

    // =====================================================================
    // RULE-002 (P0, F115L070.src:498-619): percent-of-Part-Amount min/max clamp.
    // =====================================================================
    @Nested
    @DisplayName("RULE-002: charge amount computed from percentage of Part Amount, clamped to min/max")
    class Rule002MinMaxClamp {

        @Test
        @DisplayName("rule002_belowMinimum_raisedToMinimumAndPercentRecomputed")
        void rule002_belowMinimum_raisedToMinimumAndPercentRecomputed() {
            // Rule card's own worked example: 2.000% of 10,000.00 = 200.00, raised to the
            // 250.00 minimum, percent recomputed to 250*100/10000 = 2.500% (F115L070.src:565-585).
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "2.000", "0.000", "250.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness()
                    .withContractAmount(contractAmountRow("USD"))
                    .withPartAmount(dec("10000.000"));

            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3));

            ChargeRow merged = result.charges().get(0);
            assertAmount(dec("250.000"), merged.amount());
            assertAmount(dec("2.500"), merged.percentage());
        }

        @Test
        @DisplayName("rule002_aboveMaximum_cappedAtMaximumAndPercentRecomputed")
        void rule002_aboveMaximum_cappedAtMaximumAndPercentRecomputed() {
            // 10.000% of 10,000.00 = 1000.00, capped at Maximum-Charge = 500.00, percent
            // recomputed to 500*100/10000 = 5.000% (F115L070.src:587-611).
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "10.000", "500.000", "0.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness()
                    .withContractAmount(contractAmountRow("USD"))
                    .withPartAmount(dec("10000.000"));

            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3));

            ChargeRow merged = result.charges().get(0);
            assertAmount(dec("500.000"), merged.amount());
            assertAmount(dec("5.000"), merged.percentage());
        }

        @Test
        @DisplayName("rule002_withinRange_noClampApplied")
        void rule002_withinRange_noClampApplied() {
            // 5.000% of 10,000.00 = 500.00, between min 100 and max 1000 - untouched.
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "5.000", "1000.000", "100.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness()
                    .withContractAmount(contractAmountRow("USD"))
                    .withPartAmount(dec("10000.000"));

            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3));

            ChargeRow merged = result.charges().get(0);
            assertAmount(dec("500.000"), merged.amount());
            assertAmount(dec("5.000"), merged.percentage());
        }

        @Test
        @DisplayName("rule002_zeroComputedAmount_notRaisedToMinimum")
        void rule002_zeroComputedAmount_notRaisedToMinimum() {
            // F115L070.src:566-568: the minimum floor requires WS-Charge-Amount > 0 too -
            // a zero computed charge stays zero even with a positive minimum configured.
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "250.000", 'T');
            // Percentage is zero, so this row never even enters the clamp - use a
            // non-zero percent but zero base instead, via a zero Part-Amount:
            ChargeRow mainNonZeroPct = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "1.000", "0.000", "250.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness()
                    .withContractAmount(contractAmountRow("USD"))
                    .withPartAmount(BigDecimal.ZERO.setScale(3));

            // base = 0 -> chargeAmount falls back to Work's (already-copied-from-Main)
            // amount, which is itself 0.000 here - the floor must NOT fire.
            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(mainNonZeroPct), snapshot(part), context(3));

            assertAmount(dec("0.000"), result.charges().get(0).amount());
        }

        @Test
        @DisplayName("rule002_partChargeAlreadyNonZero_mainIgnoredEntirely_noClamp")
        void rule002_partChargeAlreadyNonZero_mainIgnoredEntirely_noClamp() {
            // F115L070.src:509-519: if Part_Charge already carries a non-zero amount OR
            // percentage, Main_Charge is never even consulted - so no clamp runs, even
            // though Main's own min/max would otherwise force an adjustment.
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "50.000", "0.000", "999999.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "42.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness(); // no lookups configured - must never be called

            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3));

            assertAmount(dec("42.000"), result.charges().get(0).amount());
        }

        @Test
        @DisplayName("rule002_pctOfContractAmount_neverClamped_evenWithPaSeqNoSupplied")
        void rule002_pctOfContractAmount_neverClamped_evenWithPaSeqNoSupplied() {
            // F115L070.src:535-540: the clamp is gated on MethCalc-Pct-Of-PA specifically -
            // a Pct-Of-CA charge falls back to Main's value with NO clamp at all, regardless
            // of min/max configuration.
            ChargeRow main = row("FEE1", 'P', "DRWEE", "0.000", "USD", "50.000", "0.000", "999999.000", 'T');
            ChargeRow part = row("FEE1", 'P', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness(); // no lookups configured - must never be called

            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3));

            assertAmount(dec("0.000"), result.charges().get(0).amount());
            assertAmount(dec("50.000"), result.charges().get(0).percentage());
        }

        @Test
        @DisplayName("rule002_percentRecompute_truncatesRatherThanRounds_QUIRK")
        void rule002_percentRecompute_truncatesRatherThanRounds_QUIRK() {
            // QUIRK: F115L070.src:576-580 has NO "Rounded" keyword, unlike the initial
            // amount-from-percentage Compute at :553-558. 1.000% of 3,007.000 = 30.070,
            // below the 100.000 minimum -> floored to 100.000; percent recomputed as
            // 100*100/3007 = 3.325573... - TRUNCATED to 3.325, not rounded to 3.326.
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "1.000", "0.000", "100.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness()
                    .withContractAmount(contractAmountRow("USD"))
                    .withPartAmount(dec("3007.000"));

            ChargeMergeResult result = harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3));

            assertAmount(dec("100.000"), result.charges().get(0).amount());
            assertAmount(dec("3.325"), result.charges().get(0).percentage());
        }

        @Test
        @DisplayName("rule002_partAmountZeroWithBelowMinimumFallback_divideByZero_UNGUARDED")
        void rule002_partAmountZeroWithBelowMinimumFallback_divideByZero_UNGUARDED() {
            // Latent legacy bug, pinned as-is per "the legacy code is the oracle": neither
            // F115L070.src:576-580 nor :602-606 has an "On Size Error" clause, so if the
            // Part-Amount base is zero AND the (fallback) amount is positive-but-below-
            // minimum, the legacy Compute for the recomputed percentage would itself
            // divide by zero. This Java port reproduces that rather than silently
            // defending against it.
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "50.000", "USD", "5.000", "0.000", "100.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness()
                    .withContractAmount(contractAmountRow("USD"))
                    .withPartAmount(BigDecimal.ZERO.setScale(3));

            assertThrows(ArithmeticException.class, () -> harness.service().mergeMainAndPart(
                    snapshot(main), snapshot(part), context(3)));
        }

        @Test
        @DisplayName("rule002_partAmountRowNotFound_returnsError302")
        void rule002_partAmountRowNotFound_returnsError302() {
            // F115L070.src:802-804 (E400's Return-Error branch): Z700-Error-AE, paragraph E302.
            ChargeRow main = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "5.000", "0.000", "0.000", 'T');
            ChargeRow part = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            TestHarness harness = new TestHarness().withContractAmount(contractAmountRow("USD"));
            // No Part-Amount configured -> lookup returns empty.

            ChargeMergeErrorException ex = assertThrows(ChargeMergeErrorException.class,
                    () -> harness.service().mergeMainAndPart(snapshot(main), snapshot(part), context(3)));
            assertEquals("E302", ex.paragraph());
        }
    }

    // =====================================================================
    // RULE-005 (P1, F115L070.src:121-180): scope follows PA-Seq-No.
    // =====================================================================
    @Nested
    @DisplayName("RULE-005: charge computation scope follows whether a part amount is specified")
    class Rule005ScopeFollowsPartAmount {

        @Test
        @DisplayName("rule005_paSeqNoZero_onlyMainLevelComputed")
        void rule005_paSeqNoZero_onlyMainLevelComputed() {
            // F115L070.src:124-131: PA-Seq-No = 0 -> only C300-Read-Main-Charge runs.
            RecordingChargeListingPort port = new RecordingChargeListingPort();
            port.mainResult = snapshot(row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T'));
            ChargeMergeServiceImpl service = new TestHarness().withPort(port).service();

            ChargeMergeResult result = service.process(addRequest(0, "SHOW"));

            assertEquals(1, port.calls.size());
            assertEquals(0, port.calls.get(0).paSeqNo());
            assertAmount(dec("10.000"), result.charges().get(0).amount());
        }

        @Test
        @DisplayName("rule005_paSeqNoNonZero_bothMainAndPartLevelComputed")
        void rule005_paSeqNoNonZero_bothMainAndPartLevelComputed() {
            // F115L070.src:143-151: PA-Seq-No != 0, not AMEND -> both C300 (Main, PA-Seq-No
            // forced to 0) AND C400 (Part, PA-Seq-No = 3) run.
            RecordingChargeListingPort port = new RecordingChargeListingPort();
            port.mainResult = snapshot(row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T'));
            port.partResult = snapshot(row("FEE1", 'S', "DRWEE", "5.000", "USD", "0.000", "0.000", "0.000", 'T'));
            ChargeMergeServiceImpl service = new TestHarness().withPort(port).service();

            service.process(addRequest(3, "SHOW"));

            assertEquals(2, port.calls.size());
            assertTrue(port.calls.stream().anyMatch(q -> q.paSeqNo() == 0));
            assertTrue(port.calls.stream().anyMatch(q -> q.paSeqNo() == 3));
        }
    }

    // =====================================================================
    // RULE-008 (P1, F115L070.src:621-634): Settlement/Part-Amount summed.
    // =====================================================================
    @Nested
    @DisplayName("RULE-008: settlement/part-amount charges are summed, not recalculated")
    class Rule008SettlementSummed {

        @Test
        @DisplayName("rule008_settlementMethod_mainAndPartAmountsAdded")
        void rule008_settlementMethod_mainAndPartAmountsAdded() {
            // Rule card's own worked example: 30.00 (Main) + 20.00 (Part) = 50.00.
            ChargeRow main = row("COURIER-FEE", 'S', "DRWEE", "30.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("COURIER-FEE", 'S', "DRWEE", "20.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(snapshot(main), snapshot(part), context(0));

            assertAmount(dec("50.000"), result.charges().get(0).amount());
        }

        @Test
        @DisplayName("rule008_partAmountMethod_mainAndPartAmountsAdded")
        void rule008_partAmountMethod_mainAndPartAmountsAdded() {
            ChargeRow main = row("FEE2", 'A', "DRWEE", "12.500", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("FEE2", 'A', "DRWEE", "7.500", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(snapshot(main), snapshot(part), context(0));

            assertAmount(dec("20.000"), result.charges().get(0).amount());
        }

        @Test
        @DisplayName("rule008_methodCodeC_sharedBySettlementAndCanceling_stillSummed_QUIRK")
        void rule008_methodCodeC_sharedBySettlementAndCanceling_stillSummed_QUIRK() {
            // QUIRK (see ChargeMethodOfCalculation javadoc): method code 'C' satisfies BOTH
            // MethCalc-Canceling and MethCalc-Settlement's 88-levels at once. F115L070 never
            // tests Canceling anywhere, so a 'C'-coded charge is processed as Settlement.
            ChargeRow main = row("FEE3", 'C', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("FEE3", 'C', "DRWEE", "5.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(snapshot(main), snapshot(part), context(0));

            assertAmount(dec("15.000"), result.charges().get(0).amount());
        }
    }

    // =====================================================================
    // RULE-009 (P1, F115L070.src:637-734): totals split by credit routing.
    // =====================================================================
    @Nested
    @DisplayName("RULE-009: buyer/seller charge totals split by credit routing")
    class Rule009TotalsByCreditRouting {

        @Test
        @DisplayName("rule009_sellerChargesCreditedToOtherAndOwnBank_summedIntoTotalChargeSeller")
        void rule009_sellerChargesCreditedToOtherAndOwnBank_summedIntoTotalChargeSeller() {
            // Rule card's own worked example: 40.00 (Cred-T-Bank) + 10.00 (Cred-W-Bank) = 50.00.
            ChargeRow mainA = row("FEEA", 'S', "DRWER", "40.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow mainB = row("FEEB", 'S', "DRWER", "10.000", "USD", "0.000", "0.000", "0.000", 'W');
            ChargeRow partA = row("FEEA", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow partB = row("FEEB", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'W');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(mainA, mainB)), snapshot(List.of(partA, partB)), context(0));

            assertAmount(dec("50.000"), result.totals().totalChargeSeller());
            assertAmount(dec("40.000"), result.totals().sellerCreditOtherBank());
            assertAmount(dec("10.000"), result.totals().sellerCreditOwnBank());
        }

        @Test
        @DisplayName("rule009_buyerChargesCreditedToOtherAndOwnBank_summedIntoTotalChargeBuyer")
        void rule009_buyerChargesCreditedToOtherAndOwnBank_summedIntoTotalChargeBuyer() {
            ChargeRow mainA = row("FEEA", 'S', "DRWEE", "15.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow mainB = row("FEEB", 'S', "DRWEE", "5.000", "USD", "0.000", "0.000", "0.000", 'W');
            ChargeRow partA = row("FEEA", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow partB = row("FEEB", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'W');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(mainA, mainB)), snapshot(List.of(partA, partB)), context(0));

            assertAmount(dec("20.000"), result.totals().totalChargeBuyer());
        }

        @Test
        @DisplayName("rule009_agentCommission_summedFromMainAndPartTablesOwnTotals_notFromMergedRows")
        void rule009_agentCommission_summedFromMainAndPartTablesOwnTotals_notFromMergedRows() {
            // F115L070.src:722-727: Work.Tot-Agent-Commission = Main.Tot-Agent-Commission +
            // Part.Tot-Agent-Commission - summed from what F115L060 already computed per
            // table, independent of anything in the merged rows themselves.
            ChargeRow main = row("FEE1", 'S', "DRWEE", "1.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("FEE1", 'S', "DRWEE", "1.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeTotals mainTotals = totalsWithAgentCommission(dec("30.000"));
            ChargeTotals partTotals = totalsWithAgentCommission(dec("12.500"));
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    new ChargeListingSnapshot(List.of(main), mainTotals),
                    new ChargeListingSnapshot(List.of(part), partTotals),
                    context(0));

            assertAmount(dec("42.500"), result.totals().totalAgentCommission());
        }

        @Test
        @DisplayName("rule009_creditToAgentBank_excludedFromEverySellerBuyerTotal_EDGE_CASE")
        void rule009_creditToAgentBank_excludedFromEverySellerBuyerTotal_EDGE_CASE() {
            // Rule card edge case: F115L070.src:641-649, 669-676 (the Credit-Agent-Bank
            // accumulation arms) are commented out - a charge routed to the agent bank
            // contributes to NEITHER totalChargeSeller NOR totalChargeBuyer, and the
            // dedicated *CreditAgentBank accumulator fields stay zero forever.
            ChargeRow main = row("FEE1", 'S', "DRWER", "999.000", "USD", "0.000", "0.000", "0.000", 'A');
            ChargeRow part = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'A');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(snapshot(main), snapshot(part), context(0));

            assertAmount(dec("0.000"), result.totals().totalChargeSeller());
            assertAmount(dec("0.000"), result.totals().sellerCreditAgentBank());
            assertAmount(dec("0.000"), result.totals().buyerCreditAgentBank());
        }
    }

    // =====================================================================
    // RULE-010 (P1, F115L070.src:852-884): reversal floored at zero.
    // =====================================================================
    @Nested
    @DisplayName("RULE-010: subtracting already-stored charges when reversing input, floored at zero")
    class Rule010ReverseFloorsAtZero {

        @Test
        @DisplayName("rule010_settlementMethod_inputMinusStored_negativeFlooredToZero")
        void rule010_settlementMethod_inputMinusStored_negativeFlooredToZero() {
            // Rule card's own worked example: 30.00 - 45.00 = -15.00, floored to 0.00.
            ChargeRow input = row("FEE1", 'S', "DRWEE", "30.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow stored = row("FEE1", 'S', "DRWEE", "45.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            List<ChargeRow> result = service.reverseStoredCharges(List.of(input), List.of(stored));

            assertAmount(dec("0.000"), result.get(0).amount());
        }

        @Test
        @DisplayName("rule010_settlementMethod_positiveDifference_notFloored")
        void rule010_settlementMethod_positiveDifference_notFloored() {
            ChargeRow input = row("FEE1", 'S', "DRWEE", "100.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow stored = row("FEE1", 'S', "DRWEE", "45.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            List<ChargeRow> result = service.reverseStoredCharges(List.of(input), List.of(stored));

            assertAmount(dec("55.000"), result.get(0).amount());
        }

        @Test
        @DisplayName("rule010_pctOfCaOrPaMethod_noSubtraction")
        void rule010_pctOfCaOrPaMethod_noSubtraction() {
            // F115L070.src:874-881: Continue - no change to the input value at all.
            ChargeRow input = row("FEE1", 'P', "DRWEE", "30.000", "USD", "2.000", "0.000", "0.000", 'T');
            ChargeRow stored = row("FEE1", 'P', "DRWEE", "45.000", "USD", "3.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            List<ChargeRow> result = service.reverseStoredCharges(List.of(input), List.of(stored));

            assertAmount(dec("30.000"), result.get(0).amount());
        }

        @Test
        @DisplayName("rule010_noMatchingStoredCharge_inputLeftUnchanged")
        void rule010_noMatchingStoredCharge_inputLeftUnchanged() {
            // F115L070.src:843-848: "If not W-Ind2 > No-Tab-Occ" guard - no match, no F100 call.
            ChargeRow input = row("UNMATCHED", 'S', "DRWEE", "30.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow stored = row("OTHER-FEE", 'S', "DRWEE", "999.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            List<ChargeRow> result = service.reverseStoredCharges(List.of(input), List.of(stored));

            assertAmount(dec("30.000"), result.get(0).amount());
        }

        @Test
        @DisplayName("rule010_blankChargeTypeTerminatesInputList")
        void rule010_blankChargeTypeTerminatesInputList() {
            // F115L070.src:818-822: the Perform loop stops at the first blank Charge-Type -
            // any row after it is never even considered, regardless of content.
            ChargeRow first = row("FEE1", 'S', "DRWEE", "30.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow blank = row("", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow afterBlank = row("FEE1", 'S', "DRWEE", "30.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow stored = row("FEE1", 'S', "DRWEE", "45.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            List<ChargeRow> result = service.reverseStoredCharges(
                    List.of(first, blank, afterBlank), List.of(stored));

            assertAmount(dec("0.000"), result.get(0).amount()); // 30 - 45 floored to 0
            assertAmount(dec("30.000"), result.get(2).amount()); // never reached, unchanged
        }
    }

    // =====================================================================
    // RULE-014 (P0, F115L070.src:361-421): duplicate charge detection.
    // =====================================================================
    @Nested
    @DisplayName("RULE-014: duplicate/conflicting charge detection between Main and Part charge records")
    class Rule014DuplicateChargeDetection {

        @Test
        @DisplayName("rule014_sameChargeNonZeroOnBothAdjacentPartRows_abendsD201")
        void rule014_sameChargeNonZeroOnBothAdjacentPartRows_abendsD201() {
            // F115L070.src:387-396 (D201): both adjacent Work(=Part) rows non-zero for the
            // same charge type -> "registered twice on Part_Charge".
            ChargeRow main1 = row("FEE1", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE1", 'S', "DRWER", "20.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeAbendException ex = assertThrows(ChargeMergeAbendException.class,
                    () -> service.mergeMainAndPart(
                            snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0)));

            assertEquals("D201", ex.paragraph());
            assertEquals("TF-SY-DUPLICATE-CHARGE", ex.legacyMessageCode());
            assertEquals("FEE1", ex.variableData());
        }

        @Test
        @DisplayName("rule014_sameChargeNonZeroOnBothAdjacentMainRows_abendsD202")
        void rule014_sameChargeNonZeroOnBothAdjacentMainRows_abendsD202() {
            // F115L070.src:398-408 (D202): "registered twice on Main_Charge".
            ChargeRow main1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE1", 'S', "DRWER", "20.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeAbendException ex = assertThrows(ChargeMergeAbendException.class,
                    () -> service.mergeMainAndPart(
                            snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0)));

            assertEquals("D202", ex.paragraph());
            assertEquals("FEE1", ex.variableData());
        }

        @Test
        @DisplayName("rule014_differentChargeTypesAdjacent_noDuplicateCheckApplies")
        void rule014_differentChargeTypesAdjacent_noDuplicateCheckApplies() {
            // F115L070.src:368-370: D200 only compares adjacent rows sharing the same
            // Charge-Type - different types side by side never trigger D201/D202.
            ChargeRow main1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE2", 'S', "DRWER", "20.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE2", 'S', "DRWER", "20.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0));

            assertEquals(2, result.charges().size());
        }
    }

    // =====================================================================
    // RULE-020 (P1, F115L070.src:332-358): main/part consistency.
    // =====================================================================
    @Nested
    @DisplayName("RULE-020: consistency between main and part charge records")
    class Rule020MainPartConsistency {

        @Test
        @DisplayName("rule020_chargeTypeMismatchAtSamePosition_abendsD101")
        void rule020_chargeTypeMismatchAtSamePosition_abendsD101() {
            // Rule card's own example: row 3 Main = HANDLING/BUYER, row 3 Part =
            // HANDLING/SELLER - wait, that's a role mismatch; using distinct charge types
            // at the same position pins the Charge-Type half of the check.
            ChargeRow main = row("HANDLING", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("OTHERFEE", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeAbendException ex = assertThrows(ChargeMergeAbendException.class,
                    () -> service.mergeMainAndPart(snapshot(main), snapshot(part), context(0)));

            assertEquals("D101", ex.paragraph());
            assertEquals("TF-SY-INCONSISTENCY-VAR1", ex.legacyMessageCode());
            // F115L070.src:353: the substituted variable is the hard-coded module name
            // 'F115L070' itself, NOT the mismatching charge type - a genuine diagnostics quirk.
            assertEquals("F115L070", ex.variableData());
        }

        @Test
        @DisplayName("rule020_roleTypeMismatchAtSamePosition_abendsD101")
        void rule020_roleTypeMismatchAtSamePosition_abendsD101() {
            ChargeRow main = row("HANDLING", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("HANDLING", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            assertThrows(ChargeMergeAbendException.class,
                    () -> service.mergeMainAndPart(snapshot(main), snapshot(part), context(0)));
        }

        @Test
        @DisplayName("rule020_matchingChargeTypeAndRole_noAbend")
        void rule020_matchingChargeTypeAndRole_noAbend() {
            ChargeRow main = row("HANDLING", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part = row("HANDLING", 'S', "DRWEE", "5.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(snapshot(main), snapshot(part), context(0));

            assertAmount(dec("15.000"), result.charges().get(0).amount());
        }
    }

    // =====================================================================
    // RULE-034 (P1, F115L070.src:234-249): scope depends on part-amount, at
    // the C200-Qualify-Read level (distinct from RULE-005's C000 dispatch).
    // =====================================================================
    @Nested
    @DisplayName("RULE-034: charge data scope depends on whether a part-amount is specified")
    class Rule034ScopeFollowsPartAmountData {

        @Test
        @DisplayName("rule034_paSeqNoSupplied_mainQualificationForcesPaSeqNoToZero")
        void rule034_paSeqNoSupplied_mainQualificationForcesPaSeqNoToZero() {
            // F115L070.src:239-244: both Main and Part working copies derive from the same
            // qualified input, differing only in PA-Seq-No - Main's is forced to 0.
            RecordingChargeListingPort port = new RecordingChargeListingPort();
            port.mainResult = snapshot(row("FEE1", 'S', "DRWEE", "1.000", "USD", "0.000", "0.000", "0.000", 'T'));
            port.partResult = snapshot(row("FEE1", 'S', "DRWEE", "1.000", "USD", "0.000", "0.000", "0.000", 'T'));
            ChargeMergeServiceImpl service = new TestHarness().withPort(port).service();

            service.process(addRequest(7, "SHOW"));

            ChargeListingQuery mainQuery = port.calls.stream().filter(q -> q.paSeqNo() == 0).findFirst().orElseThrow();
            ChargeListingQuery partQuery = port.calls.stream().filter(q -> q.paSeqNo() == 7).findFirst().orElseThrow();
            assertEquals(0, mainQuery.paSeqNo());
            assertEquals(7, partQuery.paSeqNo());
            // F115L070.src:143-146: the "which rate are we on" fix threads PA-Seq-No
            // into Main's In-Check-Pa specifically for this (not-AMEND) branch.
            assertEquals(7, mainQuery.checkPaSeqNo());
        }

        @Test
        @DisplayName("rule034_amendOperation_readsPartOnly_totalsZeroed_noMergeLogicRuns")
        void rule034_amendOperation_readsPartOnly_totalsZeroed_noMergeLogicRuns() {
            // F115L070.src:137-141: AMEND- -> C400-Read-Part-Charge only; D000's Part-Only
            // branch explicitly zeroes totals (F115L070.src:303-308); E000 never runs.
            RecordingChargeListingPort port = new RecordingChargeListingPort();
            port.partResult = new ChargeListingSnapshot(
                    List.of(row("FEE1", 'S', "DRWEE", "5.000", "USD", "0.000", "0.000", "0.000", 'T')),
                    totalsWithAgentCommission(dec("999.000"))); // F115L060's own total - must be discarded
            ChargeMergeServiceImpl service = new TestHarness().withPort(port).service();

            ChargeMergeResult result = service.process(addRequest(7, "AMEND-CANCEL"));

            assertEquals(1, port.calls.size());
            assertEquals(7, port.calls.get(0).paSeqNo());
            assertAmount(dec("5.000"), result.charges().get(0).amount());
            assertAmount(BigDecimal.ZERO.setScale(3), result.totals().totalAgentCommission());
        }
    }

    // =====================================================================
    // RULE-035 (P1, F115L070.src:422-476): paying-party override.
    // =====================================================================
    @Nested
    @DisplayName("RULE-035: part-charge paying party overrides main-charge paying party")
    class Rule035PayingPartyOverride {

        @Test
        @DisplayName("rule035_settlementMethod_amountRelocatedFromMainOtherRowToPreferredRow")
        void rule035_settlementMethod_amountRelocatedFromMainOtherRowToPreferredRow() {
            // D230 (F115L070.src:436-446): Part says row1 (DRWEE) pays (Work1 != 0), Main
            // says row2 (DRWER) pays (Main2 != 0) - Part wins: Main's amount is relocated
            // from row2 to row1 before E100 adds Part(1) + Main(1).
            ChargeRow main1 = row("FEE1", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE1", 'S', "DRWER", "40.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0));

            assertAmount(dec("50.000"), result.charges().get(0).amount()); // 10 (part) + 40 (relocated main)
            assertAmount(dec("0.000"), result.charges().get(1).amount());  // Main's old row emptied
        }

        @Test
        @DisplayName("rule035_pctMethod_otherMainRowZeroedNotRelocated")
        void rule035_pctMethod_otherMainRowZeroedNotRelocated() {
            // D230, Pct-Of-CA/PA arm (F115L070.src:425-434): the OTHER Main row is simply
            // zeroed (amount AND percentage) - nothing is relocated, since E100 will
            // recompute row1 from Work1's own (already non-zero) values directly.
            ChargeRow main1 = row("FEE1", 'Q', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE1", 'Q', "DRWER", "0.000", "USD", "9.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'Q', "DRWEE", "10.000", "USD", "2.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE1", 'Q', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0));

            // Work1 already non-zero (10.000/2.000) -> E100's fallback-to-Main branch never
            // fires for row1, Part's own value is kept as-is.
            assertAmount(dec("10.000"), result.charges().get(0).amount());
            assertAmount(dec("2.000"), result.charges().get(0).percentage());
        }

        @Test
        @DisplayName("rule035_symmetricCase_d260_amountRelocatedFromMainToRow2")
        void rule035_symmetricCase_d260_amountRelocatedFromMainToRow2() {
            // D260 (F115L070.src:451-476): the mirror image - Part says row2 pays, Main
            // says row1 pays.
            ChargeRow main1 = row("FEE1", 'S', "DRWEE", "40.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'S', "DRWEE", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE1", 'S', "DRWER", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0));

            assertAmount(dec("0.000"), result.charges().get(0).amount());
            assertAmount(dec("50.000"), result.charges().get(1).amount()); // 10 (part) + 40 (relocated main)
        }

        @Test
        @DisplayName("rule035_neitherPatternMatches_noOverrideApplied")
        void rule035_neitherPatternMatches_noOverrideApplied() {
            // Both Part rows agree with Main on which position pays (or neither table has
            // anything) - none of D200's 4 explicit "When" arms match, so nothing happens;
            // E100 simply keeps each row's own already-consistent value.
            ChargeRow main1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow main2 = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part1 = row("FEE1", 'S', "DRWEE", "10.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeRow part2 = row("FEE1", 'S', "DRWER", "0.000", "USD", "0.000", "0.000", "0.000", 'T');
            ChargeMergeServiceImpl service = new TestHarness().service();

            ChargeMergeResult result = service.mergeMainAndPart(
                    snapshot(List.of(main1, main2)), snapshot(List.of(part1, part2)), context(0));

            assertAmount(dec("20.000"), result.charges().get(0).amount()); // Settlement: 10 + 10
            assertAmount(dec("0.000"), result.charges().get(1).amount());
        }
    }

    // =====================================================================
    // Fixture helpers
    // =====================================================================

    private static BigDecimal dec(String value) {
        return new BigDecimal(value).setScale(3);
    }

    private static void assertAmount(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private static ChargeRow row(String chargeType, char methodCode, String roleType, String amount,
                                  String currency, String percent, String maxCharge, String minCharge, char credit) {
        return new ChargeRow(chargeType, new ChargeMethodOfCalculation(methodCode), roleType,
                dec(amount), currency, dec(percent), dec(maxCharge), dec(minCharge),
                ChargeCreditRoute.fromCode(credit), "", "", 0L, 0L, "");
    }

    private static ChargeListingSnapshot snapshot(ChargeRow row) {
        return new ChargeListingSnapshot(List.of(row), ChargeTotals.ZERO);
    }

    private static ChargeListingSnapshot snapshot(List<ChargeRow> rows) {
        return new ChargeListingSnapshot(rows, ChargeTotals.ZERO);
    }

    private static ChargeTotals totalsWithAgentCommission(BigDecimal agentCommission) {
        return new ChargeTotals(
                BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3),
                BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3),
                agentCommission);
    }

    private static ChargeMergeContext context(int paSeqNo) {
        return ChargeMergeContext.of(FIN_INST_ID, CONTRACT_TYPE, CONTRACT_NO, paSeqNo);
    }

    private static ChargeMergeRequest addRequest(int paSeqNo, String operationType) {
        return new ChargeMergeRequest("ADD", ChargeTableVersion.ACTUAL, FIN_INST_ID, CONTRACT_TYPE, CONTRACT_NO,
                paSeqNo, operationType, List.of(), ChargeTotals.ZERO, "EN", false);
    }

    private static ContractAmountRow contractAmountRow(String currency) {
        return new ContractAmountRow(FIN_INST_ID, "", CONTRACT_TYPE, CONTRACT_NO, currency,
                BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3), 0, 0);
    }

    private static PartAmountRow partAmountRow(BigDecimal amount) {
        return new PartAmountRow(FIN_INST_ID, "", CONTRACT_TYPE, CONTRACT_NO, "USD", 3,
                false, IncomSwift.UNSPECIFIED, amount, "OPEN", "TESTUSER", false);
    }

    // =====================================================================
    // Test doubles
    // =====================================================================

    /** Assembles a {@link ChargeMergeServiceImpl} with configurable fakes for each dependency. */
    private static final class TestHarness {
        private ChargeListingPort port = query -> {
            throw new AssertionError("ChargeListingPort should not be called by this test");
        };
        private final Map<String, ContractAmountRow> contractAmounts = new HashMap<>();
        private final Map<String, PartAmountRow> partAmounts = new HashMap<>();

        TestHarness withPort(ChargeListingPort port) {
            this.port = port;
            return this;
        }

        TestHarness withContractAmount(ContractAmountRow row) {
            contractAmounts.put(row.finInstId() + "/" + row.contractType() + "/" + row.contractNo(), row);
            return this;
        }

        TestHarness withPartAmount(BigDecimal amount) {
            partAmounts.put(FIN_INST_ID + "/" + CONTRACT_TYPE + "/" + CONTRACT_NO + "/USD/3", partAmountRow(amount));
            return this;
        }

        ChargeMergeServiceImpl service() {
            ContractAmountLookup contractAmountLookup = (finInstId, contractType, contractNo) ->
                    Optional.ofNullable(contractAmounts.get(finInstId + "/" + contractType + "/" + contractNo));
            PartAmountLookup partAmountLookup = (finInstId, contractType, contractNo, currency, paSeqNo) ->
                    Optional.ofNullable(partAmounts.get(finInstId + "/" + contractType + "/" + contractNo + "/" + currency + "/" + paSeqNo));
            return new ChargeMergeServiceImpl(port, contractAmountLookup, partAmountLookup,
                    new FakeErrorMessageService(), new FakeMessageRepository());
        }
    }

    /** Records every query it receives instead of throwing. */
    private static final class RecordingChargeListingPort implements ChargeListingPort {
        final List<ChargeListingQuery> calls = new java.util.ArrayList<>();
        ChargeListingSnapshot mainResult = snapshot(List.of());
        ChargeListingSnapshot partResult = snapshot(List.of());

        @Override
        public ChargeListingSnapshot readCharges(ChargeListingQuery query) {
            calls.add(query);
            return query.paSeqNo() == 0 ? mainResult : partResult;
        }
    }

    /** Trivial ErrorMessageService stand-in - always resolves to a fixed, non-credential-shaped string. */
    private static final class FakeErrorMessageService implements ErrorMessageService {
        @Override
        public ErrorMessageResult lookupMessage(ErrorMessageRequest request, MessageRepository repository, boolean cicsEnvironment) {
            return new ErrorMessageResult(request.messageCode(), "resolved: " + request.messageCode());
        }

        @Override
        public VariableSubstitutionResult substituteVariables(String template, List<MessageVariable> variables) {
            throw new UnsupportedOperationException("not needed by ChargeMergeServiceImpl");
        }
    }

    /** Empty MessageRepository stand-in - ChargeMergeServiceImpl never calls it directly (ErrorMessageService does). */
    private static final class FakeMessageRepository implements MessageRepository {
        @Override
        public Optional<MessageRow> find(MessageKey key) {
            return Optional.empty();
        }
    }
}
