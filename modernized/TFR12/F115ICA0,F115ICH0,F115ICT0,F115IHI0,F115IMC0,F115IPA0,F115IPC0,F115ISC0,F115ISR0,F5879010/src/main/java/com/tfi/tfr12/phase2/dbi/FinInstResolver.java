package com.tfi.tfr12.phase2.dbi;

/**
 * Resolves a caller-supplied external institution number to its internal
 * Fin-Inst-ID, mirroring every DBI module's N100-Fetch-Fin-Inst-ID paragraph,
 * which calls F5879010 to do this (RULE-018's documented edge case).
 * Implemented by {@code OrgMasterDataClient} in Phase 2's orgdata package.
 */
public interface FinInstResolver {
    /**
     * @param finInstNo the external institution number
     * @return the resolved internal Fin-Inst-ID
     * @throws MissingKeyFieldException("TF-FIN-INST-MISSING") if it cannot be resolved
     */
    long resolveFinInstId(String finInstNo);
}
