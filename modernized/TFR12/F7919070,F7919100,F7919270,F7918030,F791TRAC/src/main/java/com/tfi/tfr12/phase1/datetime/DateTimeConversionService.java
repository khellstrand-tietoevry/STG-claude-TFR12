package com.tfi.tfr12.phase1.datetime;

/**
 * Contract for the modern replacement of legacy program F7919270.src
 * ("Generell submodul for å konvertere mellom timestamp-formater.").
 *
 * <p>Accepts exactly one of several input shapes (date+time-of-day in one of
 * two precisions, a 16-digit timestamp, or a DB2 timestamp string) and returns
 * every equivalent representation. See {@link TimestampConversionRequest} for
 * the exact combination rules and a documented quirk in how microseconds
 * interact with the TMSH (hundredths-precision) time field.
 */
public interface DateTimeConversionService {

    /**
     * @param request one of the supported input-field combinations
     * @return every output representation, fully populated
     * @throws DateConversionException if no recognizable combination of fields
     *                                  was supplied (F7919270.src D001/D002),
     *                                  the date fails calendar validation
     *                                  (C100-Valider-Dato), or the time-of-day
     *                                  is out of range - hour outside 00-24, or
     *                                  hour=24 with a nonzero minute/second
     *                                  (C200-Valider-Tid)
     */
    TimestampConversionResult convert(TimestampConversionRequest request);
}
