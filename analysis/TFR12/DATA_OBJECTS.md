# Data Objects — STG/TFR12

## RTFRE120 (TFR12-V01) — Service Input/Output Record
Source: `legacy/STG/TFR12/copybooks/RTFRE120.copy:18`

| Field | Type | Note |
|---|---|---|
| RTFRE120-I-Fin-Inst-No-D | X(18) | Mandatory input (checked FTFRK120.src:155-161). |
| RTFRE120-I-Contract-Type-D | X(3) | Mandatory input (FTFRK120.src:163-173); drives GL/applicability classification via TF_Sys_Code lookup. |
| RTFRE120-I-Contract-No-D | 9(7) | Mandatory (FTFRK120.src:175-183); must be numeric if present (FTFRK120.src:597-610). |
| RTFRE120-I-Part-Amount-No-D | 9(2) | Optional; must be numeric if present (FTFRK120.src:612-626); presence toggles Main-only vs Main+Part charge scope (F115L070.src:234-249). |
| RTFRE120-I-Operation-Type-D | X(20) | Mandatory (FTFRK120.src:185-195); values SHOW-AGENT (FTFRL120.src:236,292) and ACCEPT-ILC (FTFRL120.src:1021) trigger special-case handling. |
| RTFRE120-O-Last-Saved-Date-D / O-Agent-Commission-D / O-Total-Ch-Buyer-D / O-To… | X(26) / 9(14)V9(3) | Output-only fields; rejected if supplied as input (FTFRK120.src:250-352). |
| RTFRE120-O-Charge-Type-T / O-Charge-Amount-T / O-Currency-T / O-Paying-Party-T… | table, 20 X(20)/9(14)V9(3)/etc. | Output charge list, one row per charge type; populated from R115L070/WA-Charge working storage in FTFRL120. |
| RTFRE120-O-Period-D / B-Read-From-D / B-Read-To-D | 9(2)/9(4) | Optional identifier fields validated numeric if present (FTFRK120.src:628-667). |

Used by: Mandatory input fields for charge inquiry, Output fields cannot be supplied as input, Numeric format for optional identifier fields, SHOW-AGENT operation bypasses normal charge listing, ACCEPT-ILC special charge-row consolidation, Buyer/seller charge totals split by credit routing, Mandatory composite natural key to identify a Trade Finance contract/charge rec…

## R400CH01-STG-Header
Source: `legacy/STG/TFR12/copybooks/R400CH02.copy:23`

| Field | Type | Note |
|---|---|---|
| R400CH01-Status-Code | X(02) | 88-levels OK(spaces)/Warning(AW)/Error(AE)/Severe-Error(AS) (R400CH02.copy:81-85); set by Z600/Z700/Z800/Z900 paragraphs in FTFRK120.src:875-975. |
| R400CH01-Message-Code / Message-Key / Message-Data | X(30)/X(10)/X(60) | Populated on every validation failure, e.g. FTFRK120.src:157-160. |
| R400CH01-Fininst-Id / Fininst-Id-Data | 9(18) | Carries resolved institution id alongside header. |
| R400CH01-System-Date | 9(08) | Used as G100-Read-Main-Contract / SW-Actual vs SW-Work switch input (FTFRL120.src:230-244) and as 'today' basis for org-unit style as-of lookups. |

Used by: Standard status-code lifecycle for the charge service, Mandatory input fields for charge inquiry, Output fields cannot be supplied as input, Contract data-version selection (Actual vs Work)

## R7919999-Error-Area (Standard error/abend area)
Source: `legacy/STG/TFR12/copybooks/R7919999.copy:16`

| Field | Type | Note |
|---|---|---|
| R7919999-Return-Code | S9(01) Comp-3 | 88-levels Return-OK(0,4,5)/Return-Warning(4,5)/Return-Error(1,2)/Return-Abend(9) (R7919999.copy:27-34); the four-state lifecycle every DBI and Servo module eva… |
| R7919999-Module-Tab (Module-Name, Module-Para) Occurs 20 | X(08)/X(04) | Abend call-stack trace, appended in Z900-Abend (FTFRK120.src:956-968). |
| R7919999-Message | X(80) | Free-text error detail, e.g. F115L070.src Z000-Error-Message. |

Used by: Standard status-code lifecycle for the charge service, Charge inquiry requires the referenced contract/amount data to exist

## R115ICA0-Contract-Amount (DBI: TF_Contract_Amount)
Source: `legacy/STG/TFR12/copybooks/R115ICA0.copy:65`

| Field | Type | Note |
|---|---|---|
| R115ICA0-In-Table-Version | X(16), 88 In-Actual/In-Work | Actual vs Work version selector (R115ICA0.copy:103-105); read in F115L070.src:202-208, F115L150.src:309-314. |
| R115ICA0-Fin-Inst-ID / Contract-Type / Contract-No / Currency | S9(18) Comp-3 / X(03) / S9(07) Comp-3 / X(03) | Composite natural key. |
| R115ICA0-Original-Amount | S9(14)V9(03) Comp-3 | Original contract amount field. |
| R115ICA0-Remaining-Amount | S9(14)V9(03) Comp-3 | Outstanding balance; superseded by Curr-O-Amount for pct-of-CA calc (F115L060.src:1341-1361, comment 'Feilretting NDL, beløp hentes fra curr-original amount'). |
| R115ICA0-Curr-O-Amount | S9(14)V9(03) Comp-3 | Field actually used for percentage-of-contract-amount charges with no part amount, in place of Remaining-Amount (F115L060.src:1347-1361; F115L150.src:454-460). |
| R115ICA0-Min-Pct-Tolerance / Max-Pct-Tolerance / Max-Amount | S9(04) Comp / Comp-3 | Tolerance and DBI-computed max cap fields (R115ICA0.copy:149-156). |

Used by: Contract data-version selection (Actual vs Work), Percentage-of-contract-amount charges without a part amount are based on the or…, Percentage-based charge amount derived from Contract or Part Amount, Charge inquiry requires the referenced contract/amount data to exist, Charge currency is re-labelled to contract currency without recalculating the a…, Mandatory composite natural key to identify a Trade Finance contract/charge rec…

## R115ICH0-Main-Charge (DBI: TF_Main_Charge)
Source: `legacy/STG/TFR12/copybooks/R115ICH0.copy:71`

| Field | Type | Note |
|---|---|---|
| R115ICH0-Fin-Inst-ID / Fin-Inst-No | S9(18) Comp-3 / X(18) | ID resolved from No via N100-Fetch-Fin-Inst-ID when only No supplied (F115ICH0.src:874-889). |
| R115ICH0-Contract-Type / Contract-No | X(03) / S9(07) Comp-3 | Part of composite key. |
| R115ICH0-Related-Party (CA-Currency, PA-Seq-No, RP-Role-Type) | X(03)/S9(04) Comp/X(05) | Composite key segment identifying payer role. |
| R115ICH0-Charge-Type / CH-Seq-No | X(20)/S9(04) Comp | Composite key tail. |
| R115ICH0-Charge-Amount / Currency / Charge-Pct / Charge-Text | S9(14)V9(03) Comp-3 / X(03) / S9(03)V9(03) Comp-3 / X(30) | Stored charge payload. |

Used by: Duplicate/conflicting charge detection between Main and Part charge records, Consistency between main and part charge records, Part-charge paying party overrides main-charge paying party, Mandatory composite natural key to identify a Trade Finance contract/charge rec…, Charge record update requires a fully resolvable composite key (asymmetric with…, Charge computation scope follows whether a part amount is specified, Stored charge percentage back-derived when missing

## R115IPC0-Part-Charge (DBI: TF_Part_Charge)
Source: `legacy/STG/TFR12/copybooks/R115IPC0.copy:72`

| Field | Type | Note |
|---|---|---|
| R115IPC0-Fin-Inst-ID / Contract-Type / Contract-No | S9(18) Comp-3 / X(03) / S9(07) Comp-3 | Composite key head. |
| R115IPC0-Part-Amount (CA-Currency, PA-Seq-No) | X(03)/S9(04) Comp | Ties charge to a specific part amount. |
| R115IPC0-Role-Type / Charge (CT-Type) | X(05)/X(20) | Composite key tail. |
| R115IPC0-Amount / Currency / Role-Paying-Pty / Charge-Pct / Charge-Text | S9(14)V9(03) Comp-3 / X(03) / X(05) / S9(03)V9(03) Comp-3 /… | Role-Paying-Pty is a single field — only one payer per charge row. |

Used by: Duplicate/conflicting charge detection between Main and Part charge records, Consistency between main and part charge records, Part-charge paying party overrides main-charge paying party, Buyer/seller both charged is not allowed (single payer per charge), Settlement/part-amount charges are summed, not recalculated

## R115IPA0-Part-Amount (DBI: TF_Part_Amount)
Source: `legacy/STG/TFR12/copybooks/R115IPA0.copy:73`

| Field | Type | Note |
|---|---|---|
| R115IPA0-Key (Fin-Inst-ID, Contract-ID, CA-Currency, PA-Seq-No) | composite | Composite natural key. |
| R115IPA0-Amount / Prev-Amount / Curr-Amount / Excess-Amount | S9(14)V9(03) Comp-3 | Part-amount used as %-of-PA basis (F115L070.src:544-546; F115L060.src:1354-1356). |
| R115IPA0-Incom-Swift | X(01), 88 Incom-Yes/Incom-No/Has-Been-Settled('A') | Has-Been-Settled flag (R115IPA0.copy:183) suppresses percentage recalculation (F115L060.src:604-628). |
| R115IPA0-Status / In-Progress | X(12)/X(01) | Part-amount lifecycle status. |

Used by: Percentage-based charge amount derived from Contract or Part Amount, Settled part-amount suppresses recalculation of percentage-based charge, Charge computation scope follows whether a part amount is specified, Charge data scope depends on whether a part-amount is specified

## R115IMC0-Main-Contract (DBI: TF_Main_Contract)
Source: `legacy/STG/TFR12/copybooks/R115IMC0.copy:70`

| Field | Type | Note |
|---|---|---|
| R115IMC0-In-Table-Version | X(16), 88 In-Actual/In-Work | Actual/Work version selector. |
| R115IMC0-Key (Fin-Inst-ID, Customer-ID, Contract-Type, Contract-No) | composite | Composite natural key, ID resolvable from No (R115IMC0.copy:117-127 pattern). |
| R115IMC0-Status / In-Progress | X(12)/X(01) | In-Progress-Yes drives SW-Work vs SW-Actual selection in FTFRL120.src:238-244. |
| R115IMC0-Availability | X(01), 88 At-Sight/Deferred/Accept/Mixed | Contract classification attribute. |

Used by: Contract data-version selection (Actual vs Work), Mandatory composite natural key to identify a Trade Finance contract/charge rec…, SHOW-AGENT operation bypasses normal charge listing

## R115IHI0-History (DBI: TF_History, append-only)
Source: `legacy/STG/TFR12/copybooks/R115IHI0.copy:60`

| Field | Type | Note |
|---|---|---|
| R115IHI0-In-Action | X(16), 88 In-Get-1st/In-Get-Next/In-Insert/In-Delete only | No In-Select or In-Update 88-level defined (R115IHI0.copy:69-73) — table cannot be selected/updated in place, only appended or (rarely) deleted. |
| R115IHI0-Key (Fin-Inst-ID, Contract-ID, Part-Amount, HI-Seq-No) | composite | Composite key including sequence number. |
| R115IHI0-Operation-Type / User-Id / Timestamp / Text | X(20)/X(07)/X(26)/X(150) | Audit-trail payload; Text is redefined for Agent-Text and SilentC-Text messages (R115IHI0.copy:153-171). |

Used by: History records are append-only (no direct select or update)

## R115ICT0-Sys-Charge-Type (DBI: TF_Sys_Charge_Type)
Source: `legacy/STG/TFR12/copybooks/R115ICT0.copy:58`

| Field | Type | Note |
|---|---|---|
| R115ICT0-Key (Fin-Inst-Id, CT-Type, Regno) | S9(18) Comp-3/X(20)/S9(04) Comp | Lookup first tries Fin-Inst-Id; falls back to Regno if not found (F115L150.src:209-234). |
| R115ICT0-Method-Calc | X(01), 88 Part-Amount/Canceling/Settlement/Pct-Of-CA/Pct-Of… | Drives all charge-computation branching (F115L070.src:498-634; F115L060.src:1341-1364). |
| R115ICT0-Credit | X(01), 88 Agent/Other-Bank/Own-Bank | Credit routing basis for buyer/seller totals (F115L070.src:637-720; F115L060.src:1196-1249). |
| R115ICT0-Paying-Pty | X(06), 88 ANY/Buyer/Seller/GLACC/Agent-pty | Combined with contract-type switches to derive fee-payer role (F115L060.src:499-560+). |
| R115ICT0-Read-POP | X(01), 88 Read-POP-Yes(space)/Read-POP-No('N') | Opt-out flag for party-specific price override lookup (F115L060.src:1097-1101). |
| R115ICT0-GL-Coll-Acc / GL-ELC-Acc / GL-ILC-Acc / GL-E-Coll-Acc / GL-GAR-Acc / P… | S9(11) Comp-3 / X(06) / S9(05) Comp-3 / S9(03)v9(2) Comp-3 | GL account and applicability configuration, keyed by contract type via TF_Sys_Code classification. |

Used by: Contract-type classification for GL account and applicability rules, Charge-type configuration falls back from institution-specific to registration-…, Party-specific charge price override (POP) is priced as of today's processing d…, Fee-payer role determined by contract type and charge role category, Percentage-based charge amount derived from Contract or Part Amount, Minimum/maximum charge caps with percentage recomputation, Buyer/seller charge totals split by credit routing, Settlement/part-amount charges are summed, not recalculated

## R115ISC0-Sys-Code (DBI: TF_Sys_Code)
Source: `legacy/STG/TFR12/copybooks/R115ISC0.copy:54`

| Field | Type | Note |
|---|---|---|
| R115ISC0-Key (ST-Type, Fin-Inst-Id, Contr-Type, SC-Code) | X(20)/S9(18) Comp-3/X(03)/X(20) | ST-Type='CONTRACT-TYPE' used to classify contracts (F115L150.src:172-186). |
| R115ISC0-Property (redefined: Contract-Name, Contr-Imp-Exp, Range-Type, Product… | X(80) redefined group | Product-No/Product-Seq-No drive product-pricing lookup key (F115L150.src:263-273). |

Used by: Contract-type classification for GL account and applicability rules, System-Code update key validation is unintentionally bypassed when Contract-Typ…, Fee-payer role determined by contract type and charge role category

## R115ISR0-Sys-Code-Rel (DBI: TF_Sys_Code_Rel, create/delete only)
Source: `legacy/STG/TFR12/copybooks/R115ISR0.copy:55`

| Field | Type | Note |
|---|---|---|
| R115ISR0-In-Action | X(16), 88 In-Get-1st/In-Get-Next/In-Select/In-Insert/In-Del… | 88 In-Update is commented out (R115ISR0.copy:69: '* 88 R115ISR0-In-Update Value ''UPDATE''.') — no update path exists. |
| R115ISR0-From (ST-Type, Contr-Type, SC-Code) / To (ST-Type, Contr-Type, SC-Code… | X(20)/X(03)/X(20) | Maps e.g. Operation-Type to permissible Charge-Type (consumed in F115L060.src:484-560, D000-Read-Permissible-Charges). |

Used by: System code relationship records (e.g. valid Operation-Type to Charge-Type mapp…, Fee-payer role determined by contract type and charge role category

## R349IPP0-Prod-Tjen-Pris (DBI: PP_Prod_Tjen_Pris, product pricing)
Source: `legacy/STG/TFR12/copybooks/R349IPP0.copy:55`

| Field | Type | Note |
|---|---|---|
| R349IPP0-Prisbelop / Prisprosent | S9(7)V9(2) Comp-3 / S9(2)V9(3) Comp-3 | Flat-amount and percentage price; both non-zero is rejected as 'TF-SY-INVALID-CONTENT-TAB-VAR1' (F115L150.src:392-411). |
| R349IPP0-Ovre-Prisbelop / Ovre-Prispst / Nedre-Prisbelop / Nedre-Prispst | S9(7)V9(2)/S9(2)V9(3) Comp-3 | Upper/lower price band bounds; used in graduated pricing loop (F115L150.src:463-519). |
| R349IPP0-Gyldig-Til-Belop / Gyldig-Fra-Dato / Gyldig-Til-Dato | S9(9) Comp/Comp-3 | Amount-band and validity-date qualification for tiered pricing rows. |
| R349IPP0-Minimumpris / Maksimumpris | S9(9) Comp | Min/max charge caps carried through to WS-POP-Min/Max-Charge. |

Used by: Product pricing must define either a flat amount or a percentage, not both, Graduated/tiered percentage pricing across amount bands, Product pricing amounts are converted from the bank's base currency to the cont…, Party-specific charge price override (POP) is priced as of today's processing d…

## R116L030-Currency-Xchange (currency conversion utility, LMOD VA)
Source: `legacy/STG/TFR12/copybooks/R116L030.copy:67`

| Field | Type | Note |
|---|---|---|
| R116L030-In-From-Currency / In-To-Currency | X(03), 88 Basic-Cur('***') | F115L150.src:471-479/541-547 sets From=Basic-Cur (bank base currency), To=contract currency (R115ICA0-Currency). |
| R116L030-In-From-Amount / To-Amount | S9(14)V9(03) Comp-3 | Converted product-pricing amount. |
| R116L030-In-Which-Rate | X(01), 88 Use-Parameter/Buy/Sell/Hist-Rate | Rate-selection qualifier. |

Used by: Product pricing amounts are converted from the bank's base currency to the cont…

## R115L060-Comp-Charge (internal working record: Charge Listing/Computation, 'as-stored')
Source: `legacy/STG/TFR12/copybooks/R115L060.copy:18`

| Field | Type | Note |
|---|---|---|
| R115L060-In-PA-Seq-No | S9(04) Comp | Zero => read TF_Main_Charge only; non-zero => read TF_Part_Charge (R115L060.copy comment lines 6-9; F115L060.src:604-630). |
| R115L060-Charge-Element (Occurs 50): Charge-Type, Charge-Method-Calc(88s), Char… | table | Seller = DRWER/BENEF; Buyer = DRWEE/ISSUI/CUSTM/spaces (R115L060.copy:74-80). |
| R115L060-Totals-Seller / Totals-Buyer (Ch-*-Cred-Agent/T-Bank/W-Bank, Total-Cha… | S9(14)V9(3) Comp-3 group | Per-payer, per-credit-route accumulators. |
| R115L060-Totals-Agent (Tot-Agent-Commission) | S9(14)V9(3) Comp-3 | Agent commission total. |

Used by: Percentage-based charge amount derived from Contract or Part Amount, Minimum/maximum charge caps with percentage recomputation, Duplicate/conflicting charge detection between Main and Part charge records, Consistency between main and part charge records, Part-charge paying party overrides main-charge paying party, Buyer/seller both charged is not allowed (single payer per charge), Buyer/seller charge totals split by credit routing, Settlement/part-amount charges are summed, not recalculated, Percentage-of-contract-amount charges without a part amount are based on the or…, Settled part-amount suppresses recalculation of percentage-based charge, Charge currency is re-labelled to contract currency without recalculating the a…, Fee-payer role determined by contract type and charge role category, Charge computation scope follows whether a part amount is specified, Charge data scope depends on whether a part-amount is specified, Stored charge percentage back-derived when missing

## R115L070-Comp-Charge (internal/external record: Charge Listing for presentation & input reversal)
Source: `legacy/STG/TFR12/copybooks/R115L070.copy:23`

| Field | Type | Note |
|---|---|---|
| R115L070-In-Action | X(16), 88 In-Add/In-Subtract | Add = figures for display/settlement; Subtract = modify end-user input for DB update (R115L070.copy:5-19; F115L070.src:123-180). |
| R115L070-Charge-Element (Occurs 50): Charge-Type, Charge-Method-Calc, Charge-Ro… | table | Same shape as R115L060 but merged/adjusted figures, not raw DB values. |
| R115L070-Totals-Seller / Totals-Buyer / Totals-Agent | S9(14)V9(3) Comp-3 group | Consumed directly by FTFRL120 to populate RTFRE120-O-Total-Ch-*. |

Used by: Subtracting already-stored charges when reversing input, floored at zero, Settlement/part-amount charges are summed, not recalculated, Percentage-based charge amount derived from Contract or Part Amount, Minimum/maximum charge caps with percentage recomputation, ACCEPT-ILC special charge-row consolidation, Buyer/seller charge totals split by credit routing

## R115L150-TF-POP (Read POP / party-specific price override result)
Source: `legacy/STG/TFR12/copybooks/R115L150.copy:9`

| Field | Type | Note |
|---|---|---|
| R115L150-In-Table-Version | X(16), 88 In-Actual/In-Work | Actual/Work selector passed through from caller. |
| R115L150-In-Valid-Date | S9(09) Comp-3 | Set to R7919070-Ut-Dato (today) in F115L060.src:1278, or R400CH01-System-Date in FTFRL120.src:991 — always 'today', never contract date. |
| R115L150-Default-Charge (Percent-Comm, Charge-Amount, Currency) | S9(02)V9(03)/S9(14)V9(03) Comp-3/X(03) | Resolved override price. |
| R115L150-Max-Min-Charge (Maximum-Charge, Minimum-Charge) | S9(14)V9(03) Comp-3 | Converted min/max caps. |

Used by: Party-specific charge price override (POP) is priced as of today's processing d…, Contract data-version selection (Actual vs Work)

## R7919100-Konverter-Dato (date-format conversion utility)
Source: `legacy/STG/TFR12/copybooks/R7919100.copy:34`

| Field | Type | Note |
|---|---|---|
| R7919100-In-Glidende-Vindu (In-Aar-Frem-I-Tid) | 9(02) | 'Sliding window' for two-digit-year century resolution; default 49 years before / 50 years after today (R7919100.copy:115-121); implemented in F7919100.src F00… |
| R7919100-In-Dato-AMD9/DMA9/AMD7/DMA7/JUL/DB2/A4D3/A2D3 | various Comp-3/X | Multi-format date input. |
| R7919100-Ut-Dato-* (same format set) | various | When all input dates are zero, output AMD9 is forced to sentinel '00010101' rather than aborting (F7919100.src:498-513). |

Used by: Sliding-window century determination for two-digit-year dates, Sub-minimum input date normalized to sentinel null date
