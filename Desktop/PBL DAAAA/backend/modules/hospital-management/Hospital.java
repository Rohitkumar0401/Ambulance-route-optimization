package hospital;

/**
 * Hospital.java
 * Data model — mirrors the hospitals table in MySQL.
 * Fields: id, name, address, latitude, longitude, contact, facilities,
 *         totalBeds, availableBeds, isAvailable, operatingHours, rating
 */
public class Hospital {

    public int     id;
    public String  name;
    public String  address;
    public double  latitude;
    public double  longitude;
    public String  contact;
    public String  facilities;      // JSON array string e.g. ["Emergency","ICU"]
    public int     totalBeds;
    public int     availableBeds;
    public boolean isAvailable;
    public String  operatingHours;  // e.g. "24/7" or "08:00-20:00"
    public double  rating;          // 0.0 – 5.0

    // transient — used only during nearest-hospital search, not persisted
    public double distance = 0.0;

    public Hospital() {}

    public Hospital(int id, String name, String address,
                    double latitude, double longitude,
                    String contact, String facilities,
                    int totalBeds, int availableBeds,
                    boolean isAvailable, String operatingHours, double rating) {
        this.id             = id;
        this.name           = name;
        this.address        = address;
        this.latitude       = latitude;
        this.longitude      = longitude;
        this.contact        = contact;
        this.facilities     = facilities;
        this.totalBeds      = totalBeds;
        this.availableBeds  = availableBeds;
        this.isAvailable    = isAvailable;
        this.operatingHours = operatingHours;
        this.rating         = rating;
    }

    // ── Haversine distance in km ──────────────────────────────────────────────
    public double distanceTo(double lat, double lon) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat - this.latitude);
        double dLon = Math.toRadians(lon - this.longitude);
        double a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(this.latitude))
                    * Math.cos(Math.toRadians(lat))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ── Bed occupancy percentage (0–100) ──────────────────────────────────────
    public int occupancyPercent() {
        if (totalBeds <= 0) return 0;
        int occupied = totalBeds - availableBeds;
        return (int) Math.round((occupied * 100.0) / totalBeds);
    }

    // ── Serialize to JSON ─────────────────────────────────────────────────────
    public String toJson() {
        return String.format(
            "{\"id\":%d,\"name\":\"%s\",\"address\":\"%s\"," +
            "\"latitude\":%f,\"longitude\":%f," +
            "\"contact\":\"%s\",\"facilities\":%s," +
            "\"totalBeds\":%d,\"availableBeds\":%d," +
            "\"isAvailable\":%b,\"operatingHours\":\"%s\"," +
            "\"rating\":%.1f,\"occupancyPercent\":%d}",
            id,
            escapeJson(name),
            escapeJson(address),
            latitude, longitude,
            escapeJson(contact == null ? "" : contact),
            (facilities == null || facilities.isEmpty()) ? "[]" : facilities,
            totalBeds, availableBeds,
            isAvailable,
            escapeJson(operatingHours == null ? "24/7" : operatingHours),
            rating,
            occupancyPercent()
        );
    }

    // ── Serialize with distance field (nearest-hospital response) ─────────────
    public String toJsonWithDistance() {
        return String.format(
            "{\"id\":%d,\"name\":\"%s\",\"address\":\"%s\"," +
            "\"latitude\":%f,\"longitude\":%f," +
            "\"contact\":\"%s\",\"facilities\":%s," +
            "\"totalBeds\":%d,\"availableBeds\":%d," +
            "\"isAvailable\":%b,\"operatingHours\":\"%s\"," +
            "\"rating\":%.1f,\"occupancyPercent\":%d,\"distance\":%.4f}",
            id,
            escapeJson(name),
            escapeJson(address),
            latitude, longitude,
            escapeJson(contact == null ? "" : contact),
            (facilities == null || facilities.isEmpty()) ? "[]" : facilities,
            totalBeds, availableBeds,
            isAvailable,
            escapeJson(operatingHours == null ? "24/7" : operatingHours),
            rating,
            occupancyPercent(),
            distance
        );
    }

    // ── Escape special characters for JSON strings ────────────────────────────
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
