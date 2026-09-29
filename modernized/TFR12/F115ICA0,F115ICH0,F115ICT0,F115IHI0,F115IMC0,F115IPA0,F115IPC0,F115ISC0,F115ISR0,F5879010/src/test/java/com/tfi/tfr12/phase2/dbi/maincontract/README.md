# F115IMC0 (TF_Main_Contract) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/TFR12/src/F115IMC0.src` (table `TF_Main_Contract`) — the most
heavily-used table in the system (the main contract record), following the
same generic DBI pattern established by the pilot table, `F115ICA0`
(TF_Contract_Amount). No specific `RULE-NNN` id in
`analysis/TFR12/BUSINESS_RULES.md` names F115IMC0 by module, but RULE-018
("the same pattern... is repeated independently in every F115I*0
table-interface module") and RULE-016 (the insert/update key-check asymmetry)
both explicitly describe this table's pattern generically, so tests here are
named against those two ids.

Covers:

- `MainContractKey` / `MainContractRow`
  (`src/main/java/.../dbi/maincontract/`) — the Java contract for the table's
  natural key and row, hand-traced from
  `legacy/TFR12/copybooks/R115IMC0.copy`.
- `MainContractKeyPolicy` — a real implementation of the shared
  `com.tfi.tfr12.phase2.dbi.KeyPolicy<MainContractRow>`, ported from legacy
  `H100-Check-Qualification` (`F115IMC0.src:915-987`).
- `MainContractServiceTest` — the tests themselves, plus wiring tests for the
  shared `DbiEngine<MainContractKey, MainContractRow>` against an in-memory
  fake `KeyedRepository` (there is no real DB2 access here — F115IMC0 needs a
  DB2 precompiler that isn't available in this environment).

Every expected value was hand-traced from the COBOL source and copybook, not
observed from a running legacy program. The source is ISO-8859-1/Latin-1, not
UTF-8; column 7 `*` marks a comment, columns 8-72 are the code area.

## RULE-018 — the mandatory key, and where it is (and isn't) enforced

`H100-Check-Qualification` requires two elements before it lets an Update
through: Fin-Inst (ID, or No resolved via `F5879010`), and the composite
Contract-Type + Contract-No (`F115IMC0.src:917-985`). Unlike
TF_Contract_Amount (Currency), TF_Main_Charge or TF_Part_Charge (role/charge
elements), TF_Main_Contract has no fourth table-specific mandatory field.
Customer-ID is documented as optional (`R115IMC0.copy:104-106,124`) and is
never checked. Missing fields map to error mnemonics: `TF-FIN-INST-MISSING`,
`TF-CONTRACT-MISSING` (shared by both Contract-Type and Contract-No).

**Read this before touching `MainContractKeyPolicy` or replicating this
pattern elsewhere**: in the literal F115IMC0 source,
`H100-Check-Qualification` is invoked from exactly one place —
`H000-Update` (`F115IMC0.src:905`), confirmed independently already for
F115ICA0, F115ICH0, F115IPC0, F115IPA0 and F115ICT0. `F000-Select` and
`I000-Delete` both route through `F100-Prepare-Qualification`
(`F115IMC0.src:715-734`) instead, which copies whatever qualification fields
were supplied (with a Fin-Inst-No→ID resolution) but never checks for a
missing field and never raises a `TF-*-MISSING` error — a missing key on
Select just surfaces later as `DB-FOREKOMST-NF-Q` ("row not found") once DB2
is queried, and a missing key on Delete is never rejected before the
`DELETE` runs. This contradicts the copybook's own comment ("Contract-Type
and -No is mandatory for Select/Update/Delete",
`R115IMC0.copy:98-101,129-130`) — the comment claims a check the control flow
doesn't perform outside Update.

The shared `DbiEngine` (already built, not this task's to redesign)
deliberately applies `MainContractKeyPolicy` before Select, Update AND Delete
uniformly — see its own Javadoc. That is a **hardening decision for the
modernized system, not a faithful line-for-line reproduction of F115IMC0's
Select/Delete behavior**. `MainContractServiceTest` pins both facts side by
side:

- `rule018_updateRejectsIncompleteKey_matchesLegacyH100Update` — matches
  literal legacy behavior (Update really does check).
- `rule018_selectRejectsIncompleteKey_widerThanLegacySelect` and
  `rule018_deleteRejectsIncompleteKey_widerThanLegacyDelete` — explicitly
  named and commented as **wider** than legacy.

**Four When-branches collapse to a two-branch model**: F115IMC0's own
Fin-Inst check (`F115IMC0.src:918-938`) has FOUR When-branches (data-area
Fin-Inst-ID, data-area Fin-Inst-No, qualification-area In-Fin-Inst-ID,
qualification-area In-Fin-Inst-No) rather than the two seen in F115ICA0,
because the legacy copybook keeps the "data area" and "qualification area"
as separate fields. `MainContractRow` collapses each area-pair into a single
field (same simplification already applied to `ContractAmountRow`), so
`MainContractKeyPolicy` observes the same two-branch shape used elsewhere:
ID present -> continue; else No present -> resolve; else missing. See
`MainContractRow`'s and `MainContractKeyPolicy`'s own Javadoc for the full
line trace.

**"Last failure wins", not fail-fast**: `F115IMC0.src:917-985` runs its three
mandatory-field checks (Fin-Inst, Contract-Type, Contract-No) as three
independent `Evaluate True ... End-Evaluate` blocks with no early exit — a
failing check overwrites the single scalar error-message field and keeps
going. With more than one field missing, the mnemonic actually returned is
whichever check failed **last** (Contract-No, then Contract-Type, then
Fin-Inst), not the first. Since Contract-Type and Contract-No share the same
mnemonic, this ordering is only observable between Fin-Inst and
{Contract-Type, Contract-No} — `LastFailureWinsTests` in
`MainContractServiceTest` pins exactly this.

**Capabilities**: F115IMC0's `C000-Interface` (`F115IMC0.src:318-356`)
dispatches all six actions (Get-1st, Get-Next, Select, Insert, Update,
Delete) with no condition-name commented out, unlike F115ISR0 (RULE-027) or
F115IHI0 (RULE-028) — so this table uses `Capabilities.FULL`.

**In-Progress**: `R115IMC0-In-Progress` (`R115IMC0.copy:169-171`) is included
on `MainContractRow` as a meaningfully distinctive field for this table — it
drives the Actual-vs-Work-in-progress data-version switch used across the
whole TFR12 function (RULE-026, `FTFRL120.src:233-246`, "confirmed" in
`analysis/TFR12/RULE_REVIEWS.json`). That switching logic itself is out of
scope for this module and is not exercised by these tests — only the raw
flag value F115IMC0 itself reads/writes is modeled and tested here.

RULE-018 has no reviewer verdict yet in `analysis/TFR12/RULE_REVIEWS.json`
(unlike e.g. RULE-026, which was confirmed by the system owner) — treat it as
read from the code as-is, but it hasn't been through the same explicit
sign-off, and the Select/Delete widening above is worth a second look before
being relied on.

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=MainContractServiceTest
```

Requires Java 21. All 18 test methods currently pass (3 nested classes:
`KeyPolicyTests` with `LastFailureWinsTests` nested inside it, and
`DbiEngineWiringTests`) — `MainContractKeyPolicy` is a real implementation,
not a stub, so the suite is expected to be green today.

Note: running the whole module's `mvn test` (not just
`-Dtest=MainContractServiceTest`) also executes other tables' tests
(`contractamount`, `syscode`, `partcharge`, ...) written by other work in
this same shared module; failures there are outside this test class's scope.

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests
actually pin RULE-018 (rather than passing by accident), the "last failure
wins" deferral in `MainContractKeyPolicy`'s Fin-Inst check was temporarily
changed to fail fast (`throw` immediately instead of setting
`pendingFailure`), `mvn test -Dtest=MainContractServiceTest` was re-run, and
exactly the 3 tests in `LastFailureWinsTests` went red (expected
`TF-CONTRACT-MISSING`, got `TF-FIN-INST-MISSING`). The change was then
reverted and the suite is green again (18/18). If you change
`MainContractKeyPolicy`, repeat this exercise: break one small thing that
matters, confirm at least one test goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115IMC0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to `KeyPolicyTests` (single-field checks),
   `LastFailureWinsTests` (multi-field-missing precedence), or
   `DbiEngineWiringTests` (engine wiring), or create a new nested class if it
   covers a different paragraph/branch.
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule id
   in the test method name (e.g. `rule018_...`) or a comment directly above
   it — `modernize-verify` finds tests by that id, and an unnamed test counts
   for no rule.
4. If the case needs a new field on `MainContractKey`/`MainContractRow`,
   extend the record and update its Javadoc to cite the copybook line
   backing the new field.
5. Run `mvn test -Dtest=MainContractServiceTest` and confirm the new test
   executes (not skipped) and passes against the real
   `MainContractKeyPolicy` — and, if it's meant to catch a regression, prove
   it can fail by temporarily breaking the corresponding line in
   `MainContractKeyPolicy`.
