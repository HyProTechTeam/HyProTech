package HyProTechTeam.energy;

import java.util.Locale;

/** Converts and formats energy and transfer values for display. */
public final class EnergyUnits {
    public static final double JOULES_PER_WH = 3600.0;

    private EnergyUnits() {
    }

    /** Converts joules to watt-hours using 3,600 joules per watt-hour. */
    public static double toWh(int joules) {
        return joules / JOULES_PER_WH;
    }

    /** Formats converted watt-hours with two decimal places and a US decimal separator. */
    public static String formatWh(int joules) {
        return String.format(Locale.US, "%.2f", toWh(joules));
    }

    /** Formats an energy value using the appropriate joule scale. */
    public static String formatEnergy(int joules) {
        return formatJoules(joules);
    }

    /** Formats current energy and capacity as two independently scaled values. */
    public static String formatEnergyWithCapacity(int energy, int capacity) {
        return formatJoules(energy) + " / " + formatJoules(capacity);
    }

    /** Appends the watt unit to a power value. */
    public static String formatWatts(int watts) {
        return watts + " W";
    }

    /** Formats positive transfer values as J/t, kJ/t, or MJ/t based on their magnitude. */
    public static String formatJoulesPerTick(int joules) {
        if (joules >= 1_000_000) {
            double mj = joules / 1_000_000.0;
            return String.format(Locale.US, "%.2f MJ/t", mj);
        }
        if (joules >= 1000) {
            double kj = joules / 1000.0;
            return String.format(Locale.US, "%.1f kJ/t", kj);
        }
        return joules + " J/t";
    }

    /** Formats positive transfer values as J/s, kJ/s, or MJ/s based on their magnitude. */
    public static String formatJoulesPerSecond(int joules) {
        if (joules >= 1_000_000) {
            double mj = joules / 1_000_000.0;
            return String.format(Locale.US, "%.2f MJ/s", mj);
        }
        if (joules >= 1000) {
            double kj = joules / 1000.0;
            return String.format(Locale.US, "%.1f kJ/s", kj);
        }
        return joules + " J/s";
    }

    /** Chooses J, kJ, or MJ by absolute magnitude while preserving the sign. */
    public static String formatJoules(int joules) {
        // Widen before taking the absolute value so Integer.MIN_VALUE remains representable.
        long abs = Math.abs((long) joules);
        if (abs >= 1_000_000) {
            double mj = joules / 1_000_000.0;
            return String.format(Locale.US, "%.2f MJ", mj);
        }
        if (abs >= 1000) {
            double kj = joules / 1000.0;
            return String.format(Locale.US, "%.1f kJ", kj);
        }
        return joules + " J";
    }
}
