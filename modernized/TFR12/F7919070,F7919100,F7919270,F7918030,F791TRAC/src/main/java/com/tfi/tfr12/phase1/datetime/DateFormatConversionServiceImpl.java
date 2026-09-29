package com.tfi.tfr12.phase1.datetime;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Set;

/**
 * Replacement for legacy program F7919100.src. See {@link DateFormatConversionService}
 * for the contract, and RULE-036/RULE-037 in analysis/STG/TFR12/BUSINESS_RULES.md
 * for the two confirmed business rules encoded here.
 *
 * <p><b>Scope gap (see TRANSFORMATION_NOTES.md):</b> JULIAN, A4D3 and A2D3 are
 * only characterized here as OUTPUT formats. As INPUT (source) formats they are
 * implemented per the documented forward formulas' natural inverse, but no
 * characterization test exercises them as a source, so treat that path as
 * unverified until a follow-up test pins it.
 */
public final class DateFormatConversionServiceImpl implements DateFormatConversionService {

    @Override
    public DateConversionResult convert(DateConversionRequest request, Clock clock) {
        int year;
        int month;
        int day;

        try {
            ParsedDate parsed = parseSource(request, clock);
            year = parsed.year;
            month = parsed.month;
            day = parsed.day;
            // Final calendar validation (F7919040-Valider-Dato / F000-Glidende-Vindu's
            // own LocalDate.of calls already validate 2-digit-year sources; this
            // covers the 4-digit-year sources too).
            LocalDate.of(year, month, day);
        } catch (DateTimeException | NumberFormatException | IndexOutOfBoundsException e) {
            throw new DateConversionException("Invalid date '" + request.sourceValue()
                    + "' for source format " + request.sourceFormat() + ": " + e.getMessage());
        }

        Set<DateFormat> targets = request.targetFormats() != null
                ? request.targetFormats()
                : DateConversionRequest.DEFAULT_TARGET_FORMATS;

        String amd9 = String.format("%04d%02d%02d", year, month, day);
        String dma9 = targets.contains(DateFormat.DMA9) ? String.format("%02d%02d%04d", day, month, year) : null;
        String amd7 = targets.contains(DateFormat.AMD7) ? String.format("%02d%02d%02d", year % 100, month, day) : null;
        String dma7 = targets.contains(DateFormat.DMA7) ? String.format("%02d%02d%02d", day, month, year % 100) : null;
        Integer julian = targets.contains(DateFormat.JULIAN)
                ? Math.max(0, (year - 1900) * 360 + (month - 1) * 30 + day)
                : null;
        String db2 = targets.contains(DateFormat.DB2) ? String.format("%04d-%02d-%02d", year, month, day) : null;
        String a4d3 = null;
        String a2d3 = null;
        if (targets.contains(DateFormat.A4D3) || targets.contains(DateFormat.A2D3)) {
            int ordinal = LocalDate.of(year, month, day).getDayOfYear();
            if (targets.contains(DateFormat.A4D3)) {
                a4d3 = String.format("%04d%03d", year, ordinal);
            }
            if (targets.contains(DateFormat.A2D3)) {
                a2d3 = String.format("%02d%03d", year % 100, ordinal);
            }
        }

        return new DateConversionResult(amd9, dma9, amd7, dma7, julian, db2, a4d3, a2d3);
    }

    private record ParsedDate(int year, int month, int day) {
    }

    private static final long AMD9_MIN = 10_101L;      // 00010101
    private static final long DMA9_MIN = 1_010_001L;   // 01010001
    private static final long AMD7_MIN = 101L;         // 000101
    private static final long DMA7_MIN = 10_100L;      // 010100

    private ParsedDate parseSource(DateConversionRequest request, Clock clock) {
        String v = request.sourceValue();
        switch (request.sourceFormat()) {
            case AMD9 -> {
                if (Long.parseLong(v) < AMD9_MIN) {
                    return sentinel();
                }
                return new ParsedDate(Integer.parseInt(v.substring(0, 4)),
                        Integer.parseInt(v.substring(4, 6)), Integer.parseInt(v.substring(6, 8)));
            }
            case DMA9 -> {
                if (Long.parseLong(v) < DMA9_MIN) {
                    return sentinel();
                }
                return new ParsedDate(Integer.parseInt(v.substring(4, 8)),
                        Integer.parseInt(v.substring(2, 4)), Integer.parseInt(v.substring(0, 2)));
            }
            case AMD7 -> {
                if (Long.parseLong(v) < AMD7_MIN) {
                    return sentinel();
                }
                int rawYear = Integer.parseInt(v.substring(0, 2));
                int month = Integer.parseInt(v.substring(2, 4));
                int day = Integer.parseInt(v.substring(4, 6));
                return new ParsedDate(resolveCentury(rawYear, month, day, clock, request.yearsAheadOverride()), month, day);
            }
            case DMA7 -> {
                if (Long.parseLong(v) < DMA7_MIN) {
                    return sentinel();
                }
                int day = Integer.parseInt(v.substring(0, 2));
                int month = Integer.parseInt(v.substring(2, 4));
                int rawYear = Integer.parseInt(v.substring(4, 6));
                return new ParsedDate(resolveCentury(rawYear, month, day, clock, request.yearsAheadOverride()), month, day);
            }
            case JULIAN -> {
                // Inverse of (year-1900)*360 + (month-1)*30 + day. Not characterized
                // by a test as an input path - see the class-level scope note.
                long j = Long.parseLong(v);
                int year = (int) (1900 + j / 360);
                int rem = (int) (j % 360);
                int month = rem / 30 + 1;
                int day = rem % 30;
                if (day == 0) {
                    day = 30;
                    month -= 1;
                }
                return new ParsedDate(year, month, day);
            }
            case DB2 -> {
                return parseDb2(v);
            }
            case A4D3 -> {
                // Not characterized by a test as an input path.
                int year = Integer.parseInt(v.substring(0, 4));
                int ordinal = Integer.parseInt(v.substring(4, 7));
                LocalDate d = LocalDate.ofYearDay(year, ordinal);
                return new ParsedDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
            }
            case A2D3 -> {
                // Not characterized by a test as an input path. RULE-037's window
                // also applies here per F7919100.src D700-Konv-Fra-A2D3.
                int rawYear = Integer.parseInt(v.substring(0, 2));
                int ordinal = Integer.parseInt(v.substring(2, 5));
                int tentativeFullYear = ((LocalDate.now(clock).getYear() / 100) * 100) + rawYear;
                LocalDate tentative = LocalDate.ofYearDay(tentativeFullYear, ordinal);
                int resolvedYear = resolveCentury(rawYear, tentative.getMonthValue(), tentative.getDayOfMonth(),
                        clock, request.yearsAheadOverride());
                LocalDate resolved = LocalDate.ofYearDay(resolvedYear, ordinal);
                return new ParsedDate(resolved.getYear(), resolved.getMonthValue(), resolved.getDayOfMonth());
            }
            default -> throw new DateConversionException("Unsupported source format: " + request.sourceFormat());
        }
    }

    private ParsedDate sentinel() {
        return new ParsedDate(1, 1, 1);
    }

    /** RULE-037: sliding-window century determination, compared at full-date precision. */
    private int resolveCentury(int rawYear2, int month, int day, Clock clock, Integer yearsAheadOverride) {
        int yearsAhead = (yearsAheadOverride == null || yearsAheadOverride == 0) ? 50 : yearsAheadOverride;
        LocalDate today = LocalDate.now(clock);
        LocalDate windowUpperBound = today.plusYears(yearsAhead);
        int currentCenturyBase = (today.getYear() / 100) * 100;
        int candidateYear = currentCenturyBase + rawYear2;
        LocalDate candidate = LocalDate.of(candidateYear, month, day);
        if (candidate.isAfter(windowUpperBound)) {
            candidateYear -= 100;
        }
        return candidateYear;
    }

    /**
     * F7919100.src D501: detects ISO ("YYYY-MM-DD"), EUR ("dd.mm.yyyy") or USA
     * ("mm/dd/yyyy") by delimiter; anything else is rejected. A blank value
     * (COBOL Spaces) is treated as the RULE-036 sentinel, consistent with the
     * rest of the rule - not directly characterized by a test, see
     * TRANSFORMATION_NOTES.md.
     */
    private ParsedDate parseDb2(String v) {
        if (v == null || v.isBlank()) {
            return sentinel();
        }
        if (v.contains("-")) {
            String[] parts = v.split("-");
            return new ParsedDate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        if (v.contains(".")) {
            String[] parts = v.split("\\.");
            return new ParsedDate(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
        if (v.contains("/")) {
            String[] parts = v.split("/");
            return new ParsedDate(Integer.parseInt(parts[2]), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        throw new DateConversionException("Unrecognized DB2 date delimiter: " + v);
    }
}
