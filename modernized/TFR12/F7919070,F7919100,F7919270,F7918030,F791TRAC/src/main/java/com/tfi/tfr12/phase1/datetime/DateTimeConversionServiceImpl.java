package com.tfi.tfr12.phase1.datetime;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Replacement for legacy program F7919270.src. See {@link DateTimeConversionService}
 * and {@link TimestampConversionRequest} for the exact input-combination rules
 * and the documented TMSH/microseconds overwrite quirk.
 */
public final class DateTimeConversionServiceImpl implements DateTimeConversionService {

    @Override
    public TimestampConversionResult convert(TimestampConversionRequest r) {
        int date;
        int timeTms;
        int timeTmsh;
        int microseconds;

        boolean hasDate = r.date() != null;
        boolean hasTms = r.timeTms() != null;
        boolean hasTmsh = r.timeTmsh() != null;
        boolean hasMicros = r.microseconds() != null;
        boolean hasTs16 = r.timestamp16() != null;
        boolean hasDb2 = r.timestampDb2() != null && !r.timestampDb2().isBlank();

        try {
            if (hasDb2 && !hasDate && !hasTms && !hasTmsh && !hasMicros && !hasTs16) {
                // Combo 1: DB2 timestamp alone.
                String v = r.timestampDb2();
                date = Integer.parseInt(v.substring(0, 4) + v.substring(5, 7) + v.substring(8, 10));
                timeTms = Integer.parseInt(v.substring(11, 13) + v.substring(14, 16) + v.substring(17, 19));
                microseconds = Integer.parseInt(v.substring(20, 26));
                int hundredths = microseconds / 10_000;
                timeTmsh = timeTms * 100 + hundredths;
            } else if (hasTs16 && !hasDate && !hasTms && !hasTmsh && !hasMicros && !hasDb2) {
                // Combo 2: 16-digit timestamp alone.
                String v = String.format("%016d", r.timestamp16());
                date = Integer.parseInt(v.substring(0, 8));
                timeTmsh = Integer.parseInt(v.substring(8, 16));
                timeTms = timeTmsh / 100;
                int hundredths = timeTmsh % 100;
                microseconds = hundredths * 10_000;
            } else if (hasDate && hasTms && !hasTmsh && !hasTs16 && !hasDb2) {
                // Combo 3: date + TMS (+ optional microseconds, passed through unchanged).
                date = r.date();
                timeTms = r.timeTms();
                microseconds = hasMicros ? r.microseconds() : 0;
                int hundredths = microseconds / 10_000;
                timeTmsh = timeTms * 100 + hundredths;
            } else if (hasDate && hasTmsh && !hasTms && !hasTs16 && !hasDb2) {
                // Combo 4: date + TMSH (+ optional microseconds - overwrite quirk).
                date = r.date();
                timeTmsh = r.timeTmsh();
                timeTms = timeTmsh / 100;
                int hundredths = timeTmsh % 100;
                if (hasMicros) {
                    // Quirk: only the last 4 digits of the supplied microseconds survive;
                    // the first 2 (hundredths position) are overwritten by TMSH's hundredths.
                    microseconds = hundredths * 10_000 + (r.microseconds() % 10_000);
                } else {
                    microseconds = hundredths * 10_000;
                }
            } else if (hasDate && !hasTms && !hasTmsh && !hasMicros && !hasTs16 && !hasDb2) {
                // Combo 5: date alone, time defaults to all-zero.
                date = r.date();
                timeTms = 0;
                timeTmsh = 0;
                microseconds = 0;
            } else {
                throw new DateConversionException(
                        "No recognizable input field combination supplied (F7919270.src D001/D002)");
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new DateConversionException("Malformed timestamp input: " + e.getMessage());
        }

        validateDate(date);
        validateTime(timeTms);

        long timestamp16 = date * 100_000_000L + timeTmsh;
        String timestampDb2 = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%06d",
                date / 10000, (date / 100) % 100, date % 100,
                timeTms / 10000, (timeTms / 100) % 100, timeTms % 100, microseconds);

        return new TimestampConversionResult(date, timeTms, timeTmsh, microseconds, timestamp16, timestampDb2);
    }

    private void validateDate(int date) {
        try {
            LocalDate.of(date / 10000, (date / 100) % 100, date % 100);
        } catch (DateTimeException e) {
            throw new DateConversionException("Invalid date: " + date);
        }
    }

    /**
     * F7919270.src C200-Valider-Tid: hour 00-24, minute/second 00-59, except
     * hour=24 is only valid with minute=00 and second=00.
     */
    private void validateTime(int timeTms) {
        int hour = timeTms / 10000;
        int minute = (timeTms / 100) % 100;
        int second = timeTms % 100;
        if (hour < 0 || hour > 24) {
            throw new DateConversionException("Hour out of range: " + hour);
        }
        if (hour == 24 && (minute != 0 || second != 0)) {
            throw new DateConversionException("Hour 24 only valid with minute=00 and second=00");
        }
        if (minute < 0 || minute > 59 || second < 0 || second > 59) {
            throw new DateConversionException("Minute/second out of range: " + timeTms);
        }
    }
}
