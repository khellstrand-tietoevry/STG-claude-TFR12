# TF_Part_Charge (F115IPC0) characterization tests

Pins **RULE-016** (`analysis/TFR12/BUSINESS_RULES.md`) for `F115IPC0.src`, the
DBI module for table `TF_Part_Charge`: the 7-element mandatory-key check that
`H100-Check-Qualification` (`F115IPC0.src:946-1086`) runs before
Select/Update/Delete, and the fact that `G000-Insert`
(`F115IPC0.src:860-904`) performs none of it.

## What is under test

- `PartChargeKey` — the 7-field natural key (Fin-Inst-ID, Contract-Type,
  Contract-No, CA-Currency, PA-Seq-No, Role-Type, CT-Type).
- `PartChargeRow` — the key plus a representative slice of `R115IPC0-Data`.
- `PartChargeKeyPolicy` — the hand-traced Java equivalent of
  `H100-Check-Qualification`, one `if` per legacy `Evaluate True` block, same
  error mnemonics, same source-line comments.

There is no compiled legacy oracle available in this environment (no DB2
precompiler — see the repo's `TRANSFORMATION_NOTES.md`), so these tests are
**hand-traced characterization tests**: every expected value was derived by
reading `F115IPC0.src` line by line, not by running the COBOL. Each test
names `RULE-016` and cites the exact source lines it pins.

## Running

From this Maven module (`modernized/TFR12/F115ICA0,.../`):

```
mvn test -Dtest=PartChargeServiceTest
```

or `mvn test` to run the whole module's suite.

## Adding a new case

1. Find the exact legacy behavior in `legacy/TFR12/src/F115IPC0.src` (or the
   copybook `legacy/TFR12/copybooks/R115IPC0.copy` for field layout/types).
   Never trust a comment or a summary over the actual `PERFORM`/`EVALUATE`
   control flow.
2. If it is a new mandatory-field/error-code pairing, add a case to
   `PartChargeKeyPolicy` citing the source line range, then add a test in
   `PartChargeServiceTest` that names the rule id (`RULE-016` or whichever
   applies) in the test method name or an adjacent comment.
3. Build the test row with the `withXxx(completeRow(), ...)` helpers so only
   the one field under test differs from a fully-valid row — this avoids the
   ambiguity documented in `PartChargeKeyPolicy`'s class comment about which
   error mnemonic legacy reports when more than one mandatory field is
   missing at once (last-message-wins via the shared `Z000-Error-Message`
   area, not first-failure).
4. Run `mvn test` and confirm the new test fails against the *old*
   `PartChargeKeyPolicy` code and passes once the fix/addition is made
   (prove the test can fail before trusting it).
5. If the target behavior is not implemented yet, mark the test
   `@Disabled("pending RULE-NNN")` rather than deleting or skipping it
   silently.
