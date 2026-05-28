# 🚑 Ambulance Route Optimization System

**Team Visitors** | PBL — Design & Analysis of Algorithms | Java + React

An intelligent emergency response platform that computes the fastest and safest ambulance routes in real time. The system factors in live traffic conditions, road quality scores, roadblocks, and hospital bed availability to dispatch ambulances optimally — built entirely without any web framework, using pure Java on the backend.

---

## What This Project Does

Emergency response time is critical. In remote or congested areas, a wrong route can cost lives. This system solves that by:

- **Computing the shortest ambulance route** using a traffic-aware Dijkstra algorithm that penalizes congested roads, bad roads, and rewards emergency corridors
- **Optimizing multi-patient dispatch** — when multiple patients need pickup, the system finds the greedy nearest-neighbor route: Hospital → Patient 1 → Patient 2 → ... → Hospital
- **Monitoring road health** in real time with a composite scoring system (road quality, terrain, congestion, weather, incidents) and auto-flagging critical roads
- **Managing hospital capacity** — tracks bed availability, facilities, ratings, and finds the nearest available hospital to any location
- **Prioritizing emergency requests** using a severity-based priority queue (critical > high > medium > low)
- **Alerting authorities** when roads cross danger thresholds and generating government reports

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21 — pure `com.sun.net.httpserver` (no Spring, no framework) |
| Frontend | React 18, React Router 6 |
| Database | MySQL 8 |
| Auth | Manual HS256 JWT + Spring Security BCrypt |
| Build | `build_and_run.sh` — no Maven, no Gradle |
| Geocoding | OpenStreetMap Nominatim (free, no API key) |

---

## Project Structure

```
PBL DAAAA/
├── backend/
│   ├── MainServer.java                        ← Unified HTTP server (port 5001)
│   ├── config/
│   │   ├── DatabaseConfig.java                ← JDBC connection pool (10 connections)
│   │   └── DbInit.java                        ← Schema runner + seed data
│   ├── database/
│   │   └── schema.sql                         ← Full MySQL schema (all tables)
│   ├── utils/
│   │   └── ErrorHandler.java                  ← Shared HTTP response helpers
│   └── modules/
│       ├── user-authentication/
│       │   ├── AuthController.java            ← Register, login, user management
│       │   └── middleware/AuthMiddleware.java  ← JWT verification + role guards
│       ├── hospital-management/
│       │   ├── Hospital.java                  ← Model: Haversine distance, occupancy
│       │   ├── HospitalController.java        ← All /api/hospitals/* handlers
│       │   ├── HospitalRepository.java        ← SQL: CRUD, pool, rating, history
│       │   └── HospitalSearch.java            ← Binary search + Quicksort nearest
│       ├── route-optimization/
│       │   └── algorithms/
│       │       ├── Graph.java                 ← Typed nodes (HOSPITAL/PATIENT/STATION)
│       │       ├── Dijkstra.java              ← Ambulance Dijkstra + multi-patient optimizer
│       │       └── MapFactory.java            ← 20-node city map builder
│       ├── emergency-request/
│       │   ├── EmergencyController.java       ← Create, dequeue, update status
│       │   └── models/RequestQueue.java       ← Priority queue (severity-based)
│       ├── road-scoring/
│       │   └── RoadScoringController.java     ← Composite score, threshold flagging
│       ├── traffic-analysis/
│       │   ├── TrafficController.java         ← Traffic data, roadblock reporting
│       │   └── models/TrafficAnalyzer.java    ← Traffic analysis logic
│       └── alerts/
│           └── AlertController.java           ← Alert CRUD, government report
├── frontend/
│   ├── src/
│   │   ├── App.js                             ← Router, auth, role-based nav
│   │   └── components/
│   │       ├── Login.js                       ← Auth form with demo credentials
│   │       ├── Dashboard.js                   ← Real-time ops overview (auto-refresh 30s)
│   │       ├── EmergencyRequest.js            ← Submit + manage emergency requests
│   │       ├── RouteOptimization.js           ← Canvas map, click-to-route UI
│   │       ├── HospitalManagement.js          ← Hospital CRUD, nearest search, ratings
│   │       ├── RoadScoring.js                 ← Road score viewer + flagging
│   │       └── UserManagement.js              ← Admin user management
│   └── public/
│       ├── index.html
│       └── ambulance-route.html               ← Standalone canvas route map
├── mysql-connector-j.jar
├── spring-security-crypto.jar
├── commons-logging.jar
├── build_and_run.sh                           ← Compile + run backend
├── start.sh                                   ← Start BOTH servers with one command
└── .env                                       ← DB credentials (not committed)
```

---

## How It Works — Module by Module

### 1. Route Optimization (Dijkstra + Multi-Patient)

The core algorithm lives in `algorithms/Dijkstra.java`. When an ambulance needs to reach multiple patients:

```
Cost of edge = base_weight + (trafficLevel × 2) + (badRoad ? 50 : 0) + (emergencyRoute ? -5 : 0)
```

The multi-patient optimizer runs greedy nearest-neighbor:
1. Start at hospital node
2. Run Dijkstra from current position
3. Pick the nearest unvisited patient
4. Move there, repeat until all patients visited
5. Return ambulance to hospital

The city map has **20 nodes** — 5 hospitals, 10 patient zones, 5 ambulance stations — connected by 52 edges with real traffic/road properties.

**API:**
```
GET  /api/ambulance/map    → full graph (nodes + edges + types)
POST /api/ambulance/route  → { hospitalNode, patientNodes[] } → optimized route
```

### 2. Hospital Management

Hospitals are stored in MySQL with full metadata. Key features:

- **Nearest hospital search** — Haversine formula computes real-world distance, Quicksort ranks results
- **Binary search by name** — O(log n) exact match, falls back to partial scan
- **Address-based geocoding** — type a full address, coordinates auto-detected via OpenStreetMap Nominatim (no API key needed)
- **Rating system** — users submit 1–5 star ratings, stored individually, running average updated in hospital record
- **Audit history** — every bed update, availability change, and rating submission is logged to `hospital_history`
- **Bulk bed update** — update multiple hospitals' bed counts in one API call
- **Sort options** — by name, rating, available beds, total beds, or occupancy

### 3. Emergency Request Queue

Requests are stored in MySQL and managed via a priority queue in memory (`RequestQueue.java`). Priority order: `critical > high > medium > low`. Within the same priority, FIFO ordering applies.

Dispatchers dequeue the next highest-priority request via `GET /api/emergency/next`.

### 4. Road Scoring & Alerting

Each road gets a **composite score (0–100)** calculated from:

| Factor | Weight |
|--------|--------|
| Road quality | degradation multiplier |
| Terrain difficulty | slope/surface factor |
| Congestion level | 0.0–1.0 |
| Average speed | inverse relationship |
| Incident count | cumulative penalty |
| Weather factor | seasonal multiplier |

Scores are classified:
- **0–49** → `good` (green)
- **50–74** → `warning` (yellow)
- **75–100** → `critical` (red) — auto-generates an alert

### 5. Authentication & Role-Based Access

JWT tokens (HS256, 24h expiry) are issued on login. Every protected route verifies the token via `AuthMiddleware`. Four roles with different access:

| Role | Access |
|------|--------|
| `admin` | Everything including user management |
| `dispatcher` | Dashboard, emergency, route, hospitals, road scoring |
| `driver` | Dashboard, emergency, route, hospitals |
| `user` | Dashboard, emergency, route, hospitals |

---

## Prerequisites

- Java JDK 21+
- MySQL 8.0+
- Node.js 16+ and npm
- Three JARs in project root: `mysql-connector-j.jar`, `spring-security-crypto.jar`, `commons-logging.jar`

---

## Setup

### 1. Configure environment

Copy `.env.example` to `.env`:

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

Creates all tables and seeds:
- 5 sample hospitals
- 3 demo users (admin, dispatcher, driver)
- 8 road scores with flags
- 6 sample alerts
- 5 emergency requests

### 3. Install frontend dependencies

```bash
cd frontend && npm install
```

---

## Running the Project

### Single command (recommended)

```bash
bash start.sh
```

This compiles the Java backend, waits for it to be healthy, then starts the React frontend. Press `Ctrl+C` to stop both.

| Service | URL |
|---------|-----|
| Backend API | http://localhost:5001 |
| Frontend | http://localhost:3000 |
| Health check | http://localhost:5001/health |

### Separately

```bash
# Backend only
bash build_and_run.sh

# Frontend only
cd frontend && npm start
```

---

## Demo Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@ambulance.com` | `admin123` |
| Dispatcher | `dispatcher@ambulance.com` | `dispatch123` |
| Driver | `driver@ambulance.com` | `driver123` |

Click any row on the login screen to auto-fill credentials.

---

## API Reference

### Auth
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | None | Register new user |
| POST | `/api/auth/login` | None | Login, returns JWT |
| GET | `/api/auth/me` | Any | Get current user |
| GET | `/api/auth/users` | Admin | List all users |
| PATCH | `/api/auth/users/:id/role` | Admin | Change user role |
| DELETE | `/api/auth/users/:id` | Admin | Delete user |

### Ambulance Route Optimization
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/ambulance/map` | None | Full city graph (20 nodes, 52 edges) |
| POST | `/api/ambulance/route` | None | Compute optimized multi-patient route |

### Hospital Management
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/hospitals?sort=` | None | All hospitals (sort: name/rating/available_beds/occupancy) |
| GET | `/api/hospitals/available` | None | Only available hospitals |
| GET | `/api/hospitals/stats` | None | Aggregate stats |
| GET | `/api/hospitals/search?name=` | None | Binary search by name |
| GET | `/api/hospitals/nearest?lat=&lon=&count=` | None | Nearest by Haversine distance |
| GET | `/api/hospitals/filter?facility=` | None | Filter by facility type |
| GET | `/api/hospitals/:id` | None | Single hospital |
| GET | `/api/hospitals/:id/history` | None | Audit log |
| POST | `/api/hospitals/add` | None | Add hospital |
| POST | `/api/hospitals/:id/rate` | None | Submit rating (1–5) |
| POST | `/api/hospitals/bulk-beds` | None | Bulk update bed counts |
| PUT | `/api/hospitals/:id` | None | Full update |
| PATCH | `/api/hospitals/:id/beds` | None | Update available beds |
| PATCH | `/api/hospitals/:id/availability` | None | Toggle open/closed |
| DELETE | `/api/hospitals/:id` | None | Delete hospital |

### Emergency Requests
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/emergency/create` | None | Submit emergency request |
| GET | `/api/emergency/next` | Dispatcher+ | Dequeue next by priority |
| PUT | `/api/emergency/update-status` | Dispatcher+ | Update request status |
| GET | `/api/emergency/all` | Any | List all requests |

### Road Scoring
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/road-scoring/all` | Dispatcher+ | All road scores |
| GET | `/api/road-scoring/flagged` | Dispatcher+ | Warning + critical roads |
| GET | `/api/road-scoring/stats` | Dispatcher+ | Aggregate stats |
| POST | `/api/road-scoring/score` | Dispatcher+ | Score/update a road |
| POST | `/api/road-scoring/threshold-check` | Dispatcher+ | Run threshold check |

### Traffic Analysis
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/traffic-analysis/traffic/:roadId` | Dispatcher+ | Traffic data for road |
| GET | `/api/traffic-analysis/road-conditions/:roadId` | Dispatcher+ | Road condition |
| POST | `/api/traffic-analysis/roadblock/report` | Dispatcher+ | Report roadblock |
| POST | `/api/traffic-analysis/traffic/update` | Dispatcher+ | Update traffic condition |

### Alerts
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/alerts/active` | Dispatcher+ | Active alerts |
| GET | `/api/alerts/all` | Dispatcher+ | All alerts |
| GET | `/api/alerts/stats` | Dispatcher+ | Alert statistics |
| GET | `/api/alerts/government-report` | Dispatcher+ | Download JSON report |
| POST | `/api/alerts/create` | Dispatcher+ | Create alert |
| PATCH | `/api/alerts/:id/acknowledge` | Dispatcher+ | Acknowledge alert |
| PATCH | `/api/alerts/:id/resolve` | Admin | Resolve alert |

---

## Algorithms Used

| Algorithm | File | Purpose |
|-----------|------|---------|
| Dijkstra (ambulance-aware) | `algorithms/Dijkstra.java` | Shortest path with traffic/road penalties |
| Greedy Nearest Neighbor | `algorithms/Dijkstra.java` | Multi-patient route optimization |
| Binary Search | `HospitalSearch.java` | O(log n) hospital lookup by name |
| Quicksort | `HospitalSearch.java` | Sort hospitals by Haversine distance |
| Priority Queue | `RequestQueue.java` | Severity-based emergency dispatch |
| Haversine Formula | `Hospital.java` | Real-world GPS distance between coordinates |
| Composite Scoring | `RoadScoringController.java` | Weighted road health index |

---

## Database Schema

Key tables:

| Table | Purpose |
|-------|---------|
| `users` | Auth — username, email, BCrypt password, role |
| `hospitals` | Hospital data — beds, facilities, location, rating |
| `hospital_history` | Audit log for bed/availability/rating changes |
| `hospital_ratings` | Individual rating submissions (for running average) |
| `emergency_requests` | Patient emergencies with severity and status |
| `road_scores` | Composite road health scores and flag status |
| `road_conditions` | Per-road condition type (good/fair/poor/closed) |
| `traffic_data` | Congestion levels and average speeds |
| `roadblocks` | Active roadblock reports |
| `alerts` | System alerts with severity and acknowledgment |
| `activity_log` | User action audit trail |

---

## Troubleshooting

**MySQL connection refused**
```bash
mysql -u root -p   # verify MySQL is running
```

**Port already in use**
```bash
# Kill existing processes
pkill -f "backend.MainServer"
pkill -f "react-scripts"
# Then restart
bash start.sh
```

**Compilation errors — JARs missing**
```bash
ls *.jar
# Should show: mysql-connector-j.jar, spring-security-crypto.jar, commons-logging.jar
```

**Frontend not loading**
```bash
cd frontend && npm install && npm start
```

**Login says "invalid credentials"**
```bash
# Re-seed the database
bash build_and_run.sh db-init
```

---

## Team

| Member | Role | Module |
|--------|------|--------|
| Rohit Kumar | Lead | Route Optimization (Dijkstra) + User Authentication |
| Rahul Singh | Developer | Hospital & Location Management |
| Karan Singh | Developer | Traffic & Road Condition Analysis |
| Kabeer Kandari | Developer | Emergency Request Handling |
