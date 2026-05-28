package traffic;

import java.util.List;
import java.util.Map;

/**
 * TrafficAnalyzer.java
 * Traffic pattern analysis and congestion scoring.
 * Converted from backend/modules/traffic-analysis/models/TrafficAnalyzer.js
 *
 * JS:
 *   class TrafficAnalyzer {
 *     analyzeTrafficPattern(trafficData) { ... }
 *     calculateCongestionScore(trafficData) { ... }
 *     predictTraffic(roadId, timeOfDay) { ... }
 *   }
 *   module.exports = new TrafficAnalyzer();  // singleton
 */
public class TrafficAnalyzer {

    private static final TrafficAnalyzer INSTANCE = new TrafficAnalyzer();
    public  static TrafficAnalyzer getInstance() { return INSTANCE; }

    private static final double NORMAL_SPEED = 60.0; // km/h

    // ── analyzeTrafficPattern — mirrors JS analyzeTrafficPattern() ────────────
    // Returns: { congestionLevel, recommendation }
    public Map<String, Object> analyzeTrafficPattern(List<Map<String, Object>> trafficData) {
        double congestionScore = calculateCongestionScore(trafficData);
        String recommendation  = congestionScore > 0.7 ? "avoid" : "proceed";
        return Map.of(
            "congestionLevel", congestionScore,
            "recommendation",  recommendation
        );
    }

    // ── calculateCongestionScore — mirrors JS calculateCongestionScore() ──────
    // JS: avgSpeed = sum(average_speed) / count; return max(0, 1 - avgSpeed/60)
    public double calculateCongestionScore(List<Map<String, Object>> trafficData) {
        if (trafficData == null || trafficData.isEmpty()) return 0.0;

        double sum = 0;
        for (Map<String, Object> d : trafficData) {
            Object speed = d.get("average_speed");
            if (speed instanceof Number) {
                sum += ((Number) speed).doubleValue();
            }
        }
        double avgSpeed = sum / trafficData.size();
        return Math.max(0.0, 1.0 - (avgSpeed / NORMAL_SPEED));
    }

    // ── predictTraffic — mirrors JS predictTraffic() ──────────────────────────
    // Returns a static prediction (placeholder — mirrors JS stub)
    public Map<String, Object> predictTraffic(String roadId, String timeOfDay) {
        return Map.of(
            "predictedCongestion", 0.5,
            "confidence",          0.8
        );
    }
}
