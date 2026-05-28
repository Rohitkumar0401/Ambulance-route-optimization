package mdvrp;

import java.util.*;

public class Dijkstra {

    // ─────────────────────────────────────────────────────────────────────────
    // Result object for shortest path
    // ─────────────────────────────────────────────────────────────────────────

    public static class Result {

        public final Map<Integer, Double> dist;
        public final Map<Integer, Integer> prev;

        Result(Map<Integer, Double> dist,
               Map<Integer, Integer> prev) {

            this.dist = dist;
            this.prev = prev;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIJKSTRA ALGORITHM
    // Finds shortest route for ambulance
    // ─────────────────────────────────────────────────────────────────────────

    public static Result dijkstra(Graph graph, int source) {

        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();

        // Initialize all distances to infinity

        for (int nodeId : graph.getNodes().keySet()) {

            dist.put(nodeId, Double.MAX_VALUE);

            prev.put(nodeId, -1);
        }

        dist.put(source, 0.0);

        // Priority Queue

        PriorityQueue<double[]> pq =
                new PriorityQueue<>(
                        Comparator.comparingDouble(a -> a[0])
                );

        pq.offer(new double[]{0.0, source});

        Set<Integer> visited = new HashSet<>();

        while (!pq.isEmpty()) {

            double[] current = pq.poll();

            double currentDistance = current[0];

            int currentNode = (int) current[1];

            if (visited.contains(currentNode)) {
                continue;
            }

            visited.add(currentNode);

            // Explore neighbors

            for (Graph.Edge edge :
                    graph.getAdj().getOrDefault(
                            currentNode,
                            Collections.emptyList()
                    )) {

                int nextNode = edge.to;

                // ── Ambulance cost calculation ───────────────────────────

                double trafficPenalty = edge.trafficLevel * 2;

                double roadPenalty = edge.badRoad ? 50 : 0;

                double emergencyBonus =
                        edge.emergencyRoute ? -5 : 0;

                double totalWeight =
                        edge.weight
                                + trafficPenalty
                                + roadPenalty
                                + emergencyBonus;

                double newDistance =
                        currentDistance + totalWeight;

                if (newDistance <
                        dist.getOrDefault(
                                nextNode,
                                Double.MAX_VALUE
                        )) {

                    dist.put(nextNode, newDistance);

                    prev.put(nextNode, currentNode);

                    pq.offer(
                            new double[]{
                                    newDistance,
                                    nextNode
                            }
                    );
                }
            }
        }

        return new Result(dist, prev);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RECONSTRUCT SHORTEST PATH
    // ─────────────────────────────────────────────────────────────────────────

    public static List<Integer> reconstructPath(

            Map<Integer, Integer> prev,
            int source,
            int destination
    ) {

        LinkedList<Integer> path =
                new LinkedList<>();

        int current = destination;

        while (current != -1) {

            path.addFirst(current);

            if (current == source) {
                break;
            }

            current =
                    prev.getOrDefault(current, -1);

            // Safety check

            if (path.size() > prev.size() + 2) {

                return Collections.emptyList();
            }
        }

        if (path.isEmpty()
                || path.getFirst() != source) {

            return Collections.emptyList();
        }

        return path;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AMBULANCE ROUTE OPTIMIZATION
    // Hospital → Multiple Patients → Return Hospital
    // ─────────────────────────────────────────────────────────────────────────

    public static AmbulanceRouteResult optimizeAmbulanceRoute(

            Graph graph,
            int hospitalNode,
            List<Integer> patientNodes
    ) {

        List<Integer> visitedPatients =
                new ArrayList<>();

        List<Integer> fullRoute =
                new ArrayList<>();

        double totalDistance = 0;

        Set<Integer> remainingPatients =
                new LinkedHashSet<>(patientNodes);

        int currentLocation = hospitalNode;

        fullRoute.add(hospitalNode);

        while (!remainingPatients.isEmpty()) {

            Result result =
                    dijkstra(graph, currentLocation);

            int nearestPatient = -1;

            double bestDistance =
                    Double.MAX_VALUE;

            // Find nearest patient

            for (int patient : remainingPatients) {

                double distance =
                        result.dist.getOrDefault(
                                patient,
                                Double.MAX_VALUE
                        );

                if (distance < bestDistance) {

                    bestDistance = distance;

                    nearestPatient = patient;
                }
            }

            // No reachable patient

            if (nearestPatient == -1) {
                break;
            }

            // Get shortest path

            List<Integer> path =
                    reconstructPath(
                            result.prev,
                            currentLocation,
                            nearestPatient
                    );

            if (path.size() > 1) {

                fullRoute.addAll(
                        path.subList(1, path.size())
                );
            }

            totalDistance += bestDistance;

            visitedPatients.add(nearestPatient);

            remainingPatients.remove(nearestPatient);

            currentLocation = nearestPatient;
        }

        // Return ambulance to hospital

        Result returnResult =
                dijkstra(graph, currentLocation);

        List<Integer> returnPath =
                reconstructPath(
                        returnResult.prev,
                        currentLocation,
                        hospitalNode
                );

        if (returnPath.size() > 1) {

            fullRoute.addAll(
                    returnPath.subList(1, returnPath.size())
            );
        }

        totalDistance +=
                returnResult.dist.getOrDefault(
                        hospitalNode,
                        0.0
                );

        return new AmbulanceRouteResult(

                hospitalNode,
                visitedPatients,
                fullRoute,
                totalDistance
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROUTE RESULT OBJECT
    // ─────────────────────────────────────────────────────────────────────────

    public static class AmbulanceRouteResult {

        public final int hospital;

        public final List<Integer> patientsVisited;

        public final List<Integer> completeRoute;

        public final double totalDistance;

        AmbulanceRouteResult(

                int hospital,
                List<Integer> patientsVisited,
                List<Integer> completeRoute,
                double totalDistance
        ) {

            this.hospital = hospital;

            this.patientsVisited = patientsVisited;

            this.completeRoute = completeRoute;

            this.totalDistance = totalDistance;
        }

        // Convert to JSON

        public String toJson() {

            StringBuilder sb =
                    new StringBuilder();

            sb.append("{");

            sb.append("\"hospital\":")
                    .append(hospital)
                    .append(",");

            sb.append("\"patientsVisited\":[");

            for (int i = 0;
                 i < patientsVisited.size();
                 i++) {

                if (i > 0) sb.append(",");

                sb.append(patientsVisited.get(i));
            }

            sb.append("],");

            sb.append("\"completeRoute\":[");

            for (int i = 0;
                 i < completeRoute.size();
                 i++) {

                if (i > 0) sb.append(",");

                sb.append(completeRoute.get(i));
            }

            sb.append("],");

            sb.append(
                    String.format(
                            "\"totalDistance\":%.2f",
                            totalDistance
                    )
            );

            sb.append("}");

            return sb.toString();
        }
    }
}