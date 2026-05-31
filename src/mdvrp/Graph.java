package mdvrp;

import java.util.*;

public class Graph {

    /* ═══════════════════════════════════════════════
       NODE TYPES
    ═══════════════════════════════════════════════ */

    public enum NodeType {

        HOSPITAL,
        PATIENT,
        AMBULANCE_STATION,
        EMERGENCY,
        TRAFFIC
    }

    /* ═══════════════════════════════════════════════
       NODE
    ═══════════════════════════════════════════════ */

    public static class Node {

        public int id;

        public String label;

        public NodeType type;

        public int x;

        public int y;

        public Node(
                int id,
                String label,
                NodeType type,
                int x,
                int y
        ) {

            this.id = id;

            this.label = label;

            this.type = type;

            this.x = x;

            this.y = y;
        }
    }

    /* ═══════════════════════════════════════════════
       EDGE
    ═══════════════════════════════════════════════ */

    public static class Edge {

        public int from;

        public int to;

        public double weight;

        public int trafficLevel;

        public boolean emergencyRoute;

        public boolean badRoad;

        public Edge(
                int from,
                int to,
                double weight,
                int trafficLevel,
                boolean emergencyRoute,
                boolean badRoad
        ) {

            this.from = from;

            this.to = to;

            this.weight = weight;

            this.trafficLevel = trafficLevel;

            this.emergencyRoute =
                    emergencyRoute;

            this.badRoad = badRoad;
        }
    }

    /* ═══════════════════════════════════════════════
       STORAGE
    ═══════════════════════════════════════════════ */

    private final Map<Integer, Node> nodes =
            new HashMap<>();

    private final Map<Integer, List<Edge>> adj =
            new HashMap<>();

    private final List<Edge> edges =
            new ArrayList<>();

    /* ═══════════════════════════════════════════════
       ADD NODE
    ═══════════════════════════════════════════════ */

    public void addNode(Node node) {

        nodes.put(node.id, node);

        adj.putIfAbsent(
                node.id,
                new ArrayList<>()
        );
    }

    /* ═══════════════════════════════════════════════
       ADD EDGE
    ═══════════════════════════════════════════════ */

    public void addEdge(
            int from,
            int to,
            double weight,
            int trafficLevel,
            boolean emergencyRoute,
            boolean badRoad
    ) {

        Edge edge =
                new Edge(
                        from,
                        to,
                        weight,
                        trafficLevel,
                        emergencyRoute,
                        badRoad
                );

        edges.add(edge);

        adj.putIfAbsent(
                from,
                new ArrayList<>()
        );

        adj.putIfAbsent(
                to,
                new ArrayList<>()
        );

        adj.get(from).add(edge);

        /* Undirected graph */

        adj.get(to).add(

                new Edge(
                        to,
                        from,
                        weight,
                        trafficLevel,
                        emergencyRoute,
                        badRoad
                )
        );
    }

    /* ═══════════════════════════════════════════════
       GETTERS
    ═══════════════════════════════════════════════ */

    public Map<Integer, Node> getNodes() {

        return nodes;
    }

    public Map<Integer, List<Edge>> getAdj() {

        return adj;
    }

    public List<Edge> getEdges() {

        return edges;
    }

    /* ═══════════════════════════════════════════════
       BAD ROAD DETECTION
    ═══════════════════════════════════════════════ */

    public void detectBadRoads() {

        for (Edge e : edges) {

            if (e.weight > 180) {

                e.badRoad = true;
            }
        }
    }

    /* ═══════════════════════════════════════════════
       EUCLIDEAN DISTANCE
    ═══════════════════════════════════════════════ */

    public static double euclidean(
            Node a,
            Node b
    ) {

        double dx = a.x - b.x;

        double dy = a.y - b.y;

        return Math.sqrt(
                dx * dx + dy * dy
        );
    }

    /* ═══════════════════════════════════════════════
       TO JSON
    ═══════════════════════════════════════════════ */

    public String toJson() {

        StringBuilder sb =
                new StringBuilder();

        sb.append("{");

        /* Nodes */

        sb.append("\"nodes\":[");

        int count = 0;

        for (Node n : nodes.values()) {

            if (count++ > 0)
                sb.append(",");

            sb.append("{");

            sb.append("\"id\":")
                    .append(n.id)
                    .append(",");

            sb.append("\"label\":\"")
                    .append(n.label)
                    .append("\",");

            sb.append("\"type\":\"")
                    .append(
                            n.type
                                    .name()
                                    .toLowerCase()
                    )
                    .append("\",");

            sb.append("\"x\":")
                    .append(n.x)
                    .append(",");

            sb.append("\"y\":")
                    .append(n.y);

            sb.append("}");
        }

        sb.append("],");

        /* Edges */

        sb.append("\"edges\":[");

        for (int i = 0; i < edges.size(); i++) {

            Edge e = edges.get(i);

            if (i > 0)
                sb.append(",");

            sb.append("{");

            sb.append("\"from\":")
                    .append(e.from)
                    .append(",");

            sb.append("\"to\":")
                    .append(e.to)
                    .append(",");

            sb.append("\"weight\":")
                    .append(e.weight)
                    .append(",");

            sb.append("\"trafficLevel\":")
                    .append(e.trafficLevel)
                    .append(",");

            sb.append("\"emergencyRoute\":")
                    .append(e.emergencyRoute)
                    .append(",");

            sb.append("\"badRoad\":")
                    .append(e.badRoad);

            sb.append("}");
        }

        sb.append("]");

        sb.append("}");

        return sb.toString();
    }
}