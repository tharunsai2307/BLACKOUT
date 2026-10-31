package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.utils.DateTimeUtils;
import org.junit.Test;

public class DateTimeUtilsTest {

    @Test
    public void testGetRelativeTimeSpanJustNow() {
        long now = System.currentTimeMillis();
        String result = DateTimeUtils.getRelativeTimeSpan(now - 10000); // 10 seconds ago
        assertEquals("Just now", result);
    }

    @Test
    public void testGetRelativeTimeSpanMinutes() {
        long now = System.currentTimeMillis();
        String result = DateTimeUtils.getRelativeTimeSpan(now - (5 * 60 * 1000)); // 5 minutes ago
        assertEquals("5 mins ago", result);
    }

    @Test
    public void testFormatFullDateTime() {
        long now = System.currentTimeMillis();
        String formatted = DateTimeUtils.formatFullDateTime(now);
        assertNotNull(formatted);
        assertTrue(formatted.length() > 5);
    }
}
