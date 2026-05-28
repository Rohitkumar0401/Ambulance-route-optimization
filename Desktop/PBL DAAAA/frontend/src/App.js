import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, NavLink, Navigate } from 'react-router-dom';
import './App.css';
import Login            from './components/Login';
import Dashboard        from './components/Dashboard';
import EmergencyRequest from './components/EmergencyRequest';
import RouteOptimization from './components/RouteOptimization';
import HospitalManagement from './components/HospitalManagement';
import RoadScoring      from './components/RoadScoring';
import UserManagement   from './components/UserManagement';

// ── Role permission map ───────────────────────────────────────────────────────
// What each role can access
export const PERMISSIONS = {
  admin:      ['dashboard', 'emergency', 'route', 'hospitals', 'road-scoring', 'users'],
  dispatcher: ['dashboard', 'emergency', 'route', 'hospitals', 'road-scoring'],
  driver:     ['dashboard', 'emergency', 'route', 'hospitals'],
  user:       ['dashboard', 'emergency', 'route', 'hospitals'],
};

export const ROLE_LABELS = {
  admin:      { label: 'Admin',      color: '#dc2626', bg: '#fee2e2' },
  dispatcher: { label: 'Dispatcher', color: '#d97706', bg: '#fef3c7' },
  driver:     { label: 'Driver',     color: '#2563eb', bg: '#dbeafe' },
  user:       { label: 'User',       color: '#059669', bg: '#d1fae5' },
};

// ── Nav items (shown based on role) ──────────────────────────────────────────
const NAV_ITEMS = [
  { to: '/',             perm: 'dashboard',    icon: '▦',  label: 'Dashboard'    },
  { to: '/emergency',    perm: 'emergency',    icon: '🚨', label: 'Emergency'    },
  { to: '/route',        perm: 'route',        icon: '🗺', label: 'Route'        },
  { to: '/hospitals',    perm: 'hospitals',    icon: '🏥', label: 'Hospitals'    },
  { to: '/road-scoring', perm: 'road-scoring', icon: '🚧', label: 'Road Scoring' },
  { to: '/users',        perm: 'users',        icon: '👥', label: 'Users'        },
];

// ── Protected route wrapper ───────────────────────────────────────────────────
function Protected({ user, perm, children }) {
  const allowed = PERMISSIONS[user?.role] || [];
  if (!allowed.includes(perm)) {
    return (
      <div style={{ textAlign: 'center', padding: '80px 24px' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>🔒</div>
        <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0f172a', marginBottom: 8 }}>Access Denied</h2>
        <p style={{ color: '#64748b', fontSize: 14 }}>
          Your role <strong>{user?.role}</strong> does not have permission to view this page.
        </p>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 8 }}>
          Contact an administrator to request access.
        </p>
      </div>
    );
  }
  return children;
}

// ── Navbar ────────────────────────────────────────────────────────────────────
function Navbar({ user, onLogout }) {
  const allowed = PERMISSIONS[user?.role] || [];
  const roleInfo = ROLE_LABELS[user?.role] || ROLE_LABELS.user;

  return (
    <nav className="navbar">
      <div className="nav-inner">
        <a href="/" className="nav-brand">
          <span className="nav-brand-icon">🚑</span>
          <div className="nav-brand-text">
            <div className="nav-brand-title">Ambulance Route Optimizer</div>
            <div className="nav-brand-sub">Team Visitors · v2.0</div>
          </div>
        </a>

        <div className="nav-links">
          {NAV_ITEMS.filter(n => allowed.includes(n.perm)).map(n => (
            <NavLink key={n.to} to={n.to} end={n.to === '/'}
              className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
              <span>{n.icon}</span>{n.label}
            </NavLink>
          ))}
        </div>

        <div className="nav-right">
          <div className="nav-user">
            <div className="nav-avatar">{user?.username?.[0]?.toUpperCase() || 'A'}</div>
            <div>
              <div className="nav-username">{user?.username || 'User'}</div>
              <div style={{
                fontSize: 10, fontWeight: 700, color: roleInfo.color,
                background: roleInfo.bg, padding: '1px 6px', borderRadius: 4,
                display: 'inline-block', marginTop: 1
              }}>{roleInfo.label}</div>
            </div>
          </div>
          <button className="nav-logout" onClick={onLogout}>Sign out</button>
        </div>
      </div>
    </nav>
  );
}

// ── App ───────────────────────────────────────────────────────────────────────
export default function App() {
  const [auth, setAuth] = useState(false);
  const [user, setUser] = useState(null);

  useEffect(() => {
    const t = localStorage.getItem('token');
    const u = localStorage.getItem('user');
    if (t && u) {
      try { setAuth(true); setUser(JSON.parse(u)); } catch (_) {}
    }
  }, []);

  const login = (userData, token) => {
    setAuth(true); setUser(userData);
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(userData));
  };

  const logout = () => {
    setAuth(false); setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  };

  if (!auth) {
    return (
      <Router>
        <Routes>
          <Route path="/login" element={<Login onLogin={login} />} />
          <Route path="*"      element={<Navigate to="/login" replace />} />
        </Routes>
      </Router>
    );
  }

  return (
    <Router>
      <Navbar user={user} onLogout={logout} />
      <div className="app-body">
        <div className="page">
          <Routes>
            <Route path="/" element={
              <Protected user={user} perm="dashboard"><Dashboard user={user} /></Protected>
            } />
            <Route path="/emergency" element={
              <Protected user={user} perm="emergency"><EmergencyRequest user={user} /></Protected>
            } />
            <Route path="/route" element={
              <Protected user={user} perm="route"><RouteOptimization /></Protected>
            } />
            <Route path="/hospitals" element={
              <Protected user={user} perm="hospitals"><HospitalManagement user={user} /></Protected>
            } />
            <Route path="/road-scoring" element={
              <Protected user={user} perm="road-scoring"><RoadScoring user={user} /></Protected>
            } />
            <Route path="/users" element={
              <Protected user={user} perm="users"><UserManagement currentUser={user} /></Protected>
            } />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}
