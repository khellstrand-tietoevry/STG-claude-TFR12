package com.tfi.tfr12.phase1.errormessage;

/**
 * A single lookup key into the PO_Feilmelding table (or its in-memory copybook
 * mirror, R791ERDL), mirroring W-R791ERDC-Key / the POFM-* host variables used
 * by F7918030.src D310-SQL.
 *
 * @param organizationId the FK_OAKO_ID qualifier (0 = "no specific organization",
 *                        the fallback value the legacy code retries with)
 * @param messageCode    the message code being looked up (POFM_Meldingskode)
 * @param language       ISO language code (POFM_Spraak); the legacy default is
 *                        "NO" when the caller supplies spaces
 *                        (F7918030.src D100-Les-Feilmelding)
 */
public record MessageKey(long organizationId, String messageCode, String language) {
}
