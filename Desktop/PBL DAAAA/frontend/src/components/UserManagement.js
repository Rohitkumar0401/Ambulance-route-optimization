import React, { useState, useEffect, useCallback } from 'react';
import { ROLE_LABELS } from '../App';

const API = 'http://localhost:5001/api';

const ROLES = ['admin', 'dispatcher', 'driver', 'user'];
const BLANK_FORM = { username: '', email: '', password: '', role: 'user' };

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${localStorage.getItem('token')}`,
  };
}

function RoleBadge({ role }) {
  const info = ROLE_LABELS[role] || { label: role, color: '#475569', bg: '#f1f5f9' };
  return (
    <span style={{
      background: info.bg, color: info.color,
      padding: '3px 10px', borderRadius: 20,
      fontSize: 11, fontWeight: 700,
    }}>{info.label}</span>
  );
}

export default function UserManagement({ currentUser }) {
  const [users,   setUsers]   = useState([]);
  const [log,     setLog]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab,     setTab]     = useState('users');
  const [form,    setForm]    = useState(BLANK_FORM);
  const [busy,    setBusy]    = useState(false);
  const [error,   setError]   = useState('');
  const [ok,      setOk]      = useState('');
  const [editId,  setEditId]  = useState(null);
  const [editRole,setEditRole]= useState('');

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const load = useCallback(async () => {
    try {
      const [ur, lr] = await Promise.all([
        fetch(`${API}/auth/users`,        { headers: authHeaders() }).then(r => r.json()),
        fetch(`${API}/auth/activity-log`, { headers: authHeaders() }).then(r => r.json()),
      ]);
      if (ur.success) setUsers(ur.data);
      if (lr.success) setLog(lr.data);
    } catch (_) {}
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Create user
  const createUser = async (e) => {
    e.preventDefault();
    setError(''); setOk(''); setBusy(true);
    try {
      const res  = await fetch(`${API}/auth/users`, {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify(form),
      });
      const data = await res.json();
      if (data.success) {
        setOk(`User "${form.username}" created with role "${form.role}"`);
        setForm(BLANK_FORM); load();
      } else setError(data.error || 'Failed to create user');
    } catch (_) { setError('Cannot connect to server'); }
    finally { setBusy(false); }
  };

  // Update role
  const saveRole = async (id) => {
    try {
      const res  = await fetch(`${API}/auth/users/${id}/role`, {
        method: 'PATCH', headers: authHeaders(),
        body: JSON.stringify({ role: editRole }),
      });
      const data = await res.json();
      if (data.success) { setEditId(null); load(); }
      else alert(data.error);
    } catch (_) { alert('Error updating role'); }
  };

  // Delete user
  const deleteUser = async (id, name) => {
    if (!window.confirm(`Delete user "${name}"? This cannot be undone.`)) return;
    try {
      const res  = await fetch(`${API}/auth/users/${id}`, {
        method: 'DELETE', headers: authHeaders(),
      });
      const data = await res.json();
      if (data.success) load();
      else alert(data.error);
    } catch (_) { alert('Error deleting user'); }
  };

  const ACTION_ICONS = {
    login:       { icon: '🔑', color: '#2563eb', bg: '#dbeafe' },
    role_change: { icon: '🔄', color: '#d97706', bg: '#fef3c7' },
    delete_user: { icon: '🗑', color: '#dc2626', bg: '#fee2e2' },
    create_user: { icon: '➕', color: '#059669', bg: '#d1fae5' },
  };

  if (loading) return <div className="loading-wrap"><div className="spinner" /></div>;

  const roleCount = ROLES.reduce((acc, r) => {
    acc[r] = users.filter(u => u.role === r).length;
    return acc;
  }, {});

  return (
    <div>
      <div className="page-header">
        <h1>User Management</h1>
        <p>Manage accounts, roles and permissions · Admin only</p>
      </div>

      {/* Stats */}
      <div className="stat-grid" style={{ marginBottom: 20 }}>
        <div className="stat-card">
          <div className="stat-icon">👥</div>
          <div className="stat-label">Total Users</div>
          <div className="stat-value">{users.length}</div>
        </div>
        {ROLES.map(r => {
          const info = ROLE_LABELS[r];
          return (
            <div key={r} className="stat-card">
              <div className="stat-icon" style={{ opacity: 1, fontSize: 16 }}>
                <span style={{ background: info.bg, color: info.color, padding: '2px 8px', borderRadius: 6, fontSize: 11, fontWeight: 700 }}>{info.label}</span>
              </div>
              <div className="stat-label">{info.label}s</div>
              <div className="stat-value" style={{ color: info.color }}>{roleCount[r] || 0}</div>
            </div>
          );
        })}
      </div>

      <div className="tabs">
        <button className={`tab-btn${tab === 'users'  ? ' active' : ''}`} onClick={() => setTab('users')}>👥 All Users ({users.length})</button>
        <button className={`tab-btn${tab === 'create' ? ' active' : ''}`} onClick={() => setTab('create')}>➕ Create User</button>
        <button className={`tab-btn${tab === 'log'    ? ' active' : ''}`} onClick={() => setTab('log')}>📋 Activity Log ({log.length})</button>
      </div>

      {/* Users table */}
      {tab === 'users' && (
        <div className="section-card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Permissions</th>
                  <th>Joined</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map(u => (
                  <tr key={u.id}>
                    <td style={{ color: 'var(--text-muted)', fontWeight: 600 }}>#{u.id}</td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{
                          width: 30, height: 30, borderRadius: '50%',
                          background: ROLE_LABELS[u.role]?.bg || '#f1f5f9',
                          color: ROLE_LABELS[u.role]?.color || '#475569',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontWeight: 800, fontSize: 13,
                        }}>{u.username[0].toUpperCase()}</div>
                        <span style={{ fontWeight: 600 }}>
                          {u.username}
                          {u.id === currentUser?.id && (
                            <span style={{ marginLeft: 6, fontSize: 10, color: 'var(--primary)', fontWeight: 700 }}>(you)</span>
                          )}
                        </span>
                      </div>
                    </td>
                    <td style={{ color: 'var(--text-muted)' }}>{u.email}</td>
                    <td>
                      {editId === u.id ? (
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                          <select value={editRole} onChange={e => setEditRole(e.target.value)}
                            style={{ padding: '4px 8px', borderRadius: 6, border: '1.5px solid var(--border)', fontSize: 12 }}>
                            {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                          </select>
                          <button className="btn btn-success btn-sm" onClick={() => saveRole(u.id)}>✓</button>
                          <button className="btn btn-outline btn-sm" onClick={() => setEditId(null)}>✕</button>
                        </div>
                      ) : (
                        <RoleBadge role={u.role} />
                      )}
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                        {getPermissionSummary(u.role).map(p => (
                          <span key={p} style={{ background: '#f1f5f9', color: '#475569', padding: '2px 7px', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{p}</span>
                        ))}
                      </div>
                    </td>
                    <td style={{ color: 'var(--text-light)', fontSize: 12 }}>
                      {new Date(u.created_at).toLocaleDateString()}
                    </td>
                    <td>
                      {u.id !== currentUser?.id && (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-outline btn-sm"
                            onClick={() => { setEditId(u.id); setEditRole(u.role); }}>
                            ✏ Role
                          </button>
                          <button className="btn btn-danger btn-sm"
                            onClick={() => deleteUser(u.id, u.username)}>
                            🗑
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Role permissions legend */}
          <div style={{ padding: '16px 20px', borderTop: '1px solid var(--border)', background: '#fafbff' }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: .5, marginBottom: 12 }}>Role Permissions</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 10 }}>
              {ROLES.map(r => {
                const info = ROLE_LABELS[r];
                return (
                  <div key={r} style={{ background: info.bg, borderRadius: 8, padding: '10px 14px' }}>
                    <div style={{ fontWeight: 700, color: info.color, fontSize: 13, marginBottom: 6 }}>{info.label}</div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {getPermissionSummary(r).map(p => (
                        <span key={p} style={{ background: 'rgba(255,255,255,0.7)', color: info.color, padding: '2px 7px', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{p}</span>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Create user form */}
      {tab === 'create' && (
        <div className="form-card" style={{ maxWidth: 560 }}>
          <div className="form-section-title">➕ Create New User</div>
          {error && <div className="alert alert-error">⚠ {error}</div>}
          {ok    && <div className="alert alert-success">✓ {ok}</div>}
          <form onSubmit={createUser}>
            <div className="form-grid" style={{ marginBottom: 16 }}>
              <div className="form-group">
                <label>Username *</label>
                <input placeholder="Full name" value={form.username}
                  onChange={e => set('username', e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Email *</label>
                <input type="email" placeholder="user@example.com" value={form.email}
                  onChange={e => set('email', e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Password *</label>
                <input type="password" placeholder="Min. 8 characters" value={form.password}
                  onChange={e => set('password', e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Role *</label>
                <select value={form.role} onChange={e => set('role', e.target.value)}>
                  {ROLES.map(r => (
                    <option key={r} value={r}>{ROLE_LABELS[r].label} — {getPermissionSummary(r).join(', ')}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Role preview */}
            <div style={{ background: ROLE_LABELS[form.role]?.bg, borderRadius: 8, padding: '12px 16px', marginBottom: 16 }}>
              <div style={{ fontWeight: 700, color: ROLE_LABELS[form.role]?.color, fontSize: 13, marginBottom: 6 }}>
                {ROLE_LABELS[form.role]?.label} permissions:
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {getPermissionSummary(form.role).map(p => (
                  <span key={p} style={{ background: 'rgba(255,255,255,0.7)', color: ROLE_LABELS[form.role]?.color, padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 600 }}>✓ {p}</span>
                ))}
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {busy ? '⏳ Creating…' : '➕ Create User'}
              </button>
              <button type="button" className="btn btn-outline" onClick={() => setForm(BLANK_FORM)}>Clear</button>
            </div>
          </form>
        </div>
      )}

      {/* Activity log */}
      {tab === 'log' && (
        <div className="section-card">
          <div className="section-header">
            <h3>📋 Activity Log</h3>
            <span className="badge badge-blue">{log.length} entries</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Time</th><th>User</th><th>Role</th><th>Action</th><th>Details</th></tr>
              </thead>
              <tbody>
                {log.length === 0
                  ? <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: 32 }}>No activity recorded yet</td></tr>
                  : log.map(entry => {
                    const ai = ACTION_ICONS[entry.action] || { icon: '•', color: '#475569', bg: '#f1f5f9' };
                    return (
                      <tr key={entry.id}>
                        <td style={{ color: 'var(--text-light)', fontSize: 12, whiteSpace: 'nowrap' }}>
                          {new Date(entry.created_at).toLocaleString()}
                        </td>
                        <td style={{ fontWeight: 600 }}>{entry.username || '—'}</td>
                        <td>{entry.role ? <RoleBadge role={entry.role} /> : '—'}</td>
                        <td>
                          <span style={{ background: ai.bg, color: ai.color, padding: '3px 10px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                            {ai.icon} {entry.action.replace('_', ' ')}
                          </span>
                        </td>
                        <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>{entry.details}</td>
                      </tr>
                    );
                  })
                }
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

// Helper: human-readable permission list per role
function getPermissionSummary(role) {
  const map = {
    admin:      ['Dashboard', 'Emergency', 'Route', 'Hospitals', 'Road Scoring', 'User Mgmt'],
    dispatcher: ['Dashboard', 'Emergency', 'Route', 'Hospitals', 'Road Scoring'],
    driver:     ['Dashboard', 'Route'],
    user:       ['Dashboard', 'Route'],
  };
  return map[role] || [];
}
