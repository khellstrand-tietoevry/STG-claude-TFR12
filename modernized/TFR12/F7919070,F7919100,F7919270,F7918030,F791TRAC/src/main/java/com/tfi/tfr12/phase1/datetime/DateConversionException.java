package com.tfi.tfr12.phase1.datetime;

/**
 * Raised for input the legacy module would have abended on (e.g. a calendar-
 * invalid AMD9 date such as month 13, surfaced via F7919100.src C000-Beregning's
 * call to F7919040-Valider-Dato, or an unrecognized DB2 date layout at D501).
 */
public class DateConversionException extends RuntimeException {
    public DateConversionException(String message) {
        super(message);
    }
}
