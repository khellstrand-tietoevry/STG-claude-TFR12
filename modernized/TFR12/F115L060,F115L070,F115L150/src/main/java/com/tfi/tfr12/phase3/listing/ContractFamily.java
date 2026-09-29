package com.tfi.tfr12.phase3.listing;

import java.util.Set;

/**
 * The four contract "families" recognized by legacy program {@code F115L060},
 * paragraph {@code C300-Set-Contract-Type} (legacy/TFR12/src/F115L060.src:253-269).
 *
 * <p>RULE-019 (P1, {@code analysis/TFR12/BUSINESS_RULES.md}): every contract type
 * accepted by the charge engine must map to exactly one of these four families; the
 * family drives both which general-ledger account is used
 * (F115L060.src:1001-1017, see {@link #glAccount}) and which paying-party role
 * mapping applies (RULE-033, F115L060.src:499-570). Any contract type outside the
 * fixed list below aborts processing (F115L060.src:266-268, paragraph {@code C301}),
 * so {@link #classify(String)} throws rather than returning a "none of the above"
 * value.
 */
public enum ContractFamily {

    /** {@code Sw-Coll} (F115L060.src:255-259). */
    COLLECTION,
    /** {@code Sw-Imp-LC} (F115L060.src:260-261). */
    IMPORT_LC,
    /** {@code Sw-Exp-LC} (F115L060.src:262-263). */
    EXPORT_LC,
    /** {@code Sw-Gar} (F115L060.src:264-265, added by the "ndl"/"gar" 2013 patch). */
    GUARANTEE;

    private static final Set<String> COLLECTION_TYPES =
            Set.of("IDP", "IDA", "ICP", "ICA", "EDP", "EDA", "ECP", "ECA");
    private static final Set<String> IMPORT_LC_TYPES = Set.of("ILC", "ILU");
    private static final Set<String> EXPORT_LC_TYPES = Set.of("ELC", "ECC");
    private static final Set<String> GUARANTEE_TYPES = Set.of("ILG", "ELG");

    /**
     * C300-Set-Contract-Type (F115L060.src:253-269).
     *
     * @throws UnrecognizedContractTypeException mirroring the {@code Z900-Abend}
     *         (fatal, not a soft validation error) at F115L060.src:266-268
     */
    public static ContractFamily classify(String contractType) {
        if (COLLECTION_TYPES.contains(contractType)) {
            return COLLECTION;
        }
        if (IMPORT_LC_TYPES.contains(contractType)) {
            return IMPORT_LC;
        }
        if (EXPORT_LC_TYPES.contains(contractType)) {
            return EXPORT_LC;
        }
        if (GUARANTEE_TYPES.contains(contractType)) {
            return GUARANTEE;
        }
        throw new UnrecognizedContractTypeException(contractType);
    }

    /**
     * J100-Iter-Output, {@code Evaluate True} on the family switches
     * (F115L060.src:1001-1017): selects which of the charge type's four
     * GL-account fields is used for this family.
     */
    public long glAccount(SysChargeTypeDetails chargeTypeConfig) {
        return switch (this) {
            case COLLECTION -> chargeTypeConfig.glCollAcc();
            case IMPORT_LC -> chargeTypeConfig.glIlcAcc();
            case EXPORT_LC -> chargeTypeConfig.glElcAcc();
            case GUARANTEE -> chargeTypeConfig.glGarAcc();
        };
    }
}
