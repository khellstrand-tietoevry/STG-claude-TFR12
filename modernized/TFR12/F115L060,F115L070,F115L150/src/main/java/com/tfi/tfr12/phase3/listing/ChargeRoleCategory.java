package com.tfi.tfr12.phase3.listing;

/**
 * The 5 mutually-exclusive values of {@code R115ICT0-Paying-Pty}
 * (legacy/STG/TFR12/copybooks/R115ICT0.copy:126-131), i.e. which abstract
 * category of party a {@code TF_Sys_Charge_Type} row says can be charged: a
 * general-ledger account, the agent, "any" (both sides), the buyer side, or the
 * seller side.
 *
 * <p>Consumed by RULE-033 ({@code analysis/TFR12/BUSINESS_RULES.md}), which
 * translates this abstract category (together with the contract's
 * {@link ContractFamily}) into the concrete paying-party role type(s)
 * ({@code DRWEE}/{@code DRWER}/{@code CUSTM}/{@code BENEF}/{@code ISSUI}/{@code GLACC}/blank)
 * used to search {@code TF_Main_Charge}/{@code TF_Part_Charge}
 * (F115L060.src:499-570, paragraph {@code D000-Read-Permissible-Charges}).
 */
public enum ChargeRoleCategory {
    /** {@code R115ICT0-GLACC}, copybook value {@code 'GLACC '}. */
    GLACC,
    /** {@code R115ICT0-Agent-pty}, copybook value {@code 'AGENT '}. */
    AGENT_PTY,
    /** {@code R115ICT0-ANY}, copybook value 6 spaces. */
    ANY,
    /** {@code R115ICT0-Buyer}, copybook value {@code 'BUYER '}. */
    BUYER,
    /** {@code R115ICT0-Seller}, copybook value {@code 'SELLER'}. */
    SELLER;

    /**
     * Decodes the raw {@code Pic X(06)} legacy byte value (copybooks/R115ICT0.copy:126-131).
     * Comparison is done on the trimmed value since the 88-level condition-names are
     * fixed-width, space-padded literals.
     */
    public static ChargeRoleCategory fromLegacyCode(String rawPayingPty) {
        String trimmed = rawPayingPty == null ? "" : rawPayingPty.trim();
        return switch (trimmed) {
            case "" -> ANY;
            case "GLACC" -> GLACC;
            case "AGENT" -> AGENT_PTY;
            case "BUYER" -> BUYER;
            case "SELLER" -> SELLER;
            default -> throw new IllegalArgumentException(
                    "Unrecognized R115ICT0-Paying-Pty value: '" + rawPayingPty + "'");
        };
    }
}
