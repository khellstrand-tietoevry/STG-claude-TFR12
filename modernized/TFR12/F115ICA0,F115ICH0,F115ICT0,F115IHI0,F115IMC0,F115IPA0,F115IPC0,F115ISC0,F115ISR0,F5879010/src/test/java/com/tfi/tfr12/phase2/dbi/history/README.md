# F115IHI0 (TF_History) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/TFR12/src/F115IHI0.src` (table `TF_History`), one of the 9
near-identical `F115I*0` table-interface modules in Phase 2's "Data Access
Layer" work. This table is the odd one out: **it is append-only** —
Select and Update are disabled entirely (RULE-028).

Covers:

- `HistoryKey` / `HistoryRow` (`src/main/java/.../dbi/history/`) — the Java
  contract for the table's natural key and row, hand-traced from
  `legacy/TFR12/copybooks/R115IHI0.copy`.
- `HistoryKeyPolicy` — a real (but deliberately empty) implementation of the
  shared `com.tfi.tfr12.phase2.dbi.KeyPolicy<HistoryRow>`. See "Key
  validation finding" below before assuming this should look like the other
  tables' key policies.
- `HistoryServiceTest` — the tests themselves, plus wiring tests for the
  shared `DbiEngine<HistoryKey, HistoryRow>` against an in-memory fake
  `KeyedRepository` (there is no real DB2 access here — F115IHI0 needs a DB2
  precompiler that isn't available in this environment).

Every expected value was hand-traced from the COBOL source and copybook, not
observed from a running legacy program. The source is ISO-8859-1/Latin-1,
not UTF-8; column 7 `*` marks a comment, columns 8-72 are the code area.

## RULE-028 — TF_History is append-only

`C000-Interface` (`F115IHI0.src:309-347`) dispatches on the caller's
requested action:

```
When R115IHI0-In-Get-1st   -> live   (:314-317)
When R115IHI0-In-Get-Next  -> live   (:319-321)
When R115IHI0-In-Select    -> COMMENTED OUT (:323-325)
When R115IHI0-In-Insert    -> live   (:327-328)
When R115IHI0-In-Update    -> COMMENTED OUT (:330-331)
When R115IHI0-In-Delete    -> live   (:333-334)
When Other                 -> 'GL-IN-AKSJON-UV', Z900-Abend (:336-343)
```

A Select or Update request falls through to `When Other` and abends. Both
`F000-Select` (`F115IHI0.src:705-757`) and `H000-Update`
(`F115IHI0.src:880-891`) still exist as dead source text — every line of
each paragraph is prefixed with the column-7 comment marker `*` — confirming
this is a deliberate disablement, not an accidental deletion partway
through. `Capabilities.APPEND_ONLY` (`select=false, update=false`,
cursor/insert/delete all `true`) was already defined for this table before
this task started; it was verified against the source directly rather than
assumed, and it matches exactly.

## Key validation finding — this table has NONE, for any live action

Unlike ContractAmount/ContractCharge/PartCharge (where
`H100-Check-Qualification` is invoked from `H000-Update` only), **F115IHI0's
`H100-Check-Qualification` is invoked from nowhere reachable at all**:

- It is still defined (`F115IHI0.src:892-1017`) and looks, field for field,
  like the sibling tables' own H100 (Fin-Inst, Contract-Type, Contract-No,
  CA-Currency, PA-Seq-No, and — unique to this table — HI-Seq-No).
- Its only call site in the whole file is `Perform H100-Check-Qualification`
  at line 882 — which is itself inside the commented-out `H000-Update`
  paragraph (`F115IHI0.src:880-891). Confirmed with
  `grep -n "H100-Check-Qualification" F115IHI0.src`: the only two hits are
  the paragraph's own header (892) and that one dead call (882).
- `I000-Delete` (`F115IHI0.src:1045-1052`, live) calls
  `F100-Prepare-Qualification` (`:1047`), not H100. That paragraph
  (`:758-782`) just copies whatever qualification fields were supplied
  (resolving Fin-Inst-No → Fin-Inst-ID if only the external number was
  given, `:774-780`) with no mandatory-field check and no `TF-*-MISSING`
  error for anything missing. A Delete with an all-blank/zero key is never
  rejected by F115IHI0 itself before `I200-Delete-Row` issues the DELETE.
- `G000-Insert` / `G100-Move-Input-To-DCL` (`F115IHI0.src:810-852`) also
  performs no key check, matching RULE-016's Insert-is-unchecked asymmetry
  seen in every table.

So `HistoryKeyPolicy.validate(...)` is an intentional no-op — not a stub
standing in for unfinished work. Do **not** port the
ContractAmount/ContractCharge/PartCharge four-field-check pattern here by
analogy; the control flow genuinely does not have it for this table.
`HistoryServiceTest`'s `KeyPolicyIsNoOpTests` pins that a fully blank record
passes validation without even touching the `FinInstResolver`.

In practice, because `Capabilities.APPEND_ONLY` already disables Select and
Update — the only two actions `DbiEngine` ever runs a `KeyPolicy` before —
`DbiEngine` never calls `HistoryKeyPolicy.validate(...)` for this table at
all when wired normally. `CapabilityGatePrecedesKeyPolicyTests` proves this
directly with a spy `KeyPolicy` that records whether it was invoked.

RULE-028 has no reviewer verdict yet in `analysis/TFR12/RULE_REVIEWS.json` —
treat it as read from the code as-is.

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=HistoryServiceTest
```

Requires Java 21. All 10 test methods currently pass (3 nested classes:
`DbiEngineWiringTests`, `KeyPolicyIsNoOpTests`,
`CapabilityGatePrecedesKeyPolicyTests`, plus one top-level sanity test on
`Capabilities.APPEND_ONLY`'s field values) — `HistoryKeyPolicy` is a real
(if empty) implementation, not a placeholder that throws
`UnsupportedOperationException`, so the suite is expected to be green today.

Note: running the whole module's `mvn test` (not just `-Dtest=
HistoryServiceTest`) may also execute other tables' tests (`contractamount`,
`syscode`, `partcharge`, `maincharge`, ...) written by other work in this
same shared module; failures there are outside this test class's scope.

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests
actually pin RULE-028, `DbiEngineWiringTests`'s engine was temporarily wired
with `Capabilities.FULL` instead of `Capabilities.APPEND_ONLY`, and
`mvn test -Dtest=HistoryServiceTest` was re-run: exactly the two tests that
assert Select and Update are rejected
(`rule028_selectOneThrowsActionNotSupported`,
`rule028_updateThrowsActionNotSupported`) went red — "Expected
DbiActionNotSupportedException to be thrown, but nothing was thrown" — while
the other 8 stayed green. The wiring was then reverted and the suite is
green again (10/10). If you change `Capabilities.APPEND_ONLY` or this
table's wiring, repeat this exercise: break one small thing that matters,
confirm at least one test goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115IHI0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to `DbiEngineWiringTests` (action dispatch/capability
   wiring), `KeyPolicyIsNoOpTests` (key-policy behavior), or
   `CapabilityGatePrecedesKeyPolicyTests` (ordering between the capability
   gate and the key policy), or create a new nested class if it covers a
   different paragraph/branch (e.g. the cursor-qualification logic in
   `D100-Prepare-Input` / `D200-Prepare-Qualification` /
   `E100-Check-Qualification`, which this initial pass does not yet cover
   beyond the basic `getRange` happy path).
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule
   id in the test method name (e.g. `rule028_...`) or a comment directly
   above it — `modernize-verify` finds tests by that id, and an unnamed test
   counts for no rule.
4. If the case needs a new field on `HistoryKey`/`HistoryRow`, extend the
   record and update its Javadoc to cite the copybook line backing the new
   field.
5. Run `mvn test -Dtest=HistoryServiceTest` and confirm the new test
   executes (not skipped) and passes — and, if it's meant to catch a
   regression, prove it can fail by temporarily breaking the corresponding
   wiring or logic first.
