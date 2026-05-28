package routeopt;

import routeopt.DijkstraAlgorithm.*;

import java.util.*;

/**
 * AStarAlgorithm.java
 * A* heuristic shortest-path algorithm.
 * Converted from backend/modules/route-optimization/algorithms/aStar.js
 *
 * JS:
 *   function aStar(graph, sourceId, targetId) { ... }
 *   exports.findPath = async (start, destination, ...) => { ... }
 *
 * Uses haversine distance as the admissible heuristic h(n) — same as JS.
 */
public class AStarAlgorithm {

    // ── MinHeap (f-score based) ───────────────────────────────────────────────
    // Mirrors JS MinHeap in aStar.js (comparator: item.f)
    private static class FMinHeap {
        private final List<double[]> heap = new ArrayList<>(); // [f, g]
        private final List<String>   ids  = new ArrayList<>();

        void push(double f, double g, String id) {
            heap.add(new double[]{f, g});
            ids.add(id);
            up(heap.size() - 1);
        }

        double[] pop() {
            if (heap.isEmpty()) return null;
            double[] top = heap.get(0);
            String   tid = ids.get(0);
            int last = heap.size() - 1;
            heap.set(0, heap.get(last)); ids.set(0, ids.get(last));
            heap.remove(last); ids.remove(last);
            if (!heap.isEmpty()) down(0);
            return new double[]{top[0], top[1], ids.size()}; // caller uses separate ids list
        }

        String popId() {
            if (heap.isEmpty()) return null;
            String tid = ids.get(0);
            int last = heap.size() - 1;
            heap.set(0, heap.get(last)); ids.set(0, ids.get(last));
            heap.remove(last); ids.remove(last);
            if (!heap.isEmpty()) down(0);
            return tid;
        }

        double peekG() { return heap.isEmpty() ? 0 : heap.get(0)[1]; }
        boolean isEmpty() { return heap.isEmpty(); }

        private void up(int i) {
            while (i > 0) {
                int p = (i-1)>>1;
                if (heap.get(p)[0] <= heap.get(i)[0]) break;
                swap(p, i); i = p;
            }
        }
        private void down(int i) {
            int n = heap.size();
            while (true) {
                int s = i, l = 2*i+1, r = 2*i+2;
                if (l < n && heap.get(l)[0] < heap.get(s)[0]) s = l;
                if (r < n && heap.get(r)[0] < heap.get(s)[0]) s = r;
                if (s == i) break;
                swap(s, i); i = s;
            }
        }
        private void swap(int a, int b) {
            double[] td = heap.get(a); heap.set(a, heap.get(b)); heap.set(b, td);
            String   ts = ids.get(a);  ids.set(a, ids.get(b));   ids.set(b, ts);
        }
    }

    // ── aStar() — mirrors JS aStar(graph, sourceId, targetId) ────────────────
    @SuppressWarnings("unchecked")
    public static Map<String, Object> aStar(Graph graph, String sourceId, String targetId) {
        NodeData destNode = graph.getNode(targetId);

        Map<String, Double> gScore = new HashMap<>();
        Map<String, String> prev   = new HashMap<>();
        Set<String>         closed = new HashSet<>();
        FMinHeap            pq     = new FMinHeap();

        for (String id : graph.allIds()) {
            gScore.put(id, Double.MAX_VALUE);
            prev.put(id, null);
        }
        gScore.put(sourceId, 0.0);

        double h0 = heuristic(graph.getNode(sourceId), destNode);
        pq.push(h0, 0.0, sourceId);

        while (!pq.isEmpty()) {
            double gCur = pq.peekG();
            String u    = pq.popId();

            if (u.equals(targetId)) break;
            if (closed.contains(u)) continue;
            closed.add(u);

            for (Edge edge : graph.neighbors(u)) {
                double newG = gCur + edge.weight;
                if (newG < gScore.getOrDefault(edge.to, Double.MAX_VALUE)) {
                    gScore.put(edge.to, newG);
                    prev.put(edge.to, u);
                    double f = newG + heuristic(graph.getNode(edge.to), destNode);
                    pq.push(f, newG, edge.to);
                }
            }
        }

        // Reconstruct path
        LinkedList<String> path   = new LinkedList<>();
        String             cur    = targetId;
        int                maxLen = prev.size() + 2;
        while (cur != null) {
            path.addFirst(cur);
            if (cur.equals(sourceId)) break;
            cur = prev.get(cur);
            if (path.size() > maxLen) return Map.of("path", List.of(), "dist", Double.MAX_VALUE);
        }
        if (path.isEmpty() || !path.getFirst().equals(sourceId))
            return Map.of("path", List.of(), "dist", Double.MAX_VALUE);

        return Map.of("path", new ArrayList<>(path), "dist", gScore.getOrDefault(targetId, Double.MAX_VALUE));
    }

    // ── heuristic — haversine to destination ─────────────────────────────────
    private static double heuristic(NodeData n, NodeData dest) {
        return DijkstraAlgorithm.haversine(n.lat, n.lon, dest.lat, dest.lon);
    }

    // ── findPath — mirrors JS exports.findPath() ──────────────────────────────
    @SuppressWarnings("unchecked")
    public static DijkstraAlgorithm.RouteResult findPath(
            double startLat, double startLon,
            double destLat,  double destLon,
            List<double[]> hospitals,
            Map<String, double[]> roadConditions) {

        if (startLat == destLat && startLon == destLon) {
            return new DijkstraAlgorithm.RouteResult(
                List.of("start"),
                List.of(new double[]{startLat, startLon}),
                0.0, 0, List.of(), "astar", 1
            );
        }

        Graph g = DijkstraAlgorithm.buildGraph(
            startLat, startLon, destLat, destLon, hospitals, roadConditions
        );

        Map<String, Object> result = aStar(g, "start", "dest");
        List<String>        pathIds = (List<String>) result.get("path");
        double              totalDist = (double) result.get("dist");

        if (pathIds.isEmpty() || totalDist == Double.MAX_VALUE)
            throw new RuntimeException("No path found between start and destination");

        List<double[]> pathCoords = new ArrayList<>();
        for (String id : pathIds) {
            NodeData n = g.getNode(id);
            pathCoords.add(new double[]{n.lat, n.lon});
        }

        int estTime = (int) Math.round((totalDist / 40.0) * 60);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < pathIds.size() - 1; i++) {
            NodeData a = g.getNode(pathIds.get(i));
            NodeData b = g.getNode(pathIds.get(i + 1));
            steps.add(Map.of(
                "step",     i + 1,
                "from",     a.label,
                "to",       b.label,
                "distance", String.format("%.2f", DijkstraAlgorithm.haversine(a.lat, a.lon, b.lat, b.lon))
            ));
        }

        return new DijkstraAlgorithm.RouteResult(
            pathIds, pathCoords, totalDist, estTime, steps, "astar", g.size()
        );
    }
}
