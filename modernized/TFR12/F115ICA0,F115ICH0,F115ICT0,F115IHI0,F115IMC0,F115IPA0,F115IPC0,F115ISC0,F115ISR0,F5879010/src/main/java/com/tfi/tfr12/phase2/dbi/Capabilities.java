package com.tfi.tfr12.phase2.dbi;

/**
 * Which actions a table's legacy DBI module actually supports, per its own
 * C000-Interface dispatcher. Most tables enable everything; two documented
 * exceptions:
 * <ul>
 *   <li>RULE-027 (F115ISR0 / TF_Sys_Code_Rel): {@code update=false} — a
 *       relationship can only be deleted and re-inserted, never updated in
 *       place (the Update dispatch condition is commented out).</li>
 *   <li>RULE-028 (F115IHI0 / TF_History): {@code select=false, update=false} —
 *       history rows are append-only, readable only via the cursor
 *       (Get-1st/Get-Next), not by a direct single-row select.</li>
 * </ul>
 */
public record Capabilities(boolean select, boolean cursor, boolean insert, boolean update, boolean delete) {

    public static final Capabilities FULL = new Capabilities(true, true, true, true, true);

    /** RULE-027: F115ISR0 (TF_Sys_Code_Rel) — create/delete only. */
    public static final Capabilities CREATE_DELETE_ONLY = new Capabilities(true, true, true, false, true);

    /** RULE-028: F115IHI0 (TF_History) — append-only, cursor read, no direct select or update. */
    public static final Capabilities APPEND_ONLY = new Capabilities(false, true, true, false, true);
}
