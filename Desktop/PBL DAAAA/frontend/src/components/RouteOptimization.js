import React, { useState, useEffect, useRef, useCallback } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix Leaflet's broken default icon paths when bundled with webpack
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: require('leaflet/dist/images/marker-icon-2x.png'),
  iconUrl:       require('leaflet/dist/images/marker-icon.png'),
  shadowUrl:     require('leaflet/dist/images/marker-shadow.png'),
});

const API = 'http://localhost:5001/api';

const QUICK = [
  { label: 'City Center → District HQ',   sLat:'28.6139', sLon:'77.2090', dLat:'28.7041', dLon:'77.1025' },
  { label: 'Remote Area → City Hospital', sLat:'28.5355', sLon:'77.3910', dLat:'28.6139', dLon:'77.2090' },
  { label: 'North City → South Clinic',   sLat:'28.6800', sLon:'77.1500', dLat:'28.5700', dLon:'77.2500' },
];

const scoreColor = s => s >= 80 ? '#ef4444' : s >= 60 ? '#f59e0b' : '#10b981';

function makeIcon(emoji, bg, size = 36) {
  return L.divIcon({
    html: `<div style="
      background:${bg};width:${size}px;height:${size}px;border-radius:50%;
      display:flex;align-items:center;justify-content:center;
      font-size:${Math.round(size * 0.5)}px;
      border:2.5px solid white;box-shadow:0 2px 8px rgba(0,0,0,0.3);
      line-height:1">${emoji}</div>`,
    className: '',
    iconSize:   [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor:[0, -(size / 2)],
  });
}

export default function RouteOptimization() {
  const mapDivRef = useRef(null);
  const mapRef    = useRef(null);
  const lgRef     = useRef(null);

  const [form, setForm] = useState({
    sLat: '28.6139', sLon: '77.2090',
    dLat: '28.7041', dLon: '77.1025',
    algo: 'dijkstra',
  });
  const [route,     setRoute]     = useState(null);
  const [loading,   setLoading]   = useState(false);
  const [animating, setAnimating] = useState(false);
  const [animStep,  setAnimStep]  = useState(0);
  const [error,     setError]     = useState('');
  const [tab,       setTab]       = useState('map');
  const [hospitals, setHospitals] = useState([]);
  const [roads,     setRoads]     = useState([]);

  /* ── Init Leaflet (runs once after mount) ── */
  useEffect(() => {
    if (mapRef.current || !mapDivRef.current) return;

    const map = L.map(mapDivRef.current, { zoomControl: true })
      .setView([28.65, 77.15], 11);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© <a href="https://openstreetmap.org">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(map);

    lgRef.current  = L.layerGroup().addTo(map);
    mapRef.current = map;

    // Let React finish painting, then fix tile rendering
    setTimeout(() => map.invalidateSize(), 300);

    return () => {
      map.remove();
      mapRef.current = null;
      lgRef.current  = null;
    };
  }, []);

  /* ── Fetch data ── */
  useEffect(() => {
    fetch(`${API}/hospitals`).then(r => r.json())
      .then(d => { if (d.success) setHospitals(d.data); }).catch(() => {});
    fetch(`${API}/road-scoring/all`).then(r => r.json())
      .then(d => { if (d.success) setRoads(d.data); }).catch(() => {});
  }, []);

  /* ── Clear map layers ── */
  const clearMap = useCallback(() => {
    lgRef.current?.clearLayers();
  }, []);

  /* ── Draw full route (A* or after animation) ── */
  const drawRoute = useCallback((r) => {
    if (!mapRef.current || !lgRef.current) return;
    clearMap();

    const coords = r.pathCoordinates;
    if (!coords?.length) return;

    // Road score dots
    roads.forEach(rs => {
      if (!rs.latitude || !rs.longitude) return;
      L.circleMarker([+rs.latitude, +rs.longitude], {
        radius: 7, fillColor: scoreColor(rs.composite_score),
        color: 'white', weight: 2, fillOpacity: 0.85,
      }).addTo(lgRef.current)
        .bindPopup(`<b>${rs.road_name}</b><br>Score: ${rs.composite_score}/100 · ${rs.flag_status}`);
    });

    // Hospital markers
    hospitals.forEach(h => {
      L.marker([+h.latitude, +h.longitude], { icon: makeIcon('🏥', '#3b82f6', 32) })
        .addTo(lgRef.current)
        .bindPopup(`<b>${h.name}</b><br>${h.address}`);
    });

    // Route polyline
    const lls = coords.map(c => [c.latitude, c.longitude]);
    L.polyline(lls, { color: '#4f46e5', weight: 5, opacity: 0.9 }).addTo(lgRef.current);

    // Waypoint dots
    coords.forEach(c => {
      if (c.type === 'waypoint') {
        L.circleMarker([c.latitude, c.longitude], {
          radius: 4, fillColor: '#818cf8', color: 'white', weight: 2, fillOpacity: 1,
        }).addTo(lgRef.current);
      }
    });

    // Start marker
    L.marker([coords[0].latitude, coords[0].longitude], { icon: makeIcon('📍', '#10b981', 40) })
      .addTo(lgRef.current).bindPopup('<b>🚑 Start Location</b>').openPopup();

    // End marker
    L.marker(
      [coords[coords.length - 1].latitude, coords[coords.length - 1].longitude],
      { icon: makeIcon('🏥', '#ef4444', 40) }
    ).addTo(lgRef.current).bindPopup('<b>🏥 Destination</b>');

    mapRef.current.fitBounds(L.latLngBounds(lls), { padding: [60, 60] });
  }, [hospitals, roads, clearMap]);

  /* ── Dijkstra step-by-step animation ── */
  const animateDijkstra = useCallback(async (r) => {
    if (!mapRef.current || !lgRef.current) { drawRoute(r); return; }

    setAnimating(true);
    clearMap();

    const coords = r.pathCoordinates;
    const delay  = ms => new Promise(res => setTimeout(res, ms));

    // Hospital markers
    hospitals.forEach(h => {
      L.marker([+h.latitude, +h.longitude], { icon: makeIcon('🏥', '#3b82f6', 28) })
        .addTo(lgRef.current);
    });

    // Animate each edge one by one
    for (let i = 0; i < coords.length - 1; i++) {
      setAnimStep(i + 1);
      const a = coords[i], b = coords[i + 1];

      L.circleMarker([a.latitude, a.longitude], {
        radius: 6, fillColor: '#818cf8', color: 'white', weight: 2, fillOpacity: 0.9,
      }).addTo(lgRef.current);

      L.polyline(
        [[a.latitude, a.longitude], [b.latitude, b.longitude]],
        { color: '#4f46e5', weight: 4, opacity: 0.85 }
      ).addTo(lgRef.current);

      await delay(160);
    }

    // Final start / end markers
    L.marker([coords[0].latitude, coords[0].longitude], { icon: makeIcon('📍', '#10b981', 40) })
      .addTo(lgRef.current).bindPopup('<b>🚑 Start</b>').openPopup();

    L.marker(
      [coords[coords.length - 1].latitude, coords[coords.length - 1].longitude],
      { icon: makeIcon('🏥', '#ef4444', 40) }
    ).addTo(lgRef.current).bindPopup('<b>🏥 Destination</b>');

    mapRef.current.fitBounds(
      L.latLngBounds(coords.map(c => [c.latitude, c.longitude])),
      { padding: [60, 60] }
    );

    setAnimating(false);
    setAnimStep(0);
  }, [hospitals, clearMap, drawRoute]);

  /* ── Form submit ── */
  const submit = async (e) => {
    e.preventDefault();
    setError(''); setRoute(null); setLoading(true);

    const start = { latitude: +form.sLat, longitude: +form.sLon };
    const dest  = { latitude: +form.dLat, longitude: +form.dLon };

    if ([start.latitude, start.longitude, dest.latitude, dest.longitude].some(isNaN)) {
      setError('Enter valid coordinates'); setLoading(false); return;
    }

    try {
      const res  = await fetch(`${API}/route-optimization/calculate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ start, destination: dest, algorithm: form.algo }),
      });
      const data = await res.json();

      if (data.success) {
        setRoute(data.route);
        setTab('map');
        // Small delay so the map tab is visible before drawing
        setTimeout(() => {
          mapRef.current?.invalidateSize();
          if (form.algo === 'dijkstra') animateDijkstra(data.route);
          else drawRoute(data.route);
        }, 150);
      } else {
        setError(data.error || 'Route calculation failed');
      }
    } catch (_) {
      setError('Cannot connect to server. Ensure backend is running on port 5001.');
    } finally {
      setLoading(false);
    }
  };

  /* ── Render ── */
  return (
    <div>
      <div className="page-header">
        <h1>Route Optimization</h1>
        <p>Dijkstra's &amp; A* algorithm with weighted road scoring, terrain &amp; traffic factors</p>
      </div>

      <div className="route-layout">

        {/* ── Left panel ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          <div className="form-card">
            <form onSubmit={submit}>
              <div className="form-section-title">📍 Start Location</div>
              <div className="form-grid" style={{ marginBottom: 16 }}>
                <div className="form-group">
                  <label>Latitude</label>
                  <input type="number" step="any" value={form.sLat}
                    onChange={e => setForm({ ...form, sLat: e.target.value })} required />
                </div>
                <div className="form-group">
                  <label>Longitude</label>
                  <input type="number" step="any" value={form.sLon}
                    onChange={e => setForm({ ...form, sLon: e.target.value })} required />
                </div>
              </div>

              <div className="form-section-title">🏥 Destination</div>
              <div className="form-grid" style={{ marginBottom: 16 }}>
                <div className="form-group">
                  <label>Latitude</label>
                  <input type="number" step="any" value={form.dLat}
                    onChange={e => setForm({ ...form, dLat: e.target.value })} required />
                </div>
                <div className="form-group">
                  <label>Longitude</label>
                  <input type="number" step="any" value={form.dLon}
                    onChange={e => setForm({ ...form, dLon: e.target.value })} required />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 16 }}>
                <label>Algorithm</label>
                <select value={form.algo} onChange={e => setForm({ ...form, algo: e.target.value })}>
                  <option value="dijkstra">Dijkstra's (step-by-step animation)</option>
                  <option value="astar">A* (heuristic, faster)</option>
                </select>
              </div>

              {error && (
                <div className="alert alert-error" style={{ marginBottom: 12 }}>⚠ {error}</div>
              )}

              <button type="submit" className="btn btn-primary btn-full"
                disabled={loading || animating}>
                {loading    ? '⏳ Calculating…'
                 : animating ? `🔄 Animating step ${animStep}…`
                 : '→ Calculate Route'}
              </button>
            </form>
          </div>

          {/* Quick locations */}
          <div className="form-card">
            <div className="form-section-title">⚡ Quick Locations</div>
            {QUICK.map((q, i) => (
              <button key={i} className="btn btn-outline btn-full"
                style={{ marginBottom: 6, justifyContent: 'flex-start', fontSize: 12 }}
                onClick={() => setForm({ ...form, sLat: q.sLat, sLon: q.sLon, dLat: q.dLat, dLon: q.dLon })}>
                {q.label}
              </button>
            ))}
          </div>

          {/* Result card */}
          {route && (
            <div className="form-card">
              <div className="form-section-title">📊 Result</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                {[
                  ['Distance',  `${route.distance} km`],
                  ['Est. Time', `${route.estimatedTime} min`],
                  ['Algorithm', route.algorithm === 'dijkstra' ? 'Dijkstra' : 'A*'],
                  ['Nodes',     route.nodesExplored || '—'],
                  ['Waypoints', route.pathCoordinates?.length || 0],
                  ['Steps',     route.steps?.length || 0],
                ].map(([l, v]) => (
                  <div key={l} style={{ background: '#f8fafc', borderRadius: 8, padding: '10px 12px', textAlign: 'center' }}>
                    <div style={{ fontSize: 10, color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: .5, marginBottom: 4 }}>{l}</div>
                    <div style={{ fontSize: 16, fontWeight: 800, color: 'var(--primary)' }}>{v}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* ── Right panel ── */}
        <div>
          <div className="tabs">
            {[['map', '🗺 Map'], ['steps', '📋 Steps'], ['roads', '🚧 Roads']].map(([t, l]) => (
              <button key={t}
                className={`tab-btn${tab === t ? ' active' : ''}`}
                onClick={() => {
                  setTab(t);
                  if (t === 'map') setTimeout(() => mapRef.current?.invalidateSize(), 50);
                }}>
                {l}
              </button>
            ))}
          </div>

          {/* Map — always in DOM so Leaflet instance stays alive */}
          <div style={{ display: tab === 'map' ? 'block' : 'none' }}>
            <div className="map-container">
              <div className="map-toolbar">
                <h3>Live Route Map</h3>
                {route && <span className="badge badge-green">Route active</span>}
              </div>

              {animating && (
                <div className="anim-bar">
                  <div className="anim-dot" />
                  Dijkstra's algorithm running · Exploring node {animStep} of {route?.pathCoordinates?.length || '?'}
                </div>
              )}

              {/* The actual map div — explicit pixel height is required by Leaflet */}
              <div ref={mapDivRef} style={{ width: '100%', height: '500px' }} />

              {route && (
                <div className="route-stats">
                  <div className="route-stat">
                    <div className="route-stat-label">Distance</div>
                    <div className="route-stat-value">{route.distance} km</div>
                  </div>
                  <div className="route-stat">
                    <div className="route-stat-label">Est. Time</div>
                    <div className="route-stat-value">{route.estimatedTime} min</div>
                  </div>
                  <div className="route-stat">
                    <div className="route-stat-label">Algorithm</div>
                    <div className="route-stat-value">{route.algorithm === 'dijkstra' ? 'Dijkstra' : 'A*'}</div>
                  </div>
                </div>
              )}

              <div className="map-legend">
                <div className="legend-item"><div className="legend-dot" style={{ background: '#10b981' }} />Start</div>
                <div className="legend-item"><div className="legend-dot" style={{ background: '#ef4444' }} />Destination</div>
                <div className="legend-item"><div className="legend-dot" style={{ background: '#3b82f6' }} />Hospital</div>
                <div className="legend-item"><div className="legend-dot" style={{ background: '#818cf8' }} />Waypoint</div>
                <div className="legend-item"><div className="legend-dot" style={{ background: '#10b981' }} />Good road</div>
                <div className="legend-item"><div className="legend-dot" style={{ background: '#f59e0b' }} />Warning</div>
                <div className="legend-item"><div className="legend-dot" style={{ background: '#ef4444' }} />Critical</div>
              </div>
            </div>
          </div>

          {/* Steps tab */}
          <div style={{ display: tab === 'steps' ? 'block' : 'none' }}>
            <div className="form-card" style={{ maxHeight: 560, overflowY: 'auto' }}>
              <div className="form-section-title">Step-by-Step Directions</div>
              {!route
                ? <div className="empty"><div className="empty-icon">🗺</div><p>Calculate a route to see directions</p></div>
                : route.steps?.map((s, i) => (
                  <div key={i} style={{ display: 'flex', gap: 12, padding: '10px 0', borderBottom: '1px solid #f1f5f9' }}>
                    <div style={{ width: 28, height: 28, background: 'var(--primary)', color: 'white', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 12, flexShrink: 0 }}>
                      {s.step}
                    </div>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{s.from} → {s.to}</div>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>{s.distance} km</div>
                    </div>
                  </div>
                ))
              }
            </div>
          </div>

          {/* Roads tab */}
          <div style={{ display: tab === 'roads' ? 'block' : 'none' }}>
            <div className="form-card" style={{ maxHeight: 560, overflowY: 'auto' }}>
              <div className="form-section-title">Road Condition Scores</div>
              {roads.length === 0
                ? <div className="empty"><div className="empty-icon">🚧</div><p>No road scores available</p></div>
                : roads.map(r => (
                  <div key={r.id} style={{ padding: '12px 0', borderBottom: '1px solid #f1f5f9' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                      <span style={{ fontWeight: 600, fontSize: 13 }}>{r.road_name}</span>
                      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        <span style={{ fontWeight: 800, fontSize: 16, color: scoreColor(r.composite_score) }}>{r.composite_score}</span>
                        <span className={`badge ${r.flag_status === 'critical' ? 'badge-red' : r.flag_status === 'warning' ? 'badge-yellow' : 'badge-green'}`}>{r.flag_status}</span>
                      </div>
                    </div>
                    <div className="score-progress">
                      <div className="score-fill" style={{ width: `${r.composite_score}%`, background: scoreColor(r.composite_score) }} />
                    </div>
                    <div style={{ display: 'flex', gap: 12, fontSize: 11, color: 'var(--text-muted)', flexWrap: 'wrap' }}>
                      <span>Quality {parseFloat(r.road_quality || 1).toFixed(1)}×</span>
                      <span>Terrain {parseFloat(r.terrain_difficulty || 1).toFixed(1)}×</span>
                      <span>Congestion {Math.round((r.congestion_level || 0) * 100)}%</span>
                      <span>Speed {r.average_speed || 60} km/h</span>
                    </div>
                  </div>
                ))
              }
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
