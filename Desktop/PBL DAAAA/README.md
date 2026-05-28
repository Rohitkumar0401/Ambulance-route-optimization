# Ambulance Route Optimization System

**Team:** Visitors | **Course:** PBL — Design & Analysis of Algorithms

An intelligent emergency response system that computes the fastest and safest ambulance routes by factoring in real-time traffic, road conditions, and roadblocks. Includes a secondary MDVRP (Multi-Depot Vehicle Routing) module for general delivery/transport routing.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17+ (pure `com.sun.net.httpserver`) |
| Frontend | React 18, Leaflet, React Router 6 |
| Database | MySQL 8 |
| Auth | Manual HS256 JWT + Spring Security BCrypt |
| Build | `build_and_run.sh` (no Maven/Gradle) |

---

## Project Structure

```
PBL DAAAA/
├── backend/
│   ├── MainServer.java                  ← Unified HTTP server (port 5001)
│   ├── config/
│   │   ├── DatabaseConfig.java          ← Shared JDBC connection
│   │   └── DbInit.java                  ← Schema + seed data runner
│   ├── database/
│   │   └── schema.sql                   ← Full MySQL schema
│   ├── utils/
│   │   └── ErrorHandler.java
│   └── modules/
│       ├── alerts/
│       │   └── AlertController.java
│       ├── emergency-request/
│       │   ├── EmergencyController.java
│       │   └── models/RequestQueue.java
│       ├── hospital-management/         ← See hospital-management/README.md
│       │   ├── Hospital.java
│       │   ├── HospitalController.java
│       │   ├── HospitalRepository.java
│       │   └── HospitalSearch.java
│       ├── road-scoring/
│       │   └── RoadScoringController.java
│       ├── route-optimization/
│       │   ├── AmbulanceGraph.java
│       │   ├── RouteOptimizationController.java
│       │   └── algorithms/
│       │       ├── Graph.java
│       │       ├── Dijkstra.java            ← MDVRP Dijkstra (integer nodes)
│       │       ├── DijkstraAlgorithm.java   ← Ambulance Dijkstra (GPS coords)
│       │       ├── AStarAlgorithm.java
│       │       ├── MapFactory.java
│       │       ├── Main.java
│       │       └── Server.java
│       ├── traffic-analysis/
│       │   ├── TrafficController.java
│       │   └── models/TrafficAnalyzer.java
│       └── user-authentication/
│           ├── AuthController.java
│           └── middleware/AuthMiddleware.java
├── frontend/
│   ├── src/
│   │   ├── App.js
│   │   └── components/
│   │       ├── Dashboard.js
│   │       ├── EmergencyRequest.js
│   │       ├── HospitalManagement.js
│   │       ├── Login.js
│   │       ├── RoadScoring.js
│   │       ├── RouteOptimization.js
│   │       └── UserManagement.js
│   └── public/
│       ├── index.html
│       ├── hospitals.html
│       ├── mdvrp.html               ← MDVRP interactive map UI
│       ├── mdvrp-script.js
│       └── mdvrp-style.css
├── mysql-connector-j.jar
├── spring-security-crypto.jar
├── build_and_run.sh                 ← Main build + run script
└── .env                             ← Environment config (not committed)
```

---

## Prerequisites

- Java JDK 17+
- MySQL 8.0+
- Node.js 14+ and npm (for the React frontend)
- `mysql-connector-j.jar` and `spring-security-crypto.jar` in the project root

---

## Setup

### 1. Configure environment

Copy `.env.example` to `.env` and fill in your MySQL credentials:

```env
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=your_password
DB_NAME=ambulance_optimization
PORT=5001
JWT_SECRET=your_secure_jwt_secret
```

### 2. Initialize the database

```bash
bash build_and_run.sh db-init
```

This creates the database, all tables, and seeds:
- 5 sample hospitals
- Admin user (`admin@ambulance.com` / `admin123`)
- Sample road scores, alerts, and emergency requests

### 3. Install frontend dependencies

```bash
cd frontend
npm install
```

---

## Running the Application

### Backend (Java server)

```bash
bash build_and_run.sh
```

Compiles all Java source files and starts the server on **http://localhost:5001**.

### Frontend (React dev server)

```bash
cd frontend
npm start
```

Opens the React app on **http://localhost:3000**.

### MDVRP Routing UI

Once the backend is running, open **http://localhost:5001/mdvrp.html** (served as a static file) or navigate to it via the React app.

---

## API Reference

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, returns JWT |

### Emergency Requests
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/emergency/create` | Create emergency request |
| GET | `/api/emergency/next` | Dequeue next request (FIFO + priority) |
| PUT | `/api/emergency/update-status` | Update request status |
| GET | `/api/emergency/all` | List all requests |

### Hospital Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/hospitals` | List all hospitals |
| GET | `/api/hospitals/:id` | Get hospital by ID |
| POST | `/api/hospitals/add` | Add hospital |
| PUT | `/api/hospitals/:id` | Update hospital |
| DELETE | `/api/hospitals/:id` | Delete hospital |
| GET | `/api/hospitals/search?name=` | Binary search by name |
| GET | `/api/hospitals/nearest?lat=&lon=&count=` | Find nearest hospitals |

### Route Optimization
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/route-optimization/calculate` | Compute optimal route (Dijkstra/A*) |
| POST | `/api/route-optimization/reroute` | Dynamic reroute around roadblock |

### Traffic & Road Conditions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/traffic-analysis/traffic/:roadId` | Get traffic data |
| POST | `/api/traffic-analysis/traffic/update` | Update traffic condition |
| GET | `/api/traffic-analysis/road-conditions/:roadId` | Get road condition |
| POST | `/api/traffic-analysis/roadblock/report` | Report a roadblock |

### Road Scoring
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/road-scoring/score/:roadId` | Get composite road score |
| POST | `/api/road-scoring/score/update` | Update road score |

### MDVRP (Delivery/Transport Routing)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/delivery/map` | Delivery map (100 nodes) |
| POST | `/api/delivery/route` | Compute delivery route |
| GET | `/api/transport/map` | Transport map (20 nodes) |
| POST | `/api/transport/route` | Compute transport route |

### Misc
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Server health check |

---

## Algorithms

| Algorithm | Location | Use Case |
|-----------|----------|----------|
| Dijkstra (GPS) | `DijkstraAlgorithm.java` | Ambulance shortest path with real lat/lon coordinates |
| A* | `AStarAlgorithm.java` | Heuristic-guided ambulance routing |
| Dijkstra (graph) | `algorithms/Dijkstra.java` | MDVRP integer-node routing |
| Greedy Nearest Neighbour | `algorithms/Dijkstra.java` | TSP heuristic for multi-stop routes |
| Binary Search | `HospitalSearch.java` | Fast hospital lookup by name |
| Quicksort | `HospitalSearch.java` | Nearest hospital ranking by distance |
| Priority Queue (FIFO) | `RequestQueue.java` | Emergency request handling |

---

## Default Credentials

After `db-init`:

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@ambulance.com` | `admin123` |

> Change the default password before any production use.

---

## Troubleshooting

**MySQL connection refused**
```bash
mysql -u root -p   # verify MySQL is running
```

**Port already in use**
```bash
# Change PORT in .env
PORT=5002
```

**Compilation errors**
```bash
# Ensure both JARs are in the project root
ls *.jar
```

**Frontend not loading**
```bash
cd frontend && npm install && npm start
```

---

## Team

| Member | Module |
|--------|--------|
| Rohit Kumar (Lead) | Route Optimization (Dijkstra/A*) + User Authentication |
| Rahul Singh | Hospital & Location Management |
| Karan Singh | Traffic & Road Condition Analysis |
| Kabeer Kandari | Emergency Request Handling |
