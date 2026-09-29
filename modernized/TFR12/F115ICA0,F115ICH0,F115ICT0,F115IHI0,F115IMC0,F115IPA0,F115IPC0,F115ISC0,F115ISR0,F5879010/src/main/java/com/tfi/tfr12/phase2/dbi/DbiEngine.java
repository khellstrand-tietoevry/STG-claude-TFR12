package com.tfi.tfr12.phase2.dbi;

import java.util.List;
import java.util.Optional;

/**
 * The one generic pattern replacing the 9 near-identical legacy DBI modules
 * (F115ICA0, F115ICH0, F115ICT0, F115IHI0, F115IMC0, F115IPA0, F115IPC0,
 * F115ISC0, F115ISR0): each table's own H100-Check-Qualification key
 * validation ({@link KeyPolicy}) and C000-Interface action dispatch
 * ({@link Capabilities}) plug into this shared engine instead of being
 * reimplemented per table.
 *
 * <p>Key validation (RULE-018) runs before Select, Update and Delete, but
 * deliberately not before Insert, matching the legacy asymmetry (RULE-016).
 * Action-capability checks (RULE-027, RULE-028) run before validation, since
 * the legacy dispatcher rejects a disabled action outright rather than
 * reaching its qualification check.
 *
 * @param <K> the table's natural key
 * @param <R> the table's row type
 */
public final class DbiEngine<K, R> {
    private final KeyedRepository<K, R> repository;
    private final KeyPolicy<K, R> keyPolicy;
    private final Capabilities capabilities;
    private final FinInstResolver finInstResolver;

    public DbiEngine(KeyedRepository<K, R> repository, KeyPolicy<K, R> keyPolicy,
                      Capabilities capabilities, FinInstResolver finInstResolver) {
        this.repository = repository;
        this.keyPolicy = keyPolicy;
        this.capabilities = capabilities;
        this.finInstResolver = finInstResolver;
    }

    /**
     * @param keyRecord a record carrying (at least) the natural-key fields;
     *                  {@link KeyPolicy#resolveKey} both validates it and
     *                  resolves any external Fin-Inst-No before this engine
     *                  ever calls the repository — the key actually looked up
     *                  is always the resolved one, never a separately
     *                  caller-supplied key that could disagree with it.
     */
    public Optional<R> selectOne(R keyRecord) {
        requireCapability(capabilities.select(), "SELECT");
        K resolvedKey = keyPolicy.resolveKey(keyRecord, finInstResolver);
        return repository.selectOne(resolvedKey);
    }

    public List<R> getRange(K partialKey) {
        requireCapability(capabilities.cursor(), "CURSOR");
        return repository.getRange(partialKey);
    }

    public void insert(R record) {
        requireCapability(capabilities.insert(), "INSERT");
        repository.insert(record);
    }

    public void update(R record) {
        requireCapability(capabilities.update(), "UPDATE");
        keyPolicy.resolveKey(record, finInstResolver);
        repository.update(record);
    }

    /** See {@link #selectOne} for why this takes a record, not a bare key. */
    public void delete(R keyRecord) {
        requireCapability(capabilities.delete(), "DELETE");
        K resolvedKey = keyPolicy.resolveKey(keyRecord, finInstResolver);
        repository.delete(resolvedKey);
    }

    private static void requireCapability(boolean enabled, String action) {
        if (!enabled) {
            throw new DbiActionNotSupportedException(action);
        }
    }
}
