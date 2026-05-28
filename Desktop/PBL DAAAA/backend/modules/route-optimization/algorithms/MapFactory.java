// MapFactory.java
// Generates two maps:
//   Section 1 — Delivery map: 100 nodes (houses, shops, schools, hospitals, gyms)
//   Section 2 — Transport map: 20 nodes (depots + cities)
// All edges use Euclidean distance as weight.

package mdvrp;

import java.util.*;

public class MapFactory {

    // ═════════════════════════════════════════════════════════════════════════
    //  SECTION 1 — Delivery Boy Map  (100 nodes on ~800×600 canvas)
    // ═════════════════════════════════════════════════════════════════════════
    public static Graph buildDeliveryMap() {
        Graph g = new Graph();

        // Node layout: arranged in a realistic neighbourhood grid with variation
        // Types: HOUSE(1-60), SHOP(61-75), SCHOOL(76-82), HOSPITAL(83-87), GYM(88-100)

        // ── Houses (60 nodes) ─────────────────────────────────────────────────
        int[][] houseCoords = {
            {80,80},{150,75},{230,85},{310,80},{390,75},{470,80},{550,85},{630,80},{710,75},{780,80},
            {80,150},{160,145},{240,155},{320,150},{400,145},{480,150},{560,155},{640,150},{720,145},{790,150},
            {85,225},{165,220},{245,230},{325,225},{405,220},{485,225},{565,230},{645,225},{725,220},{795,225},
            {80,300},{160,295},{240,305},{320,300},{400,295},{480,300},{560,305},{640,300},{720,295},{790,300},
            {85,375},{165,370},{245,380},{325,375},{405,370},{485,375},{565,380},{645,375},{725,370},{795,375},
            {80,450},{160,445},{240,455},{320,450},{400,445},{480,450},{560,455},{640,450},{720,445},{790,450}
        };
        String[] houseNames = {
            "Oakwood Villa","Maple Cottage","Pine Residence","Cedar House","Elm Bungalow",
            "Birch Home","Willow Place","Ash Dwelling","Poplar Abode","Sycamore Lodge",
            "Sunrise Apt","Moonrise Flat","Dawn Quarters","Dusk Rooms","Zenith Home",
            "Horizon House","Crest Cottage","Summit Lodge","Valley View","Ridge Retreat",
            "Brook Side","River View","Lake Shore","Pond Edge","Creek Cottage",
            "Stream Side","Spring Villa","Well House","Fountain Home","Rain Drop Inn",
            "Garden Villa","Rose Cottage","Lily House","Daisy Home","Tulip Place",
            "Jasmine Lodge","Orchid Flat","Iris Abode","Violet Rooms","Peony Suite",
            "Hill Top","Cliff View","Meadow Home","Field House","Plain Cottage",
            "Slope Villa","Crest Flat","Peak Lodge","Summit Inn","Terrace Home",
            "North End","South Bay","East Wing","West Side","Central Apt",
            "Corner House","Junction Home","Cross Roads","Main Street","Back Lane"
        };
        for (int i = 0; i < 60; i++) {
            g.addNode(new Graph.Node(i + 1, houseNames[i], Graph.NodeType.HOUSE,
                houseCoords[i][0], houseCoords[i][1]));
        }

        // ── Shops (15 nodes, IDs 61-75) ───────────────────────────────────────
        double[][] shopCoords = {
            {200,130},{380,120},{560,130},{730,120},{120,270},
            {300,260},{500,270},{680,260},{200,350},{420,340},
            {620,350},{130,420},{340,420},{540,415},{730,425}
        };
        String[] shopNames = {
            "FreshMart","QuickShop","City Store","Super Bazaar","Corner Kiosk",
            "Daily Needs","Express Mart","Green Grocer","BakeryPlus","TechZone",
            "Fashion Hub","Book World","Toy Land","Sports Den","Pharma Plus"
        };
        for (int i = 0; i < 15; i++) {
            g.addNode(new Graph.Node(61 + i, shopNames[i], Graph.NodeType.SHOP,
                shopCoords[i][0], shopCoords[i][1]));
        }

        // ── Schools (7 nodes, IDs 76-82) ─────────────────────────────────────
        double[][] schoolCoords = {
            {200,200},{450,195},{680,205},{180,390},{460,385},{700,395},{340,310}
        };
        String[] schoolNames = {
            "St. Mary's School","Greenfield Academy","Sunrise High",
            "City Public School","Newton Institute","River Valley School","Central Academy"
        };
        for (int i = 0; i < 7; i++) {
            g.addNode(new Graph.Node(76 + i, schoolNames[i], Graph.NodeType.SCHOOL,
                schoolCoords[i][0], schoolCoords[i][1]));
        }

        // ── Hospitals (5 nodes, IDs 83-87) ───────────────────────────────────
        double[][] hospCoords = {
            {130,110},{590,105},{760,310},{350,470},{530,330}
        };
        String[] hospNames = {
            "City Hospital","Metro Medical","North Health Centre",
            "South Clinic","Central Care"
        };
        for (int i = 0; i < 5; i++) {
            g.addNode(new Graph.Node(83 + i, hospNames[i], Graph.NodeType.HOSPITAL,
                hospCoords[i][0], hospCoords[i][1]));
        }

        // ── Gyms (13 nodes, IDs 88-100) ──────────────────────────────────────
        double[][] gymCoords = {
            {270,110},{490,115},{350,200},{600,210},{250,290},
            {470,295},{700,285},{130,365},{380,365},{610,370},
            {270,455},{500,460},{720,455}
        };
        String[] gymNames = {
            "PowerFit Gym","IronZone","FlexStudio","CardioHub","StrengthBase",
            "ActiveFit","PulseFit","BodyCraft","EliteFit","SpeedFit",
            "ZenFit","CoreFit","MaxFit"
        };
        for (int i = 0; i < 13; i++) {
            g.addNode(new Graph.Node(88 + i, gymNames[i], Graph.NodeType.GYM,
                gymCoords[i][0], gymCoords[i][1]));
        }

        // ── Build edges: connect each node to its ~4 nearest neighbours ───────
        connectNearestNeighbours(g, 4);

        return g;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SECTION 2 — Transport / Depot Map  (20 nodes on ~800×550 canvas)
    // ═════════════════════════════════════════════════════════════════════════
    public static Graph buildTransportMap() {
        Graph g = new Graph();

        // ── Depots (5 nodes, IDs 1-5) ─────────────────────────────────────────
        double[][] depotCoords = {
            {120,100},{700,90},{100,430},{720,440},{410,270}
        };
        String[] depotNames = {
            "Depot North-West","Depot North-East","Depot South-West",
            "Depot South-East","Central Hub Depot"
        };
        for (int i = 0; i < 5; i++) {
            g.addNode(new Graph.Node(i + 1, depotNames[i], Graph.NodeType.DEPOT,
                depotCoords[i][0], depotCoords[i][1]));
        }

        // ── Cities / delivery centres (15 nodes, IDs 6-20) ───────────────────
        double[][] cityCoords = {
            {250,90},{420,80},{570,100},{680,220},{760,330},
            {680,430},{530,490},{370,510},{220,480},{110,350},
            {130,210},{300,200},{500,190},{600,310},{350,380}
        };
        String[] cityNames = {
            "Northfield City","Lakeville","Eastport","Highbridge","Coastal Town",
            "Southgate","Riverdale","Westwood","Harborview","Mountainside",
            "Clearwater","Midtown","Uptown Junction","Crossroads City","Valley Centre"
        };
        for (int i = 0; i < 15; i++) {
            g.addNode(new Graph.Node(6 + i, cityNames[i], Graph.NodeType.CITY,
                cityCoords[i][0], cityCoords[i][1]));
        }

        // ── Connect: each node to 3 nearest ───────────────────────────────────
        connectNearestNeighbours(g, 3);

        // ── Extra highway connections between depots ───────────────────────────
        addEdgeIfAbsent(g, 1, 2, 310.0);
        addEdgeIfAbsent(g, 1, 3, 280.0);
        addEdgeIfAbsent(g, 2, 4, 270.0);
        addEdgeIfAbsent(g, 3, 4, 290.0);
        addEdgeIfAbsent(g, 5, 1, 200.0);
        addEdgeIfAbsent(g, 5, 2, 205.0);
        addEdgeIfAbsent(g, 5, 3, 215.0);
        addEdgeIfAbsent(g, 5, 4, 210.0);

        return g;
    }

    // ── Connect each node to its K nearest neighbours ─────────────────────────
    private static void connectNearestNeighbours(Graph g, int k) {
        List<Graph.Node> nodes = new ArrayList<>(g.getNodes().values());
        for (Graph.Node u : nodes) {
            // Sort other nodes by distance
            nodes.stream()
                 .filter(v -> v.id != u.id)
                 .sorted(Comparator.comparingDouble(v -> Graph.euclidean(u, v)))
                 .limit(k)
                 .forEach(v -> addEdgeIfAbsent(g, u.id, v.id,
                     Graph.euclidean(u, v)));
        }
    }

    // ── Add edge only if neither direction already exists ─────────────────────
    private static void addEdgeIfAbsent(Graph g, int u, int v, double w) {
        List<Graph.Edge> list = g.getAdj().get(u);
        if (list == null) return;
        boolean exists = list.stream().anyMatch(e -> e.to == v);
        if (!exists) g.addEdge(u, v, w);
    }
}
