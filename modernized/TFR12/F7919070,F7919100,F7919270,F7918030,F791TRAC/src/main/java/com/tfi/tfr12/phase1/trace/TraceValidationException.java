package com.tfi.tfr12.phase1.trace;

/**
 * Raised for a missing required {@link TraceRequest} field, mirroring
 * F791TRAC.src B100-Kontroll's abends (paragraph codes B101-B104).
 */
public class TraceValidationException extends RuntimeException {

    private final String legacyParagraph;

    public TraceValidationException(String legacyParagraph, String message) {
        super(message);
        this.legacyParagraph = legacyParagraph;
    }

    /** e.g. "B101" for a missing {@link TraceRequest#program()}. */
    public String legacyParagraph() {
        return legacyParagraph;
    }
}
