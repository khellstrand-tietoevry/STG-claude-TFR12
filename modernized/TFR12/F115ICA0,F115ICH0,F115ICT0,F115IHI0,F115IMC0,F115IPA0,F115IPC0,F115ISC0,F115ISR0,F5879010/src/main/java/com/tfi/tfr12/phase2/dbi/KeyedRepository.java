package com.tfi.tfr12.phase2.dbi;

import java.util.List;
import java.util.Optional;

/**
 * Port standing in for a table's real DB2 access (no DB2 precompiler is
 * available in this environment — see TRANSFORMATION_NOTES.md). A real
 * implementation is a Phase-3-or-later concern; this module only pins the
 * per-table key-validation and capability contract that {@link DbiEngine}
 * enforces around it.
 *
 * @param <K> the table's natural key
 * @param <R> the table's row type
 */
public interface KeyedRepository<K, R> {
    /** Single-row lookup by full key (legacy "Select"). */
    Optional<R> selectOne(K key);

    /** Qualified multi-row read (legacy Get-1st + Get-Next combined for testability). */
    List<R> getRange(K partialKey);

    void insert(R record);

    void update(R record);

    void delete(K key);
}
