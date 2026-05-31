// MapFactory.java
// Generates Smart Ambulance Emergency Network Map
// Includes:
// Hospitals
// Patients
// Ambulance Stations
// Emergency Zones
// Traffic Areas
// Bad Roads

package mdvrp;

import java.util.*;

public class MapFactory {

    // ───────────────────────────────────────────────────────────────────────
    // CREATE COMPLETE CITY MAP
    // ───────────────────────────────────────────────────────────────────────

    public static Graph createCityMap() {

        Graph graph = new Graph();

        // ───────────────────────────────────────────────────────────────────
        // HOSPITAL NODES
        // IDs 1 - 5
        // ───────────────────────────────────────────────────────────────────

        graph.addNode(

                new Graph.Node(
                        1,
                        "City Hospital",
                        Graph.NodeType.HOSPITAL,
                        120,
                        100
                )
        );

        graph.addNode(

                new Graph.Node(
                        2,
                        "Metro Hospital",
                        Graph.NodeType.HOSPITAL,
                        700,
                        90
                )
        );

        graph.addNode(

                new Graph.Node(
                        3,
                        "Emergency Care Center",
                        Graph.NodeType.HOSPITAL,
                        380,
                        260
                )
        );

        graph.addNode(

                new Graph.Node(
                        4,
                        "North Medical",
                        Graph.NodeType.HOSPITAL,
                        180,
                        470
                )
        );

        graph.addNode(

                new Graph.Node(
                        5,
                        "South Trauma Center",
                        Graph.NodeType.HOSPITAL,
                        670,
                        460
                )
        );

        // ───────────────────────────────────────────────────────────────────
        // PATIENT LOCATIONS
        // IDs 6 - 15
        // ───────────────────────────────────────────────────────────────────

        graph.addNode(

                new Graph.Node(
                        6,
                        "Patient Zone A",
                        Graph.NodeType.PATIENT,
                        220,
                        150
                )
        );

        graph.addNode(

                new Graph.Node(
                        7,
                        "Patient Zone B",
                        Graph.NodeType.PATIENT,
                        320,
                        120
                )
        );

        graph.addNode(

                new Graph.Node(
                        8,
                        "Patient Zone C",
                        Graph.NodeType.PATIENT,
                        520,
                        170
                )
        );

        graph.addNode(

                new Graph.Node(
                        9,
                        "Patient Zone D",
                        Graph.NodeType.PATIENT,
                        760,
                        210
                )
        );

        graph.addNode(

                new Graph.Node(
                        10,
                        "Patient Zone E",
                        Graph.NodeType.PATIENT,
                        610,
                        310
                )
        );

        graph.addNode(

                new Graph.Node(
                        11,
                        "Patient Zone F",
                        Graph.NodeType.PATIENT,
                        450,
                        360
                )
        );

        graph.addNode(

                new Graph.Node(
                        12,
                        "Patient Zone G",
                        Graph.NodeType.PATIENT,
                        250,
                        350
                )
        );

        graph.addNode(

                new Graph.Node(
                        13,
                        "Patient Zone H",
                        Graph.NodeType.PATIENT,
                        100,
                        320
                )
        );

        graph.addNode(

                new Graph.Node(
                        14,
                        "Patient Zone I",
                        Graph.NodeType.PATIENT,
                        320,
                        520
                )
        );

        graph.addNode(

                new Graph.Node(
                        15,
                        "Patient Zone J",
                        Graph.NodeType.PATIENT,
                        560,
                        520
                )
        );

        // ───────────────────────────────────────────────────────────────────
        // AMBULANCE STATIONS
        // IDs 16 - 20
        // ───────────────────────────────────────────────────────────────────

        graph.addNode(

                new Graph.Node(
                        16,
                        "Ambulance Station 1",
                        Graph.NodeType.AMBULANCE_STATION,
                        150,
                        220
                )
        );

        graph.addNode(

                new Graph.Node(
                        17,
                        "Ambulance Station 2",
                        Graph.NodeType.AMBULANCE_STATION,
                        420,
                        80
                )
        );

        graph.addNode(

                new Graph.Node(
                        18,
                        "Ambulance Station 3",
                        Graph.NodeType.AMBULANCE_STATION,
                        760,
                        360
                )
        );

        graph.addNode(

                new Graph.Node(
                        19,
                        "Ambulance Station 4",
                        Graph.NodeType.AMBULANCE_STATION,
                        340,
                        430
                )
        );

        graph.addNode(

                new Graph.Node(
                        20,
                        "Central Ambulance Hub",
                        Graph.NodeType.AMBULANCE_STATION,
                        540,
                        260
                )
        );

        // ───────────────────────────────────────────────────────────────────
        // CONNECT NODES
        // ───────────────────────────────────────────────────────────────────

        connectNearestNeighbours(
                graph,
                4
        );

        // ───────────────────────────────────────────────────────────────────
        // ADD SPECIAL ROADS
        // distance
        // traffic level
        // bad road
        // emergency route
        // ───────────────────────────────────────────────────────────────────

        addRoad(
                graph,
                1,
                6,
                120,
                3,
                false,
                true
        );

        addRoad(
                graph,
                1,
                16,
                80,
                2,
                false,
                true
        );

        addRoad(
                graph,
                16,
                13,
                110,
                9,
                true,
                false
        );

        addRoad(
                graph,
                2,
                9,
                140,
                5,
                false,
                true
        );

        addRoad(
                graph,
                3,
                10,
                95,
                4,
                false,
                true
        );

        addRoad(
                graph,
                4,
                14,
                130,
                8,
                true,
                false
        );

        addRoad(
                graph,
                5,
                15,
                100,
                2,
                false,
                true
        );

        addRoad(
                graph,
                20,
                10,
                60,
                1,
                false,
                true
        );

        addRoad(
                graph,
                20,
                11,
                80,
                7,
                false,
                true
        );

        addRoad(
                graph,
                7,
                17,
                75,
                2,
                false,
                true
        );

        addRoad(
                graph,
                18,
                9,
                90,
                10,
                true,
                false
        );

        // ───────────────────────────────────────────────────────────────────
        // DETECT BAD ROADS
        // ───────────────────────────────────────────────────────────────────

        graph.detectBadRoads();

        return graph;
    }

    // ───────────────────────────────────────────────────────────────────────
    // CONNECT NEAREST NEIGHBOURS
    // ───────────────────────────────────────────────────────────────────────

    private static void connectNearestNeighbours(

            Graph graph,

            int neighbours

    ) {

        List<Graph.Node> nodes =

                new ArrayList<>(
                        graph.getNodes().values()
                );

        for (Graph.Node node : nodes) {

            nodes.stream()

                    .filter(
                            other ->
                                    other.id != node.id
                    )

                    .sorted(

                            Comparator.comparingDouble(

                                    other ->
                                            Graph.euclidean(
                                                    node,
                                                    other
                                            )
                            )
                    )

                    .limit(neighbours)

                    .forEach(

                            other -> {

                                double distance =

                                        Graph.euclidean(
                                                node,
                                                other
                                        );

                                addRoad(

                                        graph,

                                        node.id,

                                        other.id,

                                        distance,

                                        randomTraffic(),

                                        false,

                                        true
                                );
                            }
                    );
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // ADD ROAD
    // ───────────────────────────────────────────────────────────────────────

    private static void addRoad(

            Graph graph,

            int from,

            int to,

            double distance,

            int traffic,

            boolean badRoad,

            boolean emergencyRoute
    ) {

        // Prevent duplicate edges

        List<Graph.Edge> list =
                graph.getAdj().get(from);

        boolean exists =

                list.stream()

                        .anyMatch(
                                edge ->
                                        edge.to == to
                        );

        if (!exists) {

            graph.addEdge(

                    from,

                    to,

                    distance,

                    traffic,

                    badRoad,

                    emergencyRoute
            );
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // RANDOM TRAFFIC GENERATOR
    // ───────────────────────────────────────────────────────────────────────

    private static int randomTraffic() {

        return new Random().nextInt(10) + 1;
    }
}