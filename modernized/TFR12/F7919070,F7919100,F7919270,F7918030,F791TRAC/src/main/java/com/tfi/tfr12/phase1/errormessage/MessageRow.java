package com.tfi.tfr12.phase1.errormessage;

/**
 * A single PO_Feilmelding row, mirroring the columns F7918030.src's D310-SQL
 * selects (POFM_Meldingstekst) plus the key it was matched on.
 */
public record MessageRow(MessageKey key, String messageText) {
}
