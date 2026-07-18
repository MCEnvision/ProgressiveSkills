package com.envisione.progressiveskills.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityWheelLayoutTest {
    @Test
    void mapsEightDirectionsClockwiseFromTheTop() {
        assertEquals(0, AbilityWheelLayout.slotForDirection(0, -20, 4));
        assertEquals(1, AbilityWheelLayout.slotForDirection(20, -20, 4));
        assertEquals(2, AbilityWheelLayout.slotForDirection(20, 0, 4));
        assertEquals(3, AbilityWheelLayout.slotForDirection(20, 20, 4));
        assertEquals(4, AbilityWheelLayout.slotForDirection(0, 20, 4));
        assertEquals(5, AbilityWheelLayout.slotForDirection(-20, 20, 4));
        assertEquals(6, AbilityWheelLayout.slotForDirection(-20, 0, 4));
        assertEquals(7, AbilityWheelLayout.slotForDirection(-20, -20, 4));
    }

    @Test
    void leavesTheCenterUnselected() {
        assertEquals(-1, AbilityWheelLayout.slotForDirection(2, 2, 4));
    }
}
