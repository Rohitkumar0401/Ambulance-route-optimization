const express = require("express");
const cors = require("cors");
const { findNearestHospital, getRoute, analyzeRoadCondition } = require("./trafficModule");

const app = express();
app.use(cors());

const PORT = 3000;

// Hospital List
const hospitals = [
    { name: "AIIMS Delhi", location: "Delhi" },
    { name: "Fortis Hospital Noida", location: "Noida" },
    { name: "Max Hospital Saket", location: "Delhi" },
    { name: "Apollo Hospital Delhi", location: "Delhi" }
];

// Find Best Hospital by location name
function findBestHospital(origin, destination) {
    return (
        hospitals.find(h => h.location.toLowerCase().includes(origin.toLowerCase())) ||
        hospitals.find(h => h.location.toLowerCase().includes(destination.toLowerCase())) ||
        { name: "Nearest Government Hospital" }
    );
}

// API
app.get("/traffic", async (req, res) => {
    const { origin, destination } = req.query;

    if (!origin || !destination) {
        return res.json({ error: "Enter origin and destination" });
    }

    const traffic = await getRoute(origin, destination);
    const hospital = findBestHospital(origin, destination);
    const roadCondition = analyzeRoadCondition();

    res.json({
        request: { origin, destination },
        route: traffic,
        hospital: hospital.name,
        roadCondition
    });
});

app.listen(PORT, () => {
    console.log(`Traffic Module running on http://localhost:${PORT}`);
});