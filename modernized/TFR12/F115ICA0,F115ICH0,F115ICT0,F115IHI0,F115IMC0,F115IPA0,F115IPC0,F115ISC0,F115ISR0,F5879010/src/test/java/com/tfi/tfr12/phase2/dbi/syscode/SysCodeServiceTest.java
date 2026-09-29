package com.tfi.tfr12.phase2.dbi.syscode;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiActionNotSupportedException;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the {@code TF_Sys_Code} DBI module (legacy
 * F115ISC0.src), pinning {@link SysCodeKeyPolicy} (RULE-017) and its wiring
 * into the shared {@link DbiEngine}.
 *
 * <p>These values were hand-traced from {@code legacy/TFR12/src/F115ISC0.src}
 * (H100-Check-Qualification, lines 739-815) and
 * {@code legacy/TFR12/copybooks/R115ISC0.copy} - F115ISC0 requires a DB2
 * precompiler that is not available in this environment, so nothing here was
 * captured from a running legacy program. The DB2 access itself
 * ({@code KeyedRepository}) is faked in-memory; only the key-validation logic
 * that legacy actually implements in COBOL (not in DB2) is under test.
 *
 * <p>No real credentials appear anywhere in this file; the fake repository
 * holds only plain in-memory data, no connection strings.
 */
class SysCodeServiceTest {

    /** Always resolves to a fixed fake internal id - no real org-data lookup. */
    private static final FinInstResolver FAKE_RESOLVER = finInstNo -> {
        if (finInstNo == null || finInstNo.isBlank()) {
            throw new MissingKeyFieldException("TF-FIN-INST-MISSING");
        }
        return 90000001L; // fake internal id, shape-only, not a real value
    };

    private final SysCodeKeyPolicy policy = new SysCodeKeyPolicy();

    private static SysCodeRow row(String stType, long finInstId, String finInstNo,
                                   String contrType, String scCode) {
        return new SysCodeRow(stType, finInstId, finInstNo, contrType, scCode,
                "Representative description", "RAWPROPERTYBLOB", "CONTRACTNM");
    }

    @Nested
    @DisplayName("ST-Type validation (F115ISC0.src:741-755) - always runs")
    class StTypeValidation {

        @Test
        @DisplayName("blank ST-Type throws TF-TYPE-MISSING")
        void blankStTypeThrowsTfTypeMissing() {
            SysCodeRow r = row("", 90000001L, "", "ILC", "SC01");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(r, FAKE_RESOLVER));

            assertEquals("TF-TYPE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("null ST-Type throws TF-TYPE-MISSING (collapsed blank/null)")
        void nullStTypeThrowsTfTypeMissing() {
            SysCodeRow r = row(null, 90000001L, "", "ILC", "SC01");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(r, FAKE_RESOLVER));

            assertEquals("TF-TYPE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("non-blank ST-Type passes this check and validation proceeds")
        void nonBlankStTypePasses() {
            SysCodeRow r = row("TF", 90000001L, "", "ILC", "SC01");

            // Should not throw at all: every field is present.
            policy.resolveKey(r, FAKE_RESOLVER);
        }
    }

    @Nested
    @DisplayName("Institution validation (F115ISC0.src:757-781) - always runs")
    class InstitutionValidation {

        @Test
        @DisplayName("Fin-Inst-Id already resolved (non-zero) passes without calling the resolver")
        void finInstIdAlreadySetPasses() {
            FinInstResolver spyResolver = finInstNo -> {
                throw new AssertionError("resolver should not be called when finInstId is already set");
            };
            SysCodeRow r = row("TF", 90000001L, "", "ILC", "SC01");

            policy.resolveKey(r, spyResolver);
        }

        @Test
        @DisplayName("Fin-Inst-Id zero but Fin-Inst-No present resolves via FinInstResolver")
        void finInstIdResolvedFromFinInstNo() {
            SysCodeRow r = row("TF", 0L, "000123456789012345", "ILC", "SC01");

            // Resolver returns a value and does not throw -> validate() succeeds.
            policy.resolveKey(r, FAKE_RESOLVER);
        }

        @Test
        @DisplayName("Fin-Inst-Id zero and Fin-Inst-No blank throws TF-FIN-INST-MISSING")
        void finInstIdZeroAndFinInstNoBlankThrows() {
            SysCodeRow r = row("TF", 0L, "", "ILC", "SC01");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(r, FAKE_RESOLVER));

            assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("Fin-Inst-No present but unresolvable propagates the resolver's TF-FIN-INST-MISSING")
        void unresolvableFinInstNoPropagates() {
            FinInstResolver alwaysFails = finInstNo -> {
                throw new MissingKeyFieldException("TF-FIN-INST-MISSING");
            };
            SysCodeRow r = row("TF", 0L, "999999999999999999", "ILC", "SC01");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(r, alwaysFails));

            assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
        }
    }

    @Nested
    @DisplayName("RULE-017: Contract-Type / SC-Code validation asymmetry (F115ISC0.src:783-815)")
    class Rule017Asymmetry {

        @Test
        @DisplayName("rule017_blankContrType_and_blankScCode_reportsTfCodeMissing: "
                + "when BOTH are blank, the nested SC-Code check's later "
                + "Z000-Error-Message call overwrites the Contract-Type one, so "
                + "TF-CODE-MISSING (not TF-CONTRTYP-MISSING) is what surfaces")
        void rule017_blankContrType_and_blankScCode_reportsTfCodeMissing() {
            SysCodeRow r = row("TF", 90000001L, "", "", "");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(r, FAKE_RESOLVER));

            assertEquals("TF-CODE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule017_blankContrType_nonBlankScCode_reportsTfContrTypeMissing: "
                + "Contract-Type's own error is reported when SC-Code happens to be present")
        void rule017_blankContrType_nonBlankScCode_reportsTfContrTypeMissing() {
            SysCodeRow r = row("TF", 90000001L, "", "", "SC01");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(r, FAKE_RESOLVER));

            assertEquals("TF-CONTRTYP-MISSING", ex.legacyErrorCode());
        }

        /**
         * RULE-017 - the confirmed-preserved defect itself.
         *
         * <p>Verified directly against legacy/TFR12/src/F115ISC0.src:783-815: the
         * Contract-Type Evaluate opened at line 783 has NO closing
         * {@code End-Evaluate} before the next {@code Evaluate True} for
         * SC-Code begins at line 799 - the only {@code End-Evaluate} in that
         * span (line 813) closes the nested SC-Code evaluate, and the outer
         * Contract-Type evaluate is only closed implicitly by the paragraph's
         * period at line 815. Because of that missing End-Evaluate, the
         * mandatory SC-Code check (lines 799-813) is syntactically nested
         * inside the Contract-Type block's "When other" arm (790-796), so it
         * is UNREACHABLE whenever Contract-Type resolves through a
         * non-error branch - the normal case.
         *
         * <p>The system owner was explicitly asked whether to fix this or
         * preserve it, and chose to preserve it. This test proves the Java
         * port reproduces that exact gap: a blank SC-Code with a
         * present/resolvable Contract-Type raises NO exception at all, even
         * though SC-Code is part of the natural key and is blank. This is
         * RULE-017's confirmed-preserved defect being pinned - it is not a
         * bug in {@link SysCodeKeyPolicy}, and must not be "fixed" here.
         */
        @Test
        @DisplayName("rule017_contrTypeResolvesNormally_blankScCode_isNotValidated: "
                + "the preserved gap - SC-Code is never checked once Contract-Type resolves")
        void rule017_contrTypeResolvesNormally_blankScCode_isNotValidated() {
            SysCodeRow r = row("TF", 90000001L, "", "ILC", "");

            // Must NOT throw: this is the confirmed-preserved RULE-017 gap.
            policy.resolveKey(r, FAKE_RESOLVER);
        }

        @Test
        @DisplayName("rule017_contrTypeResolvesNormally_nonBlankScCode_alsoPasses: "
                + "the ordinary, non-buggy case for completeness")
        void rule017_contrTypeResolvesNormally_nonBlankScCode_alsoPasses() {
            SysCodeRow r = row("TF", 90000001L, "", "ILC", "SC01");

            policy.resolveKey(r, FAKE_RESOLVER);
        }
    }

    // ------------------------------------------------------------------
    // DbiEngine<SysCodeKey, SysCodeRow> wiring, with an in-memory fake
    // KeyedRepository standing in for the (unavailable) DB2 access.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("DbiEngine<SysCodeKey, SysCodeRow> wiring")
    class EngineWiring {

        private InMemorySysCodeRepository repository;
        private DbiEngine<SysCodeKey, SysCodeRow> engine;

        @BeforeEach
        void setUp() {
            repository = new InMemorySysCodeRepository();
            engine = new DbiEngine<>(repository, policy, Capabilities.FULL, FAKE_RESOLVER);
        }

        @Test
        @DisplayName("update() runs key validation before touching the repository - "
                + "invalid key never reaches KeyedRepository.update")
        void updateValidatesKeyBeforeCallingRepository() {
            SysCodeRow invalid = row("", 90000001L, "", "ILC", "SC01"); // blank ST-Type

            assertThrows(MissingKeyFieldException.class, () -> engine.update(invalid));

            assertTrue(repository.updated.isEmpty(),
                    "repository.update must not be called when key validation fails");
        }

        @Test
        @DisplayName("update() with a fully valid key reaches the repository and persists")
        void updateWithValidKeyReachesRepository() {
            SysCodeRow valid = row("TF", 90000001L, "", "ILC", "SC01");

            engine.update(valid);

            assertEquals(1, repository.updated.size());
            assertEquals(valid, repository.updated.get(0));
        }

        @Test
        @DisplayName("RULE-017 wired end-to-end: update() with blank SC-Code but a "
                + "resolved Contract-Type still reaches the repository (preserved gap)")
        void rule017_updateWithBlankScCode_stillReachesRepository() {
            SysCodeRow blankScCode = row("TF", 90000001L, "", "ILC", "");

            engine.update(blankScCode);

            assertEquals(1, repository.updated.size());
            assertEquals("", repository.updated.get(0).scCode());
        }

        @Test
        @DisplayName("insert() performs no key validation at all (RULE-016 asymmetry: "
                + "Insert takes its key wholly from input, unlike Update)")
        void insertSkipsKeyValidationEntirely() {
            SysCodeRow noKeyAtAll = new SysCodeRow("", 0L, "", "", "",
                    "desc", "prop", "name");

            // Must not throw, even though every key field is blank.
            engine.insert(noKeyAtAll);

            assertEquals(1, repository.inserted.size());
        }

        @Test
        @DisplayName("selectOne() validates the key record, then looks up by SysCodeKey")
        void selectOneValidatesThenLooksUp() {
            SysCodeKey key = new SysCodeKey("TF", 90000001L, "ILC", "SC01");
            SysCodeRow stored = row("TF", 90000001L, "", "ILC", "SC01");
            repository.seed(key, stored);

            Optional<SysCodeRow> result = engine.selectOne(stored);

            assertTrue(result.isPresent());
            assertEquals(stored, result.get());
        }

        @Test
        @DisplayName("selectOne() with an invalid key record throws before querying the repository")
        void selectOneWithInvalidKeyRecordThrowsBeforeQuery() {
            SysCodeKey key = new SysCodeKey("TF", 90000001L, "ILC", "SC01");
            SysCodeRow invalidQualifier = row("", 90000001L, "", "ILC", "SC01"); // blank ST-Type

            assertThrows(MissingKeyFieldException.class, () -> engine.selectOne(invalidQualifier));

            assertFalse(repository.selectOneCalled, "repository must not be queried when validation fails");
        }

        @Test
        @DisplayName("delete() on a table with Capabilities.FULL delegates to the repository")
        void deleteDelegatesToRepository() {
            SysCodeKey key = new SysCodeKey("TF", 90000001L, "ILC", "SC01");
            SysCodeRow keyRecord = row("TF", 90000001L, "", "ILC", "SC01");

            engine.delete(keyRecord);

            assertEquals(List.of(key), repository.deleted);
        }

        @Test
        @DisplayName("update() on a capability-restricted engine throws DbiActionNotSupportedException "
                + "before key validation even runs (mirrors GL-IN-AKSJON-UV)")
        void updateNotSupportedThrowsBeforeValidation() {
            DbiEngine<SysCodeKey, SysCodeRow> readOnlyEngine =
                    new DbiEngine<>(repository, policy,
                            new Capabilities(true, true, true, false, true),
                            FAKE_RESOLVER);
            SysCodeRow invalid = row("", 0L, "", "", ""); // would also fail validation

            assertThrows(DbiActionNotSupportedException.class, () -> readOnlyEngine.update(invalid));
            assertTrue(repository.updated.isEmpty());
        }
    }

    /** In-memory fake standing in for the unavailable DB2 access (F115ISCR/F115ISCU). */
    private static final class InMemorySysCodeRepository implements KeyedRepository<SysCodeKey, SysCodeRow> {
        private final Map<SysCodeKey, SysCodeRow> table = new HashMap<>();
        private final List<SysCodeRow> inserted = new ArrayList<>();
        private final List<SysCodeRow> updated = new ArrayList<>();
        private final List<SysCodeKey> deleted = new ArrayList<>();
        private boolean selectOneCalled;

        void seed(SysCodeKey key, SysCodeRow row) {
            table.put(key, row);
        }

        @Override
        public Optional<SysCodeRow> selectOne(SysCodeKey key) {
            selectOneCalled = true;
            return Optional.ofNullable(table.get(key));
        }

        @Override
        public List<SysCodeRow> getRange(SysCodeKey partialKey) {
            return List.copyOf(table.values());
        }

        @Override
        public void insert(SysCodeRow record) {
            inserted.add(record);
            table.put(new SysCodeKey(record.stType(), record.finInstId(), record.contrType(), record.scCode()), record);
        }

        @Override
        public void update(SysCodeRow record) {
            updated.add(record);
            table.put(new SysCodeKey(record.stType(), record.finInstId(), record.contrType(), record.scCode()), record);
        }

        @Override
        public void delete(SysCodeKey key) {
            deleted.add(key);
            table.remove(key);
        }
    }
}
