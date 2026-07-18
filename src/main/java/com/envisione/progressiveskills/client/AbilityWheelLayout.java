package com.envisione.progressiveskills.client;

public final class AbilityWheelLayout {
    public static final int SLOT_COUNT = 8;

    private AbilityWheelLayout() {
    }

    public static int slotForDirection(double x, double y, double deadZone) {
        if (x * x + y * y < deadZone * deadZone) {
            return -1;
        }
        double angle = Math.atan2(y, x) + Math.PI / 2.0D;
        double fullCircle = Math.PI * 2.0D;
        double normalized = (angle % fullCircle + fullCircle) % fullCircle;
        return Math.floorMod((int) Math.floor((normalized + Math.PI / 8.0D) / (Math.PI / 4.0D)), SLOT_COUNT);
    }

    public static Point slotCenter(int slot, int centerX, int centerY, int radius) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Ability wheel slot is invalid");
        }
        double angle = Math.PI * 2.0D * slot / SLOT_COUNT - Math.PI / 2.0D;
        return new Point(
                centerX + (int) Math.round(Math.cos(angle) * radius),
                centerY + (int) Math.round(Math.sin(angle) * radius));
    }

    public record Point(int x, int y) {
    }
}
