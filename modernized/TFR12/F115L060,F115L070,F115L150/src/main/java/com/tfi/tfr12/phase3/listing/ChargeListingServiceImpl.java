package com.tfi.tfr12.phase3.listing;

import com.tfi.tfr12.phase1.datetime.MachineTimestampService;
import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;
import com.tfi.tfr12.phase3.pricing.PopPricingService;
import com.tfi.tfr12.phase3.pricing.PricingRequest;
import com.tfi.tfr12.phase3.pricing.PricingResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * See {@link ChargeListingService} for scope notes. Paragraph mapping:
 * <ul>
 *   <li>{@code C300-Set-Contract-Type} (F115L060.src:253-269) -&gt;
 *       {@link #classifyContractType} - RULE-019.</li>
 *   <li>{@code D000-Read-Permissible-Charges} role-resolution Evaluate
 *       (F115L060.src:499-570) -&gt; {@link #resolveRoleTypes} - RULE-033.</li>
 *   <li>{@code L100-Read-Sys-Charge-Type} (F115L060.src:1104-1156) -&gt;
 *       {@link #resolveChargeTypeConfig} - RULE-038.</li>
 *   <li>{@code D100-Read-By-Paying-Pty} settlement check
 *       (F115L060.src:604-628) -&gt; {@link #shouldReadMainCharge} - RULE-029.</li>
 *   <li>{@code L000-Additional-Info} / {@code N000-Compute-Charge-Amount}
 *       (F115L060.src:1050-1101, 1341-1381) -&gt; {@link #applyAdditionalInfo} -
 *       RULE-006, RULE-007, RULE-001.</li>
 * </ul>
 */
public class ChargeListingServiceImpl implements ChargeListingService {

    /** R115ICT0-MethCalc-Pct-Of-CA, copybooks/R115ICT0.copy:108. */
    private static final String METHOD_CALC_PCT_OF_CA = "P";
    /** R115ICT0-MethCalc-Pct-Of-PA, copybooks/R115ICT0.copy:109. */
    private static final String METHOD_CALC_PCT_OF_PA = "Q";

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO2 = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal ZERO3 = BigDecimal.ZERO.setScale(3);

    private final SysChargeTypeGateway sysChargeTypeGateway;
    private final PopPricingService popPricingService;
    private final MachineTimestampService timestampService;
    private final Clock clock;

    public ChargeListingServiceImpl(SysChargeTypeGateway sysChargeTypeGateway,
                                     PopPricingService popPricingService,
                                     MachineTimestampService timestampService,
                                     Clock clock) {
        this.sysChargeTypeGateway = sysChargeTypeGateway;
        this.popPricingService = popPricingService;
        this.timestampService = timestampService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // RULE-019 (F115L060.src:253-269)
    // ------------------------------------------------------------------
    @Override
    public ContractFamily classifyContractType(String contractType) {
        return ContractFamily.classify(contractType);
    }

    // ------------------------------------------------------------------
    // RULE-033 (F115L060.src:499-570)
    // ------------------------------------------------------------------
    @Override
    public List<String> resolveRoleTypes(ChargeRoleCategory category, ContractFamily family) {
        // F115L060.src:499-570: Evaluate True also True on (R115ICT0-<category>, Sw-<family>).
        // No "When Other" exists, so unmatched (category, family) pairs read no charges at
        // all for that combination - modeled as an empty list, not an error.
        return switch (family) {
            case COLLECTION -> switch (category) {
                case GLACC -> List.of("GLACC");               // F115L060.src:500-502
                case AGENT_PTY -> List.of("");                 // F115L060.src:504-506 (WS-Agent, blank)
                case ANY -> List.of("DRWEE", "DRWER");         // F115L060.src:508-513
                case BUYER -> List.of("DRWEE");                // F115L060.src:534-536
                case SELLER -> List.of("DRWER");                // F115L060.src:552-554
            };
            case IMPORT_LC -> switch (category) {
                case ANY -> List.of("CUSTM", "BENEF");         // F115L060.src:514-519
                case BUYER -> List.of("CUSTM");                // F115L060.src:538-540
                case SELLER -> List.of("BENEF");                // F115L060.src:556-558
                case GLACC, AGENT_PTY -> List.of();
            };
            case EXPORT_LC -> switch (category) {
                case ANY -> List.of("ISSUI", "BENEF");         // F115L060.src:520-525
                case BUYER -> List.of("ISSUI");                // F115L060.src:542-544
                case SELLER -> List.of("BENEF");                // F115L060.src:560-562
                case GLACC, AGENT_PTY -> List.of();
            };
            case GUARANTEE -> switch (category) {
                case ANY -> List.of("CUSTM", "BENEF");         // F115L060.src:527-532 (ndl/gar patch)
                case BUYER -> List.of("CUSTM");                // F115L060.src:547-550 (ndl/gar patch)
                case SELLER -> List.of("BENEF");                // F115L060.src:565-568 (ndl/gar patch)
                case GLACC, AGENT_PTY -> List.of();
            };
        };
    }

    // ------------------------------------------------------------------
    // RULE-038 (F115L060.src:1104-1156)
    // ------------------------------------------------------------------
    @Override
    public SysChargeTypeDetails resolveChargeTypeConfig(String chargeType, long finInstId, int regNo) {
        Optional<SysChargeTypeDetails> byFinInst = sysChargeTypeGateway.selectByFinInst(chargeType, finInstId);
        if (byFinInst.isPresent()) {
            // F115L060.src:1153-1154: When Return-OK, Continue.
            return byFinInst.get();
        }
        // F115L060.src:1129-1140: When Return-Error, Set Return-OK, retry by Reg-No.
        Optional<SysChargeTypeDetails> byRegno = sysChargeTypeGateway.selectByRegno(chargeType, regNo);
        // F115L060.src:1147-1149: When Return-Error (again), Z700-Error-AE 'L103'.
        return byRegno.orElseThrow(() -> new ChargeTypeNotConfiguredException(chargeType, regNo));
    }

    // ------------------------------------------------------------------
    // RULE-029 (F115L060.src:604-628)
    // ------------------------------------------------------------------
    @Override
    public boolean shouldReadMainCharge(String methodCalc, int checkPaSeqNo,
                                         boolean partAmountCheckErrored, boolean partAmountHasBeenSettled) {
        boolean settlementCheckApplies = METHOD_CALC_PCT_OF_PA.equals(methodCalc) && checkPaSeqNo > 0;
        if (!settlementCheckApplies) {
            // F115L060.src:626-628 (Else branch of the outer If): E000-Read-Main-Charge
            // always runs unconditionally.
            return true;
        }
        // F115L060.src:622-625: If Return-Error Or Not Has-Been-Settled, still read.
        return partAmountCheckErrored || !partAmountHasBeenSettled;
    }

    // ------------------------------------------------------------------
    // RULE-006, RULE-007, RULE-001 (F115L060.src:1050-1101, 1341-1381)
    // ------------------------------------------------------------------
    @Override
    public PresentedCharge applyAdditionalInfo(ChargeLine raw, ContractAmountRow contractAmount,
                                                SysChargeTypeDetails chargeTypeConfig, int paSeqNo,
                                                PartAmountRow partAmount, String bankNo) {
        BigDecimal chargeAmount = raw.chargeAmount();

        // F115L060.src:1059-1062 (RULE-007): only recomputed when the stored amount is
        // zero and a percentage is present - the comment at F115L060.src:1054-1057 notes
        // this "should never occur" in practice since percentages live only in POP.
        if (isZero(chargeAmount) && !isZero(raw.percent())) {
            chargeAmount = computeChargeAmount(chargeTypeConfig.methodCalc(), contractAmount,
                    paSeqNo, partAmount, raw.percent());
        }

        // F115L060.src:1076 (RULE-006): WS-Converted-Amount is ALWAYS just WS-Charge-Amount
        // widened to 3 decimals - never FX-converted (the 1999 change removed the prior
        // currency-conversion call, F115L060.src:1064-1074 show it commented out).
        BigDecimal convertedAmount = chargeAmount.setScale(3, RoundingMode.UNNECESSARY);

        // F115L060.src:1077-1079 (RULE-006): relabel only, no conversion of the amount.
        String currency = raw.currency();
        if (!currency.equals(contractAmount.currency())) {
            currency = contractAmount.currency();
        }

        // F115L060.src:1096-1101 (RULE-001, the observable half - see class javadoc for the
        // "priced as of today" boundary note).
        BigDecimal maximumCharge;
        BigDecimal minimumCharge;
        if (!chargeTypeConfig.readPopEnabled()) {
            // F115L060.src:1097-1098: Initialize R115L150-TF-POP - all-zero, no POP call.
            maximumCharge = ZERO3;
            minimumCharge = ZERO3;
        } else {
            // F115L060.src:178-188 (B000-Initialize) + F115L060.src:1278: the machine date
            // is fetched once and forwarded as R115L150-In-Valid-Date.
            int validDate = timestampService.fetchTimestamp(clock).date8();
            PricingResult pop = popPricingService.computeDefaultCharge(new PricingRequest(
                    paSeqNo, bankNo, chargeTypeConfig.row(), contractAmount, partAmount, validDate));
            maximumCharge = pop.maximumCharge();
            minimumCharge = pop.minimumCharge();
        }

        return new PresentedCharge(raw.chargeType(), raw.roleType(), chargeAmount, convertedAmount,
                currency, raw.percent(), raw.chargeText(), maximumCharge, minimumCharge);
    }

    /**
     * N000-Compute-Charge-Amount (F115L060.src:1341-1381).
     *
     * <p>Deliberately modeled as a pure function of its arguments: in the legacy code,
     * {@code WS-CA-Or-PA-Amount} is a top-level {@code WS-Fields} element Initialized
     * once per program run (F115L060.src:175), not per call to this paragraph, so if
     * {@code methodCalc} were ever anything other than 'P'/'Q' on a call that reaches
     * here (the comment at F115L060.src:1054-1057 says that "should never occur"), the
     * legacy code would actually see whatever stale value a PRIOR charge's computation
     * left behind, not zero. That cross-call staleness is not modeled here (it is a
     * "should never occur" dead branch per the source's own comment, and modeling it
     * would require this method to carry state across unrelated charges).
     */
    private BigDecimal computeChargeAmount(String methodCalc, ContractAmountRow contractAmount,
                                            int paSeqNo, PartAmountRow partAmount, BigDecimal percent) {
        BigDecimal base;
        if (METHOD_CALC_PCT_OF_CA.equals(methodCalc)) {
            // F115L060.src:1347-1351: R115ICA0-Curr-O-Amount (current original amount).
            base = contractAmount.currOriginalAmount();
        } else if (METHOD_CALC_PCT_OF_PA.equals(methodCalc)) {
            // F115L060.src:1353-1362: PA-Seq-No supplied -> Part Amount; else -> Curr-O-Amount.
            base = (paSeqNo != 0) ? partAmount.amount() : contractAmount.currOriginalAmount();
        } else {
            base = BigDecimal.ZERO;
        }

        if (isZero(base)) {
            // F115L060.src:1375-1378 (Else): forced to zero.
            return ZERO2;
        }
        // F115L060.src:1366-1373: Compute WS-Charge-Amount Rounded = base * percent / 100.
        // WS-Charge-Amount has 2 decimals; COBOL ROUNDED (no ROUNDED MODE clause) rounds
        // half away from zero, i.e. RoundingMode.HALF_UP for positive amounts.
        return base.multiply(percent)
                .divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }
}
