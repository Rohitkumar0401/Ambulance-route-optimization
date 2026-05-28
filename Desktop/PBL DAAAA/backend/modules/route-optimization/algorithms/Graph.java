// Graph.java
// Core graph data structure using adjacency list representation
// Handles both Section 1 (delivery map) and Section 2 (transport/depot map)

package mdvrp;

import java.util.*;

public class Graph {

    // ── Node types ────────────────────────────────────────────────────────────
    public enum NodeType {
        HOUSE, SHOP, SCHOOL, HOSPITAL, GYM, DEPOT, CITY
    }

    // ── Node: holds id, label, type, coordinates ──────────────────────────────
    public static class Node {
        public final int    id;
        public final String label;
        public final NodeType type;
        public final double x, y;   // canvas coordinates

        public Node(int id, String label, NodeType type, double x, double y) {
            this.id    = id;
            this.label = label;
            this.type  = type;
            this.x     = x;
            this.y     = y;
        }

        public String toJson() {
            return String.format(
                "{\"id\":%d,\"label\":\"%s\",\"type\":\"%s\",\"x\":%.1f,\"y\":%.1f}",
                id, label, type.name().toLowerCase(), x, y
            );
        }
    }

    // ── Edge: weighted, undirected ────────────────────────────────────────────
    public static class Edge {
        public final int    to;
        public final double weight;

        public Edge(int to, double weight) {
            this.to     = to;
            this.weight = weight;
        }
    }

    // ── Graph fields ──────────────────────────────────────────────────────────
    private final Map<Integer, Node>        nodes = new LinkedHashMap<>();
    private final Map<Integer, List<Edge>>  adj   = new HashMap<>();

    // ── Add node ──────────────────────────────────────────────────────────────
    public void addNode(Node n) {
        nodes.put(n.id, n);
        adj.putIfAbsent(n.id, new ArrayList<>());
    }

    // ── Add undirected weighted edge ──────────────────────────────────────────
    public void addEdge(int u, int v, double weight) {
        adj.get(u).add(new Edge(v, weight));
        adj.get(v).add(new Edge(u, weight));
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Map<Integer, Node>       getNodes() { return nodes; }
    public Map<Integer, List<Edge>> getAdj()   { return adj;   }
    public Node                     getNode(int id) { return nodes.get(id); }
    public boolean                  hasNode(int id) { return nodes.containsKey(id); }

    // ── Export nodes list as JSON array ───────────────────────────────────────
    public String nodesToJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Node n : nodes.values()) {
            if (!first) sb.append(",");
            sb.append(n.toJson());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Export edges as JSON array ────────────────────────────────────────────
    public String edgesToJson() {
        Set<String> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Integer, List<Edge>> entry : adj.entrySet()) {
            int from = entry.getKey();
            for (Edge e : entry.getValue()) {
                // Avoid duplicates for undirected edges
                String key = Math.min(from, e.to) + "-" + Math.max(from, e.to);
                if (seen.add(key)) {
                    if (!first) sb.append(",");
                    sb.append(String.format(
                        "{\"from\":%d,\"to\":%d,\"weight\":%.1f}",
                        from, e.to, e.weight
                    ));
                    first = false;
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Euclidean distance helper ─────────────────────────────────────────────
    public static double euclidean(Node a, Node b) {
        double dx = a.x - b.x, dy = a.y - b.y;
        return Math.round(Math.sqrt(dx * dx + dy * dy) * 10.0) / 10.0;
    }
}
