package routeopt;

import mdvrp.Graph;
import mdvrp.Graph.Node;
import mdvrp.Graph.NodeType;
import mdvrp.Dijkstra;
import mdvrp.MapFactory;

import java.util.*;

/**
 * AmbulanceGraph.java
 *
 * Bridges the mdvrp graph/Dijkstra system into the ambulance management system.
 *
 * Builds a simulation graph from:
 *   - Real hospital coordinates fetched from DB (HOSPITAL nodes)
 *   - The ambulance start location (DEPOT node = ambulance position)
 *   - The destination hospital (highlighted)
 *   - Intermediate waypoints generated along the route corridor
 *
 * Uses MapFactory-style nearest-neighbour edge building and
 * Dijkstra.greedyRoute() for path computation — exactly the same
 * algorithms as the MDVRP delivery/transport system.
 *
 * Returns a SimulationResult containing:
 *   - All graph nodes (with canvas x,y coords scaled to 870×580)
 *   - All graph edges (with weights)
 *   - The computed fullPath (node IDs in visit order)
 *   - visitOrder, totalDist, startNode
 */
public class AmbulanceGraph {

    // Canvas dimensions (matches frontend/index.html canvas size)
    private static final double CANVAS_W = 870.0;
    private static final double CANVAS_H = 580.0;
    private static final double PADDING  = 60.0;

    // ── Hospital data passed in from DB ───────────────────────────────────────
    public static class HospitalData {
        public final int    id;
        public final String name;
        public final double lat, lon;

        public HospitalData(int id, String name, double lat, double lon) {
            this.id   = id;
            this.name = name;
            this.lat  = lat;
            this.lon  = lon;
        }
    }

    // ── Full simulation result ────────────────────────────────────────────────
    public static class SimulationResult {
        public final String nodesJson;
        public final String edgesJson;
        public final List<Integer> fullPath;
        public final List<Integer> visitOrder;
        public final double        totalDist;
        public final int           startNodeId;
        public final int           destNodeId;

        public SimulationResult(String nodesJson, String edgesJson,
                                List<Integer> fullPath, List<Integer> visitOrder,
                                double totalDist, int startNodeId, int destNodeId) {
            this.nodesJson   = nodesJson;
            this.edgesJson   = edgesJson;
            this.fullPath    = fullPath;
            this.visitOrder  = visitOrder;
            this.totalDist   = totalDist;
            this.startNodeId = startNodeId;
            this.destNodeId  = destNodeId;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"nodes\":").append(nodesJson).append(",");
            sb.append("\"edges\":").append(edgesJson).append(",");
            sb.append("\"fullPath\":[");
            for (int i = 0; i < fullPath.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(fullPath.get(i));
            }
            sb.append("],\"visitOrder\":[");
            for (int i = 0; i < visitOrder.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(visitOrder.get(i));
            }
            sb.append("],");
            sb.append(String.format("\"totalDist\":%.2f,", totalDist));
            sb.append("\"startNode\":").append(startNodeId).append(",");
            sb.append("\"destNode\":").append(destNodeId);
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Build an ambulance simulation graph and compute the route.
     *
     * @param ambulanceLat  ambulance current latitude
     * @param ambulanceLon  ambulance current longitude
     * @param destHospitalId  ID of the target hospital (from DB)
     * @param hospitals  all hospitals from DB
     * @param waypointCount  number of intermediate waypoints to generate (default 8)
     */
    public static SimulationResult buildAndRoute(
            double ambulanceLat, double ambulanceLon,
            int destHospitalId,
            List<HospitalData> hospitals,
            int waypointCount) {

        if (waypointCount < 4) waypointCount = 8;

        // ── Find destination hospital ─────────────────────────────────────────
        HospitalData dest = hospitals.stream()
            .filter(h -> h.id == destHospitalId)
            .findFirst()
            .orElse(hospitals.isEmpty() ? null : hospitals.get(0));

        if (dest == null) throw new RuntimeException("Destination hospital not found");

        // ── Compute bounding box for coordinate → canvas mapping ──────────────
        double minLat = ambulanceLat, maxLat = ambulanceLat;
        double minLon = ambulanceLon, maxLon = ambulanceLon;
        for (HospitalData h : hospitals) {
            minLat = Math.min(minLat, h.lat); maxLat = Math.max(maxLat, h.lat);
            minLon = Math.min(minLon, h.lon); maxLon = Math.max(maxLon, h.lon);
        }
        // Add 10% margin so nodes don't sit on the edge
        double latRange = Math.max(maxLat - minLat, 0.01);
        double lonRange = Math.max(maxLon - minLon, 0.01);
        minLat -= latRange * 0.1; maxLat += latRange * 0.1;
        minLon -= lonRange * 0.1; maxLon += lonRange * 0.1;

        // ── Build mdvrp Graph ─────────────────────────────────────────────────
        Graph g = new Graph();
        int nextId = 1;

        // Node 1 = ambulance (DEPOT)
        final int AMBULANCE_ID = nextId++;
        g.addNode(new Node(AMBULANCE_ID, "🚑 Ambulance",
            NodeType.DEPOT,
            toCanvasX(ambulanceLon, minLon, maxLon),
            toCanvasY(ambulanceLat, minLat, maxLat)));

        // Hospital nodes
        Map<Integer, Integer> hospitalNodeMap = new LinkedHashMap<>(); // dbId → graphId
        for (HospitalData h : hospitals) {
            int nodeId = nextId++;
            hospitalNodeMap.put(h.id, nodeId);
            g.addNode(new Node(nodeId,
                "🏥 " + h.name,
                NodeType.HOSPITAL,
                toCanvasX(h.lon, minLon, maxLon),
                toCanvasY(h.lat, minLat, maxLat)));
        }

        // Waypoints along the ambulance → destination corridor
        int destGraphId = hospitalNodeMap.getOrDefault(dest.id, 2);
        double dLat = dest.lat, dLon = dest.lon;

        Random rng = new Random(42); // deterministic
        for (int i = 1; i <= waypointCount; i++) {
            double t   = (double) i / (waypointCount + 1);
            double lat = ambulanceLat + (dLat - ambulanceLat) * t + (rng.nextDouble() - 0.5) * latRange * 0.15;
            double lon = ambulanceLon + (dLon - ambulanceLon) * t + (rng.nextDouble() - 0.5) * lonRange * 0.15;
            // Clamp to bounding box
            lat = Math.max(minLat, Math.min(maxLat, lat));
            lon = Math.max(minLon, Math.min(maxLon, lon));
            g.addNode(new Node(nextId++, "WP-" + i,
                NodeType.CITY,   // reuse CITY type for waypoints (blue)
                toCanvasX(lon, minLon, maxLon),
                toCanvasY(lat, minLat, maxLat)));
        }

        // ── Connect: each node to its 4 nearest neighbours ────────────────────
        // Mirrors MapFactory.connectNearestNeighbours(g, 4)
        connectNearestNeighbours(g, 4);

        // ── Run Dijkstra: ambulance → destination hospital (single path) ─────
        Dijkstra.Result dijkstraResult = Dijkstra.dijkstra(g, AMBULANCE_ID);
        List<Integer> fullPath = Dijkstra.reconstructPath(
            dijkstraResult.prev, AMBULANCE_ID, destGraphId
        );
        if (fullPath.isEmpty()) {
            throw new RuntimeException("No simulation path found to destination hospital");
        }
        List<Integer> visitOrder = List.of(destGraphId);
        double totalDist = dijkstraResult.dist.getOrDefault(destGraphId, Double.MAX_VALUE);
        if (totalDist == Double.MAX_VALUE) {
            throw new RuntimeException("Destination hospital is unreachable in simulation graph");
        }

        return new SimulationResult(
            g.nodesToJson(),
            g.edgesToJson(),
            fullPath,
            visitOrder,
            totalDist,
            AMBULANCE_ID,
            destGraphId
        );
    }

    // ── Connect each node to its K nearest neighbours ─────────────────────────
    // Direct copy of MapFactory.connectNearestNeighbours logic
    private static void connectNearestNeighbours(Graph g, int k) {
        List<Node> nodes = new ArrayList<>(g.getNodes().values());
        for (Node u : nodes) {
            nodes.stream()
                 .filter(v -> v.id != u.id)
                 .sorted(Comparator.comparingDouble(v -> Graph.euclidean(u, v)))
                 .limit(k)
                 .forEach(v -> addEdgeIfAbsent(g, u.id, v.id, Graph.euclidean(u, v)));
        }
    }

    private static void addEdgeIfAbsent(Graph g, int u, int v, double w) {
        List<mdvrp.Graph.Edge> list = g.getAdj().get(u);
        if (list == null) return;
        if (list.stream().noneMatch(e -> e.to == v)) g.addEdge(u, v, w);
    }

    // ── Coordinate → canvas pixel mapping ────────────────────────────────────
    private static double toCanvasX(double lon, double minLon, double maxLon) {
        double range = maxLon - minLon;
        if (range == 0) return CANVAS_W / 2;
        return PADDING + ((lon - minLon) / range) * (CANVAS_W - 2 * PADDING);
    }

    private static double toCanvasY(double lat, double minLat, double maxLat) {
        double range = maxLat - minLat;
        if (range == 0) return CANVAS_H / 2;
        // Invert Y: higher lat = higher on screen
        return PADDING + ((maxLat - lat) / range) * (CANVAS_H - 2 * PADDING);
    }
}
