package com.tfi.tfr12.phase1.trace;

/**
 * Stands in for the legacy F7919220 ("job/environment information") and, for
 * CICS, F791CICS (EIB accessor) calls that F791TRAC.src B000-Initialize makes.
 * Neither of those external programs exists in this repository, so the
 * contract takes this pre-resolved value instead of detecting the environment
 * itself.
 *
 * <p><b>Quirk #1 worth pinning (F791TRAC.src B000-Initialize, lines 126-154):</b>
 * the trace header's "Miljo" (environment) field is set to the literal string
 * {@code "CICS"} only when running under CICS. Both {@link EnvironmentKind#IMS}
 * and {@link EnvironmentKind#BATCH} produce the literal string {@code "BATCH"}
 * in the header - IMS is NOT distinguished from plain batch in the trace
 * record, even though F7919220 itself can tell them apart.
 *
 * <p><b>Quirk #2 worth pinning (compare F791TRAC.src B000-Initialize line 130
 * against E000-Trace-Logg line 325):</b> the trace HEADER's "Jobbnavn" field
 * is populated from {@link #cicsId()} when running under CICS (and from
 * {@link #jobName()} for IMS/BATCH) - but the separate MQ HEADER's
 * "Arbeidsstasjon" (workstation) field is ALWAYS populated from
 * {@link #jobName()}, regardless of environment. Under CICS these two fields
 * therefore end up holding two genuinely different values in the same trace
 * write: the trace header carries the CICS system id, while the MQ envelope
 * carries the batch/job name.
 *
 * @param kind                 which of the three environments this is
 * @param jobName              R7919220-Ut-Jobb-Navn; always used for the MQ
 *                             header's "Arbeidsstasjon" field, and also used
 *                             as the trace header's Jobbnavn for IMS/BATCH
 * @param cicsId               R7919220-Ut-Cics-Id (4 chars); used as the trace
 *                             header's Jobbnavn ONLY when {@code kind == CICS};
 *                             ignored for IMS/BATCH
 * @param loadName             R7919220-Ut-Load-Navn, used as the MQ header's
 *                             "Brukerid" regardless of environment kind
 * @param cicsTransactionId    R791CICS-Ut-Eibtrnid; ignored for IMS/BATCH
 * @param cicsTaskNumber       R791CICS-Ut-Eibtaskn; ignored for IMS/BATCH
 */
public record RuntimeEnvironmentInfo(
        EnvironmentKind kind,
        String jobName,
        String cicsId,
        String loadName,
        String cicsTransactionId,
        int cicsTaskNumber) {
}
