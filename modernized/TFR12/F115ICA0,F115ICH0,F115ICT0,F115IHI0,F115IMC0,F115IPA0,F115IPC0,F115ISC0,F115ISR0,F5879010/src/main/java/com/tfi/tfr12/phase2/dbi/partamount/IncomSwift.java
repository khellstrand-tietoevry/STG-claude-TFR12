package com.tfi.tfr12.phase2.dbi.partamount;

/**
 * The incoming-SWIFT Y/N half of the legacy copybook's overloaded byte
 * {@code R115IPA0-Incom-Swift} (legacy/TFR12/copybooks/R115IPA0.copy:180-183),
 * split out per RULE-029 (analysis/TFR12/BUSINESS_RULES.md, "RULE-029:
 * Settled part-amount suppresses recalculation of percentage-based charge").
 *
 * <p>The legacy field is one {@code Pic X(01)} byte with three named 88-level
 * condition-values, only one of which can ever be true on a given occurrence
 * of the byte:
 * <ul>
 *   <li>{@code R115IPA0-Incom-Yes} — value {@code 'Y'}</li>
 *   <li>{@code R115IPA0-Incom-No} — value {@code 'N'}</li>
 *   <li>{@code R115IPA0-Has-Been-Settled} — value {@code 'A'} (an unrelated
 *       settlement-lifecycle flag consumed by F115L060.src:604-628, not by
 *       F115IPA0 itself)</li>
 * </ul>
 * Any other byte value — including the space the field holds after
 * {@code Initialize} — matches none of the three condition-names; that state
 * is represented here as {@link #UNSPECIFIED}, and is also what a {@code 'A'}
 * (Has-Been-Settled) occurrence looks like from the incoming-SWIFT flag's own
 * point of view, since {@code 'A'} is neither {@code 'Y'} nor {@code 'N'}.
 *
 * <p>The person who owns this system was asked how to model the overload and
 * chose to split it into two independently-typed fields in the new domain
 * model ({@link PartAmountRow#hasBeenSettled()} and
 * {@link PartAmountRow#incomSwift()}) rather than keep one shared byte —
 * see {@code PartAmountServiceTest} for the tests pinning that the two are
 * now independent, unlike the single legacy byte that could only ever hold
 * one of the three values at a time.
 */
public enum IncomSwift {
    /** {@code R115IPA0-Incom-Yes}, legacy byte value {@code 'Y'}. */
    YES,
    /** {@code R115IPA0-Incom-No}, legacy byte value {@code 'N'}. */
    NO,
    /**
     * Legacy byte is space (post-{@code Initialize}, never set) or any value
     * other than {@code 'Y'}/{@code 'N'} — including {@code 'A'}, which is
     * meaningful only via {@link PartAmountRow#hasBeenSettled()}, not via
     * this enum.
     */
    UNSPECIFIED;

    /**
     * Decodes the raw legacy byte value into its incoming-SWIFT meaning only
     * (the settlement meaning is decoded separately by
     * {@link PartAmountRow#hasBeenSettledFromLegacyByte(char)}).
     */
    public static IncomSwift fromLegacyByte(char rawByte) {
        return switch (rawByte) {
            case 'Y' -> YES;
            case 'N' -> NO;
            default -> UNSPECIFIED;
        };
    }

    /** Re-encodes back to the legacy byte value for this flag alone (space when unspecified). */
    public char toLegacyByte() {
        return switch (this) {
            case YES -> 'Y';
            case NO -> 'N';
            case UNSPECIFIED -> ' ';
        };
    }
}
