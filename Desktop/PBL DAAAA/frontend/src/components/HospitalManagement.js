import React, { useState, useEffect } from 'react';

const API = 'http://localhost:5001/api';
const BLANK = { name:'', address:'', latitude:'', longitude:'', contact:'', facilities:'' };

export default function HospitalManagement() {
  const [hospitals, setHospitals] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [search,    setSearch]    = useState('');
  const [form,      setForm]      = useState(BLANK);
  const [busy,      setBusy]      = useState(false);
  const [error,     setError]     = useState('');
  const [ok,        setOk]        = useState('');
  const [tab,       setTab]       = useState('list');

  const set = (k,v) => setForm(f=>({...f,[k]:v}));

  const load = async () => {
    try {
      const r = await fetch(`${API}/hospitals`);
      const d = await r.json();
      if (d.success) setHospitals(d.data);
    } catch(_) {}
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const submit = async (e) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true);
    try {
      const facilities = form.facilities.split(',').map(s=>s.trim()).filter(Boolean);
      const res  = await fetch(`${API}/hospitals/add`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ ...form, latitude: parseFloat(form.latitude), longitude: parseFloat(form.longitude), facilities })
      });
      const data = await res.json();
      if (data.success) { setOk('Hospital added successfully.'); setForm(BLANK); load(); setTab('list'); }
      else setError(data.error || 'Failed to add hospital');
    } catch(_) { setError('Cannot connect to server'); }
    finally { setBusy(false); }
  };

  const filtered = hospitals.filter(h => h.name.toLowerCase().includes(search.toLowerCase()) || h.address.toLowerCase().includes(search.toLowerCase()));

  if (loading) return <div className="loading-wrap"><div className="spinner" /></div>;

  return (
    <div>
      <div className="page-header-row">
        <div className="page-header">
          <h1>Hospital Management</h1>
          <p>{hospitals.length} hospitals in the network</p>
        </div>
        <button className="btn btn-primary" onClick={()=>setTab('add')}>+ Add Hospital</button>
      </div>

      <div className="tabs">
        <button className={`tab-btn${tab==='list'?' active':''}`} onClick={()=>setTab('list')}>All Hospitals ({hospitals.length})</button>
        <button className={`tab-btn${tab==='add'?' active':''}`}  onClick={()=>setTab('add')}>+ Add Hospital</button>
      </div>

      {tab === 'list' && (
        <>
          <div style={{marginBottom:16}}>
            <input style={{padding:'9px 14px',border:'1.5px solid var(--border)',borderRadius:8,fontSize:13,width:'100%',maxWidth:400}}
              placeholder="🔍 Search hospitals…" value={search} onChange={e=>setSearch(e.target.value)} />
          </div>
          {filtered.length === 0
            ? <div className="empty"><div className="empty-icon">🏥</div><p>No hospitals found</p></div>
            : (
              <div className="hospital-grid">
                {filtered.map(h => {
                  let facs = [];
                  try { facs = typeof h.facilities === 'string' ? JSON.parse(h.facilities) : (h.facilities || []); } catch(_) {}
                  return (
                    <div key={h.id} className="hospital-card">
                      <div className="hospital-card-header">
                        <span className="hospital-icon">🏥</span>
                        <span className="badge badge-green">Active</span>
                      </div>
                      <div className="hospital-name">{h.name}</div>
                      <div className="hospital-info">
                        <div className="hospital-info-row"><span className="hospital-info-icon">📍</span><span>{h.address}</span></div>
                        <div className="hospital-info-row"><span className="hospital-info-icon">�</span><span>{h.contact || '—'}</span></div>
                        <div className="hospital-info-row"><span className="hospital-info-icon">🌐</span><span>{parseFloat(h.latitude).toFixed(4)}°N, {parseFloat(h.longitude).toFixed(4)}°E</span></div>
                      </div>
                      {facs.length > 0 && (
                        <div className="facility-tags">
                          {facs.map((f,i) => <span key={i} className="facility-tag">{f}</span>)}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )
          }
        </>
      )}

      {tab === 'add' && (
        <div className="form-card" style={{maxWidth:680}}>
          <div className="form-section-title">🏥 Add New Hospital</div>
          {error && <div className="alert alert-error">⚠ {error}</div>}
          {ok    && <div className="alert alert-success">✓ {ok}</div>}
          <form onSubmit={submit}>
            <div className="form-grid" style={{marginBottom:16}}>
              <div className="form-group" style={{gridColumn:'1/-1'}}>
                <label>Hospital Name *</label>
                <input placeholder="e.g. City General Hospital" value={form.name} onChange={e=>set('name',e.target.value)} required />
              </div>
              <div className="form-group" style={{gridColumn:'1/-1'}}>
                <label>Address *</label>
                <input placeholder="Full address" value={form.address} onChange={e=>set('address',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Latitude *</label>
                <input type="number" step="any" placeholder="28.6139" value={form.latitude} onChange={e=>set('latitude',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Longitude *</label>
                <input type="number" step="any" placeholder="77.2090" value={form.longitude} onChange={e=>set('longitude',e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Contact</label>
                <input placeholder="+91-1234567890" value={form.contact} onChange={e=>set('contact',e.target.value)} />
              </div>
              <div className="form-group">
                <label>Facilities</label>
                <input placeholder="Emergency, ICU, Surgery (comma-separated)" value={form.facilities} onChange={e=>set('facilities',e.target.value)} />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={busy}>{busy?'⏳ Adding…':'+ Add Hospital'}</button>
              <button type="button" className="btn btn-outline" onClick={()=>setForm(BLANK)}>Clear</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
