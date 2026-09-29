package com.tfi.tfr12.phase3.merge;

/**
 * Port standing in for F115L070's two calls to legacy program F115L060.src
 * (C300-Read-Main-Charge, F115L070.src:252-272; C400-Read-Part-Charge,
 * F115L070.src:274-293) - "Call 'F115L060' Using W-R115L060-{Main,Part},
 * R7919999-Error-Area".
 *
 * <p>See {@link ChargeListingSnapshot}'s javadoc for why this call-boundary
 * port lives here in {@code com.tfi.tfr12.phase3.merge} rather than in
 * {@code com.tfi.tfr12.phase3.listing}: F115L060's own module (a sibling work
 * item, out of this module's strict scope) does not yet expose a method at
 * this granularity, only its individual internal paragraphs. This port
 * implements/guesses none of F115L060's own internal computation
 * (RULE-001/003/004/006/007/011/012/013/019/029/033/038/039 all live there).
 *
 * <p>Deliberately NOT modeled: F115L060 call failures (Return-Abend "C231"/
 * "C233", Return-Error "C232"/"C234", F115L070.src:258-271, 280-292) - a real
 * implementation is free to throw its own exception for those; F115L070's own
 * 9 pinned rules for this module do not depend on that failure-path detail.
 */
public interface ChargeListingPort {

    /**
     * @param query qualification fields for one Main_Charge or Part_Charge
     *              read - see {@link ChargeListingQuery#paSeqNo()}
     * @return the charge rows and totals exactly as stored on the one table
     *         selected by {@code query.paSeqNo()}
     */
    ChargeListingSnapshot readCharges(ChargeListingQuery query);
}
