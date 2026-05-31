"use strict";

const API = "http://localhost:8080";

// ── Colours per node type ────────────────────────────────────────────────────
const NODE_COLOURS = {
  hospital:          "#ef4444",
  patient:           "#2563eb",
  ambulance_station: "#f59e0b",
  emergency:         "#8b5cf6",
  traffic:           "#6b7280",
};
const ROUTE_COLOUR   = "#dc2626";
const BADROAD_COLOUR = "#f97316";
const EDGE_COLOUR    = "#d1d5db";

// ── App state ────────────────────────────────────────────────────────────────
const state = {
  graph:         null,   // full map data from server
  routePath:     [],     // highlighted route node IDs
  hospitalNode:  null,
  patientNodes:  new Set(),
  zoom:          1.0,
  offsetX:       0,
  offsetY:       0,
  canvas:        null,
  ctx:           null,
};

// ── Boot ─────────────────────────────────────────────────────────────────────
window.addEventListener("DOMContentLoaded", () => {
  state.canvas = document.getElementById("map-canvas");
  state.ctx    = state.canvas.getContext("2d");

  state.canvas.addEventListener("click", onCanvasClick);
  state.canvas.addEventListener("wheel", e => {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.1 : 0.91;
    state.zoom = Math.min(Math.max(state.zoom * factor, 0.3), 4.0);
    renderMap();
  }, { passive: false });

  checkServer();
  loadMap();
});

// ── Server health ─────────────────────────────────────────────────────────────
async function checkServer() {
  const dot  = document.getElementById("status-dot");
  const text = document.getElementById("status-text");
  try {
    const res = await fetch(API + "/health");
    if (res.ok) {
      dot.className   = "status-dot online";
      text.textContent = "Server online";
    } else throw new Error();
  } catch {
    dot.className   = "status-dot offline";
    text.textContent = "Server offline";
    showToast("Cannot connect to Java server — is it running?", true);
  }
}

// ── Load map from server ──────────────────────────────────────────────────────
async function loadMap() {
  try {
    const res  = await fetch(API + "/api/ambulance/map");
    const data = await res.json();
    state.graph = data;

    // Build quick lookup
    state.graph.nodeMap = {};
    data.nodes.forEach(n => { state.graph.nodeMap[n.id] = n; });

    renderMap();
    buildNodeList();
    showToast("City map loaded — " + data.nodes.length + " locations");
  } catch (err) {
    showToast("Failed to load map from server", true);
  }
}

// ── Compute route ─────────────────────────────────────────────────────────────
async function computeRoute() {
  const hospitalVal  = document.getElementById("hospital-input").value.trim();
  const patientsVal  = document.getElementById("patients-input").value.trim();

  if (!hospitalVal) {
    showToast("Please enter a Hospital Node ID", true);
    return;
  }

  const hospitalNode = parseInt(hospitalVal);
  const patientNodes = patientsVal
    ? patientsVal.split(",").map(s => parseInt(s.trim())).filter(n => !isNaN(n))
    : [];

  if (patientNodes.length === 0) {
    showToast("Please enter at least one Patient Node ID", true);
    return;
  }

  showLoading(true);

  try {
    const res = await fetch(API + "/api/ambulance/route", {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body:    JSON.stringify({ hospitalNode, patientNodes }),
    });

    const data = await res.json();

    state.routePath    = data.completeRoute || [];
    state.hospitalNode = data.hospital;
    state.patientNodes = new Set(data.patientsVisited || []);

    renderMap();
    showResult(data);
    showToast("Route computed! Distance: " + data.totalDistance.toFixed(1) + " units");
  } catch (err) {
    showToast("Failed to compute route — check server", true);
  } finally {
    showLoading(false);
  }
}

// ── Show result panel ─────────────────────────────────────────────────────────
function showResult(data) {
  document.getElementById("res-hospital").textContent =
    "#" + data.hospital;

  document.getElementById("res-patients").textContent =
    (data.patientsVisited || []).map(id => "#" + id).join(", ") || "—";

  document.getElementById("res-distance").textContent =
    data.totalDistance.toFixed(1) + " units";

  document.getElementById("res-path").textContent =
    (data.completeRoute || []).join(" → ");

  document.getElementById("result-box").style.display = "block";
}

// ── Clear ─────────────────────────────────────────────────────────────────────
function clearRoute() {
  state.routePath    = [];
  state.hospitalNode = null;
  state.patientNodes = new Set();

  document.getElementById("hospital-input").value = "";
  document.getElementById("patients-input").value = "";
  document.getElementById("result-box").style.display = "none";

  renderMap();
  showToast("Route cleared");
}

// ── Render canvas ─────────────────────────────────────────────────────────────
function renderMap() {
  if (!state.graph) return;

  const ctx = state.ctx;
  const W   = state.canvas.width;
  const H   = state.canvas.height;

  ctx.save();
  ctx.clearRect(0, 0, W, H);

  // Background
  ctx.fillStyle = "#f8fafc";
  ctx.fillRect(0, 0, W, H);

  // Grid
  ctx.strokeStyle = "#e2e8f0";
  ctx.lineWidth   = 0.5;
  for (let x = 0; x < W; x += 50) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
  }
  for (let y = 0; y < H; y += 50) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
  }

  ctx.translate(state.offsetX, state.offsetY);
  ctx.scale(state.zoom, state.zoom);

  const routeSet = buildRouteEdgeSet(state.routePath);

  // Draw edges
  state.graph.edges.forEach(e => {
    const u = state.graph.nodeMap[e.from];
    const v = state.graph.nodeMap[e.to];
    if (!u || !v) return;

    const key      = edgeKey(e.from, e.to);
    const isRoute  = routeSet.has(key);
    const isBad    = e.badRoad;

    ctx.beginPath();
    ctx.moveTo(u.x, u.y);
    ctx.lineTo(v.x, v.y);

    if (isRoute) {
      ctx.strokeStyle = ROUTE_COLOUR;
      ctx.lineWidth   = 5;
      ctx.setLineDash([]);
      ctx.shadowColor = "rgba(220,38,38,0.45)";
      ctx.shadowBlur  = 12;
    } else if (isBad) {
      ctx.strokeStyle = BADROAD_COLOUR;
      ctx.lineWidth   = 2;
      ctx.setLineDash([6, 4]);
      ctx.shadowBlur  = 0;
    } else {
      ctx.strokeStyle = EDGE_COLOUR;
      ctx.lineWidth   = 1.5;
      ctx.setLineDash([]);
      ctx.shadowBlur  = 0;
    }

    ctx.stroke();
    ctx.shadowBlur = 0;
    ctx.setLineDash([]);

    // Weight label on route edges
    if (isRoute) {
      const mx    = (u.x + v.x) / 2;
      const my    = (u.y + v.y) / 2;
      const label = e.weight.toFixed(0);
      ctx.font         = "bold 10px monospace";
      ctx.textAlign    = "center";
      ctx.textBaseline = "middle";
      ctx.fillStyle    = "#fff";
      ctx.fillRect(mx - 14, my - 8, 28, 16);
      ctx.fillStyle = ROUTE_COLOUR;
      ctx.fillText(label, mx, my);
    }
  });

  // Draw nodes
  state.graph.nodes.forEach(n => {
    const isHospital = n.id === state.hospitalNode;
    const isPatient  = state.patientNodes.has(n.id);
    const isOnRoute  = state.routePath.includes(n.id);
    drawNode(ctx, n, isHospital, isPatient, isOnRoute);
  });

  ctx.restore();
}

function drawNode(ctx, n, isHospital, isPatient, isOnRoute) {
  const colour = NODE_COLOURS[n.type] || "#64748b";
  let r = 12;
  let border = null;
  let bw = 0;

  if (isHospital) {
    r = 16; border = "#dc2626"; bw = 3;
    ctx.shadowColor = "rgba(220,38,38,0.6)";
    ctx.shadowBlur  = 16;
  } else if (isPatient) {
    r = 14; border = "#2563eb"; bw = 3;
    ctx.shadowColor = "rgba(37,99,235,0.5)";
    ctx.shadowBlur  = 12;
  } else if (isOnRoute) {
    r = 13; border = "#dc2626"; bw = 2;
    ctx.shadowColor = "rgba(220,38,38,0.3)";
    ctx.shadowBlur  = 8;
  }

  ctx.beginPath();
  ctx.arc(n.x, n.y, r, 0, Math.PI * 2);
  ctx.fillStyle = isHospital ? "#ef4444" : isPatient ? "#2563eb" : colour;
  ctx.fill();

  if (border) {
    ctx.strokeStyle = border;
    ctx.lineWidth   = bw;
    ctx.stroke();
  }
  ctx.shadowBlur = 0;

  // Node ID
  ctx.font         = "bold 9px 'Poppins', sans-serif";
  ctx.textAlign    = "center";
  ctx.textBaseline = "middle";
  ctx.fillStyle    = "#fff";
  ctx.fillText(n.id, n.x, n.y);

  // Label below
  ctx.font         = "10px 'Poppins', sans-serif";
  ctx.fillStyle    = "#334155";
  ctx.textBaseline = "top";
  ctx.fillText(n.label, n.x, n.y + r + 3);
}

// ── Canvas click ──────────────────────────────────────────────────────────────
function onCanvasClick(e) {
  if (!state.graph) return;

  const rect   = state.canvas.getBoundingClientRect();
  const scaleX = state.canvas.width  / rect.width;
  const scaleY = state.canvas.height / rect.height;
  const cx     = ((e.clientX - rect.left) * scaleX - state.offsetX) / state.zoom;
  const cy     = ((e.clientY - rect.top)  * scaleY - state.offsetY) / state.zoom;

  let closest = null;
  let minDist = Infinity;

  state.graph.nodes.forEach(n => {
    const d = Math.hypot(n.x - cx, n.y - cy);
    if (d < minDist) { minDist = d; closest = n; }
  });

  if (!closest || minDist > 30) return;

  const hospitalInput  = document.getElementById("hospital-input");
  const patientsInput  = document.getElementById("patients-input");

  if (!hospitalInput.value) {
    // First click = set hospital
    hospitalInput.value = closest.id;
    state.hospitalNode  = closest.id;
    showToast("Hospital set: #" + closest.id + " — " + closest.label);
  } else {
    // Subsequent clicks = toggle patients
    if (state.patientNodes.has(closest.id)) {
      state.patientNodes.delete(closest.id);
    } else {
      state.patientNodes.add(closest.id);
    }
    patientsInput.value = [...state.patientNodes].join(",");
    showToast("Patient toggled: #" + closest.id + " — " + closest.label);
  }

  renderMap();
}

// ── Zoom controls ─────────────────────────────────────────────────────────────
function zoomIn()    { state.zoom = Math.min(state.zoom * 1.2, 4.0); renderMap(); }
function zoomOut()   { state.zoom = Math.max(state.zoom * 0.83, 0.3); renderMap(); }
function resetZoom() { state.zoom = 1.0; state.offsetX = 0; state.offsetY = 0; renderMap(); }

// ── Node list (sidebar) ───────────────────────────────────────────────────────
function buildNodeList() {
  const container = document.getElementById("node-list");
  if (!container || !state.graph) return;
  container.innerHTML = "";

  state.graph.nodes.forEach(n => {
    const div = document.createElement("div");
    div.className    = "node-item";
    div.dataset.id   = n.id;
    div.dataset.name = n.label.toLowerCase();

    const colour = NODE_COLOURS[n.type] || "#64748b";

    div.innerHTML = `
      <span class="node-dot" style="background:${colour}"></span>
      <span class="node-id">${n.id}</span>
      <span class="node-name">${n.label}</span>
      <span class="node-type-label">${n.type.replace("_"," ")}</span>
    `;

    div.addEventListener("click", () => {
      const hospitalInput = document.getElementById("hospital-input");
      const patientsInput = document.getElementById("patients-input");

      if (!hospitalInput.value) {
        hospitalInput.value = n.id;
        state.hospitalNode  = n.id;
        showToast("Hospital set: #" + n.id);
      } else {
        if (state.patientNodes.has(n.id)) state.patientNodes.delete(n.id);
        else state.patientNodes.add(n.id);
        patientsInput.value = [...state.patientNodes].join(",");
        showToast("Patient toggled: #" + n.id);
      }

      renderMap();
      // Highlight selected in list
      document.querySelectorAll(".node-item").forEach(el =>
        el.classList.toggle("selected",
          parseInt(el.dataset.id) === n.id
        )
      );
    });

    container.appendChild(div);
  });
}

function filterNodes() {
  const q = document.getElementById("search-box").value.toLowerCase();
  document.querySelectorAll(".node-item").forEach(el => {
    const match = el.dataset.name.includes(q) || el.dataset.id.includes(q);
    el.style.display = match ? "" : "none";
  });
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function edgeKey(a, b) {
  return Math.min(a,b) + "-" + Math.max(a,b);
}

function buildRouteEdgeSet(path) {
  const s = new Set();
  for (let i = 0; i < path.length - 1; i++) {
    s.add(edgeKey(path[i], path[i+1]));
  }
  return s;
}

let toastTimer = null;
function showToast(msg, isError = false) {
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.className   = "toast show" + (isError ? " error" : "");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = "toast"; }, 3000);
}

function showLoading(show) {
  document.getElementById("loading-overlay").style.display = show ? "flex" : "none";
}
