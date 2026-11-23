package com.campussync.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Unit tests for the Event model's display helpers.
 */
public class EventTest {

    @Test
    public void shortDate_isNullWhenNoStartDate() {
        Event event = new Event();
        assertNull(event.getShortDate());
        assertNull(event.getShortTime());
    }

    @Test
    public void shortDate_formatsStartDate() {
        Locale original = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.clear();
            calendar.set(2026, Calendar.NOVEMBER, 23, 14, 30);

            Event event = new Event();
            event.setStartDate(calendar.getTime());

            assertEquals("Nov 23, 2026", event.getShortDate());
            assertEquals("02:30 PM", event.getShortTime());
        } finally {
            Locale.setDefault(original);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    public void attendeeIds_areExposedForJoinChecks() {
        Event event = new Event();
        List<String> attendees = new ArrayList<>();
        attendees.add("user-1");
        event.setAttendeeIds(attendees);

        assertTrue(event.getAttendeeIds().contains("user-1"));
    }

    @Test
    public void group_memberCountHandlesNullList() {
        Group group = new Group();
        assertEquals(0, group.getMemberCount());

        List<String> members = new ArrayList<>();
        members.add("u1");
        members.add("u2");
        group.setMemberIds(members);
        assertEquals(2, group.getMemberCount());
    }
}
