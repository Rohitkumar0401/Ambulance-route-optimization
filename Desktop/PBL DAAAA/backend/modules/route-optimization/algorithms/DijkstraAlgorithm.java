package routeopt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * DijkstraAlgorithm.java
 * Route optimization helper used by RouteOptimizationController and AStarAlgorithm.
 */
public class DijkstraAlgorithm {

    public static class NodeData {
        public final String id;
        public final String label;
        public final double lat;
        public final double lon;
        public final String type;

        public NodeData(String id, String label, double lat, double lon, String type) {
            this.id = id;
            this.label = label;
            this.lat = lat;
            this.lon = lon;
            this.type = type;
        }
    }

    public static class Edge {
        public final String to;
        public final double weight;

        public Edge(String to, double weight) {
            this.to = to;
            this.weight = weight;
        }
    }

    public static class Graph {
        private final Map<String, NodeData> nodes = new LinkedHashMap<>();
        private final Map<String, List<Edge>> adj = new HashMap<>();

        public void addNode(NodeData n) {
            nodes.put(n.id, n);
            adj.putIfAbsent(n.id, new ArrayList<>());
        }

        public void addUndirectedEdge(String a, String b, double weight) {
            adj.get(a).add(new Edge(b, weight));
            adj.get(b).add(new Edge(a, weight));
        }

        public NodeData getNode(String id) {
            return nodes.get(id);
        }

        public List<Edge> neighbors(String id) {
            return adj.getOrDefault(id, List.of());
        }

        public Set<String> allIds() {
            return nodes.keySet();
        }

        public int size() {
            return nodes.size();
        }
    }

    public static class RouteResult {
        public final List<String> pathIds;
        public final List<double[]> pathCoordinates;
        public final double distance;
        public final int estimatedTime;
        public final List<Map<String, Object>> steps;
        public final String algorithm;
        public final int nodesExplored;

        public RouteResult(
                List<String> pathIds,
                List<double[]> pathCoordinates,
                double distance,
                int estimatedTime,
                List<Map<String, Object>> steps,
                String algorithm,
                int nodesExplored) {
            this.pathIds = pathIds;
            this.pathCoordinates = pathCoordinates;
            this.distance = round(distance);
            this.estimatedTime = estimatedTime;
            this.steps = steps;
            this.algorithm = algorithm;
            this.nodesExplored = nodesExplored;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"path\":[");
            for (int i = 0; i < pathIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(pathIds.get(i))).append("\"");
            }
            sb.append("],");
            sb.append("\"pathCoordinates\":[");
            for (int i = 0; i < pathCoordinates.size(); i++) {
                if (i > 0) sb.append(",");
                double[] p = pathCoordinates.get(i);
                sb.append("{\"latitude\":").append(round(p[0]))
                        .append(",\"longitude\":").append(round(p[1]));
                if (p.length > 2 && p[2] == 1.0) {
                    sb.append(",\"type\":\"waypoint\"");
                }
                sb.append("}");
            }
            sb.append("],");
            sb.append("\"distance\":").append(round(distance)).append(",");
            sb.append("\"estimatedTime\":").append(estimatedTime).append(",");
            sb.append("\"algorithm\":\"").append(escape(algorithm)).append("\",");
            sb.append("\"nodesExplored\":").append(nodesExplored).append(",");
            sb.append("\"steps\":[");
            for (int i = 0; i < steps.size(); i++) {
                if (i > 0) sb.append(",");
                Map<String, Object> step = steps.get(i);
                sb.append("{\"step\":").append(step.get("step"))
                        .append(",\"from\":\"").append(escape(String.valueOf(step.get("from")))).append("\"")
                        .append(",\"to\":\"").append(escape(String.valueOf(step.get("to")))).append("\"")
                        .append(",\"distance\":\"").append(escape(String.valueOf(step.get("distance")))).append("\"")
                        .append("}");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static Graph buildGraph(
            double startLat, double startLon,
            double destLat, double destLon,
            List<double[]> hospitals,
            Map<String, double[]> roadConditions) {
        Graph g = new Graph();
        g.addNode(new NodeData("start", "Start Location", startLat, startLon, "start"));
        g.addNode(new NodeData("dest", "Destination", destLat, destLon, "destination"));

        int idx = 0;
        for (double[] h : hospitals) {
            if (h == null || h.length < 2) continue;
            g.addNode(new NodeData("h" + idx, "Hospital " + (idx + 1), h[0], h[1], "hospital"));
            idx++;
        }

        List<double[]> waypoints = generateWaypoints(startLat, startLon, destLat, destLon, 8);
        for (int i = 0; i < waypoints.size(); i++) {
            double[] w = waypoints.get(i);
            g.addNode(new NodeData("w" + i, "Waypoint " + (i + 1), w[0], w[1], "waypoint"));
        }

        List<NodeData> nodes = new ArrayList<>();
        for (String id : g.allIds()) nodes.add(g.getNode(id));
        connectNearest(g, nodes, 4, roadConditions);
        return g;
    }

    public static RouteResult findPath(
            double startLat, double startLon,
            double destLat, double destLon,
            List<double[]> hospitals,
            Map<String, double[]> roadConditions) {
        if (startLat == destLat && startLon == destLon) {
            List<double[]> coords = List.of(new double[]{startLat, startLon, 0.0});
            return new RouteResult(List.of("start"), coords, 0.0, 0, List.of(), "dijkstra", 1);
        }

        Graph g = buildGraph(startLat, startLon, destLat, destLon, hospitals, roadConditions);
        Result result = dijkstra(g, "start");
        List<String> path = reconstructPath(result.prev, "start", "dest");
        if (path.isEmpty()) {
            throw new RuntimeException("No path found between start and destination");
        }

        double total = result.dist.getOrDefault("dest", Double.MAX_VALUE);
        if (total == Double.MAX_VALUE) {
            throw new RuntimeException("No route available");
        }

        List<double[]> coords = new ArrayList<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            NodeData n = g.getNode(path.get(i));
            double marker = "waypoint".equals(n.type) ? 1.0 : 0.0;
            coords.add(new double[]{n.lat, n.lon, marker});
            if (i < path.size() - 1) {
                NodeData nx = g.getNode(path.get(i + 1));
                double leg = haversine(n.lat, n.lon, nx.lat, nx.lon);
                steps.add(Map.of(
                        "step", i + 1,
                        "from", n.label,
                        "to", nx.label,
                        "distance", String.format("%.2f", leg)
                ));
            }
        }

        int estTime = (int) Math.round((total / 40.0) * 60.0);
        return new RouteResult(path, coords, total, estTime, steps, "dijkstra", g.size());
    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private static class Result {
        public final Map<String, Double> dist;
        public final Map<String, String> prev;

        private Result(Map<String, Double> dist, Map<String, String> prev) {
            this.dist = dist;
            this.prev = prev;
        }
    }

    private static Result dijkstra(Graph g, String sourceId) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();
        for (String id : g.allIds()) {
            dist.put(id, Double.MAX_VALUE);
            prev.put(id, null);
        }
        dist.put(sourceId, 0.0);

        PriorityQueue<NodeState> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a.cost));
        pq.offer(new NodeState(sourceId, 0.0));

        while (!pq.isEmpty()) {
            NodeState cur = pq.poll();
            if (visited.contains(cur.id)) continue;
            visited.add(cur.id);

            for (Edge e : g.neighbors(cur.id)) {
                double nd = cur.cost + e.weight;
                if (nd < dist.getOrDefault(e.to, Double.MAX_VALUE)) {
                    dist.put(e.to, nd);
                    prev.put(e.to, cur.id);
                    pq.offer(new NodeState(e.to, nd));
                }
            }
        }

        return new Result(dist, prev);
    }

    private static List<String> reconstructPath(Map<String, String> prev, String source, String target) {
        LinkedList<String> path = new LinkedList<>();
        String cur = target;
        while (cur != null) {
            path.addFirst(cur);
            if (cur.equals(source)) break;
            cur = prev.get(cur);
            if (path.size() > prev.size() + 2) return List.of();
        }
        if (path.isEmpty() || !path.getFirst().equals(source)) return List.of();
        return path;
    }

    private static void connectNearest(Graph g, List<NodeData> nodes, int k, Map<String, double[]> roadConditions) {
        for (NodeData a : nodes) {
            nodes.stream()
                    .filter(b -> !b.id.equals(a.id))
                    .sorted(Comparator.comparingDouble(b -> haversine(a.lat, a.lon, b.lat, b.lon)))
                    .limit(k)
                    .forEach(b -> {
                        if (hasEdge(g, a.id, b.id)) return;
                        double base = haversine(a.lat, a.lon, b.lat, b.lon);
                        double weighted = base * edgeMultiplier(a.id, b.id, roadConditions);
                        g.addUndirectedEdge(a.id, b.id, weighted);
                    });
        }
    }

    private static boolean hasEdge(Graph g, String from, String to) {
        for (Edge e : g.neighbors(from)) {
            if (e.to.equals(to)) return true;
        }
        return false;
    }

    private static double edgeMultiplier(String a, String b, Map<String, double[]> roadConditions) {
        String k1 = a + "_" + b;
        String k2 = b + "_" + a;
        double[] factors = roadConditions.getOrDefault(k1, roadConditions.get(k2));
        if (factors == null || factors.length < 3) return 1.0;
        double roadQuality = factors[0];
        double terrain = factors[1];
        double traffic = factors[2];
        double qualityPenalty = roadQuality > 0 ? (1.0 / roadQuality) : 1.0;
        return Math.max(0.2, qualityPenalty) * Math.max(0.2, terrain) * Math.max(0.2, traffic);
    }

    private static List<double[]> generateWaypoints(double sLat, double sLon, double dLat, double dLon, int count) {
        List<double[]> wp = new ArrayList<>();
        double latRange = Math.max(Math.abs(dLat - sLat), 0.01);
        double lonRange = Math.max(Math.abs(dLon - sLon), 0.01);
        for (int i = 1; i <= count; i++) {
            double t = (double) i / (count + 1);
            double lat = sLat + (dLat - sLat) * t;
            double lon = sLon + (dLon - sLon) * t;
            double jitter = (i % 2 == 0 ? 1 : -1) * 0.03;
            wp.add(new double[]{lat + jitter * latRange, lon - jitter * lonRange});
        }
        return wp;
    }

    private static class NodeState {
        String id;
        double cost;

        NodeState(String id, double cost) {
            this.id = id;
            this.cost = cost;
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
