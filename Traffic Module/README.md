# Traffic & Road Condition Analysis Module

**Project:** Ambulance Route Optimization in Remote Areas  
**Team:** Visitors  
**Developer:** Karan Singh  

---

## Overview

This module analyses traffic conditions and road quality for ambulance routing. It provides a REST API that returns route info, road condition, and the nearest recommended hospital for a given origin-destination pair.

---

## Tech Stack

| Layer   | Technology          |
|---------|---------------------|
| Backend | Node.js, Express.js |
| HTTP    | Axios               |

---

## Setup

**1. Install dependencies**
```bash
cd "Traffic Module"
npm install
```

**2. Start the server**
```bash
npm start
```

Server runs at `http://localhost:3000`

---

## API Endpoints

| Method | Endpoint     | Description                                      |
|--------|--------------|--------------------------------------------------|
| GET    | `/traffic`   | Get route info, road condition, hospital suggestion |

**Query params:** `origin`, `destination`

**Example:**
```
GET /traffic?origin=Delhi&destination=Noida
```

**Response:**
```json
{
  "request": { "origin": "Delhi", "destination": "Noida" },
  "route": { "distance": "20 km", "duration": "30 mins", "path": "Delhi → Noida" },
  "hospital": "Fortis Hospital Noida",
  "roadCondition": "Good"
}
```

---

## Key Functions — `trafficModule.js`

- `getDistance(lat1, lon1, lat2, lon2)` — Haversine formula, returns distance in km
- `findNearestHospital(userLat, userLng)` — finds closest hospital by GPS coordinates
- `getRoute(origin, destination)` — returns route summary
- `analyzeRoadCondition()` — returns road condition: Good / Moderate / Poor / Blocked

---

## Integration

This module is integrated into the **Hospital & Location Management Module** dashboard (Rahul Singh).  
The dashboard calls `GET /traffic` from the "Traffic & Roads" page to display route analysis alongside hospital data.

Both servers must be running for full integration:
- Hospital Module → `http://localhost:3001`
- Traffic Module → `http://localhost:3000`
