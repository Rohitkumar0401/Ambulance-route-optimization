import React, { useState, useEffect } from 'react';

const API = 'http://localhost:5001/api';
const scoreColor = s => s >= 80 ? '#ef4444' : s >= 60 ? '#f59e0b' : '#10b981';
const flagBadge  = s => ({ critical:'badge-red', warning:'badge-yellow', good:'badge-green' }[s] || 'badge-gray');

const BLANK = { roadId:'', roadName:'', latitude:'', longitude:'', roadQuality:1.0, terrainDifficulty:1.0, congestionLevel:0.0, averageSpeed:60, incidentCount:0, weatherFactor:1.0 };

export default function RoadScoring() {
  const [roads,   setRoads]   = useState([]);
  const [stats,   setStats]   = useState(null);
  const [loading, setLoading] = useState(true);
  const [form,    setForm]    = useState(BLANK);
  const [busy,    setBusy]    = useState(false);
  const [error,   setError]   = useState('');
  const [ok,      setOk]      = useState('');
  const [tab,     setTab]     = useState('all');

  const set = (k,v) => setForm(f=>({...f,[k]:v}));

  const load = async () => {
    try {
      const [r, s] = await Promise.all([
        fetch(`${API}/road-scoring/all`).then(r=>r.json()),
        fetch(`${API}/road-scoring/stats`).then(r=>r.json()),
      ]);
      if (r.success) setRoads(r.data);
      if (s.success) setStats(s.data);
    } catch(_) {}
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const submit = async (e) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true);
    try {
      const res  = await fetch(`${API}/road-scoring/score`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({
          ...form,
          roadQuality:       parseFloat(form.roadQuality),
          terrainDifficulty: parseFloat(form.terrainDifficulty),
          congestionLevel:   parseFloat(form.congestionLevel),
          averageSpeed:      parseFloat(form.averageSpeed),
          incidentCount:     parseInt(form.incidentCount),
          weatherFactor:     parseFloat(form.weatherFactor),
          latitude:  form.latitude  ? parseFloat(form.latitude)  : null,
          longitude: form.longitude ? parseFloat(form.longitude) : null,
        })
      });
      const data = await res.json();
      if (data.success) {
        setOk(`Road scored: ${data.compositeScore}/100 · Status: ${data.flagStatus}`);
        setForm(BLANK); load(); setTab('all');
      } else setError(data.error || 'Scoring failed');
    } catch(_) { setError('Cannot connect to server'); }
    finally { setBusy(false); }
  };

  if (loading) return <div className="loading-wrap"><div className="spinner" /></div>;

  const flagged = roads.filter(r => r.flag_status !== 'good');

  return (
    <div>
      <div className="page-header">
        <h1>Road Scoring & Flagging</h1>
        <p>Score roads on quality, terrain, traffic · Automatic threshold checks & government alerts</p>
      </div>

      {/* Stats */}
      {stats && (
        <div className="stat-grid" style={{marginBottom:20}}>
          {[
            { label:'Total Roads',    value: stats.total||0,   cls:'',        icon:'🛣️' },
            { label:'Good Roads',     value: stats.good||0,    cls:'success', icon:'🟢' },
            { label:'Warning Roads',  value: stats.warning||0, cls:'warning', icon:'🟡' },
            { label:'Critical Roads', value: stats.critical||0,cls:'danger',  icon:'🔴' },
            { label:'Avg Score',      value: `${Math.round(stats.avgScore||0)}/100`, cls: stats.avgScore>=60?'danger':'success', icon:'📊' },
          ].map(c => (
            <div key={c.label} className={`stat-card ${c.cls}`}>
              <div className="stat-icon">{c.icon}</div>
              <div className="stat-label">{c.label}</div>
              <div className="stat-value">{c.value}</div>
            </div>
          ))}
        </div>
      )}

      <div className="tabs">
        <button className={`tab-btn${tab==='all'?' active':''}`}     onClick={()=>setTab('all')}>All Roads ({roads.length})</button>
        <button className={`tab-btn${tab==='flagged'?' active':''}`} onClick={()=>setTab('flagged')}>Flagged ({flagged.length})</button>
        <button className={`tab-btn${tab==='score'?' active':''}`}   onClick={()=>setTab('score')}>+ Score a Road</button>
      </div>

      {/* All roads */}
      {tab === 'all' && (
        <div className="section-card">
          <div className="table-wrap">
            <table>
              <thead><tr><th>Road</th><th>Score</th><th>Status</th><th>Quality</th><th>Terrain</th><th>Congestion</th><th>Speed</th><th>Incidents</th></tr></thead>
              <tbody>
                {roads.length === 0
                  ? <tr><td colSpan={8} style={{textAlign:'center',color:'var(--text-muted)',padding:32}}>No roads scored yet</td></tr>
                  : roads.map(r => (
                    <tr key={r.id}>
                      <td>
                        <div style={{fontWeight:600}}>{r.road_name}</div>
                        <div style={{fontSize:11,color:'var(--text-muted)'}}>{r.road_id}</div>
                      </td>
                      <td>
                        <div style={{fontWeight:800,fontSize:16,color:scoreColor(r.composite_score)}}>{r.composite_score}</div>
                        <div className="score-progress" style={{width:60,marginTop:4}}>
                          <div className="score-fill" style={{width:`${r.composite_score}%`,background:scoreColor(r.composite_score)}} />
                        </div>
                      </td>
                      <td><span className={`badge ${flagBadge(r.flag_status)}`}>{r.flag_status}</span></td>
                      <td>{parseFloat(r.road_quality||1).toFixed(1)}×</td>
                      <td>{parseFloat(r.terrain_difficulty||1).toFixed(1)}×</td>
                      <td>{Math.round((r.congestion_level||0)*100)}%</td>
                      <td>{r.average_speed||60} km/h</td>
                      <td>{r.incident_count||0}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Flagged roads */}
      {tab === 'flagged' && (
        <div>
          {flagged.length === 0
            ? <div className="empty"><div className="empty-icon">✅</div><p>All roads within acceptable thresholds</p></div>
            : flagged.sort((a,b)=>b.composite_score-a.composite_score).map(r => (
              <div key={r.id} className="section-card" style={{marginBottom:12,borderLeft:`4px solid ${scoreColor(r.composite_score)}`}}>
                <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',marginBottom:10}}>
                  <div>
                    <div style={{fontWeight:700,fontSize:15}}>{r.road_name}</div>
                    <div style={{fontSize:12,color:'var(--text-muted)'}}>{r.road_id}</div>
                  </div>
                  <div style={{display:'flex',gap:10,alignItems:'center'}}>
                    <span style={{fontWeight:900,fontSize:22,color:scoreColor(r.composite_score)}}>{r.composite_score}/100</span>
                    <span className={`badge ${flagBadge(r.flag_status)}`}>{r.flag_status}</span>
                  </div>
                </div>
                <div className="score-progress"><div className="score-fill" style={{width:`${r.composite_score}%`,background:scoreColor(r.composite_score)}} /></div>
                <div style={{display:'flex',gap:20,fontSize:12,color:'var(--text-muted)',flexWrap:'wrap',marginTop:8}}>
                  <span>🛣️ Quality: <strong>{parseFloat(r.road_quality||1).toFixed(1)}×</strong></span>
                  <span>⛰️ Terrain: <strong>{parseFloat(r.terrain_difficulty||1).toFixed(1)}×</strong></span>
                  <span>🚦 Congestion: <strong>{Math.round((r.congestion_level||0)*100)}%</strong></span>
                  <span>🚗 Speed: <strong>{r.average_speed||60} km/h</strong></span>
                  <span>⚠️ Incidents: <strong>{r.incident_count||0}</strong></span>
                </div>
                {r.flag_status === 'critical' && (
                  <div className="alert alert-error" style={{marginTop:10,marginBottom:0}}>
                    ⚠ THRESHOLD VIOLATED — Immediate action required. Government alert has been generated.
                  </div>
                )}
              </div>
            ))
          }
        </div>
      )}

      {/* Score form */}
      {tab === 'score' && (
        <div className="form-card" style={{maxWidth:720}}>
          <div className="form-section-title">➕ Score a Road</div>
          {error && <div className="alert alert-error">⚠ {error}</div>}
          {ok    && <div className="alert alert-success">✓ {ok}</div>}
          <form onSubmit={submit}>
            <div className="form-grid" style={{marginBottom:16}}>
              <div className="form-group">
                <label>Road ID *</label>
                <input placeholder="e.g. RD009" value={form.roadId} onChange={e=>set('roadId',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Road Name *</label>
                <input placeholder="e.g. NH-48 Bypass" value={form.roadName} onChange={e=>set('roadName',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Latitude</label>
                <input type="number" step="any" placeholder="28.6139" value={form.latitude} onChange={e=>set('latitude',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Longitude</label>
                <input type="number" step="any" placeholder="77.2090" value={form.longitude} onChange={e=>set('longitude',e.target.value)} />
              </div>
            </div>

            <div className="form-section-title">Scoring Factors (higher = worse condition)</div>
            <div className="form-grid" style={{marginBottom:20}}>
              <div className="form-group">
                <label>Road Quality (1.0–3.0)</label>
                <input type="number" step="0.1" min="1" max="3" value={form.roadQuality} onChange={e=>set('roadQuality',e.target.value)} />
                <small>1.0 = perfect · 2.0 = poor · 3.0 = impassable</small>
              </div>
              <div className="form-group">
                <label>Terrain Difficulty (1.0–3.0)</label>
                <input type="number" step="0.1" min="1" max="3" value={form.terrainDifficulty} onChange={e=>set('terrainDifficulty',e.target.value)} />
                <small>1.0 = flat · 2.0 = hilly · 3.0 = mountain</small>
              </div>
              <div className="form-group">
                <label>Congestion Level (0.0–1.0)</label>
                <input type="number" step="0.05" min="0" max="1" value={form.congestionLevel} onChange={e=>set('congestionLevel',e.target.value)} />
                <small>0 = free flow · 1 = blocked</small>
              </div>
              <div className="form-group">
                <label>Average Speed (km/h)</label>
                <input type="number" min="0" max="200" value={form.averageSpeed} onChange={e=>set('averageSpeed',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Incident Count</label>
                <input type="number" min="0" value={form.incidentCount} onChange={e=>set('incidentCount',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Weather Factor (1.0–2.0)</label>
                <input type="number" step="0.1" min="1" max="2" value={form.weatherFactor} onChange={e=>set('weatherFactor',e.target.value)} />
                <small>1.0 = clear · 1.5 = rain · 2.0 = severe</small>
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={busy}>{busy?'⏳ Scoring…':'📊 Calculate & Save Score'}</button>
              <button type="button" className="btn btn-outline" onClick={()=>setForm(BLANK)}>Clear</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
