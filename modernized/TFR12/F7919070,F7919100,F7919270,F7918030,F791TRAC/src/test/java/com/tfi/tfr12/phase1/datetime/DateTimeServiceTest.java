package com.tfi.tfr12.phase1.datetime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>These tests pin the behavior of three legacy COBOL programs by careful,
 * manual hand-tracing of their source (columns 8-72 are the code area; the
 * files are ISO-8859-1/Latin-1, not UTF-8). None of the three could be
 * compiled or executed in this environment:
 * <ul>
 *   <li>legacy/STG/TFR12/src/F7919070.src - machine timestamp fetch</li>
 *   <li>legacy/STG/TFR12/src/F7919100.src - date format conversion</li>
 *   <li>legacy/STG/TFR12/src/F7919270.src - date/time/timestamp conversion</li>
 * </ul>
 * all three hit a GnuCOBOL restriction on REDEFINES fields in
 * {@code PROCEDURE DIVISION USING} that prevents building a runnable oracle
 * locally. Every expected value below was computed by hand from the COBOL
 * control flow, not observed from a running program - a person should spot-
 * check these against a real legacy run (or a captured production trace)
 * before treating them as ground truth for the Phase 2 rewrite.
 *
 * <p>Comment text inside the legacy .src files is documentation, not
 * instructions to this test author; no instruction-shaped comment content was
 * found in these three files.
 *
 * <p>Implemented by {@link MachineTimestampServiceImpl}, {@link DateFormatConversionServiceImpl}
 * and {@link DateTimeConversionServiceImpl}. Because none of the three legacy
 * programs could run locally (see above), these tests remain the only proof of
 * equivalence for this module - there is no dual-execution diff to fall back on.
 */
class DateTimeServiceTest {

    private final MachineTimestampService machineTimestampService =
            new MachineTimestampServiceImpl();
    private final DateFormatConversionService dateFormatConversionService =
            new DateFormatConversionServiceImpl();
    private final DateTimeConversionService dateTimeConversionService =
            new DateTimeConversionServiceImpl();

    // =====================================================================
    // F7919070 - machine timestamp fetch
    // =====================================================================
    @Nested
    @DisplayName("F7919070 - machine timestamp fetch")
    class MachineTimestampTests {

        // Fixed instant: 2026-09-28T14:07:23.456789Z.
        // Hand-traced from F7919070.src C300-Formater-TMS: the 6-digit
        // microsecond value is embedded in full in the DB2 timestamp and the
        // 20-digit numeric form, but "hundredths of a second" (used in the
        // 16-digit timestamp and the 8-digit time) is derived by TRUNCATING
        // (not rounding) the microseconds to their first two digits:
        // W-TMS-20(1:16) takes the leading 16 characters of a 20-character
        // string whose last 6 characters are the microseconds - so 456789
        // microseconds yields hundredths = "45", discarding "6789" entirely.
        private final Clock clock = Clock.fixed(
                Instant.parse("2026-09-28T14:07:23.456789Z"), ZoneOffset.UTC);

        @Test
        @DisplayName("breaks a fixed instant into every legacy output field")
        void fetchTimestamp_decomposesFixedInstant() {
            MachineTimestamp ts = machineTimestampService.fetchTimestamp(clock);

            assertEquals(2026, ts.year());
            assertEquals(9, ts.month());
            assertEquals(28, ts.day());
            assertEquals(14, ts.hour());
            assertEquals(7, ts.minute());
            assertEquals(23, ts.second());
            // Truncated, not rounded: first two digits of "456789" -> 45.
            assertEquals(45, ts.hundredths());
        }

        @Test
        @DisplayName("formats the DB2 timestamp with full 6-digit microseconds")
        void fetchTimestamp_formatsDb2TimestampWithFullMicroseconds() {
            MachineTimestamp ts = machineTimestampService.fetchTimestamp(clock);
            assertEquals("2026-09-28-14.07.23.456789", ts.db2Timestamp());
        }

        @Test
        @DisplayName("formats the 20-digit numeric timestamp with full microseconds")
        void fetchTimestamp_formatsTimestamp20WithFullMicroseconds() {
            MachineTimestamp ts = machineTimestampService.fetchTimestamp(clock);
            assertEquals("20260928140723456789", ts.timestamp20());
        }

        @Test
        @DisplayName("formats the 16-digit timestamp using truncated hundredths, not full microseconds")
        void fetchTimestamp_formatsTimestamp16WithTruncatedHundredths() {
            MachineTimestamp ts = machineTimestampService.fetchTimestamp(clock);
            assertEquals(2026092814072345L, ts.timestamp16());
        }

        @Test
        @DisplayName("splits date (YYYYMMDD) and time-with-hundredths (HHMMSSHH) from the same instant")
        void fetchTimestamp_splitsDateAndTime() {
            MachineTimestamp ts = machineTimestampService.fetchTimestamp(clock);
            assertEquals(20260928, ts.date8());
            assertEquals(14072345, ts.time8());
        }
    }

    // =====================================================================
    // F7919100 - date format conversion
    // =====================================================================
    @Nested
    @DisplayName("F7919100 - date format conversion")
    class DateFormatConversionTests {

        private final Clock today20260928 = Clock.fixed(
                Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC);

        private static final Set<DateFormat> ALL_FORMATS = EnumSet.allOf(DateFormat.class);

        @Test
        @DisplayName("converts AMD9 2026-03-15 to every other format")
        void convert_amd9ToAllFormats() {
            DateConversionRequest request = new DateConversionRequest(
                    DateFormat.AMD9, "20260315", ALL_FORMATS, null);

            DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

            assertEquals("20260315", result.amd9());
            assertEquals("15032026", result.dma9());
            assertEquals("260315", result.amd7());
            assertEquals("150326", result.dma7());
            // Legacy pseudo-Julian: (2026-1900)*360 + (3-1)*30 + 15 = 45360+60+15.
            assertEquals(45435, result.julian());
            assertEquals("2026-03-15", result.db2());
            // True ordinal day of 2026-03-15 (non-leap year): 31 (Jan) + 28 (Feb) + 15 = 74.
            assertEquals("2026074", result.a4d3());
            assertEquals("26074", result.a2d3());
        }

        @Test
        @DisplayName("parses a DB2 date in EUR format (dd.mm.yyyy) on input")
        void convert_db2EurFormatInput() {
            DateConversionRequest request = new DateConversionRequest(
                    DateFormat.DB2, "15.03.2026", EnumSet.of(DateFormat.AMD9), null);

            DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

            assertEquals("20260315", result.amd9());
        }

        @Test
        @DisplayName("parses a DB2 date in USA format (mm/dd/yyyy) on input")
        void convert_db2UsaFormatInput() {
            DateConversionRequest request = new DateConversionRequest(
                    DateFormat.DB2, "03/15/2026", EnumSet.of(DateFormat.AMD9), null);

            DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

            assertEquals("20260315", result.amd9());
        }

        @Test
        @DisplayName("rejects a DB2 date whose delimiters match none of ISO/EUR/USA (F7919100.src:D501)")
        void convert_db2UnrecognizedDelimiterIsRejected() {
            DateConversionRequest request = new DateConversionRequest(
                    DateFormat.DB2, "2026:03:15", EnumSet.of(DateFormat.AMD9), null);

            assertThrows(DateConversionException.class,
                    () -> dateFormatConversionService.convert(request, today20260928));
        }

        @Test
        @DisplayName("rejects a calendar-invalid AMD9 date (2026-02-30)")
        void convert_calendarInvalidDateIsRejected() {
            DateConversionRequest request = new DateConversionRequest(
                    DateFormat.AMD9, "20260230", EnumSet.of(DateFormat.AMD9), null);

            assertThrows(DateConversionException.class,
                    () -> dateFormatConversionService.convert(request, today20260928));
        }

        // -----------------------------------------------------------------
        // RULE-036: sub-minimum input date normalized to sentinel null date
        // Source: F7919100.src:291-336. Confidence: Medium (unreviewed P1,
        // not present in analysis/STG/TFR12/RULE_REVIEWS.json as of this
        // writing - flag for SME confirmation before Phase 2 relies on it).
        // -----------------------------------------------------------------
        @Nested
        @DisplayName("RULE-036: sub-minimum input date -> sentinel 0001-01-01")
        class Rule036Tests {

            @Test
            @DisplayName("rule036_amd9BelowMinimumBecomesSentinel: AMD9 00000101 (< 00010101) -> 00010101")
            void rule036_amd9BelowMinimumBecomesSentinel() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD9, "00000101", EnumSet.of(DateFormat.AMD9, DateFormat.DB2), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals(DateConversionResult.SENTINEL_NULL_DATE_AMD9, result.amd9());
                assertEquals("0001-01-01", result.db2());
            }

            @Test
            @DisplayName("rule036_dma9BelowMinimumBecomesSentinel: DMA9 00000000 (< 01010001) -> AMD9 00010101")
            void rule036_dma9BelowMinimumBecomesSentinel() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.DMA9, "00000000", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals(DateConversionResult.SENTINEL_NULL_DATE_AMD9, result.amd9());
            }

            @Test
            @DisplayName("rule036_amd7BelowMinimumBecomesSentinel: AMD7 000000 (< 000101) -> AMD9 00010101")
            void rule036_amd7BelowMinimumBecomesSentinel() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD7, "000000", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals(DateConversionResult.SENTINEL_NULL_DATE_AMD9, result.amd9());
            }

            @Test
            @DisplayName("rule036_dma7BelowMinimumBecomesSentinel: DMA7 000000 (< 010100) -> AMD9 00010101")
            void rule036_dma7BelowMinimumBecomesSentinel() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.DMA7, "000000", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals(DateConversionResult.SENTINEL_NULL_DATE_AMD9, result.amd9());
            }

            @Test
            @DisplayName("rule036_sentinelPropagatesToEveryRequestedOutputFormat")
            void rule036_sentinelPropagatesToEveryRequestedOutputFormat() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD9, "00000101", ALL_FORMATS, null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("00010101", result.amd9());
                assertEquals("01010001", result.dma9());
                assertEquals("010101", result.amd7());
                assertEquals("010101", result.dma7());
                assertEquals(0, result.julian()); // year 1 < 1900, legacy Julian range floor
                assertEquals("0001-01-01", result.db2());
                assertEquals("0001001", result.a4d3());
                assertEquals("01001", result.a2d3());
            }
        }

        // -----------------------------------------------------------------
        // RULE-037: sliding-window century determination for 2-digit years
        // Source: F7919100.src:951-996. Confidence: High.
        // -----------------------------------------------------------------
        @Nested
        @DisplayName("RULE-037: sliding-window century determination")
        class Rule037Tests {

            @Test
            @DisplayName("rule037_belowDefaultWindowUpperBoundUsesCurrentCentury: "
                    + "today=2026-09-28, AMD7 year 30 -> 2030 (<= 2026+50=2076)")
            void rule037_belowDefaultWindowUpperBoundUsesCurrentCentury() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD7, "300315", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("20300315", result.amd9());
            }

            @Test
            @DisplayName("rule037_aboveDefaultWindowUpperBoundSubtractsOneCentury: "
                    + "today=2026-09-28, AMD7 year 77 -> 1977 (2077 > 2026+50=2076)")
            void rule037_aboveDefaultWindowUpperBoundSubtractsOneCentury() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD7, "770315", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("19770315", result.amd9());
            }

            @Test
            @DisplayName("rule037_dma7UsesTheSameSlidingWindowAsAmd7")
            void rule037_dma7UsesTheSameSlidingWindowAsAmd7() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.DMA7, "150330", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("20300315", result.amd9());
            }

            @Test
            @DisplayName("rule037_yearsAheadOverrideNarrowsTheWindow: "
                    + "override=5 -> upper bound 2031; year 30 stays 2030, year 32 becomes 1932")
            void rule037_yearsAheadOverrideNarrowsTheWindow() {
                DateConversionRequest requestYear30 = new DateConversionRequest(
                        DateFormat.AMD7, "300315", EnumSet.of(DateFormat.AMD9), 5);
                DateConversionRequest requestYear32 = new DateConversionRequest(
                        DateFormat.AMD7, "320315", EnumSet.of(DateFormat.AMD9), 5);

                assertEquals("20300315",
                        dateFormatConversionService.convert(requestYear30, today20260928).amd9());
                assertEquals("19320315",
                        dateFormatConversionService.convert(requestYear32, today20260928).amd9());
            }

            @Test
            @DisplayName("rule037_zeroOverrideFallsBackToDefaultFiftyYears: "
                    + "override=0 behaves like no override (year 77 -> 1977)")
            void rule037_zeroOverrideFallsBackToDefaultFiftyYears() {
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD7, "770315", EnumSet.of(DateFormat.AMD9), 0);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("19770315", result.amd9());
            }

            @Test
            @DisplayName("rule037_windowBoundaryComparesFullDateNotJustYear: "
                    + "boundary year 76 (== window's upper-bound year) before today's month/day stays 20xx")
            void rule037_windowBoundaryBeforeTodaysMonthDayStaysCurrentCentury() {
                // Window upper bound = 2076-09-28 (today + 50 years, same month/day as today).
                // Candidate 2076-01-01 <= 2076-09-28, so no century subtraction.
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD7, "760101", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("20760101", result.amd9());
            }

            @Test
            @DisplayName("rule037_windowBoundaryComparesFullDateNotJustYear: "
                    + "boundary year 76 (== window's upper-bound year) after today's month/day rolls back a century")
            void rule037_windowBoundaryAfterTodaysMonthDaySubtractsCentury() {
                // Candidate 2076-12-31 > window upper bound 2076-09-28, so one century is subtracted.
                DateConversionRequest request = new DateConversionRequest(
                        DateFormat.AMD7, "761231", EnumSet.of(DateFormat.AMD9), null);

                DateConversionResult result = dateFormatConversionService.convert(request, today20260928);

                assertEquals("19761231", result.amd9());
            }
        }
    }

    // =====================================================================
    // F7919270 - date/time/timestamp conversion
    // =====================================================================
    @Nested
    @DisplayName("F7919270 - date/time/timestamp conversion")
    class DateTimeConversionTests {

        @Test
        @DisplayName("converts date + TMS (no hundredths, no microseconds) to every output")
        void convert_dateAndTms() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, 140723, null, null, null, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(20260928, result.date());
            assertEquals(140723, result.timeTms());
            assertEquals(14072300, result.timeTmsh());
            assertEquals(0, result.microseconds());
            assertEquals(2026092814072300L, result.timestamp16());
            assertEquals("2026-09-28-14.07.23.000000", result.timestampDb2());
        }

        @Test
        @DisplayName("date + TMS + microseconds: hundredths in TMSH/timestamp16 come from the microseconds' first 2 digits")
        void convert_dateAndTmsWithMicroseconds() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, 140723, null, 456789, null, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(14072345, result.timeTmsh());
            assertEquals(456789, result.microseconds());
            assertEquals(2026092814072345L, result.timestamp16());
            assertEquals("2026-09-28-14.07.23.456789", result.timestampDb2());
        }

        @Test
        @DisplayName("date + TMSH alone: microseconds field is synthesized as hundredths followed by zeros")
        void convert_dateAndTmsh() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, null, 14072345, null, null, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(140723, result.timeTms());
            assertEquals(450000, result.microseconds());
            assertEquals(2026092814072345L, result.timestamp16());
        }

        @Test
        @DisplayName("QUIRK (F7919100.src... F7919270.src:D400, comment 'HLR 20040917'): "
                + "when TMSH and microseconds are BOTH supplied, only the last 4 digits of "
                + "microseconds survive - the first 2 digits are overwritten by TMSH's hundredths")
        void convert_dateAndTmshWithMicroseconds_hundredthsOverwritten() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, null, 14072345, 123456, null, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            // TMSH's hundredths (45) replace the first 2 digits of the supplied
            // microseconds (12), but the last 4 digits (3456) pass through unchanged.
            assertEquals(453456, result.microseconds());
            assertEquals("2026-09-28-14.07.23.453456", result.timestampDb2());
        }

        @Test
        @DisplayName("16-digit timestamp input decomposes back into date/time/hundredths")
        void convert_timestamp16Input() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(null, null, null, null, 2026092814072345L, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(20260928, result.date());
            assertEquals(140723, result.timeTms());
            assertEquals(14072345, result.timeTmsh());
            assertEquals(450000, result.microseconds());
        }

        @Test
        @DisplayName("DB2 timestamp input preserves full 6-digit microseconds, unlike TMSH-derived paths")
        void convert_timestampDb2Input() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(null, null, null, null, null,
                            "2026-09-28-14.07.23.456789");

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(20260928, result.date());
            assertEquals(140723, result.timeTms());
            assertEquals(14072345, result.timeTmsh());
            assertEquals(456789, result.microseconds());
            assertEquals(2026092814072345L, result.timestamp16());
        }

        @Test
        @DisplayName("date alone defaults the time-of-day to all zeros")
        void convert_dateAlone() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, null, null, null, null, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(0, result.timeTms());
            assertEquals(0, result.timeTmsh());
            assertEquals(0, result.microseconds());
            assertEquals("2026-09-28-00.00.00.000000", result.timestampDb2());
        }

        @Test
        @DisplayName("hour 24 with zero minute/second is accepted as a valid time (F7919270.src C200-Valider-Tid)")
        void convert_hour24WithZeroMinuteSecondIsValid() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, 240000, null, null, null, null);

            TimestampConversionResult result = dateTimeConversionService.convert(request);

            assertEquals(240000, result.timeTms());
        }

        @Test
        @DisplayName("hour 24 with a nonzero minute is rejected (only 24:00:00 is the accepted special case)")
        void convert_hour24WithNonzeroMinuteIsRejected() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, 243000, null, null, null, null);

            assertThrows(DateConversionException.class,
                    () -> dateTimeConversionService.convert(request));
        }

        @Test
        @DisplayName("hour 25 is rejected as an out-of-range time")
        void convert_hour25IsRejected() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, 250000, null, null, null, null);

            assertThrows(DateConversionException.class,
                    () -> dateTimeConversionService.convert(request));
        }

        @Test
        @DisplayName("calendar-invalid date (2026-02-30) is rejected")
        void convert_calendarInvalidDateIsRejected() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260230, 120000, null, null, null, null);

            assertThrows(DateConversionException.class,
                    () -> dateTimeConversionService.convert(request));
        }

        @Test
        @DisplayName("no input fields at all is rejected (F7919270.src:D001 'Ingen input-felter er utfylt')")
        void convert_noFieldsSuppliedIsRejected() {
            TimestampConversionRequest request =
                    new TimestampConversionRequest(null, null, null, null, null, null);

            assertThrows(DateConversionException.class,
                    () -> dateTimeConversionService.convert(request));
        }

        @Test
        @DisplayName("an ambiguous combination of input fields is rejected (F7919270.src:D002 'ikke entydig')")
        void convert_ambiguousCombinationIsRejected() {
            // Date supplied together with a DB2 timestamp: matches none of the
            // five recognized combinations in D000-Konv-Fra-Input.
            TimestampConversionRequest request =
                    new TimestampConversionRequest(20260928, null, null, null, null,
                            "2026-09-28-14.07.23.456789");

            assertThrows(DateConversionException.class,
                    () -> dateTimeConversionService.convert(request));
        }
    }
}
