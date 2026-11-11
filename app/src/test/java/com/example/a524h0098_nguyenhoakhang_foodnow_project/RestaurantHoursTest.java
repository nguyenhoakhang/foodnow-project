package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RestaurantHoursTest {
    private static final String ZONE = "Asia/Ho_Chi_Minh";
    private static final long MIDNIGHT_UTC = 1704067200000L;

    @Test
    public void usesRestaurantTimezoneAndClosingBoundary() {
        assertEquals(RestaurantHours.Status.OPEN,
                RestaurantHours.getStatus(true, "06:00", "23:00", ZONE, MIDNIGHT_UTC));
        assertEquals(RestaurantHours.Status.CLOSED,
                RestaurantHours.getStatus(true, "06:00", "07:00", ZONE, MIDNIGHT_UTC));
    }

    @Test
    public void handlesOvernightAndInvalidData() {
        assertEquals(RestaurantHours.Status.OPEN,
                RestaurantHours.getStatus(true, "22:00", "08:00", ZONE, MIDNIGHT_UTC));
        assertEquals(RestaurantHours.Status.CLOSED,
                RestaurantHours.getStatus(false, "22:00", "08:00", ZONE, MIDNIGHT_UTC));
        assertEquals(RestaurantHours.Status.UNKNOWN,
                RestaurantHours.getStatus(true, "25:00", "08:00", ZONE, MIDNIGHT_UTC));
        assertEquals(RestaurantHours.Status.UNKNOWN,
                RestaurantHours.getStatus(true, "06:00", "23:00", "invalid", MIDNIGHT_UTC));
    }
}
