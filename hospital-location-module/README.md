# Hospital & Location Management Module

**Project:** Ambulance Route Optimization in Remote Areas
**Team:** Visitors
**Developer:** Rahul Singh

---

## Overview

This module manages hospital data and geographic locations for the Ambulance Route Optimization system. It provides a REST API and a dashboard UI.

Built using **pure Node.js only** — no Express, no external frameworks, no database.
Data is stored in JSON files.

**Algorithms implemented:**
- Binary Search — hospital name lookup, O(log n)
- Haversine Formula — real-world GPS distance in km
- Merge Sort — ranks hospitals by distance, O(n log n)

---

## Tech Stack

| Layer    | Technology                        |
|----------|-----------------------------------|
| Backend  | Node.js (built-in `http` module)  |
| Storage  | JSON files                        |
| Frontend | HTML, CSS, Vanilla JS             |

No npm packages required. No `npm install` needed.

---

## Folder Structure

```
hospital-location-module/
├── server.js          → Pure Node.js HTTP server + all API logic
├── package.json
├── data/
│   ├── hospitals.json → Hospital records (read/write)
│   └── locations.json → Location records (read/write)
└── dashboard/
    ├── index.html     → Dashboard UI
    ├── style.css      → Dark theme styles
    └── app.js         → Dashboard logic
```

---

## Setup

```bash
node server.js
```

No `npm install` needed — uses only Node.js built-in modules (`http`, `fs`, `path`, `url`).

Server runs at `http://localhost:3001`

Open `dashboard/index.html` in your browser.

---

## API Endpoints

### Hospitals

| Method | Endpoint                           | Description                              |
|--------|------------------------------------|------------------------------------------|
| GET    | `/api/hospitals`                   | All active hospitals                     |
| GET    | `/api/hospitals/:id`               | Single hospital by ID                    |
| GET    | `/api/hospitals/search?name=`      | Binary Search by exact name — O(log n)   |
| GET    | `/api/hospitals/nearest?lat=&lng=` | Nearest hospitals — Haversine + Merge Sort |
| POST   | `/api/hospitals`                   | Add new hospital (saved to JSON)         |

### Locations

| Method | Endpoint          | Description                      |
|--------|-------------------|----------------------------------|
| GET    | `/api/locations`  | All locations                    |
| POST   | `/api/locations`  | Add new location (saved to JSON) |

---

## Algorithms

**Binary Search** — O(log n)
Hospitals sorted alphabetically, then binary searched by name.

**Haversine Formula**
Calculates real-world distance in km between two GPS lat/lng points.

**Merge Sort** — O(n log n)
Sorts hospitals by `distance_km` for the nearest hospital feature.

---

## Data Files

`data/hospitals.json` — hospital records:
```json
{
  "id": 1,
  "name": "City General Hospital",
  "address": "12 Main Road, District A",
  "latitude": 30.3165,
  "longitude": 78.0322,
  "contact": "01234567890",
  "specialization": "General",
  "available_beds": 20,
  "is_active": true
}
```

`data/locations.json` — location records:
```json
{
  "id": 1,
  "name": "Remote Village B",
  "latitude": 30.1234,
  "longitude": 77.9876,
  "region": "Remote Area B",
  "is_remote": true
}
```

---

## Integration Points

| Module | Usage |
|--------|-------|
| Route Optimization (Rohit) | Calls `GET /api/hospitals/nearest` before computing optimal path |
| Emergency Handling (Kabeer) | Calls `GET /api/hospitals/:id` to check bed availability |
| Traffic & Roads (Karan) | Uses hospital coordinates for routing decisions |
