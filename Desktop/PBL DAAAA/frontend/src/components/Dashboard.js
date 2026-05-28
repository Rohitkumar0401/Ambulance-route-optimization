import React, { useState, useEffect, useCallback } from 'react';

const API = 'http://localhost:5001/api';
const authH = () => ({ 'Authorization': `Bearer ${localStorage.getItem('token')}` });

const SEV_BADGE = { critical: 'badge-red', high: 'badge-yellow', medium: 'badge-blue', low: 'badge-green' };
const STATUS_BADGE = { pending: 'badge-yellow', assigned: 'badge-blue', in_progress: 'badge-purple', completed: 'badge-green', cancelled: 'badge-gray' };
const scoreColor = s => s >= 80 ? '#ef4444' : s >= 60 ? '#f59e0b' : '#10b981';
const flagBadge  = s => ({ critical: 'badge-red', warning: 'badge-yellow', good: 'badge-green' }[s] || 'badge-gray');

export default function Dashboard() {
  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(true);
  const [tick,    setTick]    = useState(new Date());

  const load = useCallback(async () => {
    try {
      const [h, em, rs, as_, fr, al] = await Promise.allSettled([
        fetch(`${API}/hospitals`,            { headers: authH() }).then(r => r.json()),
        fetch(`${API}/emergency/all`,        { headers: authH() }).then(r => r.json()),
        fetch(`${API}/road-scoring/stats`,   { headers: authH() }).then(r => r.json()),
        fetch(`${API}/alerts/stats`,         { headers: authH() }).then(r => r.json()),
        fetch(`${API}/road-scoring/flagged`, { headers: authH() }).then(r => r.json()),
        fetch(`${API}/alerts/active`,        { headers: authH() }).then(r => r.json()),
      ]);
      const get = (r, def) => r.status === 'fulfilled' && r.value?.success ? r.value : def;
      const hospitals  = get(h,  { data: [] });
      const emergency  = get(em, { data: [] });
      const roadStats  = get(rs, { data: {} });
      const alertStats = get(as_, { data: {} });
      const flagged    = get(fr, { data: [] });
      const alerts     = get(al, { data: [] });

      const emList = emergency.data || [];
      setData({
        stats: {
          activeEm:      emList.filter(r => ['pending','in_progress','assigned'].includes(r.status)).length,
          hospitals:     hospitals.data.length,
          flaggedRoads:  parseInt(roadStats.data.warning || 0) + parseInt(roadStats.data.critical || 0),
          criticalRoads: parseInt(roadStats.data.critical || 0),
          activeAlerts:  parseInt(alertStats.data.active || 0),
          criticalAlerts:parseInt(alertStats.data.critical || 0),
          avgScore:      Math.round(parseFloat(roadStats.data.avgScore || 0)),
          totalRoads:    parseInt(roadStats.data.total || 0),
        },
        flagged:    flagged.data.slice(0, 5),
        alerts:     alerts.data.slice(0, 6),
        emergencies: emList.slice(0, 6),
      });
      setTick(new Date());
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); const t = setInterval(load, 30000); return () => clearInterval(t); }, [load]);

  const ack = async (id) => { await fetch(`${API}/alerts/${id}/acknowledge`, { method: 'PATCH', headers: { ...authH(), 'Content-Type': 'application/json' }, body: JSON.stringify({ acknowledgedBy: 'dispatcher' }) }); load(); };
  const res = async (id) => { await fetch(`${API}/alerts/${id}/resolve`, { method: 'PATCH', headers: authH() }); load(); };

  const dlReport = async () => {
    const r = await fetch(`${API}/alerts/government-report`, { headers: authH() });
    const d = await r.json();
    if (d.success) {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([JSON.stringify(d.report, null, 2)], { type: 'application/json' }));
      a.download = `govt-report-${new Date().toISOString().split('T')[0]}.json`;
      a.click();
    }
  };

  if (loading) return <div className="loading-wrap"><div className="spinner" /></div>;
  const { stats, flagged, alerts, emergencies } = data;

  const STAT_CARDS = [
    { label: 'Active Emergencies', value: stats.activeEm,      icon: '🚨', cls: stats.activeEm > 0 ? 'danger' : 'success', sub: stats.activeEm > 0 ? 'Requires attention' : 'All clear' },
    { label: 'Hospitals Online',   value: stats.hospitals,     icon: '🏥', cls: 'success', sub: 'All operational' },
    { label: 'Flagged Roads',      value: stats.flaggedRoads,  icon: '🚧', cls: stats.criticalRoads > 0 ? 'danger' : 'warning', sub: `${stats.criticalRoads} critical · ${stats.flaggedRoads - stats.criticalRoads} warning` },
    { label: 'Active Alerts',      value: stats.activeAlerts,  icon: '🔔', cls: stats.criticalAlerts > 0 ? 'danger' : 'warning', sub: `${stats.criticalAlerts} critical` },
    { label: 'Avg Road Score',     value: `${stats.avgScore}/100`, icon: '📊', cls: stats.avgScore >= 60 ? 'danger' : 'success', sub: stats.avgScore >= 80 ? 'Poor condition' : stats.avgScore >= 60 ? 'Moderate' : 'Good' },
    { label: 'Roads Monitored',    value: stats.totalRoads,    icon: '🛣️', cls: 'accent', sub: 'Threshold checks active' },
  ];

  return (
    <div>
      <div className="page-header-row">
        <div className="page-header">
          <h1>Operations Dashboard</h1>
          <p>Real-time monitoring · Updated {tick.toLocaleTimeString()}</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-outline btn-sm" onClick={load}>↺ Refresh</button>
          <button className="btn btn-success btn-sm" onClick={dlReport}>↓ Govt Report</button>
        </div>
      </div>

      {/* Stats */}
      <div className="stat-grid">
        {STAT_CARDS.map(c => (
          <div key={c.label} className={`stat-card ${c.cls}`}>
            <div className="stat-icon">{c.icon}</div>
            <div className="stat-label">{c.label}</div>
            <div className="stat-value">{c.value}</div>
            <div className="stat-sub">{c.sub}</div>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(440px, 1fr))', gap: 20, marginBottom: 20 }}>
        {/* Flagged Roads */}
        <div className="section-card">
          <div className="section-header">
            <h3>🚧 Flagged Roads</h3>
            <span className="badge badge-red">{flagged.length} flagged</span>
          </div>
          <div className="section-body">
            {flagged.length === 0
              ? <div className="empty"><div className="empty-icon">✅</div><p>No roads flagged</p></div>
              : flagged.map(r => (
                <div key={r.id} className="road-item">
                  <div className="road-item-header">
                    <span className="road-name">{r.road_name}</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span className="road-score" style={{ color: scoreColor(r.composite_score) }}>{r.composite_score}</span>
                      <span className={`badge ${flagBadge(r.flag_status)}`}>{r.flag_status}</span>
                    </div>
                  </div>
                  <div className="score-bar-bg">
                    <div className="score-bar" style={{ width: `${r.composite_score}%`, background: scoreColor(r.composite_score) }} />
                  </div>
                  <div className="road-meta">
                    <span>Quality {parseFloat(r.road_quality||1).toFixed(1)}×</span>
                    <span>Speed {r.average_speed||60} km/h</span>
                    <span>Congestion {Math.round((r.congestion_level||0)*100)}%</span>
                    <span>Incidents {r.incident_count||0}</span>
                  </div>
                </div>
              ))
            }
          </div>
        </div>

        {/* Active Alerts */}
        <div className="section-card">
          <div className="section-header">
            <h3>🔔 Active Alerts</h3>
            <span className="badge badge-red">{alerts.length} active</span>
          </div>
          <div className="section-body">
            {alerts.length === 0
              ? <div className="empty"><div className="empty-icon">✅</div><p>No active alerts</p></div>
              : alerts.map(a => (
                <div key={a.id} className="alert-item" style={{ background: a.severity === 'critical' ? '#fff1f2' : a.severity === 'high' ? '#fffbeb' : '#f0f9ff' }}>
                  <div className="alert-item-header">
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <span className={`badge ${SEV_BADGE[a.severity] || 'badge-gray'}`}>{a.severity}</span>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'capitalize' }}>{a.alert_type?.replace('_',' ')}</span>
                    </div>
                    <div className="alert-actions">
                      <button className="btn btn-outline btn-sm" style={{ padding: '3px 8px', fontSize: 11 }} onClick={() => ack(a.id)}>ACK</button>
                      <button className="btn btn-success btn-sm" style={{ padding: '3px 8px', fontSize: 11 }} onClick={() => res(a.id)}>RESOLVE</button>
                    </div>
                  </div>
                  <div className="alert-item-msg">{a.message}</div>
                  <div className="alert-item-time">{new Date(a.created_at).toLocaleString()}</div>
                </div>
              ))
            }
          </div>
        </div>
      </div>

      {/* Emergency Requests */}
      <div className="section-card">
        <div className="section-header">
          <h3>🚑 Recent Emergency Requests</h3>
          <span className="badge badge-blue">{emergencies.length} shown</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th><th>Patient</th><th>Severity</th><th>Status</th><th>Contact</th><th>Description</th><th>Time</th>
              </tr>
            </thead>
            <tbody>
              {emergencies.length === 0
                ? <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: 32 }}>No emergency requests</td></tr>
                : emergencies.map(em => (
                  <tr key={em.id}>
                    <td style={{ color: 'var(--text-muted)', fontWeight: 600 }}>#{em.id}</td>
                    <td style={{ fontWeight: 600 }}>{em.patient_name || '—'}</td>
                    <td><span className={`badge ${SEV_BADGE[em.severity] || 'badge-gray'}`}>{em.severity}</span></td>
                    <td><span className={`badge ${STATUS_BADGE[em.status] || 'badge-gray'}`}>{em.status}</span></td>
                    <td style={{ color: 'var(--text-muted)' }}>{em.contact}</td>
                    <td style={{ color: 'var(--text-muted)', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{em.description || '—'}</td>
                    <td style={{ color: 'var(--text-light)', whiteSpace: 'nowrap' }}>{new Date(em.created_at).toLocaleString()}</td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
