package com.tfi.tfr12.phase1.datetime;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * Replacement for legacy program F7919070.src. See {@link MachineTimestampService}
 * for the rationale behind taking an explicit {@link Clock} instead of reading
 * the wall clock directly.
 */
public final class MachineTimestampServiceImpl implements MachineTimestampService {

    @Override
    public MachineTimestamp fetchTimestamp(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int year = now.getYear();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();
        // Full 6-digit microsecond precision, then truncated (not rounded) to
        // its first two digits for the "hundredths" field, matching
        // F7919070.src C300-Formater-TMS: Move W-TMS-20(1:16) To ...-TMS-16.
        int microseconds = now.getNano() / 1_000;
        int hundredths = microseconds / 10_000;

        String db2Timestamp = String.format("%04d-%02d-%02d-%02d.%02d.%02d.%06d",
                year, month, day, hour, minute, second, microseconds);
        String timestamp20 = String.format("%04d%02d%02d%02d%02d%02d%06d",
                year, month, day, hour, minute, second, microseconds);
        long timestamp16 = Long.parseLong(String.format("%04d%02d%02d%02d%02d%02d%02d",
                year, month, day, hour, minute, second, hundredths));
        int date8 = Integer.parseInt(String.format("%04d%02d%02d", year, month, day));
        int time8 = Integer.parseInt(String.format("%02d%02d%02d%02d", hour, minute, second, hundredths));

        return new MachineTimestamp(year, month, day, hour, minute, second, hundredths,
                db2Timestamp, timestamp20, timestamp16, date8, time8);
    }
}
