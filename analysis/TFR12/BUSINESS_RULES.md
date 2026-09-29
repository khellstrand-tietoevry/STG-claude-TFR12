# Business Rules — STG/TFR12

At extraction: 39 confirmed rules (7 P0); later steps may add or correct rules below. Each citation was checked by a second agent that read the cited lines; 7 candidate rules were refuted and left out.

| ID | Name | Category | Priority | Source | Confidence |
|---|---|---|---|---|---|
| RULE-001 | Party-specific charge price override (POP) is priced as of today's processing d… | Calculation | P0 | `F115L060.src:1257-1280` | Medium |
| RULE-002 | Charge amount computed from percentage of contract/part amount, clamped to min/… | Calculation | P0 | `src/F115L070.src:498-619` | Medium |
| RULE-003 | Graduated/tiered percentage pricing across amount bands | Calculation | P0 | `src/F115L150.src:463-519` | Medium |
| RULE-004 | Minimum/maximum charge caps with percentage recomputation | Calculation | P0 | `src/FTFRL120.src:856-935` | High |
| RULE-005 | Charge computation scope follows whether a part amount is specified | Calculation | P1 | `legacy/TFR12/src/F115L070.src:121-180` | Medium |
| RULE-006 | Charge currency is re-labelled to contract currency without recalculating the a… | Calculation | P1 | `src/F115L060.src:1050-1101` | Low |
| RULE-007 | Percentage-based charge amount derived from Contract or Part Amount | Calculation | P1 | `src/F115L060.src:1341-1381` | Medium |
| RULE-008 | Settlement/part-amount charges are summed, not recalculated | Calculation | P1 | `src/F115L070.src:621-634` | High |
| RULE-009 | Buyer/seller charge totals split by credit routing | Calculation | P1 | `src/F115L070.src:637-734` | Medium |
| RULE-010 | Subtracting already-stored charges when reversing input, floored at zero | Calculation | P1 | `src/F115L070.src:852-884` | Medium |
| RULE-011 | Percentage-of-contract-amount charges without a part amount are based on the or… | Calculation | P1 | `src/F115L150.src:441-461` | Low |
| RULE-012 | Product pricing amounts are converted from the bank's base currency to the cont… | Calculation | P1 | `src/F115L150.src:539-579` | High |
| RULE-013 | Stored charge percentage back-derived when missing | Calculation | P1 | `src/FTFRL120.src:803-849` | High |
| RULE-014 | Duplicate/conflicting charge detection between Main and Part charge records | Validation | P0 | `src/F115L070.src:361-421` | Medium |
| RULE-015 | Buyer/seller both charged is not allowed (single payer per charge) | Validation | P0 | `src/FTFRL120.src:1119-1170` | Medium |
| RULE-016 | Charge record update requires a fully resolvable composite key (asymmetric with… | Validation | P1 | `F115ICH0.src:868-996` | High |
| RULE-017 | System-Code update key validation is unintentionally bypassed when Contract-Typ… | Validation | P1 | `F115ISC0.src:739-815` | High |
| RULE-018 | Mandatory composite natural key to identify a Trade Finance contract/charge rec… | Validation | P1 | `src/F115ICA0.src:806-894` | High |
| RULE-019 | Contract-type classification for GL account and applicability rules | Validation | P1 | `src/F115L060.src:253-269` | High |
| RULE-020 | Consistency between main and part charge records | Validation | P1 | `src/F115L070.src:332-358` | High |
| RULE-021 | Product pricing must define either a flat amount or a percentage, not both | Validation | P1 | `src/F115L150.src:390-438` | High |
| RULE-022 | Mandatory input fields for charge inquiry | Validation | P1 | `src/FTFRK120.src:145-196` | High |
| RULE-023 | Numeric format for optional identifier fields | Validation | P1 | `src/FTFRK120.src:589-668` | High |
| RULE-024 | Charge inquiry requires the referenced contract/amount data to exist | Validation | P1 | `src/FTFRL120.src:303-341` | High |
| RULE-025 | Output fields cannot be supplied as input | Validation | P2 | `src/FTFRK120.src:244-541` | High |
| RULE-026 | Contract data-version selection (Actual vs Work) | Lifecycle | P0 | `src/FTFRL120.src:228-246` | Medium |
| RULE-027 | System code relationship records (e.g. valid Operation-Type to Charge-Type mapp… | Lifecycle | P1 | `F115ISR0.src:326-360` | High |
| RULE-028 | History records are append-only (no direct select or update) | Lifecycle | P1 | `src/F115IHI0.src:309-347` | High |
| RULE-029 | Settled part-amount suppresses recalculation of percentage-based charge | Lifecycle | P1 | `src/F115L060.src:604-628` | Medium |
| RULE-030 | Standard status-code lifecycle for the charge service | Lifecycle | P1 | `src/FTFRH120.src:203-263` | High |
| RULE-031 | ACCEPT-ILC special charge-row consolidation | Lifecycle | P1 | `src/FTFRL120.src:1018-1117` | Medium |
| RULE-032 | SHOW-AGENT operation bypasses normal charge listing | Lifecycle | P1 | `src/FTFRL120.src:290-296` | High |
| RULE-033 | Fee-payer role determined by contract type and charge role category | Policy | P1 | `src/F115L060.src:479-592` | Medium |
| RULE-034 | Charge data scope depends on whether a part-amount is specified | Policy | P1 | `src/F115L070.src:234-249` | High |
| RULE-035 | Part-charge paying party overrides main-charge paying party | Policy | P1 | `src/F115L070.src:422-476` | Medium |
| RULE-036 | Sub-minimum input date normalized to sentinel null date | Policy | P1 | `src/F7919100.src:291-336` | Medium |
| RULE-037 | Sliding-window century determination for two-digit-year dates | Policy | P1 | `src/F7919100.src:951-996` | High |
| RULE-038 | Charge-type configuration falls back from institution-specific to registration-… | Policy | P2 | `src/F115L060.src:1104-1156` | Medium |
| RULE-039 | Organization unit attributes are looked up as of today's system date | Policy | P2 | `src/F5879010.src:537-566` | Medium |

## Calculation

### RULE-001: Party-specific charge price override (POP) is priced as of today's processing date, not the contract date, unless the c…
**Category:** Calculation
**Priority:** P0
**Source:** `F115L060.src:1257-1280`
**Plain English:** When computing a charge, the system checks whether that charge type is configured to read a negotiated Percent-of-Party (POP) price agreement; unless the charge type explicitly turns this off, it looks up the customer-specific price/min/max that is valid on today's calendar date (the machine date captured when the batch/transaction started), not the date the contract or part amount was booked, an…
**Specification:**
  Given Charge type CT for institution X has R115ICT0-Read-POP not equal to 'N' (i.e. POP lookup is enabled, which is the default)
  When  F115L060 computes the charge amount for that charge type on any date, e.g. 2026-09-28
  Then  L400-Read-POP calls F115L150 with In-Valid-Date = today's date (R7919070-Ut-Dato, obtained once per run in B000-Initialize at F115L060.src:180-188) rather than the contract's original date, so the negotiated price/percentage and min/max charge caps in effect today are used even for a contract booked on an earlier date; if Read-POP = 'N', the lookup is skipped and R115L150-TF-POP is left initialized/zero (F115L060.src:1096-1101), so the charge type's own default configuration is used instead
**Parameters:** R115ICT0-Read-POP: ' ' = read POP (default), 'N' = skip POP lookup (R115ICT0.copy:123-125)
**Edge cases handled:** No POP records found for the key (R349IPP0 total rows = 0): F115L150 returns Return-OK with blank/zero output, effectively no override (F115L150.src:145-148); Exactly one POP record: unique flat-amount-or-percentage override applied (F115L150.src:149-151, already covered elsewhere for the flat/percent exclusivity rule); Multiple POP records: tiered/graduated pricing applies (already covered elsewhere)
**Confidence:** Medium — Is it intentional that a customer's negotiated (POP) charge price/min/max is always evaluated as of today's processing date rather than the contract's booking date or a transaction value date? If the negotiated price changes between when a contract/part-amount was created and when a charge on it is later computed, should the price in effect at booking time apply instead of today's price?

### RULE-002: Charge amount computed from percentage of contract/part amount, clamped to min/max
**Category:** Calculation
**Priority:** P0
**Source:** `src/F115L070.src:498-619`
**Also cited:** src/F115L070.src:498-619
**Plain English:** For charges priced as a percentage of the contract or part amount, the fee is the stored percentage applied to the relevant amount, rounded, and then forced up to the charge type's minimum or down to its maximum if it falls outside that configured range (the percentage shown is recalculated to match after clamping).
**Specification:**
  Given Method-of-Calc = Pct-Of-PA, stored percentage = 2.000%, Part-Amount = 10,000.00, Minimum-Charge = 250.00, Maximum-Charge = 0 (no cap)
  When  F115L070's E100-Merge-Charges computes the charge for this row
  Then  Raw amount = 10,000.00 * 2.000 / 100 = 200.00 (rounded); since 200.00 < 250.00 minimum, the charge is raised to 250.00 and the displayed percentage is recomputed to 250.00*100/10,000.00 = 2.500%
**Parameters:** Rounding: standard COBOL ROUNDED (half-up) on the Compute at line 553-558; Maximum-Charge = 0 means uncapped (line 588-590 requires Maximum-Charge > 0 to apply)
**Edge cases handled:** If Part_Charge already carries a non-zero amount or percentage, the stored Main_Charge percentage is ignored entirely and no min/max reclamping is performed (lines 509-519); Only applies when Part-Amount-No > 0 and method is specifically Pct-Of-PA (not Pct-Of-CA) - line 535-540
**Confidence:** Medium — P0 panel doubts spec fidelity: Verified against legacy/TFR12/src/F115L070.src:498-619 (E100-Merge-Charges) in the STG-claude-TFR12 workspace copy. What the code actually does (confirmed by column-position nesting check): - Line 498-502: 'Evaluate True' on the Work record's Method-of-Calc, with 'When Pct-Of-CA' falling through to 'When Pct-Of-PA' — so both method types share the block at 509-619.…

### RULE-003: Graduated/tiered percentage pricing across amount bands
**Category:** Calculation
**Priority:** P0
**Source:** `src/F115L150.src:463-519`
**Also cited:** copybooks/F115L150.src? see src/F115L150.src:463-519
**Plain English:** When a product's pricing has multiple tiers (each valid up to a threshold amount), the default charge is built by applying each tier's percentage only to the portion of the contract/part amount that falls within that tier, and summing the resulting increments, until the full amount is covered.
**Specification:**
  Given Tier 1: valid up to 10,000 at 1.0%; Tier 2: valid up to 25,000 at 0.5%; contract amount (converted to basic currency) = 20,000
  When  F115L150's E000-Process-Multiple-Occ walks the tiers
  Then  Increment 1 = (10,000 - 0) * 1.0% = 100.00; Increment 2 = (20,000 - 10,000) * 0.5% = 50.00 (since 20,000 <= the tier-2 threshold of 25,000, the tier is only applied up to the actual amount); total charge = 150.00
**Parameters:** n/a
**Edge cases handled:** The amount being tiered is first currency-converted to the institution's basic currency before being compared against the tier thresholds (lines 471-480)
**Confidence:** Medium — Confirm the tiering formula (increment = (min(tier threshold, total amount) - previous threshold) * tier percent / 100, summed across tiers) matches the intended graduated-commission pricing model.

### RULE-004: Minimum/maximum charge caps with percentage recomputation
**Category:** Calculation
**Priority:** P0
**Source:** `src/FTFRL120.src:856-935`
**Also cited:** src/FTFRL120.src:856-942; src/FTFRL120.src:856-942
**Plain English:** A charge that would otherwise be computed from the bank's default price list is floored at a configured minimum and capped at a configured maximum; when the amount is adjusted to the floor or cap, the displayed commission percentage is recalculated to match.
**Specification:**
  Given A default (POP-derived) charge amount of 50.00 with Minimum-Charge 100.00 and Maximum-Charge 1,000.00 for the charge type, and a contract/part amount base of 20,000.00
  When  the system builds default values for a charge that has no stored amount/percentage
  Then  because 50.00 < 100.00 (the minimum) and the computed amount is > 0, the charge amount is raised to 100.00 and, if a percentage existed, the percentage is recomputed as 100.00 * 100 / 20,000.00 = 0.500%; symmetrically, if the default amount exceeds a non-zero maximum, the amount is capped at the maximum and the percentage is recomputed the same way; otherwise the POP-supplied amount and percentage are used unchanged
**Parameters:** Minimum-Charge and Maximum-Charge are per-charge-type, per-bank configuration values held in the POP pricing table (R115L150 / TF_POP); Maximum-Charge of 0 is treated as 'no maximum'
**Edge cases handled:** Maximum-Charge = 0 means no cap is applied even if amount exceeds it; Recomputed percentage only happens if a percentage already existed (>0) on the default row; otherwise percentage is forced to 0; If the min/max-adjusted base amount is 0, the recomputed percentage is forced to 0 instead of dividing by zero
**Confidence:** High

### RULE-005: Charge computation scope follows whether a part amount is specified
**Category:** Calculation
**Priority:** P1
**Source:** `legacy/TFR12/src/F115L070.src:121-180`
**Also cited:** (paragraph C000-Interface)
**Plain English:** When a charge computation request identifies a specific part amount, charges are computed at both the main-contract level and that part-amount level; if no part amount is specified, only main-contract-level charges are computed.
**Specification:**
  Given Given an F115L070 request supplies In-PA-Seq-No = 3 along with the contract keys
  When  When C200-Qualify-Read prepares the underlying calls to F115L060
  Then  Then C300-Read-Main-Charge is invoked with PA-Seq-No forced to 0 (i.e. main-contract-level charges) and C400-Read-Part-Charge is additionally invoked with PA-Seq-No = 3 (part-amount-level charges); if In-PA-Seq-No had been 0 (omitted), only the main-contract-level call would run
**Parameters:** N/A
**Edge cases handled:** Both the 'Main' and 'Part' working copies are derived from the same qualified input record, differing only in the PA-Seq-No field
**Confidence:** Medium — Citation was corrected by referee (The cited section, C200-Qualify-Read (legacy/TFR12/src/F115L070.src:195-249), only builds two working copies of the F115L060 request — W-R115L060-Main (with PA-Seq-No forced to zero, see the Move Zero at line 243) and W-R115L060-Part (retaining the original PA-Seq-No, line 241) — when R115L060-In-PA-Seq-No is non-zero (lines 239-249). A comment at 234-237 docume…

### RULE-006: Charge currency is re-labelled to contract currency without recalculating the amount
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L060.src:1050-1101`
**Also cited:** src/F115L060.src:1064-1093
**Plain English:** If a charge's stored amount is zero but a percentage is stored, the amount is (defensively) recomputed from the percentage; otherwise, when the charge's currency differs from the contract's currency, the displayed currency is simply overwritten with the contract's currency and the amount is carried over unconverted.
**Specification:**
  Given A charge record with WS-Charge-Amount = 0 and WS-Percent = 2.5, contract amount 100,000 in USD
  When  additional-info processing runs for that charge
  Then  the charge amount is recomputed via the percentage-of-amount calculation (N000-Compute-Charge-Amount); for any charge whose currency does not match the contract's currency, the currency field is reset to the contract's currency but the numeric amount is left unchanged (a 1999 change removed the prior currency-conversion call)
**Parameters:** none (behavioral switch, not a configurable rate)
**Edge cases handled:** Charge amount non-zero and currency matches contract currency: amount and currency untouched; Charge amount non-zero and currency differs from contract currency: currency label overwritten, amount not converted
**Suspected defect:** Amount is not converted when currency is forcibly relabeled, which can misstate charge amounts in reports whenever a charge's native currency differs from the contract currency. The inline history comment ('19/08/1999... apparently should not recompute after all, but the currency code should be changed') suggests this was a deliberate but undocumented policy change rather than a bug, but the net effect is a monetary amount shown under the wrong currency code without conversion.
**Confidence:** Low — When a stored charge's currency differs from the contract currency, should the amount be converted to the contract currency, or is simply relabeling the currency (without conversion) the intended, still-correct behavior from the 1999 change noted in the comments? / Is it intentional/safe that a charge amount is never actually FX-converted when its stored currency differs from the contract currenc…

### RULE-007: Percentage-based charge amount derived from Contract or Part Amount
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L060.src:1341-1381`
**Plain English:** When a trade-finance charge is defined by a commission percentage rather than a fixed amount, the charge is calculated as that percentage of the contract's current original amount (or, if the charge belongs to a specific part/drawdown, of that part amount).
**Specification:**
  Given A charge type whose calculation method is 'percent of Contract Amount' (MethCalc-Pct-Of-CA) with WS-Percent set to 2.500 and the contract's Curr-O-Amount (current original amount) is 100,000.00
  When  the charge amount is (re)computed because the stored amount is zero and a percentage is present
  Then  WS-Charge-Amount = 100,000.00 * 2.500 / 100 = 2,500.00, rounded (COBOL ROUNDED, round-half-away-from-zero to the amount's decimal places); if the method is 'percent of Part Amount' and a part-amount sequence number is supplied, the Part Amount (R115IPA0-Amount) is used instead of the Contract Amount; if the base amount is zero, the charge amount is forced to zero
**Parameters:** Percent base 100 (i.e. percentage expressed as whole number, e.g. 2.5 = 2.5%); rounding = COBOL ROUNDED (half-up)
**Edge cases handled:** Base amount (CA or PA) = 0 -> charge amount forced to 0, no divide-by-zero; Part-amount sequence supplied but zero -> falls back to Contract Amount; Only triggered when stored charge amount is 0 and a stored percent is non-zero (comment notes this situation 'should never occur' because percentages are meant to live only in the POP pricing table,…
**Confidence:** Medium — P0 panel split on whether this is critical to the system's core purpose or a costly-if-wrong rule (Read legacy/STG/TFR12/src/F115L060.src:1341-1381 (paragraph N000-Compute-Charge-Amount) plus its only caller at lines 1059-1061. Faithfulness: The rule card matches the code exactly. N000-Compute-Charge-Amount (1341-1381) sets WS-CA-Or-PA-Amount from R115ICA0-Curr-O-Amount when R115ICT0-MethCalc-Pct…

### RULE-008: Settlement/part-amount charges are summed, not recalculated
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L070.src:621-634`
**Plain English:** For charges priced as a flat settlement fee or a direct part-amount charge, the amounts stored against the main contract and against the specific part-amount are simply added together to get the total charge for that row.
**Specification:**
  Given Main_Charge amount = 30.00 and Part_Charge amount = 20.00 for the same 'COURIER-FEE' charge type with Method-of-Calc = Settlement
  When  F115L070's E100-Merge-Charges processes this charge type
  Then  The combined charge amount for the row becomes 50.00
**Parameters:** n/a
**Confidence:** High

### RULE-009: Buyer/seller charge totals split by credit routing
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L070.src:637-734`
**Also cited:** src/F115L070.src:637-735
**Plain English:** Charges are summed into separate buyer and seller totals depending on whether the credit for the charge goes to the presenting/other bank or to the bank's own account, and grand totals per party and for the agent's commission are derived from those sub-totals.
**Specification:**
  Given Seller-side charges of 40.00 credited to another bank and 10.00 credited to the bank's own account
  When  F115L070's E200-Accumulate-Totals runs across all charge rows
  Then  Total-Charge-Seller = 40.00 + 10.00 = 50.00; Total Agent Commission = Main-table agent commission + Part-table agent commission
**Parameters:** n/a
**Edge cases handled:** Charges credited to an agent bank are not currently summed into the party totals - that branch is commented out (lines 641-649, 669-676), so any 'Credit-Agent-Bank' charges are effectively excluded f…
**Confidence:** Medium — Is it intentional that charges routed to an agent bank are excluded from the buyer/seller total-charge figures (the Credit-Agent-Bank accumulation code is commented out), or is this a latent defect? / Is the commented-out 'Ch-Seller-Cred-Agent'/'Ch-Buyer-Cred-Agent' accumulation intentionally disabled (agent commission tracked only via TF_History) or is this a residual bug that should be restored…

### RULE-010: Subtracting already-stored charges when reversing input, floored at zero
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L070.src:852-884`
**Plain English:** When an end-user's newly entered charge figure needs to be reduced by what has already been recorded (used when updating/undoing charges), the already-stored settlement/part-amount charge is subtracted from the input value, and the result is never allowed to go negative.
**Specification:**
  Given Input charge amount = 30.00 and an already-stored (Main_Charge) settlement amount of 45.00
  When  F115L070's F100-Modify-Input-Values runs for a Settlement or Part-Amount method charge
  Then  30.00 - 45.00 = -15.00, which is floored to 0.00
**Parameters:** n/a
**Edge cases handled:** For Pct-Of-CA/Pct-Of-PA methods, no subtraction is performed at all (line 874-881)
**Confidence:** Medium — Confirm the intended meaning of the 'Subtract' action path (used for AMEND/reversal flows) - specifically that percentage-based charges are deliberately left unadjusted here while flat/settlement charges are reduced by the stored amount.

### RULE-011: Percentage-of-contract-amount charges without a part amount are based on the original contract amount, not the outstand…
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L150.src:441-461`
**Plain English:** When a charge is priced as a percentage and no part-amount applies, the charge is calculated as percentage times the contract's original amount; a percentage-of-part-amount charge instead uses the specific part amount. A historical bug fix changed the contract-amount case to use the original amount instead of the amount still outstanding.
**Specification:**
  Given A contract with original amount 200,000 and percentage-based pricing of 0.5%, with no part-amount specified
  When  the default charge amount is computed
  Then  the charge amount is 200,000 x 0.5% / 100 = 1,000.00 using the contract's original amount (R115ICA0-Curr-O-Amount); the alternative calculation against the remaining/outstanding amount is present in the code as a commented-out line and not used
**Parameters:** Percentage rate WS-Percent-Comm (from TF_Sys_Charge_Type/product pricing); divisor 100
**Edge cases handled:** Part-Amount-No > 0 and pricing method is 'percent of part amount' (MethCalc-Pct-Of-PA): charge = percent x part amount / 100 (D100-Compute-Charge-Amount); Otherwise: charge = percent x original contract amount / 100 (D200-Compute-Charge-Amount)
**Confidence:** Low — Should percentage-based charges without a part-amount be calculated against the contract's original amount or its current outstanding (remaining) amount? The commented-out code and 'Feilretting' (bug-fix) note suggest this was deliberately changed at some point — confirm the original-amount basis is still correct.

### RULE-012: Product pricing amounts are converted from the bank's base currency to the contract currency
**Category:** Calculation
**Priority:** P1
**Source:** `src/F115L150.src:539-579`
**Plain English:** Flat charge amounts and min/max charge caps defined in the pricing table (POP) are stored in the bank's home/base currency and must be converted into the contract's currency via the exchange-rate module before being used or returned.
**Specification:**
  Given A POP-defined maximum charge of 500.00 in the bank's base currency (e.g. NOK) for a contract denominated in EUR
  When  the default charge amount, minimum, and maximum are prepared for output
  Then  each non-zero amount (charge, min, max) is passed individually through F116L030 (currency exchange module) to convert from the base currency to the contract's currency before use
**Parameters:** none (uses live exchange rate via F116L030)
**Edge cases handled:** POP charge amount = 0 and percent commission <= 0 (a bug-fix branch): the zero charge amount is copied through as-is without conversion, to avoid an erroneous non-zero result; POP min or max = 0: left as zero, not converted
**Confidence:** High

### RULE-013: Stored charge percentage back-derived when missing
**Category:** Calculation
**Priority:** P1
**Source:** `src/FTFRL120.src:803-849`
**Also cited:** src/FTFRL120.src:803-850
**Plain English:** When a charge has already been registered with a stored amount but no stored percentage, and its calculation method is percentage-based, the percentage is derived from the stored amount divided by the relevant contract or part-amount base.
**Specification:**
  Given A previously stored charge row with Charge-Amount > 0 but Charge-Percentage = 0, using calculation method 'percent of contract amount' or 'percent of part amount'
  When  I100-Stored-Values processes the row
  Then  The base amount is read (part amount if a part-amount number is supplied and method is not percent-of-contract-amount, else the contract's current original amount), and the percentage is computed as Amount × 100 ÷ Base; if the base amount is 0, percentage is forced to 0
**Parameters:** Formula: Percent = Amount × 100 ÷ Base-Amount
**Edge cases handled:** If the stored Charge-Percentage is already > 0, it is used as-is and no recomputation happens; The row is always marked Selected = 'Y' when a stored amount or percentage exists, regardless of the recomputation outcome
**Confidence:** High

## Validation

### RULE-014: Duplicate/conflicting charge detection between Main and Part charge records
**Category:** Validation
**Priority:** P0
**Source:** `src/F115L070.src:361-421`
**Also cited:** src/F115L070.src:361-420
**Plain English:** The same charge type/role must not carry a non-zero amount on both the adjacent rows being compared (current vs. previous) in both the merged working list and the raw Main_Charge list at once — if it does, this is treated as a data-integrity error and the transaction aborts rather than silently double-charging.
**Specification:**
  Given Two adjacent charge rows of the same Charge-Type where both the working (merged) charge amount and the Main_Charge amount are non-zero on both rows
  When  consistency checking runs while pre-processing the charge list
  Then  processing aborts with error code TF-SY-DUPLICATE-CHARGE (abend paragraph D201/D202) rather than producing a total that double-counts the charge
**Parameters:** Error codes: TF-SY-DUPLICATE-CHARGE, TF-SY-INCONSISTENCY-VAR1
**Edge cases handled:** If paying party differs between Main_ and Part_Charge for the same charge type, Part_Charge's value takes precedence and the other row's amount/percentage is zeroed out (D230/D260); Main_Charge and Part_Charge charge-type/role-type must correspond 1:1 by table position; any mismatch aborts with TF-SY-INCONSISTENCY-VAR1
**Confidence:** Medium — P0 panel doubts spec fidelity: Read legacy/TFR12/src/F115L070.src (symlink legacy/TFR12 -> STG/TFR12, resolving to /Users/kristofferhellstrand/git/tfi-workspace/STG-claude-TFR12/legacy/STG/TFR12/src/F115L070.src), lines 280-459, focusing on the cited D200-Check-Paying-Party section (lines 361-421). WHAT THE CODE ACTUALLY DOES (lines 363-420): D200 is invoked only for adjacent occurrences (W-Ind1…

### RULE-015: Buyer/seller both charged is not allowed (single payer per charge)
**Category:** Validation
**Priority:** P0
**Source:** `src/FTFRL120.src:1119-1170`
**Also cited:** src/FTFRL120.src:1119-1136; src/FTFRL120.src:1120-1173
**Plain English:** For a normal (non-ACCEPT-ILC) charge listing, if the same charge type appears twice in a row with both occurrences marked as 'selected' (i.e., both a buyer-side and seller-side charge for the same fee), that is an invalid state and processing aborts; otherwise the second occurrence overwrites, is ignored, or is written fresh according to which sides are actually charged.
**Specification:**
  Given Two consecutive rows for the same charge type where both are flagged Selected='Y'
  When  the system walks the merged charge list building the output rows
  Then  processing aborts with error TF-SY-DUPLICATE-CHARGE (paragraph I361); if only one of the two rows is selected the later one simply overwrites the earlier placeholder; if neither is selected, any paying party is cleared and the row is overwritten with a blank result
**Parameters:** N/A (control-flow policy, not a numeric parameter)
**Edge cases handled:** For operation type ACCEPT-ILC a different, more permissive rule set (I330/I345) governs suppression: a customer-charge credited to the bank's own bank is always written; otherwise the previous or new…; Rows flagged for suppression (SW-Ignore/SW-Overwrite) reduce the running row count so pagination stays consistent
**Confidence:** Medium — P0 panel doubts spec fidelity: Verified directly against legacy/STG/TFR12/src/FTFRL120.src (the workspace path legacy/TFR12/... does not exist in this checkout; the real file lives at legacy/STG/TFR12/src/FTFRL120.src, 1551 lines total). Core P0 claim — CONFIRMED, matches code exactly: I360-Normal-Row-Suppression (lines 1120-1173) is only reached for non-ACCEPT-ILC operation types (see I300-Check…

### RULE-016: Charge record update requires a fully resolvable composite key (asymmetric with insert)
**Category:** Validation
**Priority:** P1
**Source:** `F115ICH0.src:868-996`
**Plain English:** Before an existing Main Charge (or Part Charge) record can be updated, the system must be able to fully identify which record it is — institution, contract, role/paying-party, currency/part-amount, charge type and charge sequence number — falling back to alternate qualification fields if the primary key fields are blank, and rejecting the update with a specific error code if any piece is still mi…
**Specification:**
  Given A caller invokes F115ICH0 with R115ICH0-In-Update set, and the direct key fields (Fin-Inst-ID, Contract-Type, Contract-No, RP-Role-Type, Charge-Type, CH-Seq-No) are blank/zero
  When  H100-Check-Qualification runs before the update
  Then  the system first tries the alternate '-In-' qualification fields (looking up Fin-Inst-ID from Fin-Inst-No via N100-Fetch-Fin-Inst-ID if needed); if a field is still unresolved it sets Return-Error with mnemonic 'TF-FIN-INST-MISSING', 'TF-CONTRACT-MISSING', 'TF-PARTY-MISSING' or 'TF-CHARGE-MISSING' and the update does not proceed. The identical pattern (7 mandatory elements: Fin-Inst, Contract-Type/No, CA-Currency, PA-Seq-No, Role-Type, CT-Type) protects Part_Charge updates in F115IPC0.src:946-1086. In both modules, G000-Insert (F115ICH0.src:783-826, F115IPC0.src:860-904) calls G100-Move-Input-To-DCL directly and never performs this check, so a new charge row can be inserted with an unresolved/blank key while an update to an existing row cannot.
**Parameters:** Error mnemonics: TF-FIN-INST-MISSING, TF-CONTRACT-MISSING, TF-PARTY-MISSING, TF-CHARGE-MISSING, TF-PART-AMOUNT-MISSING, TF-ROLE-MISSING, TF-CHARGE-MISSING (F115IPC0)
**Edge cases handled:** Fin-Inst-ID resolved from Fin-Inst-No via N100-Fetch-Fin-Inst-ID when the numeric ID is zero; Part_Charge (F115IPC0.src:946-1086) requires 2 additional key elements (CA-Currency, PA-Seq-No) beyond Main Charge; Insert path performs no equivalent key-completeness check
**Confidence:** High

### RULE-017: System-Code update key validation is unintentionally bypassed when Contract-Type is already known
**Category:** Validation
**Priority:** P1
**Source:** `F115ISC0.src:739-815`
**Plain English:** When updating a system-code master record, the check that a code value (SC-Code) is present is supposed to run for every update, but a missing 'End-Evaluate' means that check is only reached when the Contract-Type check itself falls through to its error branch — so in the normal case (Contract-Type already known) the code value is never validated or defaulted before the update runs.
**Specification:**
  Given A request to update a TF_Sys_Code record where Contract-Type is already present on the loaded record or supplied as input (the common/normal case)
  When  H100-Check-Qualification executes: the Evaluate block for Contract-Type (F115ISC0.src:783-796) has no closing End-Evaluate before the next 'Evaluate True' for SC-Code begins (F115ISC0.src:799), so the SC-Code Evaluate is syntactically nested inside the Contract-Type block's 'When other' arm and only that single End-Evaluate at line 813 closes both
  Then  Because the Contract-Type check matched a non-error branch ('Continue' or the Move at 787-788), control never enters the nested 'When other' arm, so the SC-Code mandatory check/defaulting (799-813) is skipped entirely — R115ISC0-SC-Code is passed unresolved into G100-Move-Input-To-DCL and H200-Update-Row, and no 'TF-CODE-MISSING' error is ever raised in this path even when SC-Code is blank
**Edge cases handled:** The two preceding key checks in the same paragraph (ST-Type at 741-755, Institution at 757-781) each correctly close with their own End-Evaluate, showing the SC-Code block is the outlier, not an inte…; The equivalent Insert path (G100-Move-Input-To-DCL, F115ISC0.src:678-699) has no such conditional check because Insert takes the key wholly from input, so only Update is affected
**Suspected defect:** Missing End-Evaluate after the Contract-Type qualification block (expected around F115ISC0.src:796) causes the mandatory SC-Code check to be unreachable whenever Contract-Type resolves normally, letting Update proceed with an unvalidated/blank code key.
**Confidence:** High

### RULE-018: Mandatory composite natural key to identify a Trade Finance contract/charge record, with Institution-ID resolvable from…
**Category:** Validation
**Priority:** P1
**Source:** `src/F115ICA0.src:806-894`
**Plain English:** Before a Select/Update/Delete against a core Trade Finance table (Contract Amount, Main Charge, Part Charge, etc.), the interface requires the full natural key of the record (financial institution, contract type, contract number, and - depending on the table - currency, part-amount sequence, role/party, and charge type); if the caller supplies the institution's external number instead of its inte…
**Specification:**
  Given A caller invokes F115ICA0 with In-Action = Update, supplying Contract-Type, Contract-No and Currency, but leaving both Fin-Inst-ID and Fin-Inst-No blank
  When  H100-Check-Qualification runs before the update
  Then  None of the Evaluate branches for Fin-Inst-ID/Fin-Inst-No resolve, so the 'When other' branch fires: an error message 'TF-FIN-INST-MISSING' is built and R7919999-Return-Error is set, preventing the update from proceeding
**Parameters:** Error codes: TF-FIN-INST-MISSING, TF-CONTRACT-MISSING, TF-CURRENCY-MISSING, TF-PART-AMOUNT-MISSING, TF-ROLE-MISSING, TF-CHARGE-MISSING (message codes passed to F7918030 error-message lookup).
**Edge cases handled:** If only Fin-Inst-No (external institution number) is given, N100-Fetch-Fin-Inst-ID calls F5879010 to resolve it to the internal Fin-Inst-ID before the key check succeeds (F115ICA0.src:1111-1135); The exact set of mandatory key fields differs by table: Contract Amount additionally requires Currency (F115ICA0.src:877-892); Part Charge additionally requires CA-Currency, PA-Seq-No, Role-Type and…; The same pattern (and the same error codes) is repeated independently in every F115I*0 table-interface module, so a fix or change to key validation must be replicated across all of them
**Confidence:** High

### RULE-019: Contract-type classification for GL account and applicability rules
**Category:** Validation
**Priority:** P1
**Source:** `src/F115L060.src:253-269`
**Plain English:** Every contract type accepted by the charge engine must map to exactly one of four recognized product families (Collection, Import Letter of Credit, Export Letter of Credit, Guarantee); this classification drives which general-ledger account is used and which charge rules apply, and any unrecognized contract type is rejected outright.
**Specification:**
  Given Contract-Type = 'IDP' (an Import Documentary Presentation)
  When  the charge module determines the contract family
  Then  the contract is classified as 'Collection' (SW-Coll), which selects the Collection GL account (R115ICT0-GL-Coll-Acc); contract types ILC/ILU classify as Import LC, ELC/ECC as Export LC, and ILG/ELG as Guarantee, each selecting their own GL account; any other contract type value aborts processing (paragraph C301)
**Parameters:** Recognized types: Collection = IDP, IDA, ICP, ICA, EDP, EDA, ECP, ECA; Import LC = ILC, ILU; Export LC = ELC, ECC; Guarantee = ILG, ELG
**Edge cases handled:** Any contract type outside this fixed list causes an abend rather than a soft validation error
**Confidence:** High

### RULE-020: Consistency between main and part charge records
**Category:** Validation
**Priority:** P1
**Source:** `src/F115L070.src:332-358`
**Plain English:** When both the main contract's charges and a part-amount's charges are read together, each corresponding row must be for the same charge type and role (buyer/seller); if the two charge tables have drifted out of step, processing is aborted.
**Specification:**
  Given Row 3 of the Main_Charge list is charge type 'HANDLING' / role 'BUYER', but row 3 of the Part_Charge list is charge type 'HANDLING' / role 'SELLER'
  When  F115L070's D100-Check-Consistency compares the two rows
  Then  Message TF-SY-INCONSISTENCY-VAR1 is raised and the program abends via Z900-Abend
**Parameters:** n/a
**Confidence:** High

### RULE-021: Product pricing must define either a flat amount or a percentage, not both
**Category:** Validation
**Priority:** P1
**Source:** `src/F115L150.src:390-438`
**Plain English:** A product/service price table entry used to derive a default charge must specify exactly one of a flat price amount or a price percentage; having both (or working out to neither) determines how the default charge is computed, but having both is invalid configuration.
**Specification:**
  Given A PP_Prod_Tjen_Pris row has Prisbelop = 100.00 and Prisprosent = 1.500 (both non-zero)
  When  F115L150's D000-Process-Unique-Occurence evaluates the single matching price row
  Then  Message TF-SY-INVALID-CONTENT-TAB-VAR1 is raised and the program returns an Error (AE) via Z700-Error-AE, refusing to compute a default charge
**Parameters:** n/a
**Edge cases handled:** Both zero -> charge amount/percent left at 0, only min/max carried through (lines 392-399); Amount only -> flat fee, min/max come from Ovre/Nedre-Prisbelop (lines 412-419); Percent only -> percentage charge computed against Part-Amount (if Pct-Of-PA and a part amount is specified) or otherwise the contract's current original amount (lines 421-435)
**Confidence:** High

### RULE-022: Mandatory input fields for charge inquiry
**Category:** Validation
**Priority:** P1
**Source:** `src/FTFRK120.src:145-196`
**Also cited:** src/FTFRK120.src:145-195
**Plain English:** A request to read/compute trade-finance charges must specify Financial Institution Number, Contract Type, Contract Number, and Operation Type; if any is missing, the request is rejected.
**Specification:**
  Given An incoming RTFRE120 request where Contract-No is not supplied (indicator not 'U')
  When  FTFRK120's C100-Mandatory-Fields input control runs
  Then  The field is flagged 'F' (failed), message code GL-MISSING-FIELD& (or GL-ANGI-FELT& for Fin-Inst-No/Operation-Type) is set, and the call returns Error status (AE) via Z700-Error-AE, aborting further processing
**Parameters:** Mandatory fields: Fin-Inst-No, Contract-Type, Contract-No, Operation-Type
**Edge cases handled:** Applies independently to each of the 4 fields; first failing field still causes the AE return since Z700-Error-AE performs Goback
**Confidence:** High

### RULE-023: Numeric format for optional identifier fields
**Category:** Validation
**Priority:** P1
**Source:** `src/FTFRK120.src:589-668`
**Also cited:** src/FTFRK120.src:597-667
**Plain English:** When supplied, Contract Number, Rate/Part-Amount Number, Number of Periods, and the pagination bounds (Read-From, Read-To) must be numeric.
**Specification:**
  Given RTFRE120-B-Read-From is supplied but contains a non-numeric value
  When  FTFRK120's C300-Permitted-Values input control runs
  Then  The field is flagged 'F', message code GL-UGYLDIG-VERDI& is set, and the call returns Error status (AE)
**Parameters:** Fields checked: Contract-No, Part-Amount-No (Rate-Nr), Period (Perioder), Read-From (Les-Fra), Read-To (Les-Til)
**Edge cases handled:** Check is skipped entirely if the field was not supplied (indicator = 'U')
**Confidence:** High

### RULE-024: Charge inquiry requires the referenced contract/amount data to exist
**Category:** Validation
**Priority:** P1
**Source:** `src/FTFRL120.src:303-341`
**Plain English:** A charge-listing request is rejected outright if the Main Contract it refers to cannot be found (and likewise for the Contract Amount or Part Amount, when those are read); by contrast, an optional product-availability lookup is allowed to fail without stopping the request.
**Specification:**
  Given Given a ReadCharge request for a Fin-Inst-No/Contract-Type/Contract-No combination for which no TF_Main_Contract row exists
  When  When FTFRL120 performs G100-Read-Main-Contract
  Then  Then F115IMC0 returns Return-Error and FTFRL120 immediately performs Z700-Error-AE (paragraph 'G102', message code 'FM'), aborting charge processing for the whole request instead of returning an empty charge list
**Parameters:** N/A
**Edge cases handled:** The same 'not found = reject' pattern is applied to Contract Amount lookups (G500-Read-Contract-Amount, lines 449-488) and Part Amount lookups (G600-Read-Part-Amount, lines 494-537); In contrast, G200-Read-Sys-Code (PRODUCT-AVAIL lookup, lines 343-374) treats Return-Error as 'Continue' — a missing product-availability code does not abort the request
**Confidence:** High

### RULE-025: Output fields cannot be supplied as input
**Category:** Validation
**Priority:** P2
**Source:** `src/FTFRK120.src:244-541`
**Also cited:** src/FTFRK120.src:244-274
**Plain English:** Fields that the service only ever returns (e.g. Last-Saved-Date, Agent-Commission, the various total-charge and per-row output fields) must not be filled in by the caller on the way in; doing so is rejected as invalid input.
**Specification:**
  Given A caller populates RTFRE120-O-Agent-Commission on the input side of the call
  When  FTFRK120's C200-Out-Fields input control runs
  Then  The field is flagged 'F', message code GL-UTFELT-UTFYLT& is set, and the call returns Error status (AE)
**Parameters:** Output-only fields checked: Last-Saved-Date, Agent-Commission, Tot-Ch-Buy-CrThB, Tot-Ch-Buy-CrOuB, Total-Ch-Buyer, Total-Ch-Seller, Tot-Ch-Sell-CrThB, Tot-Ch-Sell-CrOuB, Charge-Type/Description/Charge-Text/Selected/Percent/Charge-Amount/Currency/Paying-Party/Percent-Comm (per row), Access-Text, Access-CC-Am, No-Read
**Edge cases handled:** Row-level output fields are checked once per occurrence in the output table (Perform ... Varying Tally)
**Confidence:** High

## Lifecycle

### RULE-026: Contract data-version selection (Actual vs Work)
**Category:** Lifecycle
**Priority:** P0
**Source:** `src/FTFRL120.src:228-246`
**Also cited:** src/FTFRL120.src:233-247; src/FTFRL120.src:233-246
**Plain English:** When looking up a trade-finance contract's charges, the system reads the live (Actual) contract data if the caller is only viewing/showing charges, or if the contract is not currently being amended; but it reads the draft (Work) version if the contract has an amendment in progress.
**Specification:**
  Given A main contract record with In-Progress = 'Y' (an amendment is currently being drafted)
  When  The TFR12 ReadCharge function is called with Operation-Type = 'REGISTER-CHARGE' (i.e. not SHOW or SHOW-AGENT)
  Then  All subsequent reads of contract amount, part amount, main contract, and charge-computation modules are qualified against the TF_Work_* (draft) tables, not the live TF_Main_Contract table
**Parameters:** Operation-Type values 'SHOW' and 'SHOW-AGENT' force Actual; In-Progress flag values 'Y'/'N' (copybooks/R115IMC0.copy:169-171); switches SW-Actual (+1) / SW-Work (+2) at src/FTFRL120.src:64-65
**Edge cases handled:** Operation-Type = 'SHOW' or 'SHOW-AGENT' always forces the Actual (live) version, even if In-Progress = 'Y'; In-Progress = 'N' (or any value other than 'Y') with any other operation type forces the Actual version; The chosen SW-Actual/SW-Work switch is propagated to every downstream data-access call in the same request (contract amount, part amount, charge list, fee-schedule lookup)
**Confidence:** Medium — P0 panel doubts spec fidelity: Code read: legacy/STG/TFR12/src/FTFRL120.src (the workspace's actual TFR12 location; legacy/TFR12/ itself is an empty stub directory, so I resolved the cited relative path to the real file). Lines 233-246 are: 233: Perform G100-Read-Main-Contract 235-246: If RTFRE120-I-Operation-Type-D = 'SHOW' Or = 'SHOW-AGENT' Set SW-Actual To True Else If R115IMC0-In-Progress-Yes…

### RULE-027: System code relationship records (e.g. valid Operation-Type to Charge-Type mappings) can only be created or removed, ne…
**Category:** Lifecycle
**Priority:** P1
**Source:** `F115ISR0.src:326-360`
**Plain English:** The table that defines relationships between two system codes (such as which charge types are valid for a given operation type) supports adding a new relationship and deleting an existing one, but there is no way to modify a relationship in place — an update request is treated as an invalid action.
**Specification:**
  Given A caller sets R115ISR0-In-Update and calls F115ISR0 to change a Sys_Code_Rel relationship (e.g. an 'OPERATION-TYPE' to 'CHARGE' link of relation-type 'VALID-OP-CHARGE', consumed by F115L060.src:341-406 to build the list of permissible charge types)
  When  C000-Interface dispatches on the requested action
  Then  the action does not match any of the enabled 88-level conditions (the In-Update condition-name is commented out of the copybook and the dispatch Evaluate in C000-Interface), so control falls to 'When Other', which logs message 'GL-IN-AKSJON-UV' (invalid action) and abends via Z900-Abend; the relationship can only be changed by deleting the old row (I000-Delete) and inserting a new one (G000-Insert)
**Parameters:** n/a
**Edge cases handled:** H000-Update and H100-Check-Qualification paragraphs (F115ISR0.src:933-1147) still exist in the source but are unreachable dead code because the dispatcher never performs them; Insert (G000-Insert) and Delete (I000-Delete) remain fully supported
**Confidence:** High

### RULE-028: History records are append-only (no direct select or update)
**Category:** Lifecycle
**Priority:** P1
**Source:** `src/F115IHI0.src:309-347`
**Plain English:** History/audit-trail rows for a contract can only be created (Insert), removed (Delete), or read via the cursor (Get-1st/Get-Next); the interface has no working path to fetch a single row directly or to update a history row once it exists.
**Specification:**
  Given Given a caller invokes the TF_History interface F115IHI0 with In-Action set to Select or Update
  When  When C000-Interface evaluates the requested action
  Then  Then because the '-In-Select' and '-In-Update' branches (calling F000-Select / H000-Update) are commented out of the Evaluate, control falls through to 'When Other', which raises error 'GL-IN-AKSJON-UV' (invalid action) and performs Z900-Abend, aborting the call
**Parameters:** Enabled actions: Get-1st, Get-Next, Insert, Delete. Disabled (dead-code) actions: Select, Update
**Edge cases handled:** The Select and Update paragraphs (F000-Select, H000-Update) still exist in the source but are unreachable, confirming the restriction is deliberate rather than an oversight
**Confidence:** High

### RULE-029: Settled part-amount suppresses recalculation of percentage-based charge
**Category:** Lifecycle
**Priority:** P1
**Source:** `src/F115L060.src:604-628`
**Plain English:** Once a part-shipment/part-amount of a contract has reached a 'settled' status, its percentage-of-part-amount charge is not re-derived from the main contract charge record, to avoid re-showing or re-charging an amount that has already been settled.
**Specification:**
  Given A charge whose calculation method is 'percent of part amount' (R115ICT0-MethCalc-Pct-Of-PA) and the caller requested a part-amount settlement check (In-Check-PA > 0)
  When  The part amount's status is verified and found to already be settled (Has-Been-Settled), with no error from the check
  Then  The routine does NOT perform E000-Read-Main-Charge for that charge type, i.e. it does not re-populate/re-charge the main-contract charge line for an already-settled part amount
**Parameters:** Status flag R115IPA0-Has-Been-Settled overloads the 'Incom-Swift' field (copybooks/R115IPA0.copy:180-183) with value 'A'; that same 1-byte field also carries unrelated Y/N 'Incom-Swift' semantics (Incom-Yes='Y', Incom-No='N')
**Edge cases handled:** If the settlement check itself returns an error, or the part amount has NOT been settled, E000-Read-Main-Charge still executes; If the calculation method is not 'percent of part amount', or no PA-check was requested, E000-Read-Main-Charge always executes unconditionally; The reused status byte ('A' = Has-Been-Settled vs 'Y'/'N' = Incom-Swift flag on the same field) looks like an overloaded/ambiguous status encoding
**Suspected defect:** R115IPA0's single-byte field is documented with two unrelated meanings (Incom-Yes/Incom-No 'Y'/'N', and Has-Been-Settled 'A') at copybooks/R115IPA0.copy:180-183, which risks the values colliding or being misread as one flag by future maintainers.
**Confidence:** Medium — Is the 'A' value on the Incom-Swift byte genuinely a distinct lifecycle state (settled) from the Y/N incoming-SWIFT flag on that same field, or is this a latent defect where two unrelated flags share one storage byte?

### RULE-030: Standard status-code lifecycle for the charge service
**Category:** Lifecycle
**Priority:** P1
**Source:** `src/FTFRH120.src:203-263`
**Plain English:** The result of calling the control (K) and logic (L) sub-modules is translated into one of a fixed set of outcomes for the caller: continue normally, continue with a warning, stop with a validation error, or abend.
**Specification:**
  Given The K-module or L-module sets R7919999-Return-Warning-Goback after processing
  When  FTFRH120's S000-Status-Code evaluates the returned status
  Then  An error message is built, the STG header status code is set to 'AW', and the program returns to its caller immediately (Goback) without further processing
**Parameters:** Status-code mapping: Return-OK -> pass through header status; Return-Warning -> 'AW' + continue; Return-Warning-Goback -> 'AW' + stop; Return-Error -> 'AE' + stop; Return-Abend -> 'AS' + stop
**Edge cases handled:** The L-module's status intentionally overrides the K-module's saved status when the L-module itself returns OK (comment lines 216-221), except a prior Warning-Goback still short-circuits before the L-…
**Confidence:** High

### RULE-031: ACCEPT-ILC special charge-row consolidation
**Category:** Lifecycle
**Priority:** P1
**Source:** `src/FTFRL120.src:1018-1117`
**Plain English:** For the 'accept import letter of credit' operation, charge rows for the same charge type are merged using a different rule set than normal (no hard reject for double-billing), driven by whether the charge is customer-role and whether the crediting bank is the bank's own.
**Specification:**
  Given Operation-Type = 'ACCEPT-ILC' and a new charge-type row differs from the previous one
  When  The row is Selected, has Role-Type 'CUSTM', and Credit-Own-Bank is set
  Then  The row is written as a new output line (SW-Write-New); other combinations of (Selected, CUSTM role, Credit-Own-Bank) either also write-new, or are ignored, per the full decision table in I330/I345
**Parameters:** Role-type value 'CUSTM' (copybooks/R115L070.copy:81); switch R115L070-Credit-Own-Bank = value 'W' (copybooks/R115L070.copy:95); consolidation states SW-Write-New / SW-Overwrite / SW-Ignore
**Edge cases handled:** When the new row's Charge-Type equals the previous row's (i.e. it's a repeat, not a new type), the alternate table I345-Special-Row-Suppression is used instead of I330, comparing the PREVIOUS row's S…; Unlike the normal path (I360), no combination in the ACCEPT-ILC tables raises a duplicate-charge abend
**Confidence:** Medium — What do the CUSTM role-type and Credit-Own-Bank flag represent operationally for an ILC acceptance, and why does this path never reject a double-billed charge (unlike every other operation type)?

### RULE-032: SHOW-AGENT operation bypasses normal charge listing
**Category:** Lifecycle
**Priority:** P1
**Source:** `src/FTFRL120.src:290-296`
**Also cited:** src/FTFRL120.src:693-747; src/FTFRL120.src:292-296
**Plain English:** A special 'show agent commission' request skips the normal fee-schedule/charge-list processing entirely and instead looks up the agent's commission from transaction history.
**Specification:**
  Given An inquiry request with Operation-Type = 'SHOW-AGENT' for a given contract
  When  The main processing section (G000-Update) evaluates the operation type
  Then  H400-Get-Agent-Commission is executed instead of H000-Get-Charges; the module reads the contract's charge history (H500-Read-History) and, only if an agent-commission history record exists (R115HI0-Agent-Excist), returns a single synthetic output row named 'AGENT-COMMISION' with the historical amount and currency (src/FTFRL120.src:693-744); if no such history record exists, no charge rows are returned at all
**Parameters:** Trigger value: RTFRE120-I-Operation-Type-D = 'SHOW-AGENT'
**Edge cases handled:** If the part-amount number is supplied (> 0) the currency/amount context is taken from the part amount record, otherwise from the contract amount record (src/FTFRL120.src:703-710)
**Confidence:** High

## Policy

### RULE-033: Fee-payer role determined by contract type and charge role category
**Category:** Policy
**Priority:** P1
**Source:** `src/F115L060.src:479-592`
**Also cited:** src/F115L060.src:479-570
**Plain English:** When looking up which parties are allowed to be charged a given fee, the system translates an abstract charge role category (GL account, Agent, Any, Buyer, Seller) into a concrete payer role (e.g. Drawee, Drawer, Customer, Beneficiary, Issuer) based on the instrument type (Collection, Import LC, Export LC, or Guarantee) of the contract.
**Specification:**
  Given A contract classified as Sw-Imp-LC (Import Letter of Credit, contract type ILC/ILU) and a permissible charge type whose role category is 'Buyer'
  When  the module resolves which party role can be billed for that charge type
  Then  the role type used to search for a charge record is set to 'CUSTM' (customer); for role category 'Any' on the same instrument type, both 'CUSTM' and 'BENEF' (beneficiary) are searched
**Parameters:** Role-category codes GLACC/Agent-Pty/Any/Buyer/Seller (from TF_Sys_Charge_Type); resolved role-type codes GLACC, DRWEE, DRWER, CUSTM, BENEF, ISSUI; instrument-type switches Sw-Coll/Sw-Imp-LC/Sw-Exp-LC/Sw-Gar (set in F115L060.src:253-269, already catalogued)
**Edge cases handled:** Collection (Sw-Coll): GLACC->'GLACC', Agent->blank role, Any->DRWEE+DRWER, Buyer->DRWEE, Seller->DRWER; Import LC (Sw-Imp-LC): Any->CUSTM+BENEF, Buyer->CUSTM, Seller->BENEF; Export LC (Sw-Exp-LC): Any->ISSUI+BENEF, Buyer->ISSUI, Seller->BENEF; Guarantee (Sw-Gar, ILG/ELG): Any->CUSTM+BENEF, Buyer->CUSTM, Seller->BENEF (added later per 'ndl'/'gar' patch markers)
**Confidence:** Medium — Is the mapping of charge-role-category to payer role (e.g. Export-LC Buyer -> ISSUI, Import-LC Buyer -> CUSTM) still correct for all currently active contract types, including any added after the Guarantee patch? / Confirm the intended paying-party roles per product category (Collection: drawee/drawer; Import LC: customer/beneficiary; Export LC: issuer/beneficiary; Guarantee: customer/beneficiary…

### RULE-034: Charge data scope depends on whether a part-amount is specified
**Category:** Policy
**Priority:** P1
**Source:** `src/F115L070.src:234-249`
**Plain English:** If the caller supplies a part-amount sequence number, both the Main Charge and Part Charge records are read and reconciled; if no part-amount is supplied, only the Main Charge record applies.
**Specification:**
  Given A charge computation request for contract 4711 with Part-Amount sequence number = 3
  When  the module qualifies which records to read
  Then  both TF_Main_Charge and TF_Part_Charge are read (Main read with PA-Seq-No forced to zero, Part read with PA-Seq-No = 3) and later reconciled for consistency (F115L070.src:332-421, already catalogued)
**Parameters:** none
**Edge cases handled:** Part-Amount sequence number = 0 or not supplied: only TF_Main_Charge is read (SW-Read-Main-Only)
**Confidence:** High

### RULE-035: Part-charge paying party overrides main-charge paying party
**Category:** Policy
**Priority:** P1
**Source:** `src/F115L070.src:422-476`
**Plain English:** If the buyer/seller designation for a charge differs between the main contract's charge record and the part-amount's charge record, the part-amount's designation wins, and the main-charge figure for the now-incorrect party is cleared (or, for additive methods, moved across).
**Specification:**
  Given Main_Charge row 1 (buyer) has amount 100.00 with method Pct-Of-CA, but Part_Charge row 1 (seller) also has an amount, i.e. buyer-designated in Work/Main but seller-designated in Part
  When  F115L070's D230-Modify-Entries runs for this mismatch
  Then  Since the method is Pct-Of-CA, the conflicting Main_Charge amount and percentage at the other position are zeroed out rather than merged
**Parameters:** Behavior differs by Method-of-Calculation: Pct-Of-CA/Pct-Of-PA -> zero out the losing entry's amount and percentage; Settlement/Part-Amount -> move the amount across instead of zeroing
**Edge cases handled:** D260-Modify-Entries handles the mirror-image case (False/True/True/False) the opposite direction
**Confidence:** Medium — Confirm that 'part-amount designation always wins over main-contract designation' is the intended business rule for who is billed when the two records disagree, rather than being treated as an error.

### RULE-036: Sub-minimum input date normalized to sentinel null date
**Category:** Policy
**Priority:** P1
**Source:** `src/F7919100.src:291-336`
**Plain English:** If a date supplied for format conversion (in any supported input format) is below that format's minimum representable value, the module treats it as a 'null date' and silently substitutes the sentinel 0001-01-01 instead of rejecting the input.
**Specification:**
  Given Given an input date in AMD9 format equal to 00000101 (below the minimum 00010101), or equivalently a DMA9 date below 01010001, an AMD7 date below 000101, or a DMA7 date below 010100
  When  When D000-Konv-Fra-Input processes the supplied date
  Then  Then the working date W-Dato-AMD9 is set to 00010101 (year 1, day 1) and that sentinel value is used to populate every requested output format, rather than the call being rejected
**Parameters:** Sentinel minimum date = 00010101 (0001-01-01)
**Edge cases handled:** Same floor is applied independently for each of the four supported input formats (AMD9, DMA9, AMD7, DMA7)
**Confidence:** Medium — Confirm 0001-01-01 is the system-wide convention for 'no date supplied' consumed correctly by all downstream callers, and that no caller actually needs a hard validation error when a sub-minimum date is passed.

### RULE-037: Sliding-window century determination for two-digit-year dates
**Category:** Policy
**Priority:** P1
**Source:** `src/F7919100.src:951-996`
**Also cited:** src/F7919100.src:951-995; src/F7919100.src:951-997
**Plain English:** When converting a date supplied with only a 2-digit year, the system determines the century using a sliding window: it takes today's date plus a caller-supplied number of years ahead (or 50 years by default) as the upper bound of the window, and picks whichever century places the date at or before that upper bound.
**Specification:**
  Given Today's date is 2026-09-28 and no override is supplied for 'years ahead'
  When  a 2-digit-year date is converted (e.g. year '30')
  Then  the upper bound of the window is 2026 + 50 = 2076; the date is assigned to the 21st century (20xx) because 2030 does not exceed 2076 — if the resulting date is above the window's upper bound, one century is subtracted (i.e. 19xx would be used instead)
**Parameters:** Default window size: 50 years ahead of today; overridable via R7919100-In-Aar-Frem-I-Tid (valid range 1-99)
**Edge cases handled:** Caller supplies In-Aar-Frem-I-Tid between 1 and 99: that value replaces the default 50-year offset; Caller supplies 0 or omits it: default 50 years is used
**Confidence:** High

### RULE-038: Charge-type configuration falls back from institution-specific to registration-number-specific
**Category:** Policy
**Priority:** P2
**Source:** `src/F115L060.src:1104-1156`
**Plain English:** When looking up a charge type's system configuration, the system first tries a record scoped to the financial institution; if none is found, it retries scoped by registration/branch number before treating the charge type as unconfigured.
**Specification:**
  Given Given a charge type has no TF_Sys_Charge_Type configuration keyed by the request's financial-institution ID/number, but does have one keyed by the request's registration number
  When  When L100-Read-Sys-Charge-Type is performed
  Then  Then the first F115ICT0 lookup (by Fin-Inst) returns Return-Error, and the module retries the same call with R115ICT0-In-Regno set to the registration number; only if that second lookup also fails is error paragraph 'L103' raised and the call rejected via Z700-Error-AE
**Parameters:** N/A
**Edge cases handled:** An abend (not a graceful reject) occurs if the second, registration-number-based lookup itself returns Return-Abend
**Confidence:** Medium — Confirm registration-number-level charge type configuration is an intended fallback default for institutions without their own override (rather than legacy/dead configuration data).

### RULE-039: Organization unit attributes are looked up as of today's system date
**Category:** Policy
**Priority:** P2
**Source:** `src/F5879010.src:537-566`
**Plain English:** When fetching an organization unit's attributes (e.g. via the iHUB Org interface), only the attribute version whose validity period covers today's business run date is returned; both the 'valid from' and 'valid to' qualification are set to the same current run date.
**Specification:**
  Given An organization unit with multiple attribute versions valid over different historical periods
  When  attributes are requested for that organization unit
  Then  the lookup filters to the single attribute version whose validity window includes today's system run date (obtained from F7918110), ignoring past or future-dated versions
**Parameters:** none (always today's system date; no as-of-date parameter is exposed to the caller)
**Edge cases handled:** No attribute version is valid for today's date: G200-Get-1st-Attribute returns not-found and G400-Extract-Attributes is skipped
**Confidence:** Medium — Is it intentional that this interface can only retrieve an organization unit's currently valid attributes, with no way to query historical or future-dated attribute versions?

## Rules requiring SME confirmation

- **RULE-001** (Medium): Is it intentional that a customer's negotiated (POP) charge price/min/max is always evaluated as of today's processing date rather than the contract's booking date or a transaction value date? If the negotiated price changes between when a contract/part-amount was created and when a charge on it is later computed, should the price in effect at booking time apply instead of today's price?
- **RULE-002** (Medium): P0 panel doubts spec fidelity: Verified against legacy/TFR12/src/F115L070.src:498-619 (E100-Merge-Charges) in the STG-claude-TFR12 workspace copy. What the code actually does (confirmed by column-position nesting check): - Line 498-502: 'Evaluate True' on the Work record's Method-of-Calc, with 'When Pct-Of-CA' falling through to 'When Pct-Of-PA' — so both method types share the block at 509-619.…
- **RULE-003** (Medium): Confirm the tiering formula (increment = (min(tier threshold, total amount) - previous threshold) * tier percent / 100, summed across tiers) matches the intended graduated-commission pricing model.
- **RULE-005** (Medium): Citation was corrected by referee (The cited section, C200-Qualify-Read (legacy/TFR12/src/F115L070.src:195-249), only builds two working copies of the F115L060 request — W-R115L060-Main (with PA-Seq-No forced to zero, see the Move Zero at line 243) and W-R115L060-Part (retaining the original PA-Seq-No, line 241) — when R115L060-In-PA-Seq-No is non-zero (lines 239-249). A comment at 234-237 docume…
- **RULE-006** (Low): When a stored charge's currency differs from the contract currency, should the amount be converted to the contract currency, or is simply relabeling the currency (without conversion) the intended, still-correct behavior from the 1999 change noted in the comments? / Is it intentional/safe that a charge amount is never actually FX-converted when its stored currency differs from the contract currenc…
- **RULE-007** (Medium): P0 panel split on whether this is critical to the system's core purpose or a costly-if-wrong rule (Read legacy/STG/TFR12/src/F115L060.src:1341-1381 (paragraph N000-Compute-Charge-Amount) plus its only caller at lines 1059-1061. Faithfulness: The rule card matches the code exactly. N000-Compute-Charge-Amount (1341-1381) sets WS-CA-Or-PA-Amount from R115ICA0-Curr-O-Amount when R115ICT0-MethCalc-Pct…
- **RULE-009** (Medium): Is it intentional that charges routed to an agent bank are excluded from the buyer/seller total-charge figures (the Credit-Agent-Bank accumulation code is commented out), or is this a latent defect? / Is the commented-out 'Ch-Seller-Cred-Agent'/'Ch-Buyer-Cred-Agent' accumulation intentionally disabled (agent commission tracked only via TF_History) or is this a residual bug that should be restored…
- **RULE-010** (Medium): Confirm the intended meaning of the 'Subtract' action path (used for AMEND/reversal flows) - specifically that percentage-based charges are deliberately left unadjusted here while flat/settlement charges are reduced by the stored amount.
- **RULE-011** (Low): Should percentage-based charges without a part-amount be calculated against the contract's original amount or its current outstanding (remaining) amount? The commented-out code and 'Feilretting' (bug-fix) note suggest this was deliberately changed at some point — confirm the original-amount basis is still correct.
- **RULE-014** (Medium): P0 panel doubts spec fidelity: Read legacy/TFR12/src/F115L070.src (symlink legacy/TFR12 -> STG/TFR12, resolving to /Users/kristofferhellstrand/git/tfi-workspace/STG-claude-TFR12/legacy/STG/TFR12/src/F115L070.src), lines 280-459, focusing on the cited D200-Check-Paying-Party section (lines 361-421). WHAT THE CODE ACTUALLY DOES (lines 363-420): D200 is invoked only for adjacent occurrences (W-Ind1…
- **RULE-015** (Medium): P0 panel doubts spec fidelity: Verified directly against legacy/STG/TFR12/src/FTFRL120.src (the workspace path legacy/TFR12/... does not exist in this checkout; the real file lives at legacy/STG/TFR12/src/FTFRL120.src, 1551 lines total). Core P0 claim — CONFIRMED, matches code exactly: I360-Normal-Row-Suppression (lines 1120-1173) is only reached for non-ACCEPT-ILC operation types (see I300-Check…
- **RULE-026** (Medium): P0 panel doubts spec fidelity: Code read: legacy/STG/TFR12/src/FTFRL120.src (the workspace's actual TFR12 location; legacy/TFR12/ itself is an empty stub directory, so I resolved the cited relative path to the real file). Lines 233-246 are: 233: Perform G100-Read-Main-Contract 235-246: If RTFRE120-I-Operation-Type-D = 'SHOW' Or = 'SHOW-AGENT' Set SW-Actual To True Else If R115IMC0-In-Progress-Yes…
- **RULE-029** (Medium): Is the 'A' value on the Incom-Swift byte genuinely a distinct lifecycle state (settled) from the Y/N incoming-SWIFT flag on that same field, or is this a latent defect where two unrelated flags share one storage byte?
- **RULE-031** (Medium): What do the CUSTM role-type and Credit-Own-Bank flag represent operationally for an ILC acceptance, and why does this path never reject a double-billed charge (unlike every other operation type)?
- **RULE-033** (Medium): Is the mapping of charge-role-category to payer role (e.g. Export-LC Buyer -> ISSUI, Import-LC Buyer -> CUSTM) still correct for all currently active contract types, including any added after the Guarantee patch? / Confirm the intended paying-party roles per product category (Collection: drawee/drawer; Import LC: customer/beneficiary; Export LC: issuer/beneficiary; Guarantee: customer/beneficiary…
- **RULE-035** (Medium): Confirm that 'part-amount designation always wins over main-contract designation' is the intended business rule for who is billed when the two records disagree, rather than being treated as an error.
- **RULE-036** (Medium): Confirm 0001-01-01 is the system-wide convention for 'no date supplied' consumed correctly by all downstream callers, and that no caller actually needs a hard validation error when a sub-minimum date is passed.
- **RULE-038** (Medium): Confirm registration-number-level charge type configuration is an intended fallback default for institutions without their own override (rather than legacy/dead configuration data).
- **RULE-039** (Medium): Is it intentional that this interface can only retrieve an organization unit's currently valid attributes, with no way to query historical or future-dated attribute versions?

## ⚠ Instruction-shaped content found in source

These lines of the source tried to steer automated analysis. A person should look at them.

- src/FTFRH120.src:10 - comment "THIS MODULE MUST NOT BE MODIFIED BY HUMAN HANDS !!!" is a generic codegen convention warning, not an instruction to this analysis; noted but not followed.
- src/FTFRL120.src:11-12 and src/FTFRK120.src:11-12 - comment "Kode between <META> and </META> tags must not be modified. Do your modifications in the specified user sections!" is a standard code-generator boilerplate directive aimed at COBOL developers, not at automated analysis; noted but not follo…
- src/FTFRH120.src:10 — comment reads "THIS MODULE MUST NOT BE MODIFIED BY HUMAN HANDS !!!"; instruction-shaped text aimed at maintainers/tooling, not acted upon, reported per protocol.

## Rules folded into another

These rules described the same behavior as another rule in a different place, so they were merged into it (the kept rule lists their locations under "Also cited"):

- Actual vs. Work-in-progress data selection (src/FTFRL120.src:233-247) into Contract data-version selection (Actual vs Work)
- Actual vs. Work version selection for charge data (src/FTFRL120.src:233-246) into Contract data-version selection (Actual vs Work)
- Agent commission shortcut for SHOW-AGENT (src/FTFRL120.src:693-747) into SHOW-AGENT operation bypasses normal charge listing
- Agent commission lookup for SHOW-AGENT (src/FTFRL120.src:292-296) into SHOW-AGENT operation bypasses normal charge listing
- Charge amount clamped to configured minimum/maximum (src/FTFRL120.src:856-942) into Minimum/maximum charge caps with percentage recomputation
- Default charge clamped to configured min/max (src/FTFRL120.src:856-942) into Minimum/maximum charge caps with percentage recomputation
- Deriving a percentage from a stored charge amount (src/FTFRL120.src:803-850) into Stored charge percentage back-derived when missing
- Min/max cap re-check for a stored percentage read from Part_Charge (src/F115L070.src:498-619) into Charge amount computed from percentage of contract/part amount, clamped to min/max
- Charge amount is not currency-converted when its currency differs from the contract currency (src/F115L060.src:1064-1093) into Charge currency is re-labelled to contract currency without recalculating the amount
- Tiered (bracket) percentage pricing for volume-based commission (copybooks/F115L150.src? see src/F115L150.src:463-519) into Graduated/tiered percentage pricing across amount bands
- Total charge aggregation by payer and by credited-bank category (src/F115L070.src:637-735) into Buyer/seller charge totals split by credit routing
- Sliding-window century resolution for 2-digit-year dates (src/F7919100.src:951-995) into Sliding-window century determination for two-digit-year dates
- Two-digit-year sliding window century resolution (src/F7919100.src:951-997) into Sliding-window century determination for two-digit-year dates
- Both parties charged for the same fee is not allowed (src/FTFRL120.src:1119-1136) into Buyer/seller both charged is not allowed (single payer per charge)
- Reject duplicate charge when both buyer and seller are billed for the same fee (src/FTFRL120.src:1120-1173) into Buyer/seller both charged is not allowed (single payer per charge)
- Duplicate charge registration guard across main/part charge tables (src/F115L070.src:361-420) into Duplicate/conflicting charge detection between Main and Part charge records
- Required input fields for ReadCharge inquiry (src/FTFRK120.src:145-195) into Mandatory input fields for charge inquiry
- Optional numeric fields must be valid numbers (src/FTFRK120.src:597-667) into Numeric format for optional identifier fields
- Output-only fields rejected as input (src/FTFRK120.src:244-274) into Output fields cannot be supplied as input
- Charge applicability is mapped to paying-party role by product type (src/F115L060.src:479-570) into Fee-payer role determined by contract type and charge role category
