package com.tfi.tfr12.phase1.errormessage;

import java.util.Optional;

/**
 * Port standing in for legacy F7918030.src's two-tier lookup source: first an
 * in-memory copybook mirror of the table (R791ERDL, read via D200-Les-Copy),
 * then a live DB2 read of PO_Feilmelding (D300-Les-DB2 / D310-SQL) if the copy
 * lookup misses. A real implementation is expected to compose two of these
 * (copy-first, DB2 fallback) or a single DB2-backed one; that composition is a
 * Phase 2 concern; this module only pins the per-attempt contract and the
 * fallback-key search order performed by {@link ErrorMessageService}.
 *
 * <p>SQLCODE mapping performed by F7918030.src D310-SQL: 0 -> row found;
 * +100 -> not found (return {@link Optional#empty()}); anything else -> the row
 * could not be read due to a database failure, distinct from "not found"
 * (throw {@link MessageRepositoryFailureException}).
 */
public interface MessageRepository {

    /**
     * @param key the fully-qualified lookup key (a specific organization id and language)
     * @return the matching row, or empty if no row matches that exact key
     * @throws MessageRepositoryFailureException if the underlying read failed
     *                                            for a reason other than "not found"
     */
    Optional<MessageRow> find(MessageKey key);
}
