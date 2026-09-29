package com.tfi.tfr12.phase2.dbi;

/**
 * A table's mandatory-key-field validation AND key resolution, run by
 * {@link DbiEngine} before Update (and, per the same H100-Check-Qualification
 * pattern, before Select and Delete) — RULE-016 and RULE-018. Each table's
 * set of mandatory fields differs (RULE-018's own note: "the same pattern...
 * is repeated independently in every F115I*0 table-interface module"), so
 * this is implemented once per table, not by {@link DbiEngine} itself.
 *
 * <p><b>{@link #resolveKey} must return the fully-resolved key</b> — in
 * particular, when a record only carries an external Fin-Inst-No, the
 * returned key's institution field must be the ID {@code resolver} resolved
 * it to, not the caller-supplied external number. {@link DbiEngine} uses
 * exactly this returned key for the actual repository call: there is no
 * second, independent key the caller can pass that might disagree with it.
 * (An earlier version of this contract took a separate caller-supplied key
 * alongside the record and never reconciled the two — fixed after
 * architecture review caught that the resolved Fin-Inst-ID was being
 * discarded instead of reaching the repository lookup.)
 *
 * <p><b>Multi-field-missing precedence is standardized across every table:
 * "last failure wins"</b> — every mandatory field is checked (no short-circuit
 * on the first failure), matching the legacy's independent, non-exiting
 * {@code Evaluate} blocks; if more than one field is missing, the error from
 * the field checked last is what's thrown, exactly mirroring
 * H100-Check-Qualification's behavior when several fields are blank at once.
 *
 * <p>Insert is deliberately NOT covered by this policy: every table's
 * G000-Insert takes the key wholly from input and performs no equivalent
 * check (RULE-016's documented asymmetry).
 *
 * @param <K> the table's natural key
 * @param <R> the table's row type
 */
public interface KeyPolicy<K, R> {
    /**
     * @param record  the record being selected/updated/deleted
     * @param resolver resolves an external Fin-Inst-No to its internal ID when
     *                 the record only carries the external number
     * @return the fully-resolved key, safe to pass to {@link KeyedRepository}
     * @throws MissingKeyFieldException if a mandatory field is still
     *                                  unresolved after checking all of them
     *                                  (last failure wins, see class javadoc)
     */
    K resolveKey(R record, FinInstResolver resolver);
}
