package com.tfi.tfr12.phase1.errormessage;

/**
 * Output of {@link ErrorMessageService#lookupMessage}, mirroring
 * R7918030-Ut-Meldingskode / R7918030-Ut-Meldingstekst.
 *
 * @param messageCode the resolved message code (unchanged from the request,
 *                     unless the lookup itself failed - see
 *                     {@link ErrorMessageService} for the "GL-SYSTEM-FEIL" case)
 * @param messageText the fully-resolved, variable-substituted message text,
 *                     or one of the legacy hardcoded fallback strings when the
 *                     lookup could not find a row
 */
public record ErrorMessageResult(String messageCode, String messageText) {
}
