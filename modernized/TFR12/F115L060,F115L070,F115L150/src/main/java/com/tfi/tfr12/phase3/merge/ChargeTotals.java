package com.tfi.tfr12.phase3.merge;

import java.math.BigDecimal;

/**
 * Mirrors {@code R115L070-Totals-Seller} / {@code -Totals-Buyer} / {@code -Totals-Agent}
 * (legacy/STG/TFR12/copybooks/R115L070.copy:101-123), populated by
 * F115L070.src's E200-Accumulate-Totals (RULE-009, lines 637-734).
 *
 * @param sellerCreditAgentBank R115L070-Ch-Seller-Cred-Agent - RULE-009 edge case:
 *                              this accumulator's arm is commented out in
 *                              F115L070.src (lines 641-649), so it is ALWAYS
 *                              zero coming out of this module, never populated
 * @param sellerCreditOtherBank R115L070-Ch-Seller-Cred-T-Bank (F115L070.src:651-658)
 * @param sellerCreditOwnBank   R115L070-Ch-Seller-Cred-W-Bank (F115L070.src:660-667)
 * @param totalChargeSeller     R115L070-Total-Charge-Seller = Cred-T-Bank + Cred-W-Bank
 *                              (F115L070.src:700-709; Cred-Agent is NOT added, matching
 *                              the commented-out line 702-703)
 * @param buyerCreditAgentBank  R115L070-Ch-Buyer-Cred-Agent - same RULE-009 edge case
 *                              (F115L070.src:669-676), always zero
 * @param buyerCreditOtherBank  R115L070-Ch-Buyer-Cred-T-Bank (F115L070.src:678-685)
 * @param buyerCreditOwnBank    R115L070-Ch-Buyer-Cred-W-Bank (F115L070.src:687-694)
 * @param totalChargeBuyer      R115L070-Total-Charge-Buyer = Cred-T-Bank + Cred-W-Bank
 *                              (F115L070.src:711-720)
 * @param totalAgentCommission  R115L070-Tot-Agent-Commission = Main's Tot-Agent-Commission
 *                              + Part's Tot-Agent-Commission (F115L070.src:722-727) - summed
 *                              from what F115L060 already computed for each table, NOT
 *                              re-derived from the merged Work rows
 */
public record ChargeTotals(
        BigDecimal sellerCreditAgentBank,
        BigDecimal sellerCreditOtherBank,
        BigDecimal sellerCreditOwnBank,
        BigDecimal totalChargeSeller,
        BigDecimal buyerCreditAgentBank,
        BigDecimal buyerCreditOtherBank,
        BigDecimal buyerCreditOwnBank,
        BigDecimal totalChargeBuyer,
        BigDecimal totalAgentCommission) {

    public static final ChargeTotals ZERO = new ChargeTotals(
            ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_AMOUNT,
            ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_AMOUNT, ChargeRow.ZERO_AMOUNT,
            ChargeRow.ZERO_AMOUNT);
}
