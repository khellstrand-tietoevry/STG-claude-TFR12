package com.tfi.tfr12.phase1.errormessage;

import java.util.List;

/**
 * Contract for the modern replacement of legacy program F7918030.src
 * ("Interface for henting av korrekt feilmelding" - interface for fetching the
 * correct error message).
 *
 * <p>Lookup fallback order (F7918030.src D200-Les-Copy / D300-Les-DB2, both
 * follow the identical sequence against their respective source): given
 * (organizationId, language) as supplied,
 * <ol>
 *   <li>try the exact (organizationId, language) key;</li>
 *   <li>if not found and organizationId != 0, retry with organizationId = 0;</li>
 *   <li>if still not found and language != "NO", retry the ORIGINAL organizationId
 *       with language = "NO";</li>
 *   <li>if still not found, and (organizationId != 0 and language != "NO"),
 *       retry with organizationId = 0 AND language = "NO".</li>
 * </ol>
 * Note each retry is independent (always relative to the original request's
 * organizationId/language, not to the previous attempt) - this is exactly what
 * the legacy code does by re-reading the original W-Konsern-Nr/W-Spraak working
 * fields before each attempt.
 *
 * <p>Fallback-text handling (F7918030.src D000-Hent-Feilmelding) distinguishes
 * TWO distinct failure modes, each with its own text, and the CICS/batch
 * distinction applies to only ONE of them - this is a genuine legacy quirk,
 * not a simplification:
 * <ul>
 *   <li><b>Repository failure</b> (a {@link MessageRepositoryFailureException}
 *       from any lookup attempt - the legacy "Retur-Abend" branch): ALWAYS
 *       yields {@link #NOT_FOUND_TEXT_BATCH} (the comma variant), even when
 *       {@code cicsEnvironment} is true. There is no CICS-specific text for
 *       this branch in the legacy source.</li>
 *   <li><b>Not found</b> (every fallback attempt returned empty, no exception -
 *       the legacy "Retur-Feil" branch): yields {@link #NOT_FOUND_TEXT_BATCH}
 *       when {@code cicsEnvironment} is false, or {@link #NOT_FOUND_TEXT_CICS}
 *       (the period variant) when {@code cicsEnvironment} is true.</li>
 * </ul>
 */
public interface ErrorMessageService {

    /**
     * Fallback text (comma) used for: (a) "not found" in a non-CICS
     * environment, AND (b) any repository failure, in EVERY environment
     * including CICS.
     */
    String NOT_FOUND_TEXT_BATCH = "En systemfeil har oppstått, kontakt FD. ";

    /**
     * Fallback text (period, not a typo - this is what the legacy source has)
     * used ONLY for "not found" while {@code cicsEnvironment} is true. Never
     * used for a repository failure, even under CICS.
     */
    String NOT_FOUND_TEXT_CICS = "En systemfeil har oppstått. kontakt FD. ";

    /**
     * @param request     the message code/language/organization to resolve, plus
     *                    any symbolic-variable substitutions
     * @param repository  the message-text source(s) to search, in the fallback
     *                    order documented on this interface
     * @param cicsEnvironment whether the caller is running under CICS (affects
     *                    only the not-found fallback text, mirroring
     *                    R7919220-Ut-CICS; has no effect on the repository-failure
     *                    fallback text - see the class-level note above)
     * @return the resolved (or fallback) message code/text
     */
    ErrorMessageResult lookupMessage(ErrorMessageRequest request, MessageRepository repository, boolean cicsEnvironment);

    /**
     * Expands {@code &key#} symbolic variable placeholders in {@code template}
     * using {@code variables}, mirroring F7918030.src E100-Bytt-Ut-Variabler.
     *
     * <p>Key edge cases the legacy scanner (E110/E120/E130/E140/E150) handles:
     * <ul>
     *   <li>An unresolved key (no matching entry in {@code variables}, or a
     *       matching entry whose value is entirely blank/low-values) is left in
     *       the output as the literal characters {@code &key} immediately
     *       followed by a literal {@code #} - it is NOT dropped, and NOT
     *       replaced with empty string.</li>
     *   <li>A bare {@code &} that is never closed with a {@code #} before the
     *       end of the template (a "false start") is emitted literally, along
     *       with whatever characters were accumulated after it.</li>
     *   <li>Trailing spaces AND trailing low-values in a variable's value are
     *       trimmed before substitution; an all-blank/all-low-values value is
     *       treated the same as "key not found".</li>
     * </ul>
     *
     * @param template  the message text, possibly containing {@code &key#} placeholders
     * @param variables the symbolic-variable substitutions to apply
     * @return the expanded text, and whether any placeholder failed to resolve
     */
    VariableSubstitutionResult substituteVariables(String template, List<MessageVariable> variables);
}
