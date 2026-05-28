// Main.java
// Entry point for Ambulance Route Optimization System
// Starts HTTP server and initializes ambulance network

package mdvrp;

public class Main {

    public static void main(String[] args) throws Exception {

        // ─────────────────────────────────────────────────────────────────────
        // DEFAULT SERVER PORT
        // ─────────────────────────────────────────────────────────────────────

        int port = 8080;

        // ─────────────────────────────────────────────────────────────────────
        // READ PORT FROM COMMAND LINE
        // Example:
        // java mdvrp.Main 9090
        // ─────────────────────────────────────────────────────────────────────

        if (args.length > 0) {

            try {

                port =
                        Integer.parseInt(args[0]);

            } catch (NumberFormatException e) {

                System.err.println(

                        "[WARNING] Invalid Port Number"
                                + " → Using Default Port 8080"
                );
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // SYSTEM START MESSAGE
        // ─────────────────────────────────────────────────────────────────────

        System.out.println(
                "\n======================================="
        );

        System.out.println(
                " AMBULANCE ROUTE OPTIMIZATION SYSTEM "
        );

        System.out.println(
                "======================================="
        );

        System.out.println(
                "Initializing Smart Emergency Network..."
        );

        // ─────────────────────────────────────────────────────────────────────
        // CREATE GRAPH NETWORK
        // ─────────────────────────────────────────────────────────────────────

        Graph cityGraph =
                MapFactory.createCityMap();

        System.out.println(
                "City Map Loaded Successfully"
        );

        System.out.println(
                "Total Locations: "
                        + cityGraph.getNodes().size()
        );

        // ─────────────────────────────────────────────────────────────────────
        // CHECK ROAD CONDITIONS
        // ─────────────────────────────────────────────────────────────────────

        System.out.println(
                "\nScanning Road Conditions..."
        );

        cityGraph.detectBadRoads();

        // ─────────────────────────────────────────────────────────────────────
        // START HTTP SERVER
        // ─────────────────────────────────────────────────────────────────────

        Server server =
                new Server(port);

        server.start();

        // ─────────────────────────────────────────────────────────────────────
        // SERVER SUCCESS MESSAGE
        // ─────────────────────────────────────────────────────────────────────

        System.out.println(
                "\nServer Started Successfully"
        );

        System.out.println(
                "Server Running On Port: "
                        + port
        );

        System.out.println(
                "Frontend URL:"
        );

        System.out.println(
                "http://localhost:"
                        + port
        );

        // ─────────────────────────────────────────────────────────────────────
        // SAMPLE AMBULANCE ROUTE TEST
        // Hospital Node = 1
        // Patient Nodes = 5, 7, 10
        // ─────────────────────────────────────────────────────────────────────

        System.out.println(
                "\nGenerating Emergency Ambulance Route..."
        );

        java.util.List<Integer> patients =
                java.util.Arrays.asList(
                        5,
                        7,
                        10
                );

        Dijkstra.AmbulanceRouteResult route =

                Dijkstra.optimizeAmbulanceRoute(

                        cityGraph,

                        1,

                        patients
                );

        System.out.println(
                "\nOptimized Route Generated"
        );

        System.out.println(
                "Patients Covered: "
                        + route.patientsVisited
        );

        System.out.println(
                "Complete Route: "
                        + route.completeRoute
        );

        System.out.println(
                "Total Distance: "
                        + route.totalDistance
        );

        // ─────────────────────────────────────────────────────────────────────
        // KEEP SERVER RUNNING
        // ─────────────────────────────────────────────────────────────────────

        System.out.println(
                "\nEmergency Monitoring Active..."
        );

        Thread.currentThread().join();
    }
}