import React, { useState, useEffect, useCallback } from 'react';

const API = 'http://localhost:5001/api';
const BLANK = {
  name:'', address:'', contact:'',
  facilities:'', totalBeds:'', availableBeds:'', isAvailable:true,
  operatingHours:'24/7', rating:''
};

// ── Geocode address → lat/lon via Nominatim (free, no API key) ────────────────
async function geocodeAddress(address) {
  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(address)}`;
  const res = await fetch(url, { headers: { 'Accept-Language': 'en' } });
  const data = await res.json();
  if (data && data.length > 0) {
    return { lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon), display: data[0].display_name };
  }
  return null;
}

// ── Star display ──────────────────────────────────────────────────────────────
function Stars({ rating }) {
  const full = Math.floor(rating);
  const half = rating - full >= 0.5;
  return (
    <span style={{color:'#f59e0b',fontSize:13}}>
      {'★'.repeat(full)}{half?'½':''}{'☆'.repeat(5-full-(half?1:0))}
      <span style={{color:'#64748b',marginLeft:4}}>{rating.toFixed(1)}</span>
    </span>
  );
}

// ── Interactive star rating ───────────────────────────────────────────────────
function RatingInput({ hospitalId, currentRating, onRated }) {
  const [hover, setHover] = useState(0);
  const [busy,  setBusy]  = useState(false);
  const [done,  setDone]  = useState(false);

  const submit = async (val) => {
    setBusy(true);
    try {
      const r = await fetch(`${API}/hospitals/${hospitalId}/rate`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ rating: val })
      });
      const d = await r.json();
      if (d.success) { setDone(true); onRated(d.newAverageRating); }
    } catch(_) {}
    finally { setBusy(false); }
  };

  if (done) return <span style={{fontSize:12,color:'#10b981'}}>✓ Thanks for rating!</span>;
  return (
    <div style={{display:'flex',alignItems:'center',gap:4}}>
      <span style={{fontSize:11,color:'#64748b'}}>Rate:</span>
      {[1,2,3,4,5].map(n => (
        <span key={n}
          onMouseEnter={()=>setHover(n)} onMouseLeave={()=>setHover(0)}
          onClick={()=>!busy&&submit(n)}
          style={{cursor:busy?'default':'pointer',fontSize:16,
            color:n<=(hover||Math.round(currentRating))?'#f59e0b':'#d1d5db',transition:'color .15s'}}>
          ★
        </span>
      ))}
    </div>
  );
}

// ── History modal ─────────────────────────────────────────────────────────────
function HistoryModal({ hospital, onClose }) {
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API}/hospitals/${hospital.id}/history`)
      .then(r=>r.json()).then(d=>{ if(d.success) setHistory(d.data); })
      .catch(()=>{}).finally(()=>setLoading(false));
  }, [hospital.id]);

  const typeLabel = {
    beds_updated:'🛏 Beds Updated', availability_changed:'🔄 Availability Changed',
    rating_submitted:'⭐ Rating Submitted', created:'✅ Created',
    updated:'✏ Updated', deleted:'🗑 Deleted',
  };

  return (
    <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,.45)',
      display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
      <div style={{background:'var(--surface)',borderRadius:12,padding:24,width:520,
        maxHeight:'80vh',display:'flex',flexDirection:'column',border:'1.5px solid var(--border)'}}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:16}}>
          <div style={{fontWeight:700,fontSize:15}}>📋 History — {hospital.name}</div>
          <button onClick={onClose} style={{background:'none',border:'none',cursor:'pointer',fontSize:18,color:'#64748b'}}>✕</button>
        </div>
        {loading && <div style={{textAlign:'center',padding:20,color:'#64748b'}}>Loading…</div>}
        {!loading && (!history||history.length===0) && (
          <div style={{textAlign:'center',padding:20,color:'#64748b'}}>No history yet.</div>
        )}
        {!loading && history && history.length>0 && (
          <div style={{overflowY:'auto',flex:1}}>
            {history.map(h=>(
              <div key={h.id} style={{padding:'10px 0',borderBottom:'1px solid var(--border)',display:'flex',flexDirection:'column',gap:3}}>
                <div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}>
                  <span style={{fontSize:13,fontWeight:600}}>{typeLabel[h.changeType]||h.changeType}</span>
                  <span style={{fontSize:11,color:'#94a3b8'}}>{new Date(h.changedAt).toLocaleString()}</span>
                </div>
                {(h.oldValue||h.newValue) && (
                  <div style={{fontSize:12,color:'#64748b'}}>
                    {h.oldValue&&<span>From: <strong>{h.oldValue}</strong> → </span>}
                    {h.newValue&&<span>To: <strong>{h.newValue}</strong></span>}
                  </div>
                )}
                {h.note&&<div style={{fontSize:11,color:'#94a3b8'}}>{h.note}</div>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Bed occupancy bar ─────────────────────────────────────────────────────────
function BedBar({ total, available }) {
  if (!total) return <span style={{color:'#94a3b8',fontSize:12}}>No bed data</span>;
  const pct = Math.round(((total-available)/total)*100);
  const colour = pct>=90?'#ef4444':pct>=70?'#f59e0b':'#10b981';
  return (
    <div style={{fontSize:12}}>
      <div style={{display:'flex',justifyContent:'space-between',marginBottom:3}}>
        <span style={{color:'#64748b'}}>{available}/{total} beds free</span>
        <span style={{color:colour,fontWeight:600}}>{pct}% occupied</span>
      </div>
      <div style={{background:'#e2e8f0',borderRadius:4,height:6}}>
        <div style={{width:`${pct}%`,background:colour,borderRadius:4,height:6,transition:'width .3s'}}/>
      </div>
    </div>
  );
}

// ── Stats bar ─────────────────────────────────────────────────────────────────
function StatsBar({ stats }) {
  if (!stats) return null;
  const occupancy = stats.totalBeds
    ? Math.round(((stats.totalBeds-stats.availableBeds)/stats.totalBeds)*100) : 0;
  return (
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:12,marginBottom:20}}>
      {[
        {label:'Total Hospitals',value:stats.total,          colour:'#3b82f6'},
        {label:'Available Now',  value:stats.availableCount, colour:'#10b981'},
        {label:'Total Beds',     value:stats.totalBeds,      colour:'#8b5cf6'},
        {label:'Bed Occupancy',  value:occupancy+'%',        colour:occupancy>=90?'#ef4444':occupancy>=70?'#f59e0b':'#10b981'},
      ].map(s=>(
        <div key={s.label} style={{background:'var(--surface)',border:'1.5px solid var(--border)',
          borderRadius:10,padding:'14px 16px',textAlign:'center'}}>
          <div style={{fontSize:22,fontWeight:700,color:s.colour}}>{s.value}</div>
          <div style={{fontSize:11,color:'#64748b',marginTop:2}}>{s.label}</div>
        </div>
      ))}
    </div>
  );
}

// ── Hospital card ─────────────────────────────────────────────────────────────
function HospitalCard({ h, onEdit, onDelete, onBeds, onHistory, onRated, onToggleAvail }) {
  const [localRating, setLocalRating] = useState(parseFloat(h.rating)||0);
  let facs = [];
  try { facs = typeof h.facilities==='string' ? JSON.parse(h.facilities) : (h.facilities||[]); } catch(_) {}
  const avail = h.isAvailable !== false;
  return (
    <div style={{background:'var(--surface)',border:'1.5px solid var(--border)',borderRadius:12,
      padding:16,display:'flex',flexDirection:'column',gap:10,opacity:avail?1:0.7}}>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start'}}>
        <div style={{display:'flex',gap:8,alignItems:'center'}}>
          <span style={{fontSize:20}}>🏥</span>
          <span style={{fontWeight:600,fontSize:14,color:'var(--text)'}}>{h.name}</span>
        </div>
        <span onClick={()=>onToggleAvail&&onToggleAvail(h)} title="Click to toggle"
          style={{fontSize:11,fontWeight:600,padding:'3px 8px',borderRadius:20,cursor:'pointer',
            background:avail?'#dcfce7':'#fee2e2',color:avail?'#16a34a':'#dc2626'}}>
          {avail?'● Open':'● Closed'}
        </span>
      </div>
      <div style={{fontSize:12,color:'#64748b',display:'flex',flexDirection:'column',gap:4}}>
        <div>📍 {h.address}</div>
        {h.contact&&<div>📞 {h.contact}</div>}
        <div>🕐 {h.operatingHours||'24/7'}</div>
        {h.distance!=null&&<div style={{color:'#3b82f6',fontWeight:600}}>📏 {h.distance.toFixed(2)} km away</div>}
      </div>
      <Stars rating={localRating}/>
      <RatingInput hospitalId={h.id} currentRating={localRating}
        onRated={avg=>{setLocalRating(avg);onRated&&onRated(h.id,avg);}}/>
      <BedBar total={h.totalBeds} available={h.availableBeds}/>
      {facs.length>0&&(
        <div style={{display:'flex',flexWrap:'wrap',gap:4}}>
          {facs.map((f,i)=>(
            <span key={i} style={{fontSize:11,background:'#eff6ff',color:'#2563eb',
              padding:'2px 8px',borderRadius:20,border:'1px solid #bfdbfe'}}>{f}</span>
          ))}
        </div>
      )}
      <div style={{display:'flex',gap:6,marginTop:4,flexWrap:'wrap'}}>
        <button onClick={()=>onEdit(h)}    style={btnStyle('#3b82f6')}>✏ Edit</button>
        <button onClick={()=>onBeds(h)}    style={btnStyle('#8b5cf6')}>🛏 Beds</button>
        <button onClick={()=>onHistory(h)} style={btnStyle('#0ea5e9')}>📋 History</button>
        <button onClick={()=>onDelete(h)}  style={btnStyle('#ef4444')}>🗑 Delete</button>
      </div>
    </div>
  );
}
const btnStyle = c => ({fontSize:11,padding:'4px 10px',borderRadius:6,border:'none',
  background:c+'22',color:c,cursor:'pointer',fontWeight:600});

// ── Shared styles ─────────────────────────────────────────────────────────────
const labelStyle = {display:'block',fontSize:12,fontWeight:600,color:'#64748b',marginBottom:4};
const inputStyle = {width:'100%',padding:'9px 12px',border:'1.5px solid var(--border)',
  borderRadius:8,fontSize:13,boxSizing:'border-box',background:'var(--bg)',color:'var(--text)'};
const alertStyle = t => ({padding:'10px 14px',borderRadius:8,marginBottom:12,fontSize:13,
  background:t==='ok'?'#dcfce7':'#fee2e2',color:t==='ok'?'#16a34a':'#dc2626'});

// ── Hospital form — address-based, geocodes automatically ─────────────────────
function HospitalForm({ initial, onSave, onCancel, busy, error, ok }) {
  const [form, setForm]       = useState(initial || BLANK);
  const [geocoding, setGeocoding] = useState(false);
  const [geoMsg, setGeoMsg]   = useState('');
  const set = (k,v) => setForm(f=>({...f,[k]:v}));
  const isEdit = !!(initial && initial.id);

  const submit = async (e) => {
    e.preventDefault();
    setGeoMsg('');

    // Geocode the address to get lat/lon
    setGeocoding(true);
    setGeoMsg('📍 Looking up address…');
    const geo = await geocodeAddress(form.address).catch(()=>null);
    setGeocoding(false);

    if (!geo) {
      setGeoMsg('⚠ Could not find coordinates for this address. Check the address and try again.');
      return;
    }
    setGeoMsg(`✓ Located: ${geo.display.substring(0,60)}…`);

    const facilities = typeof form.facilities==='string'
      ? form.facilities.split(',').map(s=>s.trim()).filter(Boolean)
      : form.facilities;

    onSave({
      ...form,
      latitude:      geo.lat,
      longitude:     geo.lon,
      totalBeds:     parseInt(form.totalBeds)     || 0,
      availableBeds: parseInt(form.availableBeds) || 0,
      rating:        parseFloat(form.rating)      || 0,
      facilities,
    });
  };

  const facilitiesStr = Array.isArray(form.facilities)
    ? form.facilities.join(', ') : form.facilities;

  return (
    <div style={{background:'var(--surface)',border:'1.5px solid var(--border)',
      borderRadius:12,padding:24,maxWidth:680}}>
      <div style={{fontWeight:700,fontSize:15,marginBottom:16}}>
        {isEdit?'✏ Edit Hospital':'🏥 Add New Hospital'}
      </div>
      {error  && <div style={alertStyle('error')}>⚠ {error}</div>}
      {ok     && <div style={alertStyle('ok')}>✓ {ok}</div>}
      {geoMsg && <div style={{...alertStyle(geoMsg.startsWith('⚠')?'error':'ok'),marginBottom:12}}>{geoMsg}</div>}
      <form onSubmit={submit}>
        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
          <div style={{gridColumn:'1/-1'}}>
            <label style={labelStyle}>Hospital Name *</label>
            <input style={inputStyle} placeholder="City General Hospital"
              value={form.name} onChange={e=>set('name',e.target.value)} required/>
          </div>
          <div style={{gridColumn:'1/-1'}}>
            <label style={labelStyle}>Address * <span style={{fontWeight:400,color:'#94a3b8'}}>(full address — coordinates are auto-detected)</span></label>
            <input style={inputStyle} placeholder="123 Main St, New Delhi, India"
              value={form.address} onChange={e=>set('address',e.target.value)} required/>
          </div>
          <div>
            <label style={labelStyle}>Contact</label>
            <input style={inputStyle} placeholder="+91-1234567890"
              value={form.contact} onChange={e=>set('contact',e.target.value)}/>
          </div>
          <div>
            <label style={labelStyle}>Operating Hours</label>
            <input style={inputStyle} placeholder="24/7 or 08:00-20:00"
              value={form.operatingHours} onChange={e=>set('operatingHours',e.target.value)}/>
          </div>
          <div>
            <label style={labelStyle}>Total Beds</label>
            <input style={inputStyle} type="number" min="0" placeholder="200"
              value={form.totalBeds} onChange={e=>set('totalBeds',e.target.value)}/>
          </div>
          <div>
            <label style={labelStyle}>Available Beds</label>
            <input style={inputStyle} type="number" min="0" placeholder="45"
              value={form.availableBeds} onChange={e=>set('availableBeds',e.target.value)}/>
          </div>
          <div>
            <label style={labelStyle}>Rating (0–5)</label>
            <input style={inputStyle} type="number" step="0.1" min="0" max="5" placeholder="4.5"
              value={form.rating} onChange={e=>set('rating',e.target.value)}/>
          </div>
          <div style={{display:'flex',alignItems:'center',gap:8,paddingTop:20}}>
            <input type="checkbox" id="avail" checked={!!form.isAvailable}
              onChange={e=>set('isAvailable',e.target.checked)}/>
            <label htmlFor="avail" style={{fontSize:13,cursor:'pointer'}}>Currently Available</label>
          </div>
          <div style={{gridColumn:'1/-1'}}>
            <label style={labelStyle}>Facilities (comma-separated)</label>
            <input style={inputStyle} placeholder="Emergency, ICU, Surgery"
              value={facilitiesStr} onChange={e=>set('facilities',e.target.value)}/>
          </div>
        </div>
        <div style={{display:'flex',gap:8,marginTop:16}}>
          <button type="submit" disabled={busy||geocoding}
            style={{...inputStyle,background:'#2563eb',color:'#fff',border:'none',
              cursor:'pointer',fontWeight:600,padding:'10px 20px',width:'auto'}}>
            {geocoding?'📍 Locating…':busy?'⏳ Saving…':isEdit?'💾 Save Changes':'+ Add Hospital'}
          </button>
          <button type="button" onClick={onCancel}
            style={{...inputStyle,background:'transparent',cursor:'pointer',padding:'10px 20px',width:'auto'}}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}

// ── Beds quick-update modal ───────────────────────────────────────────────────
function BedsModal({ hospital, onClose, onSaved }) {
  const [val, setVal] = useState(String(hospital.availableBeds));
  const [busy, setBusy] = useState(false);
  const [err,  setErr]  = useState('');
  const save = async () => {
    setBusy(true); setErr('');
    try {
      const r = await fetch(`${API}/hospitals/${hospital.id}/beds`, {
        method:'PATCH', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({availableBeds:parseInt(val)})
      });
      const d = await r.json();
      if (d.success) { onSaved(); onClose(); } else setErr(d.error||'Failed');
    } catch(_) { setErr('Cannot connect'); }
    finally { setBusy(false); }
  };
  return (
    <div style={{position:'fixed',inset:0,background:'rgba(0,0,0,.4)',
      display:'flex',alignItems:'center',justifyContent:'center',zIndex:1000}}>
      <div style={{background:'var(--surface)',borderRadius:12,padding:24,width:320,border:'1.5px solid var(--border)'}}>
        <div style={{fontWeight:700,marginBottom:12}}>🛏 Update Beds — {hospital.name}</div>
        <div style={{fontSize:12,color:'#64748b',marginBottom:12}}>Total beds: <strong>{hospital.totalBeds}</strong></div>
        {err&&<div style={alertStyle('error')}>{err}</div>}
        <label style={labelStyle}>Available Beds</label>
        <input style={{...inputStyle,marginBottom:12}} type="number" min="0"
          max={hospital.totalBeds} value={val} onChange={e=>setVal(e.target.value)}/>
        <div style={{display:'flex',gap:8}}>
          <button onClick={save} disabled={busy}
            style={{flex:1,padding:'9px',background:'#2563eb',color:'#fff',border:'none',borderRadius:8,cursor:'pointer',fontWeight:600}}>
            {busy?'⏳':'💾 Save'}
          </button>
          <button onClick={onClose}
            style={{flex:1,padding:'9px',background:'transparent',border:'1.5px solid var(--border)',borderRadius:8,cursor:'pointer'}}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Nearest hospitals panel ───────────────────────────────────────────────────
function NearestPanel() {
  const [address,   setAddress]   = useState('');
  const [count,     setCount]     = useState('5');
  const [availOnly, setAvailOnly] = useState(false);
  const [results,   setResults]   = useState(null);
  const [busy,      setBusy]      = useState(false);
  const [err,       setErr]       = useState('');

  const useMyLocation = () => {
    if (!navigator.geolocation) { setErr('Geolocation not supported'); return; }
    navigator.geolocation.getCurrentPosition(
      async p => {
        // Reverse geocode to get address string
        const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${p.coords.latitude}&lon=${p.coords.longitude}`;
        try {
          const r = await fetch(url, {headers:{'Accept-Language':'en'}});
          const d = await r.json();
          setAddress(d.display_name || `${p.coords.latitude.toFixed(5)}, ${p.coords.longitude.toFixed(5)}`);
        } catch(_) {
          setAddress(`${p.coords.latitude.toFixed(5)}, ${p.coords.longitude.toFixed(5)}`);
        }
      },
      () => setErr('Could not get location')
    );
  };

  const search = async () => {
    if (!address.trim()) { setErr('Enter an address or use your location'); return; }
    setBusy(true); setErr(''); setResults(null);
    const geo = await geocodeAddress(address).catch(()=>null);
    if (!geo) { setErr('Could not find coordinates for this address'); setBusy(false); return; }
    try {
      const url = `${API}/hospitals/nearest?lat=${geo.lat}&lon=${geo.lon}&count=${count}&availableOnly=${availOnly}`;
      const r = await fetch(url);
      const d = await r.json();
      if (d.success) setResults(d.data); else setErr(d.error||'Failed');
    } catch(_) { setErr('Cannot connect'); }
    finally { setBusy(false); }
  };

  return (
    <div>
      <div style={{background:'var(--surface)',border:'1.5px solid var(--border)',
        borderRadius:12,padding:20,marginBottom:16,maxWidth:600}}>
        <div style={{fontWeight:700,marginBottom:12}}>📍 Find Nearest Hospitals</div>
        <div style={{display:'grid',gridTemplateColumns:'1fr 80px',gap:10,marginBottom:10}}>
          <div>
            <label style={labelStyle}>Your Address / Location</label>
            <input style={inputStyle} placeholder="e.g. Connaught Place, New Delhi"
              value={address} onChange={e=>setAddress(e.target.value)}/>
          </div>
          <div>
            <label style={labelStyle}>Count</label>
            <input style={inputStyle} type="number" min="1" max="20" value={count} onChange={e=>setCount(e.target.value)}/>
          </div>
        </div>
        <div style={{display:'flex',gap:12,alignItems:'center',marginBottom:12}}>
          <label style={{fontSize:13,display:'flex',gap:6,alignItems:'center',cursor:'pointer'}}>
            <input type="checkbox" checked={availOnly} onChange={e=>setAvailOnly(e.target.checked)}/>
            Available hospitals only
          </label>
          <button onClick={useMyLocation}
            style={{fontSize:12,padding:'6px 12px',borderRadius:8,border:'1.5px solid var(--border)',background:'transparent',cursor:'pointer'}}>
            📡 Use My Location
          </button>
        </div>
        {err&&<div style={alertStyle('error')}>{err}</div>}
        <button onClick={search} disabled={busy}
          style={{padding:'9px 20px',background:'#2563eb',color:'#fff',border:'none',
            borderRadius:8,cursor:'pointer',fontWeight:600,fontSize:13}}>
          {busy?'⏳ Searching…':'🔍 Find Nearest'}
        </button>
      </div>
      {results&&(
        <div>
          <div style={{fontWeight:600,marginBottom:10,fontSize:13}}>
            {results.length} hospital{results.length!==1?'s':''} found
          </div>
          <div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(280px,1fr))',gap:12}}>
            {results.map(h=>(
              <HospitalCard key={h.id} h={h}
                onEdit={()=>{}} onDelete={()=>{}} onBeds={()=>{}} onHistory={()=>{}}/>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────
export default function HospitalManagement() {
  const [hospitals, setHospitals] = useState([]);
  const [stats,     setStats]     = useState(null);
  const [loading,   setLoading]   = useState(true);
  const [search,    setSearch]    = useState('');
  const [facility,  setFacility]  = useState('');
  const [sortBy,    setSortBy]    = useState('name');
  const [tab,       setTab]       = useState('list');
  const [editH,     setEditH]     = useState(null);
  const [bedsH,     setBedsH]     = useState(null);
  const [historyH,  setHistoryH]  = useState(null);
  const [busy,      setBusy]      = useState(false);
  const [error,     setError]     = useState('');
  const [ok,        setOk]        = useState('');

  const load = useCallback(async (sort) => {
    try {
      const sortParam = sort || sortBy;
      const [hRes, sRes] = await Promise.all([
        fetch(`${API}/hospitals?sort=${sortParam}`),
        fetch(`${API}/hospitals/stats`),
      ]);
      const hData = await hRes.json();
      const sData = await sRes.json();
      if (hData.success) setHospitals(hData.data);
      if (sData.success) setStats(sData.data);
    } catch(_) {}
    finally { setLoading(false); }
  }, [sortBy]);

  useEffect(() => { load(); }, [load]);

  const flash = (msg, isErr=false) => {
    if (isErr) { setError(msg); setOk(''); }
    else       { setOk(msg);   setError(''); }
    setTimeout(() => { setError(''); setOk(''); }, 4000);
  };

  const handleAdd = async (data) => {
    setBusy(true);
    try {
      const r = await fetch(`${API}/hospitals/add`, {
        method:'POST', headers:{'Content-Type':'application/json'},
        body: JSON.stringify(data)
      });
      const d = await r.json();
      if (d.success) { flash('Hospital added successfully.'); load(); setTab('list'); }
      else flash(d.error || 'Failed to add hospital', true);
    } catch(_) { flash('Cannot connect to server', true); }
    finally { setBusy(false); }
  };

  const handleEdit = async (data) => {
    setBusy(true);
    try {
      const r = await fetch(`${API}/hospitals/${editH.id}`, {
        method:'PUT', headers:{'Content-Type':'application/json'},
        body: JSON.stringify(data)
      });
      const d = await r.json();
      if (d.success) { flash('Hospital updated.'); load(); setEditH(null); setTab('list'); }
      else flash(d.error || 'Update failed', true);
    } catch(_) { flash('Cannot connect', true); }
    finally { setBusy(false); }
  };

  const handleDelete = async (h) => {
    if (!window.confirm(`Delete "${h.name}"? This cannot be undone.`)) return;
    try {
      const r = await fetch(`${API}/hospitals/${h.id}`, { method:'DELETE' });
      const d = await r.json();
      if (d.success) { flash('Hospital deleted.'); load(); }
      else flash(d.error || 'Delete failed', true);
    } catch(_) { flash('Cannot connect', true); }
  };

  const handleToggleAvail = async (h) => {
    try {
      const r = await fetch(`${API}/hospitals/${h.id}/availability`, {
        method:'PATCH', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({ isAvailable: !h.isAvailable })
      });
      const d = await r.json();
      if (d.success) load();
      else flash(d.error || 'Failed', true);
    } catch(_) { flash('Cannot connect', true); }
  };

  const filtered = hospitals.filter(h => {
    const matchText = !search ||
      h.name.toLowerCase().includes(search.toLowerCase()) ||
      h.address.toLowerCase().includes(search.toLowerCase());
    const matchFac = !facility || (() => {
      try {
        const facs = typeof h.facilities==='string' ? JSON.parse(h.facilities) : (h.facilities||[]);
        return facs.some(f => f.toLowerCase().includes(facility.toLowerCase()));
      } catch(_) { return false; }
    })();
    return matchText && matchFac;
  });

  const allFacilities = [...new Set(
    hospitals.flatMap(h => {
      try { return typeof h.facilities==='string' ? JSON.parse(h.facilities) : (h.facilities||[]); }
      catch(_) { return []; }
    })
  )].sort();

  if (loading) return <div style={{padding:40,textAlign:'center'}}>Loading hospitals…</div>;

  const TABS = [
    { id:'list',    label:`All (${hospitals.length})` },
    { id:'nearest', label:'📍 Nearest' },
    { id:'add',     label:'+ Add' },
  ];

  return (
    <div>
      <div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',marginBottom:16}}>
        <div>
          <h1 style={{margin:0,fontSize:20,fontWeight:700}}>Hospital Management</h1>
          <p style={{margin:'4px 0 0',color:'#64748b',fontSize:13}}>
            {hospitals.length} hospitals · {stats?.availableCount ?? '—'} available
            {stats?.avgRating ? ` · avg rating ${stats.avgRating}★` : ''}
          </p>
        </div>
        <button onClick={() => setTab('add')}
          style={{padding:'9px 16px',background:'#2563eb',color:'#fff',border:'none',
            borderRadius:8,cursor:'pointer',fontWeight:600,fontSize:13}}>
          + Add Hospital
        </button>
      </div>

      <StatsBar stats={stats} />

      {(error || ok) && (
        <div style={{...alertStyle(ok?'ok':'error'), marginBottom:12}}>{ok || error}</div>
      )}

      {/* Tabs */}
      <div style={{display:'flex',gap:4,marginBottom:16,borderBottom:'2px solid var(--border)'}}>
        {TABS.map(t => (
          <button key={t.id} onClick={() => { setTab(t.id); setEditH(null); }}
            style={{padding:'8px 16px',border:'none',background:'transparent',cursor:'pointer',
              fontSize:13,fontWeight:600,
              color: tab===t.id ? '#2563eb' : '#64748b',
              borderBottom: tab===t.id ? '2px solid #2563eb' : '2px solid transparent',
              marginBottom:-2}}>
            {t.label}
          </button>
        ))}
      </div>

      {/* List tab */}
      {tab === 'list' && (
        <>
          <div style={{display:'flex',gap:10,marginBottom:16,flexWrap:'wrap',alignItems:'center'}}>
            <input style={{...inputStyle,maxWidth:300}}
              placeholder="🔍 Search by name or address…"
              value={search} onChange={e=>setSearch(e.target.value)} />
            <select style={{...inputStyle,maxWidth:200}}
              value={facility} onChange={e=>setFacility(e.target.value)}>
              <option value="">All facilities</option>
              {allFacilities.map(f => <option key={f} value={f}>{f}</option>)}
            </select>
            <select style={{...inputStyle,maxWidth:190}}
              value={sortBy} onChange={e=>{ setSortBy(e.target.value); load(e.target.value); }}>
              <option value="name">Sort: Name A–Z</option>
              <option value="rating">Sort: Rating ↓</option>
              <option value="available_beds">Sort: Available Beds ↓</option>
              <option value="total_beds">Sort: Total Beds ↓</option>
              <option value="occupancy">Sort: Occupancy ↓</option>
            </select>
            {(search || facility) && (
              <button onClick={() => { setSearch(''); setFacility(''); }}
                style={{padding:'9px 12px',border:'1.5px solid var(--border)',borderRadius:8,
                  background:'transparent',cursor:'pointer',fontSize:12}}>
                ✕ Clear
              </button>
            )}
          </div>
          {filtered.length === 0
            ? <div style={{textAlign:'center',padding:40,color:'#64748b'}}>
                <div style={{fontSize:32,marginBottom:8}}>🏥</div>
                <p>No hospitals match your filters</p>
              </div>
            : <div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(300px,1fr))',gap:14}}>
                {filtered.map(h => (
                  <HospitalCard key={h.id} h={h}
                    onEdit={h => { setEditH(h); setTab('edit'); }}
                    onDelete={handleDelete}
                    onBeds={h => setBedsH(h)}
                    onHistory={h => setHistoryH(h)}
                    onToggleAvail={handleToggleAvail}
                    onRated={(id, avg) => setHospitals(prev =>
                      prev.map(x => x.id === id ? {...x, rating: avg} : x)
                    )} />
                ))}
              </div>
          }
        </>
      )}

      {/* Add tab */}
      {tab === 'add' && (
        <HospitalForm
          onSave={handleAdd}
          onCancel={() => setTab('list')}
          busy={busy} error={error} ok={ok} />
      )}

      {/* Edit tab */}
      {tab === 'edit' && editH && (
        <HospitalForm
          initial={{
            ...editH,
            facilities: (() => {
              try {
                return (typeof editH.facilities==='string'
                  ? JSON.parse(editH.facilities)
                  : editH.facilities||[]).join(', ');
              } catch(_) { return ''; }
            })()
          }}
          onSave={handleEdit}
          onCancel={() => { setEditH(null); setTab('list'); }}
          busy={busy} error={error} ok={ok} />
      )}

      {/* Nearest tab */}
      {tab === 'nearest' && <NearestPanel />}

      {/* Beds modal */}
      {bedsH && (
        <BedsModal
          hospital={bedsH}
          onClose={() => setBedsH(null)}
          onSaved={() => { load(); flash('Bed count updated.'); }} />
      )}

      {/* History modal */}
      {historyH && (
        <HistoryModal
          hospital={historyH}
          onClose={() => setHistoryH(null)} />
      )}
    </div>
  );
}
