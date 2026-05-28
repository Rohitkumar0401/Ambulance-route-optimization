package mdvrp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Server {

    private final HttpServer server;

    // ─────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────

    public Server(int port) throws IOException {

        server = HttpServer.create(
                new InetSocketAddress(port),
                0
        );

        // API ROUTES

        server.createContext(
                "/health",
                new HealthHandler()
        );

        server.createContext(
                "/api/ambulance/map",
                new MapHandler()
        );

        server.createContext(
                "/api/ambulance/route",
                new RouteHandler()
        );

        // FRONTEND FILES

        server.createContext(
                "/",
                new FrontendHandler()
        );

        server.setExecutor(null);
    }

    // ─────────────────────────────────────────────────────────
    // START SERVER
    // ─────────────────────────────────────────────────────────

    public void start() {

        server.start();

        System.out.println();
        System.out.println(
                "======================================="
        );
        System.out.println(
                " Ambulance Server Running"
        );
        System.out.println(
                " http://localhost:8080"
        );
        System.out.println(
                "======================================="
        );
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────
    // HEALTH CHECK
    // ─────────────────────────────────────────────────────────

    static class HealthHandler
            implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
                throws IOException {

            String response = "OK";

            exchange.getResponseHeaders().add(
                    "Content-Type",
                    "text/plain"
            );

            exchange.sendResponseHeaders(
                    200,
                    response.length()
            );

            OutputStream os =
                    exchange.getResponseBody();

            os.write(response.getBytes());

            os.close();
        }
    }

    // ─────────────────────────────────────────────────────────
    // MAP API
    // ─────────────────────────────────────────────────────────

    static class MapHandler
            implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
                throws IOException {

            Graph graph =
                    MapFactory.createCityMap();

            String json =
                    graph.toJson();

            exchange.getResponseHeaders().add(
                    "Content-Type",
                    "application/json"
            );

            exchange.getResponseHeaders().add(
                    "Access-Control-Allow-Origin",
                    "*"
            );

            byte[] bytes =
                    json.getBytes(
                            StandardCharsets.UTF_8
                    );

            exchange.sendResponseHeaders(
                    200,
                    bytes.length
            );

            OutputStream os =
                    exchange.getResponseBody();

            os.write(bytes);

            os.close();
        }
    }

    // ─────────────────────────────────────────────────────────
    // ROUTE API
    // ─────────────────────────────────────────────────────────

    static class RouteHandler
            implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
                throws IOException {

            // Allow POST only

            if (!exchange.getRequestMethod()
                    .equalsIgnoreCase("POST")) {

                exchange.sendResponseHeaders(
                        405,
                        -1
                );

                return;
            }

            // Read request body

            InputStream is =
                    exchange.getRequestBody();

            String body =
                    new String(
                            is.readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            System.out.println(
                    "Incoming JSON: " + body
            );

            // Parse hospitalNode

            int hospitalNode =
                    extractSingleInt(
                            body,
                            "hospitalNode"
                    );

            // Parse patientNodes

            int[] patients =
                    extractIntArray(
                            body,
                            "patientNodes"
                    );

            Graph graph =
                    MapFactory.createCityMap();

            java.util.List<Integer> patientList =
                    new java.util.ArrayList<>();

            for (int p : patients) {

                patientList.add(p);
            }

            Dijkstra.AmbulanceRouteResult result =
                    Dijkstra.optimizeAmbulanceRoute(
                            graph,
                            hospitalNode,
                            patientList
                    );

            String response =
                    result.toJson();

            exchange.getResponseHeaders().add(
                    "Content-Type",
                    "application/json"
            );

            exchange.getResponseHeaders().add(
                    "Access-Control-Allow-Origin",
                    "*"
            );

            byte[] bytes =
                    response.getBytes(
                            StandardCharsets.UTF_8
                    );

            exchange.sendResponseHeaders(
                    200,
                    bytes.length
            );

            OutputStream os =
                    exchange.getResponseBody();

            os.write(bytes);

            os.close();
        }

        // ─────────────────────────────────────────────
        // EXTRACT INTEGER
        // ─────────────────────────────────────────────

        private int extractSingleInt(
                String json,
                String key
        ) {

            try {

                int start =
                        json.indexOf(
                                "\"" + key + "\""
                        );

                if (start == -1)
                    return 0;

                start =
                        json.indexOf(
                                ":",
                                start
                        ) + 1;

                int end =
                        json.indexOf(
                                ",",
                                start
                        );

                if (end == -1) {

                    end =
                            json.indexOf(
                                    "}",
                                    start
                            );
                }

                return Integer.parseInt(
                        json.substring(start, end)
                                .trim()
                );

            } catch (Exception e) {

                return 0;
            }
        }

        // ─────────────────────────────────────────────
        // EXTRACT ARRAY
        // ─────────────────────────────────────────────

        private int[] extractIntArray(
                String json,
                String key
        ) {

            try {

                int start =
                        json.indexOf(
                                "\"" + key + "\""
                        );

                if (start == -1)
                    return new int[0];

                start =
                        json.indexOf(
                                "[",
                                start
                        ) + 1;

                int end =
                        json.indexOf(
                                "]",
                                start
                        );

                String content =
                        json.substring(start, end)
                                .trim();

                if (content.isEmpty()) {

                    return new int[0];
                }

                String[] parts =
                        content.split(",");

                int[] arr =
                        new int[parts.length];

                for (int i = 0;
                     i < parts.length;
                     i++) {

                    arr[i] =
                            Integer.parseInt(
                                    parts[i].trim()
                            );
                }

                return arr;

            } catch (Exception e) {

                return new int[0];
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // FRONTEND FILE HANDLER
    // ─────────────────────────────────────────────────────────

    static class FrontendHandler
            implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange)
                throws IOException {

            String path =
                    exchange.getRequestURI()
                            .getPath();

            if (path.equals("/")) {

                path = "/index.html";
            }

            File file =
                    new File(
                            "frontend" + path
                    );

            if (!file.exists()) {

                String response =
                        "404 File Not Found";

                exchange.sendResponseHeaders(
                        404,
                        response.length()
                );

                OutputStream os =
                        exchange.getResponseBody();

                os.write(response.getBytes());

                os.close();

                return;
            }

            String contentType =
                    getContentType(path);

            exchange.getResponseHeaders().add(
                    "Content-Type",
                    contentType
            );

            byte[] bytes =
                    Files.readAllBytes(
                            file.toPath()
                    );

            exchange.sendResponseHeaders(
                    200,
                    bytes.length
            );

            OutputStream os =
                    exchange.getResponseBody();

            os.write(bytes);

            os.close();
        }

        // ─────────────────────────────────────────────
        // CONTENT TYPE
        // ─────────────────────────────────────────────

        private String getContentType(
                String path
        ) {

            if (path.endsWith(".html")) {

                return "text/html";

            } else if (path.endsWith(".css")) {

                return "text/css";

            } else if (path.endsWith(".js")) {

                return "application/javascript";

            } else {

                return "text/plain";
            }
        }
    }
}