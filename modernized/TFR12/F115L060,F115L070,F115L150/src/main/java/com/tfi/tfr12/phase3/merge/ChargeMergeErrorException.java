package com.tfi.tfr12.phase3.merge;

/**
 * Mirrors F115L070's Z700-Error-AE path (F115L070.src:974-995): a recoverable
 * "application error" (AE) return, set when a called module (F115ICA0,
 * F115IPA0, F115L060) itself returns {@code R7919999-Return-Error}. Unlike
 * {@link ChargeMergeAbendException}, no message is built via Z000-Error-Message
 * before Z700-Error-AE runs at any of its call sites in F115L070 (E302, C232,
 * C234) - only the failing paragraph is recorded, which is why this exception
 * carries a paragraph but no legacy message code.
 *
 * <p>Only the RULE-002 clamp path (E300-Read-Contract-Amount /
 * E400-Read-Part-Amount, F115L070.src:737-811) is modeled by this Java port's
 * {@code ContractAmountLookup}/{@code PartAmountLookup} ports; an empty
 * {@link java.util.Optional} from either is treated as the legacy's
 * "row not found" Return-Error case (paragraph E302 for both call sites,
 * F115L070.src:762-764 and 802-804).
 */
public class ChargeMergeErrorException extends RuntimeException {

    private final String paragraph;

    public ChargeMergeErrorException(String paragraph) {
        super("F115L070 returned Error (AE) at " + paragraph);
        this.paragraph = paragraph;
    }

    /** R7919999-Paragraph, e.g. "E302". */
    public String paragraph() {
        return paragraph;
    }
}
