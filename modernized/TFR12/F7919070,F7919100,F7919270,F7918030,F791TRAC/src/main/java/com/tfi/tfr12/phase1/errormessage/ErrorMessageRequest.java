package com.tfi.tfr12.phase1.errormessage;

import java.util.List;

/**
 * Input to {@link ErrorMessageService#lookupMessage}, mirroring the
 * caller-supplied portion of R7918030-Feilmelding.
 *
 * @param messageCode        the message code to resolve (R7918030-In-Meldingskode)
 * @param language           ISO language code, or blank to mean "use default NO"
 *                           (R7918030-In-Spraak)
 * @param organizationId     the FK_OAKO_ID qualifier (R7918030-In-Konsern-Nr)
 * @param variables          symbolic-variable substitutions to apply to the
 *                           resolved template (R7918030-In-Variabel-Tabell)
 * @param alreadyFormatted   if true, {@code preformattedText} is used verbatim
 *                           and no table/DB2 lookup is performed at all
 *                           (R7918030-In-Ferdig-Melding, checked in C000-Interface)
 * @param preformattedText   the already-formatted text to use when
 *                           {@code alreadyFormatted} is true
 */
public record ErrorMessageRequest(
        String messageCode,
        String language,
        long organizationId,
        List<MessageVariable> variables,
        boolean alreadyFormatted,
        String preformattedText) {
}
