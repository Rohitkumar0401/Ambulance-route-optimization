import React, { useState, useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix Leaflet default marker icons
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl:       'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl:     'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const API  = 'http://localhost:5001/api';
const authH = () => ({
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${localStorage.getItem('token')}`,
});
const SEV_BADGE = { critical:'badge-red', high:'badge-yellow', medium:'badge-blue', low:'badge-green' };
const STA_BADGE = { pending:'badge-yellow', assigned:'badge-blue', in_progress:'badge-purple', completed:'badge-green', cancelled:'badge-gray' };
const BLANK = { patientName:'', lat:'', lon:'', severity:'high', contact:'', description:'' };

// ── Map picker component ──────────────────────────────────────────────────────
function MapPicker({ lat, lon, onChange }) {
  const mapRef  = useRef(null);
  const mapObj  = useRef(null);
  const marker  = useRef(null);

  useEffect(() => {
    if (mapObj.current) return; // already initialised
    const defaultLat = lat || 28.6139;
    const defaultLon = lon || 77.2090;

    mapObj.current = L.map(mapRef.current, { zoomControl: true }).setView([defaultLat, defaultLon], 12);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(mapObj.current);

    // Place initial marker if coords exist
    if (lat && lon) {
      marker.current = L.marker([lat, lon], { draggable: true }).addTo(mapObj.current);
      marker.current.on('dragend', e => {
        const p = e.target.getLatLng();
        onChange(p.lat.toFixed(6), p.lng.toFixed(6));
      });
    }

    // Click to place / move marker
    mapObj.current.on('click', e => {
      const { lat: clat, lng: clng } = e.latlng;
      if (marker.current) {
        marker.current.setLatLng([clat, clng]);
      } else {
        marker.current = L.marker([clat, clng], { draggable: true }).addTo(mapObj.current);
        marker.current.on('dragend', ev => {
          const p = ev.target.getLatLng();
          onChange(p.lat.toFixed(6), p.lng.toFixed(6));
        });
      }
      onChange(clat.toFixed(6), clng.toFixed(6));
    });

    return () => { mapObj.current?.remove(); mapObj.current = null; marker.current = null; };
  }, []); // eslint-disable-line

  // Sync marker when lat/lon change externally (e.g. geolocation)
  useEffect(() => {
    if (!mapObj.current || !lat || !lon) return;
    const pos = [parseFloat(lat), parseFloat(lon)];
    if (marker.current) {
      marker.current.setLatLng(pos);
    } else {
      marker.current = L.marker(pos, { draggable: true }).addTo(mapObj.current);
      marker.current.on('dragend', e => {
        const p = e.target.getLatLng();
        onChange(p.lat.toFixed(6), p.lng.toFixed(6));
      });
    }
    mapObj.current.setView(pos, mapObj.current.getZoom());
  }, [lat, lon]); // eslint-disable-line

  return (
    <div style={{borderRadius:8,overflow:'hidden',border:'1.5px solid var(--border)'}}>
      <div style={{background:'#f8fafc',padding:'8px 12px',fontSize:12,color:'#64748b',
        borderBottom:'1px solid var(--border)',display:'flex',justifyContent:'space-between',alignItems:'center'}}>
        <span>📍 Click on the map to set location · Drag marker to adjust</span>
        {lat && lon && (
          <span style={{fontWeight:600,color:'#2563eb'}}>
            {parseFloat(lat).toFixed(4)}°N, {parseFloat(lon).toFixed(4)}°E
          </span>
        )}
      </div>
      <div ref={mapRef} style={{height:280,width:'100%'}} />
    </div>
  );
}

export default function EmergencyRequest() {
  const [form,    setForm]    = useState(BLANK);
  const [list,    setList]    = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy,    setBusy]    = useState(false);
  const [error,   setError]   = useState('');
  const [ok,      setOk]      = useState('');
  const [tab,     setTab]     = useState('list');

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const load = async () => {
    try {
      const r = await fetch(`${API}/emergency/all`, { headers: authH() });
      const d = await r.json();
      if (d.success) setList(d.data);
    } catch(_) {}
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []); // eslint-disable-line

  const useMyLocation = () => {
    if (!navigator.geolocation) { setError('Geolocation not supported by your browser'); return; }
    navigator.geolocation.getCurrentPosition(
      p => set('lat', p.coords.latitude.toFixed(6)) || set('lon', p.coords.longitude.toFixed(6)),
      () => setError('Could not get your location')
    );
  };

  const submit = async (e) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true);
    if (!form.lat || !form.lon) {
      setError('Please click on the map to set the emergency location');
      setBusy(false); return;
    }
    try {
      // No auth header needed — emergency create is public
      const res = await fetch(`${API}/emergency/create`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          patientName: form.patientName,
          location: { latitude: parseFloat(form.lat), longitude: parseFloat(form.lon) },
          severity:    form.severity,
          contact:     form.contact,
          description: form.description,
        }),
      });
      const data = await res.json();
      if (data.success) {
        setOk(`✅ Emergency request #${data.requestId} created. Ambulance being dispatched.`);
        setForm(BLANK); load(); setTab('list');
      } else {
        setError(data.error || 'Failed to create request');
      }
    } catch(_) { setError('Cannot connect to server'); }
    finally { setBusy(false); }
  };

  const updateStatus = async (id, status) => {
    await fetch(`${API}/emergency/update-status`, {
      method: 'POST', headers: authH(),
      body: JSON.stringify({ requestId: id, status }),
    });
    load();
  };

  if (loading) return <div className="loading-wrap"><div className="spinner" /></div>;

  const active   = list.filter(r => ['pending','assigned','in_progress'].includes(r.status));
  const resolved = list.filter(r => ['completed','cancelled'].includes(r.status));

  const SEV_COLOUR = { critical:'#dc2626', high:'#d97706', medium:'#2563eb', low:'#059669' };

  return (
    <div>
      <div className="page-header-row">
        <div className="page-header">
          <h1>Emergency Requests</h1>
          <p>Create and manage ambulance emergency requests</p>
        </div>
        <button className="btn btn-primary" onClick={() => setTab('new')}>+ New Request</button>
      </div>

      {/* Summary stats */}
      <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:12,marginBottom:20}}>
        {[
          {label:'Total',       value:list.length,    colour:'#3b82f6'},
          {label:'Active',      value:active.length,  colour:'#f59e0b'},
          {label:'Completed',   value:list.filter(r=>r.status==='completed').length,  colour:'#10b981'},
          {label:'Critical',    value:list.filter(r=>r.severity==='critical').length, colour:'#ef4444'},
        ].map(s => (
          <div key={s.label} style={{background:'var(--surface)',border:'1.5px solid var(--border)',
            borderRadius:10,padding:'14px 16px',textAlign:'center'}}>
            <div style={{fontSize:22,fontWeight:700,color:s.colour}}>{s.value}</div>
            <div style={{fontSize:11,color:'#64748b',marginTop:2}}>{s.label}</div>
          </div>
        ))}
      </div>

      <div className="tabs">
        <button className={`tab-btn${tab==='list'?' active':''}`} onClick={()=>setTab('list')}>
          Active ({active.length})
        </button>
        <button className={`tab-btn${tab==='resolved'?' active':''}`} onClick={()=>setTab('resolved')}>
          Resolved ({resolved.length})
        </button>
        <button className={`tab-btn${tab==='new'?' active':''}`} onClick={()=>setTab('new')}>
          + New Request
        </button>
      </div>

      {/* Active list */}
      {tab === 'list' && (
        <div className="section-card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>#</th><th>Patient</th><th>Severity</th><th>Status</th><th>Contact</th><th>Location</th><th>Description</th><th>Time</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {active.length === 0
                  ? <tr><td colSpan={9} style={{textAlign:'center',color:'var(--text-muted)',padding:32}}>No active emergencies 🎉</td></tr>
                  : active.map(em => {
                    let loc = null;
                    try { loc = typeof em.location === 'string' ? JSON.parse(em.location) : em.location; } catch(_) {}
                    return (
                      <tr key={em.id}>
                        <td style={{fontWeight:600,color:'var(--text-muted)'}}>#{em.id}</td>
                        <td style={{fontWeight:600}}>{em.patient_name||'—'}</td>
                        <td><span className={`badge ${SEV_BADGE[em.severity]||'badge-gray'}`}>{em.severity}</span></td>
                        <td><span className={`badge ${STA_BADGE[em.status]||'badge-gray'}`}>{em.status?.replace('_',' ')}</span></td>
                        <td style={{color:'var(--text-muted)'}}>{em.contact}</td>
                        <td style={{fontSize:11,color:'#3b82f6',whiteSpace:'nowrap'}}>
                          {loc ? `${parseFloat(loc.latitude).toFixed(4)}°N, ${parseFloat(loc.longitude).toFixed(4)}°E` : '—'}
                        </td>
                        <td style={{color:'var(--text-muted)',maxWidth:160,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{em.description||'—'}</td>
                        <td style={{color:'var(--text-light)',whiteSpace:'nowrap',fontSize:12}}>{new Date(em.created_at).toLocaleString()}</td>
                        <td>
                          <div style={{display:'flex',gap:4}}>
                            {em.status==='pending'     && <button className="btn btn-outline btn-sm" onClick={()=>updateStatus(em.id,'assigned')}>Assign</button>}
                            {em.status==='assigned'    && <button className="btn btn-warning btn-sm"  onClick={()=>updateStatus(em.id,'in_progress')}>Start</button>}
                            {em.status==='in_progress' && <button className="btn btn-success btn-sm"  onClick={()=>updateStatus(em.id,'completed')}>Complete</button>}
                            <button className="btn btn-danger btn-sm" onClick={()=>updateStatus(em.id,'cancelled')}>✕</button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                }
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Resolved list */}
      {tab === 'resolved' && (
        <div className="section-card">
          <div className="table-wrap">
            <table>
              <thead><tr><th>#</th><th>Patient</th><th>Severity</th><th>Status</th><th>Contact</th><th>Time</th></tr></thead>
              <tbody>
                {resolved.length === 0
                  ? <tr><td colSpan={6} style={{textAlign:'center',color:'var(--text-muted)',padding:32}}>No resolved requests</td></tr>
                  : resolved.map(em => (
                    <tr key={em.id}>
                      <td style={{fontWeight:600,color:'var(--text-muted)'}}>#{em.id}</td>
                      <td style={{fontWeight:600}}>{em.patient_name||'—'}</td>
                      <td><span className={`badge ${SEV_BADGE[em.severity]||'badge-gray'}`}>{em.severity}</span></td>
                      <td><span className={`badge ${STA_BADGE[em.status]||'badge-gray'}`}>{em.status?.replace('_',' ')}</span></td>
                      <td style={{color:'var(--text-muted)'}}>{em.contact}</td>
                      <td style={{color:'var(--text-light)',fontSize:12}}>{new Date(em.created_at).toLocaleString()}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* New request form */}
      {tab === 'new' && (
        <div className="form-card" style={{maxWidth:720}}>
          <div className="form-section-title">🚨 New Emergency Request</div>
          {error && <div className="alert alert-error">⚠ {error}</div>}
          {ok    && <div className="alert alert-success">{ok}</div>}
          <form onSubmit={submit}>
            <div className="form-grid" style={{marginBottom:16}}>
              <div className="form-group">
                <label>Patient Name</label>
                <input placeholder="Full name (optional)" value={form.patientName}
                  onChange={e=>set('patientName',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Contact Number *</label>
                <input placeholder="+91-9876543210" value={form.contact}
                  onChange={e=>set('contact',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Severity *</label>
                <select value={form.severity} onChange={e=>set('severity',e.target.value)}>
                  <option value="critical">🔴 Critical — Life-threatening</option>
                  <option value="high">🟠 High — Urgent</option>
                  <option value="medium">🟡 Medium — Moderate</option>
                  <option value="low">🟢 Low — Non-urgent</option>
                </select>
              </div>
              <div className="form-group" style={{alignSelf:'flex-end'}}>
                <button type="button" onClick={useMyLocation}
                  style={{padding:'9px 14px',border:'1.5px solid var(--border)',borderRadius:8,
                    background:'white',cursor:'pointer',fontSize:13,fontWeight:600,width:'100%'}}>
                  📡 Use My GPS Location
                </button>
              </div>
            </div>

            {/* Coordinate display */}
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:10,marginBottom:12}}>
              <div className="form-group">
                <label>Latitude</label>
                <input type="number" step="any" placeholder="Click map or use GPS"
                  value={form.lat} onChange={e=>set('lat',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Longitude</label>
                <input type="number" step="any" placeholder="Click map or use GPS"
                  value={form.lon} onChange={e=>set('lon',e.target.value)} />
              </div>
            </div>

            {/* Map picker */}
            <div style={{marginBottom:16}}>
              <MapPicker
                lat={form.lat}
                lon={form.lon}
                onChange={(lat, lon) => setForm(f => ({ ...f, lat, lon }))}
              />
            </div>

            <div className="form-group" style={{marginBottom:20}}>
              <label>Description</label>
              <textarea placeholder="Describe the emergency situation…"
                value={form.description} onChange={e=>set('description',e.target.value)} />
            </div>

            {/* Severity indicator */}
            <div style={{background: form.severity==='critical'?'#fee2e2':form.severity==='high'?'#fef3c7':'#f0fdf4',
              borderRadius:8,padding:'10px 14px',marginBottom:16,fontSize:12,
              color: SEV_COLOUR[form.severity],fontWeight:600,
              border:`1px solid ${SEV_COLOUR[form.severity]}33`}}>
              {form.severity==='critical' && '🔴 CRITICAL — Immediate dispatch required'}
              {form.severity==='high'     && '🟠 HIGH — Urgent response needed'}
              {form.severity==='medium'   && '🟡 MEDIUM — Respond within 15 minutes'}
              {form.severity==='low'      && '🟢 LOW — Standard response time'}
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-danger" disabled={busy}>
                {busy ? '⏳ Creating…' : '🚑 Create Emergency Request'}
              </button>
              <button type="button" className="btn btn-outline" onClick={()=>setForm(BLANK)}>Clear</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
