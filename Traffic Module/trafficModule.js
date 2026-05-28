const axios = require("axios");

// 🔑 (Optional API key for real maps)
const API_KEY = "";

// 🏥 Hospital Data (with coordinates)
const hospitals = [
    { name: "AIIMS Delhi", lat: 28.5672, lng: 77.2100 },
    { name: "Fortis Noida", lat: 28.5355, lng: 77.3910 },
    { name: "Max Hospital Saket", lat: 28.5245, lng: 77.2066 }
];

// 📍 Distance calculation (Haversine formula)
function getDistance(lat1, lon1, lat2, lon2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;

    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * Math.PI / 180) *
        Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);

    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// 🏥 Find nearest hospital
function findNearestHospital(userLat, userLng) {
    let nearest = null;
    let minDistance = Infinity;

    hospitals.forEach(h => {
        const dist = getDistance(userLat, userLng, h.lat, h.lng);
        if (dist < minDistance) {
            minDistance = dist;
            nearest = h;
        }
    });

    return { hospital: nearest, distance: minDistance.toFixed(2) };
}

// 🚦 Get route (dummy or API)
async function getRoute(origin, destination) {
    return {
        distance: "20 km",
        duration: "30 mins",
        path: `${origin} → ${destination}`
    };
}

// 🛣️ Road condition
function analyzeRoadCondition() {
    const conditions = ["Good", "Moderate", "Poor", "Blocked"];
    return conditions[Math.floor(Math.random() * conditions.length)];
}

module.exports = {
    findNearestHospital,
    getRoute,
    analyzeRoadCondition
};