/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lgp547.anydoor.util.jackson;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class AnyDoorJacksonConfig {
    
    public static final String TIME_ZONE_KEY = "anydoor.json.timezone";
    
    public static final String DATETIME_FORMAT_KEY = "anydoor.json.datetime.format";
    
    public static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";
    
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static volatile String runtimeTimeZone;

    private static volatile String runtimeDateTimeFormat;

    public static void apply(String timezone, String dateTimeFormat) {
        runtimeTimeZone = timezone;
        runtimeDateTimeFormat = dateTimeFormat;
    }
    
    public static ZoneId getZoneId() {
        String zoneId = runtimeTimeZone;
        if (zoneId == null || zoneId.trim().isEmpty()) {
            zoneId = System.getProperty(TIME_ZONE_KEY);
        }
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
    }
    
    public static TimeZone getTimeZone() {
        return TimeZone.getTimeZone(getZoneId());
    }
    
    public static String getDateTimePattern() {
        String pattern = runtimeDateTimeFormat;
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = System.getProperty(DATETIME_FORMAT_KEY);
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            return DEFAULT_DATETIME_FORMAT;
        }
        String trimPattern = pattern.trim();
        try {
            DateTimeFormatter.ofPattern(trimPattern);
            return trimPattern;
        } catch (Exception e) {
            return DEFAULT_DATETIME_FORMAT;
        }
    }
    
    public static DateTimeFormatter getDateTimeFormatter() {
        return DateTimeFormatter.ofPattern(getDateTimePattern());
    }
    
    public static DateFormat getDateFormat() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getDateTimePattern());
        simpleDateFormat.setTimeZone(getTimeZone());
        return simpleDateFormat;
    }
    
}
