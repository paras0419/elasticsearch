/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.sql.expression.function.scalar.datetime;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;

public class NonIsoDateTimeProcessor extends BaseDateTimeProcessor {
    
    public enum NonIsoDateTimeExtractor {
        DAY_OF_WEEK(zdt -> {
            // by ISO 8601 standard, Monday is the first day of the week and has the value 1
            // non-ISO 8601 standard considers Sunday as the first day of the week and value 1
            int dayOfWeek = zdt.get(ChronoField.DAY_OF_WEEK) + 1;
            return dayOfWeek == 8 ? 1 : dayOfWeek;
        }),
        WEEK_OF_YEAR(zdt -> {
            // by ISO 8601 standard, the first week of a year is the first week with a majority (4 or more) of its days in January.
            // Other Locales may have their own standards (see Arabic or Japanese calendars).
            LocalDateTime ld = zdt.toLocalDateTime();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(zdt.getZone()), Locale.ROOT);
            cal.clear();
            cal.set(ld.get(ChronoField.YEAR), ld.get(ChronoField.MONTH_OF_YEAR) - 1, ld.get(ChronoField.DAY_OF_MONTH),
                    ld.get(ChronoField.HOUR_OF_DAY), ld.get(ChronoField.MINUTE_OF_HOUR), ld.get(ChronoField.SECOND_OF_MINUTE));

            return cal.get(Calendar.WEEK_OF_YEAR);
        });

        private final Function<ZonedDateTime, Integer> apply;

        NonIsoDateTimeExtractor(Function<ZonedDateTime, Integer> apply) {
            this.apply = apply;
        }

        public final Integer extract(ZonedDateTime dateTime) {
            return apply.apply(dateTime);
        }

        public final Integer extract(ZonedDateTime millis, String tzId) {
            return apply.apply(millis.withZoneSameInstant(ZoneId.of(tzId)));
        }
    }
    
    public static final String NAME = "nidt";

    private final NonIsoDateTimeExtractor extractor;

    public NonIsoDateTimeProcessor(NonIsoDateTimeExtractor extractor, TimeZone timeZone) {
        super(timeZone);
        this.extractor = extractor;
    }

    public NonIsoDateTimeProcessor(StreamInput in) throws IOException {
        super(in);
        extractor = in.readEnum(NonIsoDateTimeExtractor.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeEnum(extractor);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    NonIsoDateTimeExtractor extractor() {
        return extractor;
    }

    @Override
    public Object doProcess(ZonedDateTime dateTime) {
        return extractor.extract(dateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extractor, timeZone());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        NonIsoDateTimeProcessor other = (NonIsoDateTimeProcessor) obj;
        return Objects.equals(extractor, other.extractor)
                && Objects.equals(timeZone(), other.timeZone());
    }

    @Override
    public String toString() {
        return extractor.toString();
    }
}
