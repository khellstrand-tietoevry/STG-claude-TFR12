# Rule Reviews — STG/TFR12

6 of the 7 P0 rules in `BUSINESS_RULES.md` were flagged for human review (Medium confidence or an open SME question); RULE-004 was High confidence with no open question and was not asked. All 6 were reviewed and confirmed correct as extracted.

| Rule | Verdict | When | Title | Note |
|---|---|---|---|---|
| RULE-001 | confirmed | 2026-09-28T13:20:14Z | Party-specific charge price override (POP) is priced as of today's processing date, not the contract date | |
| RULE-002 | confirmed | 2026-09-28T13:20:14Z | Charge amount computed from percentage of contract/part amount, clamped to min/max | |
| RULE-003 | confirmed | 2026-09-28T13:20:14Z | Graduated/tiered percentage pricing across amount bands | |
| RULE-014 | confirmed | 2026-09-28T13:20:14Z | Duplicate/conflicting charge detection between Main and Part charge records | |
| RULE-015 | confirmed | 2026-09-28T13:20:14Z | Buyer/seller both charged is not allowed (single payer per charge) | |
| RULE-026 | confirmed | 2026-09-28T13:20:14Z | Contract data-version selection (Actual vs Work) | |
