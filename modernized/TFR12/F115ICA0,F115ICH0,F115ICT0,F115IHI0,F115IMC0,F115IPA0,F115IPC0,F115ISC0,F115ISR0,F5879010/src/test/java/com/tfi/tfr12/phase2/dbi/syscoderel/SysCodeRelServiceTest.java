package com.tfi.tfr12.phase2.dbi.syscoderel;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiActionNotSupportedException;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * Characterization tests for the {@code TF_Sys_Code_Rel} DBI module (legacy
 * F115ISR0.src), pinning RULE-027: relationships can only be created or
 * deleted, never updated in place.
 *
 * <p>These values were hand-traced from
 * {@code legacy/STG/TFR12/src/F115ISR0.src} (C000-Interface, lines 326-362;
 * G000-Insert/G100-Move-Input-To-DCL, 865-905; I000-Delete, 1174-1181;
 * F000-Select/F100-Prepare-Qualification, 741-837; D000-Get-1st and
 * D300-Select-Cursor, 370-380 / 468-596) and
 * {@code legacy/STG/TFR12/copybooks/R115ISR0.copy}. F115ISR0 requires a DB2
 * precompiler that is not available in this environment, so nothing here was
 * captured from a running legacy program - these are hand-traced
 * characterization cases, not recorded runs. The DB2 access itself
 * ({@code KeyedRepository}) is faked in-memory; only the dispatch/capability
 * and key-validation logic that legacy actually implements in COBOL (not in
 * DB2) is under test.
 *
 * <p>No real credentials appear anywhere in this file; the fake repository
 * holds only plain in-memory data, no connection strings.
 */
class SysCodeRelServiceTest {

    /** Always resolves to a fixed fake internal id - no real org-data lookup. */
    private static final FinInstResolver FAKE_RESOLVER = finInstNo -> 90000001L;

    private static SysCodeRelRow row(long finInstId, String fromStType, String fromContrType,
                                      String fromScCode, String toStType, String toContrType,
                                      String toScCode, String relationType) {
        return new SysCodeRelRow(finInstId, fromStType, fromContrType, fromScCode,
                toStType, toContrType, toScCode, relationType, "");
    }

    @Nested
    @DisplayName("RULE-027: Capabilities.CREATE_DELETE_ONLY matches F115ISR0's C000-Interface dispatch")
    class CapabilitiesMatchDispatch {

        @Test
        @DisplayName("rule027_createDeleteOnly_fieldValuesMatchDispatch: select/cursor/insert/delete "
                + "are live dispatch arms (F115ISR0.src:329-349), update is the only disabled one "
                + "(commented out at F115ISR0.src:345-346)")
        void rule027_createDeleteOnly_fieldValuesMatchDispatch() {
            Capabilities caps = Capabilities.CREATE_DELETE_ONLY;

            assertTrue(caps.select(), "R115ISR0-In-Select is a live dispatch arm (F115ISR0.src:338-340)");
            assertTrue(caps.cursor(), "R115ISR0-In-Get-1st/-Get-Next are live dispatch arms (F115ISR0.src:329-336)");
            assertTrue(caps.insert(), "R115ISR0-In-Insert is a live dispatch arm (F115ISR0.src:342-343)");
            assertFalse(caps.update(), "R115ISR0-In-Update is commented out of both the copybook "
                    + "(R115ISR0.copy:69) and C000-Interface's dispatch (F115ISR0.src:345-346)");
            assertTrue(caps.delete(), "R115ISR0-In-Delete is a live dispatch arm (F115ISR0.src:348-349)");
        }
    }

    @Nested
    @DisplayName("Select/Delete qualification (F100-Prepare-Qualification, F115ISR0.src:798-837): "
            + "no active requirement")
    class KeyPolicyNoActiveRequirement {

        private final SysCodeRelKeyPolicy policy = new SysCodeRelKeyPolicy();

        @Test
        @DisplayName("fully qualified row passes validate() without throwing")
        void fullyQualifiedRowPasses() {
            SysCodeRelRow r = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");

            policy.resolveKey(r, FAKE_RESOLVER);
        }

        @Test
        @DisplayName("wholly blank/zero row still passes validate(): F100-Prepare-Qualification "
                + "has no Evaluate/error-message/abend for a missing key field, unlike H100 "
                + "(which is unreachable dead code for this table - RULE-027)")
        void whollyBlankRowStillPasses() {
            SysCodeRelRow r = row(0L, "", "", "", "", "", "", "");

            // Must NOT throw: confirmed no active qualification requirement.
            policy.resolveKey(r, FAKE_RESOLVER);
        }
    }

    // ------------------------------------------------------------------
    // DbiEngine<SysCodeRelKey, SysCodeRelRow> wiring, with an in-memory fake
    // KeyedRepository standing in for the (unavailable) DB2 access.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("DbiEngine<SysCodeRelKey, SysCodeRelRow> wiring with Capabilities.CREATE_DELETE_ONLY")
    class EngineWiring {

        private InMemorySysCodeRelRepository repository;
        private DbiEngine<SysCodeRelKey, SysCodeRelRow> engine;

        @BeforeEach
        void setUp() {
            repository = new InMemorySysCodeRelRepository();
            engine = new DbiEngine<>(repository, new SysCodeRelKeyPolicy(),
                    Capabilities.CREATE_DELETE_ONLY, FAKE_RESOLVER);
        }

        @Test
        @DisplayName("rule027_update_throwsDbiActionNotSupportedException: mirrors "
                + "C000-Interface's 'When Other' abending on GL-IN-AKSJON-UV "
                + "(F115ISR0.src:351-358) because R115ISR0-In-Update is unreachable "
                + "(F115ISR0.src:345-346)")
        void rule027_update_throwsDbiActionNotSupportedException() {
            SysCodeRelRow anyRow = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");

            assertThrows(DbiActionNotSupportedException.class, () -> engine.update(anyRow));

            assertTrue(repository.updated.isEmpty(), "the repository must never be reached for Update");
        }

        @Test
        @DisplayName("rule027_insert_worksNormally: mirrors G000-Insert (F115ISR0.src:865-873)")
        void rule027_insert_worksNormally() {
            SysCodeRelRow toInsert = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");

            engine.insert(toInsert);

            assertEquals(List.of(toInsert), repository.inserted);
        }

        @Test
        @DisplayName("rule027_delete_worksNormally: mirrors I000-Delete (F115ISR0.src:1174-1181)")
        void rule027_delete_worksNormally() {
            SysCodeRelKey key = new SysCodeRelKey(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            SysCodeRelRow keyRecord = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            repository.seed(key, keyRecord);

            engine.delete(keyRecord);

            assertEquals(List.of(key), repository.deleted);
            assertTrue(repository.selectOne(key).isEmpty());
        }

        @Test
        @DisplayName("rule027_selectOne_worksNormally: mirrors F000-Select (F115ISR0.src:741-796)")
        void rule027_selectOne_worksNormally() {
            SysCodeRelKey key = new SysCodeRelKey(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            SysCodeRelRow stored = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            repository.seed(key, stored);

            Optional<SysCodeRelRow> result = engine.selectOne(stored);

            assertTrue(result.isPresent());
            assertEquals(stored, result.get());
        }

        @Test
        @DisplayName("rule027_getRange_worksNormally: mirrors Get-1st + Get-Next "
                + "(D000-Get-1st/E000-Get-Next, F115ISR0.src:370-380/650-661)")
        void rule027_getRange_worksNormally() {
            SysCodeRelKey key1 = new SysCodeRelKey(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            SysCodeRelKey key2 = new SysCodeRelKey(90000001L, "OPTYPE", "TFR", "OP02", "CHTYPE", "TFR", "CH02", "OPCH");
            SysCodeRelRow row1 = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            SysCodeRelRow row2 = row(90000001L, "OPTYPE", "TFR", "OP02", "CHTYPE", "TFR", "CH02", "OPCH");
            repository.seed(key1, row1);
            repository.seed(key2, row2);

            List<SysCodeRelRow> result = engine.getRange(key1);

            assertEquals(2, result.size());
            assertTrue(result.contains(row1));
            assertTrue(result.contains(row2));
        }

        @Test
        @DisplayName("rule027_changeRelationship_workaround_isDeleteThenInsert: with no Update "
                + "path, the only way to 'change' a relationship (e.g. re-point an "
                + "Operation-Type-to-Charge-Type mapping consumed by F115L060.src:341-406 "
                + "to a different Charge-Type) is to delete the old key and insert the new one")
        void rule027_changeRelationship_workaround_isDeleteThenInsert() {
            SysCodeRelKey oldKey = new SysCodeRelKey(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            SysCodeRelRow oldRelationship = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH01", "OPCH");
            repository.seed(oldKey, oldRelationship);

            // There is no engine.update(...) path available for this table (RULE-027):
            // update() is capability-disabled, so "changing" OP01's mapping from CH01
            // to CH02 must be expressed as delete-then-insert.
            engine.delete(oldRelationship);
            SysCodeRelRow newRelationship = row(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH02", "OPCH");
            engine.insert(newRelationship);

            assertTrue(repository.selectOne(oldKey).isEmpty(), "the old relationship must be gone");
            SysCodeRelKey newKey = new SysCodeRelKey(90000001L, "OPTYPE", "TFR", "OP01", "CHTYPE", "TFR", "CH02", "OPCH");
            assertEquals(Optional.of(newRelationship), repository.selectOne(newKey));
            assertEquals(List.of(oldKey), repository.deleted);
            assertEquals(List.of(newRelationship), repository.inserted);
        }

        @Test
        @Disabled("pending RULE-027 follow-up: DbiEngine.getRange() does not call KeyPolicy.validate "
                + "at all, so D300-Select-Cursor's active requirement (Fin-Inst-Id non-zero AND "
                + "From-ST-Type non-blank, else legacy abends GL-CURSOR-UV - F115ISR0.src:520-592) "
                + "is not enforced anywhere in this Java contract. Needs a decision: either extend "
                + "DbiEngine to validate before getRange, or accept the gap and document it as an "
                + "intentional relaxation.")
        @DisplayName("rule027_getRange_underQualified_wouldAbendInLegacy_GL_CURSOR_UV")
        void rule027_getRange_underQualified_wouldAbendInLegacy_GL_CURSOR_UV() {
            // Legacy: Fin-Inst-Id unset (=0) with From-ST-Type also unset never matches
            // any of the four wired SW-Use-Fi-ST-* cursor patterns (F115ISR0.src:579-582),
            // so D300-Select-Cursor's "When other" (585-592) raises GL-CURSOR-UV and
            // performs Z900-Abend. This engine currently returns normally instead.
            SysCodeRelKey underQualified = new SysCodeRelKey(0L, "", "", "", "", "", "", "");

            assertThrows(IllegalStateException.class, () -> engine.getRange(underQualified),
                    "would need DbiEngine/KeyPolicy support for cursor-qualification checks");
        }
    }

    /** In-memory fake standing in for the unavailable DB2 access (F115ISRR/F115ISRU). */
    private static final class InMemorySysCodeRelRepository
            implements KeyedRepository<SysCodeRelKey, SysCodeRelRow> {
        private final Map<SysCodeRelKey, SysCodeRelRow> table = new HashMap<>();
        private final List<SysCodeRelRow> inserted = new ArrayList<>();
        private final List<SysCodeRelRow> updated = new ArrayList<>();
        private final List<SysCodeRelKey> deleted = new ArrayList<>();

        void seed(SysCodeRelKey key, SysCodeRelRow row) {
            table.put(key, row);
        }

        @Override
        public Optional<SysCodeRelRow> selectOne(SysCodeRelKey key) {
            return Optional.ofNullable(table.get(key));
        }

        @Override
        public List<SysCodeRelRow> getRange(SysCodeRelKey partialKey) {
            return List.copyOf(table.values());
        }

        @Override
        public void insert(SysCodeRelRow record) {
            inserted.add(record);
            table.put(record.key(), record);
        }

        @Override
        public void update(SysCodeRelRow record) {
            updated.add(record);
            table.put(record.key(), record);
        }

        @Override
        public void delete(SysCodeRelKey key) {
            deleted.add(key);
            table.remove(key);
        }
    }
}
