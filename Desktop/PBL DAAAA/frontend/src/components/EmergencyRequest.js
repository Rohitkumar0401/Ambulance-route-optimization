import React, { useState, useEffect } from 'react';

const API = 'http://localhost:5001/api';
const authH = () => ({ 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('token')}` });
const SEV_BADGE = { critical:'badge-red', high:'badge-yellow', medium:'badge-blue', low:'badge-green' };
const STA_BADGE = { pending:'badge-yellow', assigned:'badge-blue', in_progress:'badge-purple', completed:'badge-green', cancelled:'badge-gray' };

const BLANK = { patientName:'', lat:'', lon:'', severity:'high', contact:'', description:'' };

export default function EmergencyRequest() {
  const [form,    setForm]    = useState(BLANK);
  const [list,    setList]    = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy,    setBusy]    = useState(false);
  const [error,   setError]   = useState('');
  const [ok,      setOk]      = useState('');
  const [tab,     setTab]     = useState('list');

  const set = (k,v) => setForm(f=>({...f,[k]:v}));

  const load = async () => {
    try {
      const r = await fetch(`${API}/emergency/all`);
      const d = await r.json();
      if (d.success) setList(d.data);
    } catch(_) {}
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const submit = async (e) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true);
    try {
      const res  = await fetch(`${API}/emergency/create`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({
          patientName: form.patientName,
          location: { latitude: parseFloat(form.lat), longitude: parseFloat(form.lon) },
          severity: form.severity,
          contact:  form.contact,
          description: form.description
        })
      });
      const data = await res.json();
      if (data.success) {
        setOk(`Emergency request #${data.requestId} created. Ambulance being dispatched.`);
        setForm(BLANK); load(); setTab('list');
      } else { setError(data.error || 'Failed to create request'); }
    } catch(_) { setError('Cannot connect to server'); }
    finally { setBusy(false); }
  };

  const updateStatus = async (id, status) => {
    await fetch(`${API}/emergency/update-status`, {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ requestId: id, status })
    });
    load();
  };

  if (loading) return <div className="loading-wrap"><div className="spinner" /></div>;

  const active   = list.filter(r => ['pending','assigned','in_progress'].includes(r.status));
  const resolved = list.filter(r => ['completed','cancelled'].includes(r.status));

  return (
    <div>
      <div className="page-header-row">
        <div className="page-header">
          <h1>Emergency Requests</h1>
          <p>Create and manage ambulance emergency requests</p>
        </div>
        <button className="btn btn-primary" onClick={() => setTab('new')}>+ New Request</button>
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
              <thead><tr><th>#</th><th>Patient</th><th>Severity</th><th>Status</th><th>Contact</th><th>Description</th><th>Time</th><th>Actions</th></tr></thead>
              <tbody>
                {active.length === 0
                  ? <tr><td colSpan={8} style={{textAlign:'center',color:'var(--text-muted)',padding:32}}>No active emergencies</td></tr>
                  : active.map(em => (
                    <tr key={em.id}>
                      <td style={{fontWeight:600,color:'var(--text-muted)'}}>#{em.id}</td>
                      <td style={{fontWeight:600}}>{em.patient_name||'—'}</td>
                      <td><span className={`badge ${SEV_BADGE[em.severity]||'badge-gray'}`}>{em.severity}</span></td>
                      <td><span className={`badge ${STA_BADGE[em.status]||'badge-gray'}`}>{em.status}</span></td>
                      <td style={{color:'var(--text-muted)'}}>{em.contact}</td>
                      <td style={{color:'var(--text-muted)',maxWidth:180,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{em.description||'—'}</td>
                      <td style={{color:'var(--text-light)',whiteSpace:'nowrap',fontSize:12}}>{new Date(em.created_at).toLocaleString()}</td>
                      <td>
                        <div style={{display:'flex',gap:4}}>
                          {em.status==='pending'    && <button className="btn btn-outline btn-sm" onClick={()=>updateStatus(em.id,'assigned')}>Assign</button>}
                          {em.status==='assigned'   && <button className="btn btn-warning btn-sm"  onClick={()=>updateStatus(em.id,'in_progress')}>Start</button>}
                          {em.status==='in_progress'&& <button className="btn btn-success btn-sm"  onClick={()=>updateStatus(em.id,'completed')}>Complete</button>}
                          <button className="btn btn-danger btn-sm" onClick={()=>updateStatus(em.id,'cancelled')}>✕</button>
                        </div>
                      </td>
                    </tr>
                  ))
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
                      <td><span className={`badge ${STA_BADGE[em.status]||'badge-gray'}`}>{em.status}</span></td>
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
        <div className="form-card" style={{maxWidth:680}}>
          <div className="form-section-title">🚨 New Emergency Request</div>
          {error && <div className="alert alert-error">⚠ {error}</div>}
          {ok    && <div className="alert alert-success">✓ {ok}</div>}
          <form onSubmit={submit}>
            <div className="form-grid" style={{marginBottom:16}}>
              <div className="form-group">
                <label>Patient Name</label>
                <input placeholder="Full name" value={form.patientName} onChange={e=>set('patientName',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Contact Number *</label>
                <input placeholder="+91-9876543210" value={form.contact} onChange={e=>set('contact',e.target.value)} required />
              </div>
            </div>
            <div className="form-grid" style={{marginBottom:16}}>
              <div className="form-group">
                <label>Latitude *</label>
                <input type="number" step="any" placeholder="28.6139" value={form.lat} onChange={e=>set('lat',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Longitude *</label>
                <input type="number" step="any" placeholder="77.2090" value={form.lon} onChange={e=>set('lon',e.target.value)} required />
              </div>
            </div>
            <div className="form-group" style={{marginBottom:16}}>
              <label>Severity *</label>
              <select value={form.severity} onChange={e=>set('severity',e.target.value)}>
                <option value="critical">� Critical — Life-threatening</option>
                <option value="high">� High — Urgent</option>
                <option value="medium">� Medium — Moderate</option>
                <option value="low">� Low — Non-urgent</option>
              </select>
            </div>
            <div className="form-group" style={{marginBottom:20}}>
              <label>Description</label>
              <textarea placeholder="Describe the emergency…" value={form.description} onChange={e=>set('description',e.target.value)} />
            </div>
            <div style={{background:'#f8fafc',borderRadius:8,padding:'10px 14px',marginBottom:16,fontSize:12,color:'var(--text-muted)'}}>
              <strong>Sample coordinates:</strong> City Center 28.6139, 77.2090 · Remote Area 28.5355, 77.3910 · District HQ 28.7041, 77.1025
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-danger" disabled={busy}>{busy?'⏳ Creating…':'🚑 Create Emergency Request'}</button>
              <button type="button" className="btn btn-outline" onClick={()=>setForm(BLANK)}>Clear</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
