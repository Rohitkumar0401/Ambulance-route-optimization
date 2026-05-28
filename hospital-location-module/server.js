const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 3001;

// ===== FILE HELPERS =====
const dataPath = (file) => path.join(__dirname, 'data', file);
const read = (file) => JSON.parse(fs.readFileSync(dataPath(file), 'utf8'));
const write = (file, data) => fs.writeFileSync(dataPath(file), JSON.stringify(data, null, 2));
// Serve static dashboard files
function serveStatic(res, filePath) {
    const ext = path.extname(filePath);
    const types = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript' };
    fs.readFile(filePath, (err, data) => {
        if (err) { res.writeHead(404); res.end('Not found'); return; }
        res.writeHead(200, { 'Content-Type': types[ext] || 'text/plain' });
        res.end(data);
    });
}

// ===== ALGORITHMS =====

// Haversine formula — real-world distance in km
function haversine(lat1, lon1, lat2, lon2) {
    const R = 6371, toRad = x => x * Math.PI / 180;
    const dLat = toRad(lat2 - lat1), dLon = toRad(lon2 - lon1);
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// Binary search by name — O(log n), array must be sorted
function binarySearch(arr, name) {
    let lo = 0, hi = arr.length - 1, key = name.toLowerCase();
    while (lo <= hi) {
        const mid = Math.floor((lo + hi) / 2);
        const n = arr[mid].name.toLowerCase();
        if (n === key) return arr[mid];
        n < key ? lo = mid + 1 : hi = mid - 1;
    }
    return null;
}

// Merge sort by distance_km — O(n log n)
function mergeSort(arr) {
    if (arr.length <= 1) return arr;
    const mid = Math.floor(arr.length / 2);
    const merge = (l, r) => {
        const res = []; let i = 0, j = 0;
        while (i < l.length && j < r.length)
            res.push(l[i].distance_km <= r[j].distance_km ? l[i++] : r[j++]);
        return res.concat(l.slice(i), r.slice(j));
    };
    return merge(mergeSort(arr.slice(0, mid)), mergeSort(arr.slice(mid)));
}

// ===== RESPONSE HELPERS =====
function send(res, status, data) {
    res.writeHead(status, {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end(JSON.stringify(data));
}

function parseBody(req) {
    return new Promise((resolve) => {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try { resolve(JSON.parse(body)); }
            catch { resolve({}); }
        });
    });
}

// ===== SERVER =====
const server = http.createServer(async (req, res) => {
    try {
        const parsed = url.parse(req.url, true);
    const pathname = parsed.pathname;
    const query = parsed.query;
    const method = req.method;

    // Handle CORS preflight
    if (method === 'OPTIONS') { send(res, 204, {}); return; }

    // Serve dashboard UI
    if (pathname === '/' && method === 'GET') {
        serveStatic(res, path.join(__dirname, 'dashboard', 'index.html')); return;
    }
    if (pathname === '/style.css' && method === 'GET') {
        serveStatic(res, path.join(__dirname, 'dashboard', 'style.css')); return;
    }
    if (pathname === '/app.js' && method === 'GET') {
        serveStatic(res, path.join(__dirname, 'dashboard', 'app.js')); return;
    }

    // Health check
    if (pathname === '/health' && method === 'GET') {
        send(res, 200, { message: 'Hospital & Location Module running' }); return;
    }

    // GET /api/hospitals
    if (pathname === '/api/hospitals' && method === 'GET') {
        send(res, 200, { success: true, data: read('hospitals.json').filter(h => h.is_active) }); return;
    }

    // GET /api/hospitals/search?name=
    if (pathname === '/api/hospitals/search' && method === 'GET') {
        if (!query.name) { send(res, 400, { success: false, message: 'Name required' }); return; }
        const sorted = read('hospitals.json').filter(h => h.is_active).sort((a, b) => a.name.localeCompare(b.name));
        const result = binarySearch(sorted, query.name);
        if (!result) { send(res, 404, { success: false, message: 'Hospital not found' }); return; }
        send(res, 200, { success: true, data: result }); return;
    }

    // GET /api/hospitals/nearest?lat=&lng=
    if (pathname === '/api/hospitals/nearest' && method === 'GET') {
        if (!query.lat || !query.lng) { send(res, 400, { success: false, message: 'lat and lng required' }); return; }
        const hospitals = read('hospitals.json').filter(h => h.is_active && h.available_beds > 0);
        if (!hospitals.length) { send(res, 404, { success: false, message: 'No hospitals available' }); return; }
        const withDist = hospitals.map(h => ({ ...h, distance_km: haversine(+query.lat, +query.lng, h.latitude, h.longitude) }));
        send(res, 200, { success: true, data: mergeSort(withDist) }); return;
    }

    // GET /api/hospitals/:id
    const hospitalIdMatch = pathname.match(/^\/api\/hospitals\/(\d+)$/);
    if (hospitalIdMatch && method === 'GET') {
        const h = read('hospitals.json').find(h => h.id === +hospitalIdMatch[1]);
        if (!h) { send(res, 404, { success: false, message: 'Hospital not found' }); return; }
        send(res, 200, { success: true, data: h }); return;
    }

    // POST /api/hospitals
    if (pathname === '/api/hospitals' && method === 'POST') {
        const body = await parseBody(req);
        const { name, address, latitude, longitude, contact, specialization, available_beds } = body;
        if (!name || !address || !latitude || !longitude) {
            send(res, 400, { success: false, message: 'name, address, latitude, longitude required' }); return;
        }
        const hospitals = read('hospitals.json');
        const newH = { id: Date.now(), name, address, latitude: +latitude, longitude: +longitude, contact, specialization, available_beds: +available_beds || 0, is_active: true };
        hospitals.push(newH);
        write('hospitals.json', hospitals);
        send(res, 201, { success: true, message: 'Hospital added', id: newH.id }); return;
    }

    // GET /api/locations
    if (pathname === '/api/locations' && method === 'GET') {
        send(res, 200, { success: true, data: read('locations.json') }); return;
    }

    // POST /api/locations
    if (pathname === '/api/locations' && method === 'POST') {
        const body = await parseBody(req);
        const { name, latitude, longitude, region, is_remote } = body;
        if (!name || !latitude || !longitude) {
            send(res, 400, { success: false, message: 'name, latitude, longitude required' }); return;
        }
        const locations = read('locations.json');
        const newL = { id: Date.now(), name, latitude: +latitude, longitude: +longitude, region: region || null, is_remote: is_remote || false };
        locations.push(newL);
        write('locations.json', locations);
        send(res, 201, { success: true, message: 'Location added', id: newL.id }); return;
    }

    // 404
    send(res, 404, { success: false, message: 'Route not found' });
    } catch (err) {
        console.error('Server error:', err.message);
        send(res, 500, { success: false, message: 'Internal server error' });
    }
});

server.listen(PORT, () => console.log(`Server running on http://localhost:${PORT}`));

process.on('uncaughtException', err => console.error('Uncaught:', err.message));
process.on('unhandledRejection', err => console.error('Unhandled:', err));
