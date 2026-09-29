package com.tfi.tfr12.phase3.merge;

/**
 * Mirrors F115L070's Z900-Abend path (F115L070.src:997-1018): an unrecoverable
 * data-integrity failure. In the legacy program this sets
 * {@code R7919999-Return-Abend}, appends the failing paragraph to the
 * module/call-stack trace table, and GOBACKs - there is no way for the caller
 * to continue processing, so this is modeled as an unchecked exception rather
 * than a result/status code.
 *
 * <p>Raised by exactly four call sites (all of which build the abend message
 * via Z000-Error-Message first, F115L070.src:954-972):
 * <ul>
 *   <li>D101 - RULE-020, TF-SY-INCONSISTENCY-VAR1 (F115L070.src:349-356)</li>
 *   <li>D201 - RULE-014, TF-SY-DUPLICATE-CHARGE, same charge twice on Part_Charge (F115L070.src:388-396)</li>
 *   <li>D202 - RULE-014, TF-SY-DUPLICATE-CHARGE, same charge twice on Main_Charge (F115L070.src:400-408)</li>
 *   <li>C001 - invalid R115L070-In-Action value, GL-IN-AKSJON-UV (F115L070.src:172-178)</li>
 * </ul>
 */
public class ChargeMergeAbendException extends RuntimeException {

    private final String paragraph;
    private final String legacyMessageCode;
    private final String variableData;
    private final String resolvedMessageText;

    public ChargeMergeAbendException(String paragraph, String legacyMessageCode, String variableData,
                                      String resolvedMessageText) {
        super("F115L070 abend at " + paragraph + ": " + legacyMessageCode + " (VAR1=" + variableData + ")"
                + (resolvedMessageText != null ? " - " + resolvedMessageText : ""));
        this.paragraph = paragraph;
        this.legacyMessageCode = legacyMessageCode;
        this.variableData = variableData;
        this.resolvedMessageText = resolvedMessageText;
    }

    /** R7919999-Paragraph, e.g. "D101". */
    public String paragraph() {
        return paragraph;
    }

    /** R7918030-In-MessageCode, e.g. "TF-SY-DUPLICATE-CHARGE". */
    public String legacyMessageCode() {
        return legacyMessageCode;
    }

    /** R7918030-In-Variable-Data(1) - every abend site uses exactly one variable, keyed 'VAR1'. */
    public String variableData() {
        return variableData;
    }

    /**
     * R7919999-Message as resolved via {@code ErrorMessageService} (F7918030),
     * or {@code null} if resolution itself failed - a message-repository
     * problem must not mask the underlying abend (see
     * {@code ChargeMergeServiceImpl#raiseAbend}).
     */
    public String resolvedMessageText() {
        return resolvedMessageText;
    }
}
