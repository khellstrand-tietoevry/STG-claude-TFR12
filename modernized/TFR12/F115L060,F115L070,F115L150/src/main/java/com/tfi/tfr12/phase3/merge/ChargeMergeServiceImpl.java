package com.tfi.tfr12.phase3.merge;

import com.tfi.tfr12.phase1.errormessage.ErrorMessageRequest;
import com.tfi.tfr12.phase1.errormessage.ErrorMessageService;
import com.tfi.tfr12.phase1.errormessage.MessageRepository;
import com.tfi.tfr12.phase1.errormessage.MessageVariable;
import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Java translation of legacy program F115L070.src ("Lists charges").
 *
 * <p>Byte-safe read of legacy/TFR12/src/F115L070.src (ISO-8859-1/Latin-1;
 * column 7 '*' = comment, columns 8-72 = code). Per explicit instruction for
 * this phase, this is a CHARACTERIZATION TRANSLATION - hand-traced against the
 * source, NOT validated against a live GnuCOBOL run.
 *
 * <p>Paragraph mapping:
 * <ul>
 *   <li>{@code C000-Interface} (F115L070.src:121-180) -&gt; {@link #process} - RULE-005.</li>
 *   <li>{@code C200-Qualify-Read} (F115L070.src:195-250) -&gt; {@link #process}'s
 *       {@link ChargeListingQuery} construction - RULE-034.</li>
 *   <li>{@code D000/D100/D200/D230/D260-*} (F115L070.src:294-477) -&gt;
 *       {@link #mergeMainAndPart} consistency/paying-party phase - RULE-014,
 *       RULE-020, RULE-035.</li>
 *   <li>{@code E000/E100/E200-*} (F115L070.src:479-735) -&gt;
 *       {@link #mergeMainAndPart} merge/accumulate phase - RULE-002, RULE-008,
 *       RULE-009.</li>
 *   <li>{@code F000/F100-*} (F115L070.src:813-884) -&gt;
 *       {@link #reverseStoredCharges} - RULE-010.</li>
 * </ul>
 */
public class ChargeMergeServiceImpl implements ChargeMergeService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int AMOUNT_SCALE = 3;
    private static final int PERCENT_SCALE = 3;

    /** F115L070.src:137-138, 161-162: "AMEND-" prefix (6 chars) selects the Part_Charge-only path. */
    private static final String AMEND_PREFIX = "AMEND-";

    private final ChargeListingPort chargeListingPort;
    private final ContractAmountLookup contractAmountLookup;
    private final PartAmountLookup partAmountLookup;
    private final ErrorMessageService errorMessageService;
    private final MessageRepository messageRepository;

    public ChargeMergeServiceImpl(ChargeListingPort chargeListingPort,
                                   ContractAmountLookup contractAmountLookup,
                                   PartAmountLookup partAmountLookup,
                                   ErrorMessageService errorMessageService,
                                   MessageRepository messageRepository) {
        this.chargeListingPort = chargeListingPort;
        this.contractAmountLookup = contractAmountLookup;
        this.partAmountLookup = partAmountLookup;
        this.errorMessageService = errorMessageService;
        this.messageRepository = messageRepository;
    }

    // =====================================================================
    // C000-Interface (F115L070.src:121-180) - RULE-005 / RULE-034
    // =====================================================================

    @Override
    public ChargeMergeResult process(ChargeMergeRequest request) {
        ChargeMergeContext context = request.toContext();
        boolean isAdd = "ADD".equals(request.action());
        boolean isSubtract = "SUBTRACT".equals(request.action());

        if (isAdd && request.paSeqNo() == 0) {
            // F115L070.src:124-131: main-contract-level only.
            ChargeListingSnapshot main = readMain(request, 0);
            return new ChargeMergeResult(main.charges(), main.totals());
        }
        if (isAdd) {
            if (isAmend(request.operationType())) {
                // F115L070.src:137-141: AMEND- -> Part_Charge only, D000's
                // Part-Only branch zeroes totals (F115L070.src:301-308); E000
                // never runs, so no merge/consistency logic applies at all.
                ChargeListingSnapshot part = readPart(request);
                return new ChargeMergeResult(part.charges(), ChargeTotals.ZERO);
            }
            // F115L070.src:143-151: full merge branch (SW-Read-Main-Part).
            ChargeListingSnapshot main = readMain(request, request.paSeqNo());
            ChargeListingSnapshot part = readPart(request);
            return mergeMainAndPart(main, part, context);
        }
        if (isSubtract && request.paSeqNo() == 0) {
            // F115L070.src:155-157: literal "Continue" - nothing happens at all.
            return new ChargeMergeResult(request.callerSuppliedCharges(), request.callerSuppliedTotals());
        }
        if (isSubtract) {
            if (isAmend(request.operationType())) {
                // F115L070.src:161-170 (If not AMEND-): AMEND- means the whole
                // inner If is skipped - also a complete no-op.
                return new ChargeMergeResult(request.callerSuppliedCharges(), request.callerSuppliedTotals());
            }
            ChargeListingSnapshot main = readMain(request, 0);
            List<ChargeRow> reversed = reverseStoredCharges(request.callerSuppliedCharges(), main.charges());
            return new ChargeMergeResult(reversed, request.callerSuppliedTotals());
        }

        // F115L070.src:172-178: When other - invalid R115L070-In-Action value.
        throw raiseAbend(context, "C001", "GL-IN-AKSJON-UV", request.action());
    }

    private static boolean isAmend(String operationType) {
        return operationType != null && operationType.length() >= AMEND_PREFIX.length()
                && operationType.substring(0, AMEND_PREFIX.length()).equals(AMEND_PREFIX);
    }

    private ChargeListingSnapshot readMain(ChargeMergeRequest request, int checkPaSeqNo) {
        return chargeListingPort.readCharges(new ChargeListingQuery(
                request.tableVersion(), request.finInstId(), request.contractType(), request.contractNo(),
                0, request.operationType(), checkPaSeqNo));
    }

    private ChargeListingSnapshot readPart(ChargeMergeRequest request) {
        return chargeListingPort.readCharges(new ChargeListingQuery(
                request.tableVersion(), request.finInstId(), request.contractType(), request.contractNo(),
                request.paSeqNo(), request.operationType(), 0));
    }

    // =====================================================================
    // D000/E000-* (F115L070.src:294-735) - RULE-002/008/009/014/020/035
    // =====================================================================

    @Override
    public ChargeMergeResult mergeMainAndPart(ChargeListingSnapshot mainSnapshot, ChargeListingSnapshot partSnapshot,
                                               ChargeMergeContext context) {
        List<ChargeRow> mainCharges = mainSnapshot.charges();
        List<ChargeRow> partCharges = partSnapshot.charges();

        // D000-Pre-Process-Charges (F115L070.src:301-308): Work starts as a
        // mutable copy of Part_Charge; Main_Charge is also copied mutably since
        // D230/D260 (RULE-035) may rewrite entries on it.
        int rowCount = partCharges.size(); // R115L060-No-Tab-Occ in Work == Part's, at this point
        List<ChargeRow> work = new ArrayList<>(partCharges);
        List<ChargeRow> main = new ArrayList<>(mainCharges);

        // F115L070.src:316-328: consistency + paying-party checks, 1-based in the source.
        for (int ind1 = 1; ind1 <= rowCount; ind1++) {
            checkConsistency(rowOrBlank(main, ind1), rowOrBlank(partCharges, ind1), context);
            if (ind1 > 1) {
                checkPayingParty(work, main, ind1, context);
            }
        }

        // E000-Process-Charges (F115L070.src:479-490).
        List<ChargeRow> merged = new ArrayList<>(rowCount);
        BigDecimal sellerOther = ChargeRow.ZERO_AMOUNT;
        BigDecimal sellerOwn = ChargeRow.ZERO_AMOUNT;
        BigDecimal buyerOther = ChargeRow.ZERO_AMOUNT;
        BigDecimal buyerOwn = ChargeRow.ZERO_AMOUNT;
        for (int ind1 = 1; ind1 <= rowCount; ind1++) {
            ChargeRow mergedRow = mergeRow(rowOrBlank(work, ind1), rowOrBlank(main, ind1), context);
            merged.add(mergedRow);

            // E200-Accumulate-Totals (F115L070.src:641-694).
            if (mergedRow.isSeller() && mergedRow.credit() == ChargeCreditRoute.OTHER_BANK) {
                sellerOther = sellerOther.add(mergedRow.amount());
            } else if (mergedRow.isSeller() && mergedRow.credit() == ChargeCreditRoute.OWN_BANK) {
                sellerOwn = sellerOwn.add(mergedRow.amount());
            } else if (mergedRow.isBuyer() && mergedRow.credit() == ChargeCreditRoute.OTHER_BANK) {
                buyerOther = buyerOther.add(mergedRow.amount());
            } else if (mergedRow.isBuyer() && mergedRow.credit() == ChargeCreditRoute.OWN_BANK) {
                buyerOwn = buyerOwn.add(mergedRow.amount());
            }
            // Credit-Agent-Bank: no arm at all (F115L070.src:641-649, 669-676 commented out).
        }

        // F115L070.src:700-709, 711-720: grand totals = other-bank + own-bank only.
        BigDecimal totalSeller = sellerOther.add(sellerOwn);
        BigDecimal totalBuyer = buyerOther.add(buyerOwn);
        // F115L070.src:722-727: summed from what F115L060 already computed per table.
        BigDecimal totalAgentCommission = mainSnapshot.totals().totalAgentCommission()
                .add(partSnapshot.totals().totalAgentCommission());

        ChargeTotals totals = new ChargeTotals(
                ChargeRow.ZERO_AMOUNT, sellerOther, sellerOwn, totalSeller,
                ChargeRow.ZERO_AMOUNT, buyerOther, buyerOwn, totalBuyer,
                totalAgentCommission);
        return new ChargeMergeResult(merged, totals);
    }

    /** D100-Check-Consistency (F115L070.src:332-359) - RULE-020. */
    private void checkConsistency(ChargeRow mainRow, ChargeRow partRow, ChargeMergeContext context) {
        boolean consistent = mainRow.chargeType().equals(partRow.chargeType())
                && mainRow.roleType().equals(partRow.roleType());
        if (!consistent) {
            // F115L070.src:350-353: the variable substituted is the hard-coded
            // literal 'F115L070' (the module's OWN name), not the mismatching
            // charge type/role - a genuine legacy diagnostics quirk, pinned as-is.
            throw raiseAbend(context, "D101", "TF-SY-INCONSISTENCY-VAR1", "F115L070");
        }
    }

    /** D200-Check-Paying-Party (F115L070.src:361-421) - RULE-014 / RULE-035. */
    private void checkPayingParty(List<ChargeRow> work, List<ChargeRow> main, int ind1, ChargeMergeContext context) {
        int ind2 = ind1 - 1;
        ChargeRow work1 = rowOrBlank(work, ind1);
        ChargeRow work2 = rowOrBlank(work, ind2);
        if (!work1.chargeType().equals(work2.chargeType())) {
            return; // F115L070.src:368-370: only applies to same-charge-type adjacent rows.
        }
        ChargeRow main1 = rowOrBlank(main, ind1);
        ChargeRow main2 = rowOrBlank(main, ind2);
        boolean w1 = isNonZero(work1.amount());
        boolean w2 = isNonZero(work2.amount());
        boolean m1 = isNonZero(main1.amount());
        boolean m2 = isNonZero(main2.amount());

        if (w1 && w2) {
            // F115L070.src:387-396 (D201): same charge registered twice on Part_Charge.
            throw raiseAbend(context, "D201", "TF-SY-DUPLICATE-CHARGE", work1.chargeType());
        } else if (m1 && m2) {
            // F115L070.src:398-408 (D202): same charge registered twice on Main_Charge.
            throw raiseAbend(context, "D202", "TF-SY-DUPLICATE-CHARGE", main1.chargeType());
        } else if (w1 && !w2 && !m1 && m2) {
            // F115L070.src:410-413 (D230): Part_Charge takes precedence at ind1.
            applyD230(work, main, ind1, ind2);
        } else if (!w1 && w2 && m1 && !m2) {
            // F115L070.src:415-418 (D260): Part_Charge takes precedence at ind2.
            applyD260(work, main, ind1, ind2);
        }
        // Else: no explicit "When" arm matches - Evaluate falls through, no action
        // (e.g. only one side ever had a value: E100 will naturally prefer it).
    }

    /** D230-Modify-Entries (F115L070.src:423-449), always keyed off Work(ind1). */
    private void applyD230(List<ChargeRow> work, List<ChargeRow> main, int ind1, int ind2) {
        ChargeRow work1 = rowOrBlank(work, ind1);
        ChargeRow main1 = rowOrBlank(main, ind1);
        ChargeRow main2 = rowOrBlank(main, ind2);
        ChargeMethodOfCalculation mc = work1.methodOfCalculation();
        if (mc.isPctOfCA() || mc.isPctOfPA()) {
            setRow(main, ind2, main2.withAmountAndPercentage(ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_PERCENT));
        } else if (mc.isSettlement() || mc.isPartAmount()) {
            setRow(main, ind1, main1.withAmount(main2.amount()));
            setRow(main, ind2, main2.withAmount(ChargeRow.ZERO_AMOUNT));
        }
        // Else (an unexpected method code, e.g. NONE): no "When" arm matches - no action.
    }

    /** D260-Modify-Entries (F115L070.src:451-477), always keyed off Work(ind2) - mirrors D230. */
    private void applyD260(List<ChargeRow> work, List<ChargeRow> main, int ind1, int ind2) {
        ChargeRow work2 = rowOrBlank(work, ind2);
        ChargeRow main1 = rowOrBlank(main, ind1);
        ChargeRow main2 = rowOrBlank(main, ind2);
        ChargeMethodOfCalculation mc = work2.methodOfCalculation();
        if (mc.isPctOfCA() || mc.isPctOfPA()) {
            setRow(main, ind1, main1.withAmountAndPercentage(ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_PERCENT));
        } else if (mc.isSettlement() || mc.isPartAmount()) {
            setRow(main, ind2, main2.withAmount(main1.amount()));
            setRow(main, ind1, main1.withAmount(ChargeRow.ZERO_AMOUNT));
        }
    }

    /** E100-Merge-Charges (F115L070.src:492-634) - RULE-002 / RULE-008. */
    private ChargeRow mergeRow(ChargeRow work, ChargeRow main, ChargeMergeContext context) {
        ChargeMethodOfCalculation mc = work.methodOfCalculation();
        if (mc.isPctOfCA() || mc.isPctOfPA()) {
            // F115L070.src:509-516 ("NEW!" fix): only when BOTH the Part-side
            // amount AND percentage are zero does Main_Charge's value apply.
            if (isZero(work.amount()) && isZero(work.percentage())) {
                ChargeRow updated = work.withAmountAndPercentage(main.amount(), main.percentage());
                // F115L070.src:535-540: the min/max clamp applies ONLY for
                // Pct-Of-PA specifically (checked on MAIN's method, not Work's -
                // they are expected to agree, RULE-020), with a positive
                // percentage and a supplied PA-Seq-No.
                if (main.methodOfCalculation().isPctOfPA()
                        && updated.percentage().compareTo(BigDecimal.ZERO) > 0
                        && context.paSeqNo() > 0) {
                    updated = applyMinMaxClamp(updated, main, context);
                }
                return updated;
            }
            return work;
        }
        if (mc.isSettlement() || mc.isPartAmount()) {
            // F115L070.src:629-632 - RULE-008: simple sum, never recalculated.
            return work.withAmount(work.amount().add(main.amount()));
        }
        return work;
    }

    /**
     * The "Feilretting" min/max clamp block (F115L070.src:534-616) - RULE-002.
     *
     * <p><b>QUIRK pinned deliberately:</b> the initial amount-from-percentage
     * Compute is ROUNDED (F115L070.src:553-558), but BOTH percent-from-amount
     * recomputations after a min or max clamp (F115L070.src:576-580, 602-606)
     * have NO "Rounded" keyword - COBOL truncates (rounds toward zero) there
     * instead. This asymmetry is preserved, not "fixed", below.
     */
    private ChargeRow applyMinMaxClamp(ChargeRow work, ChargeRow main, ChargeMergeContext context) {
        ContractAmountRow contractAmount = contractAmountLookup
                .findFirstByContract(context.finInstId(), context.contractType(), context.contractNo())
                .orElseThrow(() -> new ChargeMergeErrorException("E302"));
        PartAmountRow partAmount = partAmountLookup
                .findByKey(context.finInstId(), context.contractType(), context.contractNo(),
                        contractAmount.currency(), context.paSeqNo())
                .orElseThrow(() -> new ChargeMergeErrorException("E302"));

        // F115L070.src:544-546: always the PART amount on this path - the "CA"
        // half of WS-CA-Or-PA-Amount's name is never used here (see
        // PartAmountLookup's javadoc).
        BigDecimal base = partAmount.amount();
        BigDecimal percent = work.percentage();
        BigDecimal chargeAmount;
        if (base.compareTo(BigDecimal.ZERO) != 0) {
            // F115L070.src:553-558: Compute ... Rounded.
            chargeAmount = base.multiply(percent).divide(ONE_HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP);
        } else {
            // F115L070.src:559-562 (Else): keep whatever amount was already there.
            chargeAmount = work.amount();
        }

        // F115L070.src:565-585: under minimum? (only when the computed amount is
        // strictly positive - a zero computed amount is NOT floored up).
        if (chargeAmount.compareTo(main.minimumCharge()) < 0 && chargeAmount.compareTo(BigDecimal.ZERO) > 0) {
            chargeAmount = main.minimumCharge();
            // F115L070.src:576-580: NOT Rounded - truncates. Can divide by zero
            // (base = 0 falling through the Else above with a positive fallback
            // amount below the minimum) exactly as the legacy Compute would -
            // deliberately not defended against, see class/test javadoc.
            percent = chargeAmount.multiply(ONE_HUNDRED).divide(base, PERCENT_SCALE, RoundingMode.DOWN);
        }
        // F115L070.src:587-611: over maximum? (Maximum-Charge = 0 means uncapped).
        if (main.maximumCharge().compareTo(BigDecimal.ZERO) > 0
                && chargeAmount.compareTo(main.maximumCharge()) > 0
                && chargeAmount.compareTo(BigDecimal.ZERO) > 0) {
            chargeAmount = main.maximumCharge();
            // F115L070.src:602-606: also NOT Rounded.
            percent = chargeAmount.multiply(ONE_HUNDRED).divide(base, PERCENT_SCALE, RoundingMode.DOWN);
        }

        return work.withAmountAndPercentage(chargeAmount, percent);
    }

    // =====================================================================
    // F000/F100-* (F115L070.src:813-884) - RULE-010
    // =====================================================================

    @Override
    public List<ChargeRow> reverseStoredCharges(List<ChargeRow> inputCharges, List<ChargeRow> storedMainCharges) {
        List<ChargeRow> result = new ArrayList<>(inputCharges);
        for (int i = 0; i < result.size(); i++) {
            ChargeRow inputRow = result.get(i);
            // F115L070.src:820-821: terminates at the first blank Charge-Type.
            if (inputRow.chargeType() == null || inputRow.chargeType().isBlank()) {
                break;
            }
            Optional<ChargeRow> matched = storedMainCharges.stream()
                    .filter(m -> m.chargeType().equals(inputRow.chargeType()) && m.roleType().equals(inputRow.roleType()))
                    .findFirst();
            if (matched.isEmpty()) {
                continue; // F115L070.src:843-848: no match - input row left untouched.
            }
            ChargeRow stored = matched.get();
            ChargeMethodOfCalculation mc = stored.methodOfCalculation();
            if (mc.isSettlement() || mc.isPartAmount()) {
                // F115L070.src:865-872.
                BigDecimal newAmount = inputRow.amount().subtract(stored.amount());
                if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
                    newAmount = ChargeRow.ZERO_AMOUNT;
                }
                result.set(i, inputRow.withAmount(newAmount));
            }
            // Pct-Of-CA/Pct-Of-PA (F115L070.src:874-881): Continue, no change.
        }
        return result;
    }

    // =====================================================================
    // Z000-Error-Message (F115L070.src:954-972) + Z900-Abend (F115L070.src:997-1018)
    // =====================================================================

    private ChargeMergeAbendException raiseAbend(ChargeMergeContext context, String paragraph,
                                                  String legacyMessageCode, String variableValue) {
        String resolvedText = null;
        try {
            ErrorMessageRequest messageRequest = new ErrorMessageRequest(
                    legacyMessageCode, context.language(), context.finInstId(),
                    List.of(new MessageVariable("VAR1", variableValue)), false, "");
            resolvedText = errorMessageService.lookupMessage(messageRequest, messageRepository, false).messageText();
        } catch (RuntimeException ignored) {
            // Z000-Error-Message has no fallback of its own in F115L070; a
            // message-repository problem must not mask the real abend below.
        }
        return new ChargeMergeAbendException(paragraph, legacyMessageCode, variableValue, resolvedText);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Reproduces reading a fixed {@code Occurs 50} COBOL table slot beyond
     * {@code No-Tab-Occ} (or, here, beyond a shorter Java {@link List}'s
     * bounds): just zeros/spaces, never an index error.
     */
    private static ChargeRow rowOrBlank(List<ChargeRow> rows, int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > rows.size()) {
            return ChargeRow.BLANK;
        }
        return rows.get(oneBasedIndex - 1);
    }

    private static void setRow(List<ChargeRow> rows, int oneBasedIndex, ChargeRow value) {
        while (rows.size() < oneBasedIndex) {
            rows.add(ChargeRow.BLANK);
        }
        rows.set(oneBasedIndex - 1, value);
    }

    private static boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static boolean isNonZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) != 0;
    }
}
