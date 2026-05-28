const API = 'http://localhost:3001/api';
let allHospitals = [], allLocations = [];

// Navigation
function navigate(page) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    document.getElementById(`page-${page}`)?.classList.add('active');
    document.querySelector(`[data-page="${page}"]`)?.classList.add('active');
    const titles = { dashboard: 'Dashboard', hospitals: 'All Hospitals', 'add-hospital': 'Add Hospital', 'search-hospital': 'Search Hospital', 'nearest-hospital': 'Find Nearest', locations: 'All Locations', 'add-location': 'Add Location' };
    document.getElementById('pageTitle').textContent = titles[page] || page;
    if (page === 'hospitals') loadHospitals();
    if (page === 'locations') loadLocations();
    if (page === 'dashboard') loadDashboard();
}
document.querySelectorAll('.nav-item').forEach(i => i.addEventListener('click', e => { e.preventDefault(); navigate(i.dataset.page); }));

// API
async function api(path, opts = {}) {
    try {
        const r = await fetch(API + path, { headers: { 'Content-Type': 'application/json' }, ...opts });
        return await r.json();
    } catch { return { success: false, message: 'Server offline' }; }
}

// Toast
function toast(msg, type = 'info') {
    const el = document.getElementById('toast');
    el.className = `toast ${type}`;
    el.innerHTML = `<span>${{ success:'✅', error:'❌', info:'ℹ️' }[type]}</span><span>${msg}</span>`;
    el.classList.add('show');
    setTimeout(() => el.classList.remove('show'), 3000);
}

// API status
async function checkStatus() {
    const dot = document.getElementById('statusDot'), txt = document.getElementById('apiStatusText');
    try { const r = await fetch('http://localhost:3001/'); dot.className = r.ok ? 'status-dot online' : 'status-dot offline'; txt.textContent = r.ok ? 'API Online' : 'API Offline'; }
    catch { dot.className = 'status-dot offline'; txt.textContent = 'API Offline'; }
}

// Dashboard
async function loadDashboard() {
    const [hR, lR] = await Promise.all([api('/hospitals'), api('/locations')]);
    const h = hR.success ? hR.data : [], l = lR.success ? lR.data : [];
    document.getElementById('statHospitals').textContent = h.length;
    document.getElementById('statBeds').textContent = h.reduce((s, x) => s + (x.available_beds || 0), 0);
    document.getElementById('statLocations').textContent = l.length;
    document.getElementById('statRemote').textContent = l.filter(x => x.is_remote).length;
    document.getElementById('dashHospitalList').innerHTML = h.slice(0,5).map(x => `<div class="mini-list-item"><div><div class="item-name">${x.name}</div><div class="item-sub">${x.specialization||'General'}</div></div><span class="badge badge-success">Active</span></div>`).join('') || `<div class="empty-state"><i class="fas fa-hospital"></i><p>No hospitals</p></div>`;
    document.getElementById('dashLocationList').innerHTML = l.slice(0,5).map(x => `<div class="mini-list-item"><div><div class="item-name">${x.name}</div><div class="item-sub">${x.region||'—'}</div></div><span class="badge ${x.is_remote?'badge-warning':'badge-info'}">${x.is_remote?'Remote':'Urban'}</span></div>`).join('') || `<div class="empty-state"><i class="fas fa-map"></i><p>No locations</p></div>`;
}

// Hospitals
async function loadHospitals() {
    const tbody = document.getElementById('hospitalTableBody');
    tbody.innerHTML = `<tr><td colspan="5" class="loading-cell"><div class="spinner"></div></td></tr>`;
    const res = await api('/hospitals');
    allHospitals = res.success ? res.data : [];
    renderHospitals(allHospitals);
}
function renderHospitals(data) {
    const tbody = document.getElementById('hospitalTableBody');
    tbody.innerHTML = data.length ? data.map(h => `<tr>
        <td><span class="cell-id">#${h.id}</span></td>
        <td><div class="cell-primary">${h.name}</div><div class="cell-sub">${h.address}</div></td>
        <td>${h.specialization||'—'}</td>
        <td><span class="badge ${h.available_beds>0?'badge-success':'badge-danger'}">${h.available_beds>0?h.available_beds+' beds':'No beds'}</span></td>
        <td>${h.contact||'—'}</td>
    </tr>`).join('') : `<tr><td colspan="5"><div class="empty-state"><i class="fas fa-hospital"></i><p>No hospitals</p></div></td></tr>`;
}
function filterHospitals() {
    const q = document.getElementById('hospitalFilter').value.toLowerCase();
    renderHospitals(allHospitals.filter(h => h.name.toLowerCase().includes(q) || (h.specialization||'').toLowerCase().includes(q)));
}
async function submitAddHospital(e) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target));
    const res = await api('/hospitals', { method: 'POST', body: JSON.stringify(data) });
    res.success ? (toast('Hospital added','success'), e.target.reset(), navigate('hospitals')) : toast(res.message,'error');
}

// Search
async function searchHospital() {
    const name = document.getElementById('searchNameInput').value.trim();
    if (!name) return toast('Enter a name','info');
    const area = document.getElementById('searchResult');
    area.innerHTML = `<div class="spinner" style="margin-top:20px"></div>`;
    const res = await api(`/hospitals/search?name=${encodeURIComponent(name)}`);
    if (!res.success) { area.innerHTML = `<div class="empty-state"><i class="fas fa-search"></i><p>${res.message}</p></div>`; return; }
    const h = res.data;
    area.innerHTML = `<div class="result-card">
        <div class="result-title"><i class="fas fa-hospital"></i>${h.name}</div>
        <div class="result-field"><label>Address</label><p>${h.address}</p></div>
        <div class="result-field"><label>Specialization</label><p>${h.specialization||'—'}</p></div>
        <div class="result-field"><label>Beds</label><p>${h.available_beds}</p></div>
        <div class="result-field"><label>Contact</label><p>${h.contact||'—'}</p></div>
    </div>`;
}

// Nearest
function useMyLocation() {
    navigator.geolocation?.getCurrentPosition(p => {
        document.getElementById('nearLat').value = p.coords.latitude.toFixed(6);
        document.getElementById('nearLng').value = p.coords.longitude.toFixed(6);
        toast('Location detected','success');
    }, () => toast('Could not get location','error'));
}
async function findNearest() {
    const lat = document.getElementById('nearLat').value, lng = document.getElementById('nearLng').value;
    if (!lat || !lng) return toast('Enter coordinates','info');
    const area = document.getElementById('nearestResult');
    area.innerHTML = `<div class="spinner" style="margin-top:20px"></div>`;
    const res = await api(`/hospitals/nearest?lat=${lat}&lng=${lng}`);
    if (!res.success) { area.innerHTML = `<div class="empty-state"><i class="fas fa-map-marker-alt"></i><p>${res.message}</p></div>`; return; }
    area.innerHTML = res.data.map((h,i) => `<div class="nearest-card ${i===0?'top':''}">
        <div class="nearest-rank">${i===0?'★':i+1}</div>
        <div class="nearest-body"><div class="nearest-info"><h4>${h.name}</h4><p>${h.specialization||'General'} · ${h.available_beds} beds</p></div></div>
        <div class="nearest-dist">${h.distance_km.toFixed(2)}<span>km away</span></div>
    </div>`).join('');
}

// Locations
async function loadLocations() {
    const tbody = document.getElementById('locationTableBody');
    tbody.innerHTML = `<tr><td colspan="5" class="loading-cell"><div class="spinner"></div></td></tr>`;
    const res = await api('/locations');
    allLocations = res.success ? res.data : [];
    renderLocations(allLocations);
}
function renderLocations(data) {
    const tbody = document.getElementById('locationTableBody');
    tbody.innerHTML = data.length ? data.map(l => `<tr>
        <td><span class="cell-id">#${l.id}</span></td>
        <td><div class="cell-primary">${l.name}</div></td>
        <td>${l.region||'—'}</td>
        <td><span style="font-family:monospace;font-size:12px">${(+l.latitude).toFixed(4)}, ${(+l.longitude).toFixed(4)}</span></td>
        <td>${l.is_remote?'<span class="badge badge-warning">Remote</span>':'<span class="badge badge-info">Urban</span>'}</td>
    </tr>`).join('') : `<tr><td colspan="5"><div class="empty-state"><i class="fas fa-map"></i><p>No locations</p></div></td></tr>`;
}
function filterLocations() {
    const q = document.getElementById('locationFilter').value.toLowerCase();
    renderLocations(allLocations.filter(l => l.name.toLowerCase().includes(q) || (l.region||'').toLowerCase().includes(q)));
}
async function submitAddLocation(e) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target));
    data.is_remote = data.is_remote === 'true';
    const res = await api('/locations', { method: 'POST', body: JSON.stringify(data) });
    res.success ? (toast('Location added','success'), e.target.reset(), navigate('locations')) : toast(res.message,'error');
}

// Init
checkStatus();
setInterval(checkStatus, 15000);
loadDashboard();
