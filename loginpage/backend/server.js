const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 4000;
const CSV_FILE = path.join(__dirname, 'users.csv');
const FRONTEND = path.join(__dirname, '..', 'frontend');

// Create CSV with header if it doesn't exist
if (!fs.existsSync(CSV_FILE)) {
    fs.writeFileSync(CSV_FILE, 'name,email,password,login_type,timestamp\n');
}

function serveStatic(res, filePath) {
    const ext = path.extname(filePath);
    const types = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript' };
    fs.readFile(filePath, (err, data) => {
        if (err) { res.writeHead(404); res.end('Not found'); return; }
        res.writeHead(200, { 'Content-Type': types[ext] || 'text/plain' });
        res.end(data);
    });
}

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
    return new Promise(resolve => {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => { try { resolve(JSON.parse(body)); } catch { resolve({}); } });
    });
}

function appendCSV(name, email, password, loginType) {
    const timestamp = new Date().toISOString();
    const row = `"${name}","${email}","${password}","${loginType}","${timestamp}"\n`;
    fs.appendFileSync(CSV_FILE, row);
}

const server = http.createServer(async (req, res) => {
    const { pathname } = url.parse(req.url);
    const method = req.method;

    if (method === 'OPTIONS') { send(res, 204, {}); return; }

    // Serve frontend
    if (pathname === '/' && method === 'GET') {
        serveStatic(res, path.join(FRONTEND, 'login.html')); return;
    }
    if (pathname === '/style.css') { serveStatic(res, path.join(FRONTEND, 'style.css')); return; }
    if (pathname === '/script.js') { serveStatic(res, path.join(FRONTEND, 'script.js')); return; }

    // POST /register — save new user to CSV
    if (pathname === '/register' && method === 'POST') {
        const { name, email, password } = await parseBody(req);
        if (!name || !email || !password) {
            send(res, 400, { success: false, message: 'name, email, password required' }); return;
        }
        // Check if email already exists
        const csv = fs.readFileSync(CSV_FILE, 'utf8');
        if (csv.includes(`"${email}"`)) {
            send(res, 409, { success: false, message: 'Email already registered' }); return;
        }
        appendCSV(name, email, password, 'email');
        send(res, 201, { success: true, message: 'Registered successfully' }); return;
    }

    // POST /login — verify user from CSV
    if (pathname === '/login' && method === 'POST') {
        const { email, password } = await parseBody(req);
        if (!email || !password) {
            send(res, 400, { success: false, message: 'email and password required' }); return;
        }
        const lines = fs.readFileSync(CSV_FILE, 'utf8').trim().split('\n').slice(1);
        const found = lines.find(line => {
            // Parse quoted CSV properly
            const cols = line.match(/"([^"]*)"/g)?.map(v => v.replace(/"/g, '')) || [];
            return cols[1] === email && cols[2] === password;
        });
        if (!found) { send(res, 401, { success: false, message: 'Invalid email or password' }); return; }
        send(res, 200, { success: true, message: 'Login successful' }); return;
    }

    // POST /social-login — save social login to CSV
    if (pathname === '/social-login' && method === 'POST') {
        const { name, email, provider } = await parseBody(req);
        if (!email) { send(res, 400, { success: false, message: 'email required' }); return; }
        const csv = fs.readFileSync(CSV_FILE, 'utf8');
        if (!csv.includes(`"${email}"`)) {
            appendCSV(name || email, email, 'social-auth', provider);
        }
        send(res, 200, { success: true, message: `${provider} login successful` }); return;
    }

    send(res, 404, { success: false, message: 'Not found' });
});

server.listen(PORT, () => console.log(`Auth server running on http://localhost:${PORT}`));
process.on('uncaughtException', err => console.error('Error:', err.message));
