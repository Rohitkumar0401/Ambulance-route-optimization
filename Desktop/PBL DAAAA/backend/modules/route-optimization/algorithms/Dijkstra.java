// Dijkstra.java
// Dijkstra's shortest path algorithm using a Priority Queue (Min-Heap)
// Returns: shortest distances + predecessor map for path reconstruction

package mdvrp;

import java.util.*;

public class Dijkstra {

    // ── Result object returned by dijkstra() ──────────────────────────────────
    public static class Result {
        public final Map<Integer, Double>  dist;   // node → shortest distance from source
        public final Map<Integer, Integer> prev;   // node → predecessor on shortest path

        Result(Map<Integer, Double> dist, Map<Integer, Integer> prev) {
            this.dist = dist;
            this.prev = prev;
        }
    }

    // ── Run Dijkstra from a single source ─────────────────────────────────────
    public static Result dijkstra(Graph graph, int source) {
        Map<Integer, Double>  dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();

        // Initialise all distances to infinity
        for (int id : graph.getNodes().keySet()) {
            dist.put(id, Double.MAX_VALUE);
            prev.put(id, -1);
        }
        dist.put(source, 0.0);

        // Priority queue: [distance, nodeId]
        PriorityQueue<double[]> pq = new PriorityQueue<>(
            Comparator.comparingDouble(a -> a[0])
        );
        pq.offer(new double[]{0.0, source});

        Set<Integer> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            double[] top  = pq.poll();
            double   d    = top[0];
            int      u    = (int) top[1];

            if (visited.contains(u)) continue;
            visited.add(u);

            for (Graph.Edge edge : graph.getAdj().getOrDefault(u, Collections.emptyList())) {
                int    v    = edge.to;
                double newD = d + edge.weight;
                if (newD < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, newD);
                    prev.put(v, u);
                    pq.offer(new double[]{newD, v});
                }
            }
        }
        return new Result(dist, prev);
    }

    // ── Reconstruct path from source → target using prev map ─────────────────
    public static List<Integer> reconstructPath(Map<Integer, Integer> prev,
                                                int source, int target) {
        LinkedList<Integer> path = new LinkedList<>();
        int cur = target;
        while (cur != -1) {
            path.addFirst(cur);
            if (cur == source) break;
            cur = prev.getOrDefault(cur, -1);
            // Guard: cycle / unreachable
            if (path.size() > prev.size() + 2) return Collections.emptyList();
        }
        if (path.isEmpty() || path.getFirst() != source) return Collections.emptyList();
        return path;
    }

    // ── Greedy TSP-style route: source → visit all stops → return to source ───
    // Uses nearest-unvisited-stop heuristic, each leg computed with Dijkstra.
    public static RouteResult greedyRoute(Graph graph, int source,
                                          List<Integer> stops) {
        List<Integer>  visitOrder  = new ArrayList<>();
        List<Integer>  fullPath    = new ArrayList<>();
        double         totalDist   = 0.0;

        Set<Integer> remaining = new LinkedHashSet<>(stops);
        int          current   = source;
        fullPath.add(source);

        while (!remaining.isEmpty()) {
            // Run Dijkstra from current position
            Result result = dijkstra(graph, current);

            // Find nearest unvisited stop
            int    nearest  = -1;
            double bestDist = Double.MAX_VALUE;
            for (int stop : remaining) {
                double d = result.dist.getOrDefault(stop, Double.MAX_VALUE);
                if (d < bestDist) { bestDist = d; nearest = stop; }
            }

            if (nearest == -1) break; // unreachable stops remain

            // Reconstruct leg: current → nearest
            List<Integer> leg = reconstructPath(result.prev, current, nearest);
            if (leg.size() > 1) {
                fullPath.addAll(leg.subList(1, leg.size())); // skip duplicate current
            }
            totalDist += bestDist;
            visitOrder.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        // Return leg: last stop → source
        Result returnResult = dijkstra(graph, current);
        List<Integer> returnLeg = reconstructPath(returnResult.prev, current, source);
        if (returnLeg.size() > 1) {
            fullPath.addAll(returnLeg.subList(1, returnLeg.size()));
        }
        totalDist += returnResult.dist.getOrDefault(source, 0.0);

        return new RouteResult(visitOrder, fullPath, totalDist, source);
    }

    // ── Route result ──────────────────────────────────────────────────────────
    public static class RouteResult {
        public final List<Integer> visitOrder; // sequence of stops visited
        public final List<Integer> fullPath;   // every node in the route
        public final double        totalDist;
        public final int           startNode;

        RouteResult(List<Integer> visitOrder, List<Integer> fullPath,
                    double totalDist, int startNode) {
            this.visitOrder = visitOrder;
            this.fullPath   = fullPath;
            this.totalDist  = totalDist;
            this.startNode  = startNode;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"start\":").append(startNode).append(",");

            sb.append("\"visitOrder\":[");
            for (int i = 0; i < visitOrder.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(visitOrder.get(i));
            }
            sb.append("],");

            sb.append("\"fullPath\":[");
            for (int i = 0; i < fullPath.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(fullPath.get(i));
            }
            sb.append("],");

            sb.append(String.format("\"totalDist\":%.1f", totalDist));
            sb.append("}");
            return sb.toString();
        }
    }
}
