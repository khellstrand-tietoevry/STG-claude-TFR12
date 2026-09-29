package com.tfi.tfr12.phase3.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Java translation of legacy COBOL program {@code F115L150} ("Read POP").
 * Source: {@code legacy/STG/TFR12/src/F115L150.src} (line numbers below refer to this file
 * as read byte-safe ISO-8859-1, which is 1:1 with the COBOL sequence numbers in columns 73-80).
 *
 * <p>Paragraph mapping:
 * <ul>
 *   <li>C000-Interface (F115L150.src:123-165) -&gt; {@link #computeDefaultCharge}, dispatching
 *       on {@code R349IPP0-Ut-Ak-Rader-Totalt} (the tier-row count).</li>
 *   <li>D000-Process-Unique-Occurence / D100 / D200 (F115L150.src:390-461) -&gt;
 *       {@link #processUniqueOccurrence} - pins RULE-021 and RULE-011.</li>
 *   <li>E000-Process-Multiple-Occ (F115L150.src:463-519) -&gt;
 *       {@link #processMultipleOccurrences} - pins RULE-003.</li>
 *   <li>L100-Convert-Amounts (F115L150.src:539-579) -&gt; {@link #convertAmounts} - pins
 *       RULE-012.</li>
 * </ul>
 *
 * <p>All working-storage amount fields F115L150 computes into are COMP-3 with 3 decimal
 * places (WS-Charge-Amount, WS-Percent-Comm, WS-Max-Charge, WS-Min-Charge, WS-Difference,
 * WS-Increment all {@code Pic S9(n)V9(03)}, F115L150.src:31-47) and every {@code Compute}
 * that derives them omits the {@code Rounded} phrase, so GnuCOBOL truncates (does not
 * round) excess low-order digits. {@link #truncate3(BigDecimal)} models that truncation
 * with {@link RoundingMode#DOWN}.
 */
public class PopPricingServiceImpl implements PopPricingService {

    private static final int WORKING_SCALE = 3;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO3 = BigDecimal.ZERO.setScale(WORKING_SCALE);

    /** R115ICT0-MethCalc-Pct-Of-PA 88-level value (copybooks/R115ICT0.copy:109). */
    private static final String METHOD_CALC_PCT_OF_PA = "Q";

    private final ProductPriceGateway productPriceGateway;
    private final CurrencyExchangeGateway currencyExchangeGateway;

    public PopPricingServiceImpl(ProductPriceGateway productPriceGateway,
                                  CurrencyExchangeGateway currencyExchangeGateway) {
        this.productPriceGateway = productPriceGateway;
        this.currencyExchangeGateway = currencyExchangeGateway;
    }

    @Override
    public PricingResult computeDefaultCharge(PricingRequest request) {
        List<PricingTier> tiers = productPriceGateway.findPriceTiers(toQuery(request));

        // C000-Interface, Evaluate R349IPP0-Ut-Ak-Rader-Totalt (F115L150.src:145-164).
        if (tiers.isEmpty()) {
            // When +0: Set Return-OK to True (F115L150.src:147). J000-Output-Fields is
            // never performed in this branch, so the caller-visible record stays exactly
            // as C100-Initialize-Output left it: zero amounts, blank currency.
            return new PricingResult(ZERO3, ZERO3, "", ZERO3, ZERO3);
        }
        if (tiers.size() == 1) {
            // When +1: Perform D000-Process-Unique-Occurence, then J000 (F115L150.src:149-151).
            return processUniqueOccurrence(tiers.get(0), request);
        }
        // When other: Perform E000-Process-Multiple-Occ, then J000 (F115L150.src:161-163).
        return processMultipleOccurrences(tiers, request);
    }

    /**
     * D000-Process-Unique-Occurence (F115L150.src:390-439), an "Evaluate True also True" on
     * (Prisbelop = 0, Prisprosent = 0).
     */
    private PricingResult processUniqueOccurrence(PricingTier tier, PricingRequest request) {
        boolean flatZero = isZero(tier.prisbelop());
        boolean pctZero = isZero(tier.prisprosent());

        BigDecimal popChargeAmount = ZERO3;
        BigDecimal chargeAmount = ZERO3;
        BigDecimal percentComm = ZERO3;
        BigDecimal popMaxCharge;
        BigDecimal popMinCharge;

        if (flatZero && pctZero) {
            // F115L150.src:393-399: both zero -> only min/max carried through, from
            // Maksimumpris/Minimumpris.
            popMaxCharge = scale3(tier.maksimumpris());
            popMinCharge = scale3(tier.minimumpris());
        } else if (!flatZero && !pctZero) {
            // RULE-021 (F115L150.src:401-410): both non-zero is invalid configuration.
            throw new InvalidProductPricingConfigurationException();
        } else if (!flatZero) {
            // F115L150.src:412-419: flat amount only. Note the max/min pair here is
            // Ovre-/Nedre-Prisbelop, NOT Maksimumpris/Minimumpris.
            popChargeAmount = scale3(tier.prisbelop());
            popMaxCharge = scale3(tier.ovrePrisbelop());
            popMinCharge = scale3(tier.nedrePrisbelop());
        } else {
            // F115L150.src:421-437: percentage only.
            percentComm = scale3(tier.prisprosent());
            popMaxCharge = scale3(tier.maksimumpris());
            popMinCharge = scale3(tier.minimumpris());

            boolean pctOfPartAmount = request.hasPartAmount()
                    && METHOD_CALC_PCT_OF_PA.equals(request.chargeTypeConfig().methodCalc());
            if (pctOfPartAmount) {
                // D100-Compute-Charge-Amount (F115L150.src:441-450): percent of the part
                // amount (R115IPA0-Amount, re-read via C600 in the legacy code but the same
                // row already carried on the request).
                chargeAmount = percentOf(percentComm, request.partAmount().amount());
            } else {
                // D200-Compute-Charge-Amount (F115L150.src:452-461) - RULE-011: percent of
                // the contract's ORIGINAL amount (R115ICA0-Curr-O-Amount), F115L150.src:458.
                // The commented-out alternative at F115L150.src:457 (R115ICA0-Remaining-Amount)
                // is confirmed dead code, not used.
                chargeAmount = percentOf(percentComm, request.contractAmount().currOriginalAmount());
            }
        }

        return convertAmounts(popChargeAmount, chargeAmount, percentComm, popMaxCharge, popMinCharge, request);
    }

    /**
     * E000-Process-Multiple-Occ (F115L150.src:463-519) - RULE-003: more than one price row
     * for the same charge type means a graduated/tiered percentage schedule. Each tier's
     * percentage is applied only to the slice of the (currency-converted) amount that falls
     * within that tier's band, and the increments are summed.
     */
    private PricingResult processMultipleOccurrences(List<PricingTier> tiers, PricingRequest request) {
        // F115L150.src:154-159 (evaluated once in C000-Interface before E000 is entered):
        // Part-Amount if supplied, else the contract's REMAINING amount (Remaining-Amount) -
        // note this is a different base than RULE-011's D200 (Curr-O-Amount).
        BigDecimal paOrCaAmount = request.hasPartAmount()
                ? request.partAmount().amount()
                : request.contractAmount().remainingAmount();

        // F115L150.src:471-480: convert that amount from the contract's currency into the
        // bank's basic currency before tiering against it.
        BigDecimal totalAmountBase = currencyExchangeGateway.exchange(new CurrencyExchangeRequest(
                bankNo(request),
                paOrCaAmount,
                request.contractAmount().currency(),
                false,
                null,
                true));

        BigDecimal stored = ZERO3; // WS-Stored-Gyldig-Til-Bel, Initialized to 0 in B000.
        BigDecimal popChargeAmount = ZERO3; // WS-POP-Charge-Amount.
        int loadedIndex = 0; // Row already loaded into R349IPP0 by C400-Read-1st-Prod-Tjen-Pris.
        boolean cursorExhausted = false;

        // Perform until R7919999-Return-Error or WS-PA-or-CA-Amount-Ex <= WS-Stored-Gyldig-Til-Bel
        // (F115L150.src:483-512): tested BEFORE each iteration, so zero iterations is possible.
        while (!cursorExhausted && totalAmountBase.compareTo(stored) > 0) {
            PricingTier tier = tiers.get(loadedIndex);

            BigDecimal difference = tier.gyldigTilBelop().compareTo(totalAmountBase) < 0
                    ? scale3(tier.gyldigTilBelop()).subtract(stored)   // F115L150.src:486-489
                    : totalAmountBase.subtract(stored);                // F115L150.src:492-496 (Else)
            difference = truncate3(difference);

            BigDecimal increment = truncate3(difference.multiply(tier.prisprosent())
                    .divide(ONE_HUNDRED, 10, RoundingMode.DOWN)); // F115L150.src:500-504
            popChargeAmount = popChargeAmount.add(increment); // F115L150.src:506

            stored = scale3(tier.gyldigTilBelop()); // F115L150.src:508-509

            // C430-Call-DBI-PP-PP (Get-Next), F115L150.src:510. On success the row advances;
            // on failure (cursor exhausted) R349IPP0 keeps the LAST successfully read row
            // (C430-Call-DBI-PP-PP treats Return-Error as Continue, F115L150.src:292-293 -
            // it never re-Initializes the record), so `loadedIndex` deliberately does not
            // move past the last tier.
            if (loadedIndex + 1 < tiers.size()) {
                loadedIndex++;
            } else {
                cursorExhausted = true;
            }
        }

        // F115L150.src:514-517: Set Return-OK to True; Max/Min come from whatever row is
        // currently loaded (the last tier actually read).
        PricingTier lastLoadedTier = tiers.get(loadedIndex);
        BigDecimal popMaxCharge = scale3(lastLoadedTier.maksimumpris());
        BigDecimal popMinCharge = scale3(lastLoadedTier.minimumpris());

        // WS-Percent-Comm and WS-Charge-Amount are never touched in E000, so they are still
        // at their B000-Initialize value (0) going into L100-Convert-Amounts.
        return convertAmounts(popChargeAmount, ZERO3, ZERO3, popMaxCharge, popMinCharge, request);
    }

    /**
     * L100-Convert-Amounts (F115L150.src:539-579) - RULE-012: flat charge/min/max amounts
     * from the pricing table are in the bank's base currency and must be converted to the
     * contract's currency before use; a percentage-derived charge amount (already computed
     * against a contract-currency base by D100/D200) is NOT converted.
     */
    private PricingResult convertAmounts(BigDecimal popChargeAmount,
                                          BigDecimal chargeAmount,
                                          BigDecimal percentComm,
                                          BigDecimal popMaxCharge,
                                          BigDecimal popMinCharge,
                                          PricingRequest request) {
        String contractCurrency = request.contractAmount().currency();

        BigDecimal finalChargeAmount;
        if (!isZero(popChargeAmount)) {
            // F115L150.src:549-553.
            finalChargeAmount = exchangeFromBasic(popChargeAmount, contractCurrency, request);
        } else if (percentComm.compareTo(BigDecimal.ZERO) <= 0) {
            // F115L150.src:557-559 (the 1999 "Feilretting"/bug-fix branch): only copy the
            // zero POP charge amount through when there is no percentage in play either.
            finalChargeAmount = popChargeAmount;
        } else {
            // F115L150.src:554-561 (Else, with the Move at F115L150.src:555 commented out):
            // a percentage-based charge amount is left exactly as D100/D200 computed it -
            // it is already expressed in the contract's currency, so no FX conversion runs.
            finalChargeAmount = chargeAmount;
        }

        BigDecimal finalMaxCharge = isZero(popMaxCharge)
                ? popMaxCharge
                : exchangeFromBasic(popMaxCharge, contractCurrency, request); // F115L150.src:564-570

        BigDecimal finalMinCharge = isZero(popMinCharge)
                ? popMinCharge
                : exchangeFromBasic(popMinCharge, contractCurrency, request); // F115L150.src:572-578

        return new PricingResult(percentComm, finalChargeAmount, contractCurrency, finalMaxCharge, finalMinCharge);
    }

    private BigDecimal exchangeFromBasic(BigDecimal baseCurrencyAmount, String contractCurrency, PricingRequest request) {
        BigDecimal converted = currencyExchangeGateway.exchange(new CurrencyExchangeRequest(
                bankNo(request),
                baseCurrencyAmount,
                null,
                true,
                contractCurrency,
                false));
        return scale3(converted);
    }

    /** R115ICA0-Curr-O-Amount / R115IPA0-Amount * WS-Percent-Comm / 100, truncated to 3dp. */
    private BigDecimal percentOf(BigDecimal percent, BigDecimal base) {
        return truncate3(percent.multiply(base).divide(ONE_HUNDRED, 10, RoundingMode.DOWN));
    }

    /** R115ISC0-Fin-Inst-No(1:4) (F115L150.src:473, 546-547). */
    private String bankNo(PricingRequest request) {
        String bankNo = request.bankNo();
        return bankNo.length() <= 4 ? bankNo : bankNo.substring(0, 4);
    }

    private static ProductPriceQuery toQuery(PricingRequest request) {
        return new ProductPriceQuery(
                request.chargeTypeConfig().finInstId(),
                request.contractAmount().contractType(),
                request.chargeTypeConfig().ctType(),
                request.validDate());
    }

    private static boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal scale3(BigDecimal value) {
        return value.setScale(WORKING_SCALE);
    }

    private static BigDecimal truncate3(BigDecimal value) {
        return value.setScale(WORKING_SCALE, RoundingMode.DOWN);
    }
}
