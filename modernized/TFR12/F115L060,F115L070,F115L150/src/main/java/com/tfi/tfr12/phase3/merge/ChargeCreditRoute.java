package com.tfi.tfr12.phase3.merge;

/**
 * Mirrors {@code R115L060-Charge-Credit} / {@code R115L070-Charge-Credit}
 * (Pic X(01), legacy/STG/TFR12/copybooks/R115L060.copy:91-94 and
 * R115L070.copy:92-95) and its three 88-level condition names, used by
 * RULE-009 (F115L070.src:637-720, E200-Accumulate-Totals) to route each
 * charge's amount into the seller/buyer credit-routing sub-totals.
 */
public enum ChargeCreditRoute {
    /**
     * R115L060-Credit-Agent-Bank, Value 'A'. RULE-009 edge case: the
     * accumulation arms for this route are commented out in F115L070.src
     * (lines 641-649, 669-676) - a charge routed here is never added to any
     * seller/buyer sub-total by this module (see
     * {@link ChargeTotals#sellerCreditAgentBank()} /
     * {@link ChargeTotals#buyerCreditAgentBank()}, which stay zero).
     */
    AGENT_BANK('A'),
    /** R115L060-Credit-Other-Bank, Value 'T'. */
    OTHER_BANK('T'),
    /** R115L060-Credit-Own-Bank, Value 'W'. */
    OWN_BANK('W'),
    /** Blank/uninitialized occurrence - matches none of the three 88-levels. */
    NONE(' ');

    private final char code;

    ChargeCreditRoute(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    public static ChargeCreditRoute fromCode(char code) {
        for (ChargeCreditRoute route : values()) {
            if (route.code == code) {
                return route;
            }
        }
        return NONE;
    }
}
