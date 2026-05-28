import React, { useState } from 'react';

const API = 'http://localhost:5001/api';

export default function Login({ onLogin }) {
  const [mode, setMode]   = useState('login'); // 'login' | 'register'
  const [form, setForm]   = useState({ username: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState('');
  const [ok, setOk]       = useState('');
  const [busy, setBusy]   = useState(false);

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const submit = async (e) => {
    e.preventDefault();
    setError(''); setOk(''); setBusy(true);

    if (mode === 'register') {
      if (form.password !== form.confirm) { setError('Passwords do not match'); setBusy(false); return; }
      if (form.password.length < 8)       { setError('Password must be at least 8 characters'); setBusy(false); return; }
    }

    try {
      const endpoint = mode === 'login' ? '/auth/login' : '/auth/register';
      const body     = mode === 'login'
        ? { email: form.email, password: form.password }
        : { username: form.username, email: form.email, password: form.password };

      const res  = await fetch(API + endpoint, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      const data = await res.json();

      if (data.success) {
        if (mode === 'login') {
          onLogin(data.user, data.token);
        } else {
          setOk('Account created! Please sign in.');
          setMode('login');
          setForm({ username: '', email: form.email, password: '', confirm: '' });
        }
      } else {
        setError(data.error || (mode === 'login' ? 'Invalid credentials' : 'Registration failed'));
      }
    } catch (_) {
      setError('Cannot connect to server. Make sure backend is running on port 5001.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-page">
      {/* Left panel */}
      <div className="login-left">
        <div style={{ fontSize: 48, marginBottom: 16 }}>🚑</div>
        <h1>Ambulance Route<br />Optimization System</h1>
        <p>AI-powered emergency routing for remote areas using Dijkstra's & A* algorithms with real-time road scoring.</p>
        <div className="login-features">
          {['Dijkstra & A* route optimization','Road scoring & threshold flagging','Real-time alert & government reporting','Emergency request priority queue','Hospital proximity search'].map(f => (
            <div key={f} className="login-feature">
              <div className="login-feature-dot" />
              {f}
            </div>
          ))}
        </div>
        <div style={{ marginTop: 48, fontSize: 12, opacity: 0.5 }}>Team Visitors · Rohit Kumar · Rahul Singh · Kabeer Kandari · Karan Singh</div>
      </div>

      {/* Right panel */}
      <div className="login-right">
        <div className="login-box">
          <div className="login-logo">🔐</div>
          <h2>{mode === 'login' ? 'Welcome back' : 'Create account'}</h2>
          <p className="sub">{mode === 'login' ? 'Sign in to your account' : 'Register a new account'}</p>

          <form className="login-form" onSubmit={submit}>
            {error && <div className="alert alert-error">⚠ {error}</div>}
            {ok    && <div className="alert alert-success">✓ {ok}</div>}

            {mode === 'register' && (
              <div className="form-group">
                <label>Username</label>
                <input type="text" placeholder="Your name" value={form.username}
                  onChange={e => set('username', e.target.value)} required />
              </div>
            )}

            <div className="form-group">
              <label>Email address</label>
              <input type="email" placeholder={mode === 'login' ? 'admin@ambulance.com' : 'you@example.com'}
                value={form.email} onChange={e => set('email', e.target.value)} required />
            </div>

            <div className="form-group">
              <label>Password</label>
              <input type="password" placeholder={mode === 'register' ? 'Min. 8 characters' : '••••••••'}
                value={form.password} onChange={e => set('password', e.target.value)} required />
            </div>

            {mode === 'register' && (
              <div className="form-group">
                <label>Confirm password</label>
                <input type="password" placeholder="Re-enter password"
                  value={form.confirm} onChange={e => set('confirm', e.target.value)} required />
              </div>
            )}

            <button type="submit" className="btn btn-primary btn-full btn-lg login-submit" disabled={busy}>
              {busy ? '⏳ Please wait…' : mode === 'login' ? 'Sign in →' : 'Create account →'}
            </button>
          </form>

          <div className="login-toggle">
            {mode === 'login' ? (
              <>Don't have an account? <button onClick={() => { setMode('register'); setError(''); setOk(''); }}>Register</button></>
            ) : (
              <>Already have an account? <button onClick={() => { setMode('login'); setError(''); setOk(''); }}>Sign in</button></>
            )}
          </div>

          {mode === 'login' && (
            <div className="login-hint">
              <p><strong>Demo credentials</strong></p>
              <div style={{display:'grid',gap:6,marginTop:6}}>
                {[
                  {role:'Admin',      email:'admin@ambulance.com',      pw:'admin123'},
                  {role:'Dispatcher', email:'dispatcher@ambulance.com', pw:'dispatch123'},
                  {role:'Driver',     email:'driver@ambulance.com',     pw:'driver123'},
                ].map(c => (
                  <div key={c.role} style={{background:'white',borderRadius:6,padding:'6px 10px',
                    border:'1px solid #e2e8f0',cursor:'pointer'}}
                    onClick={() => setForm(f=>({...f,email:c.email,password:c.pw}))}>
                    <span style={{fontWeight:700,fontSize:11,color:'#4f46e5'}}>{c.role}</span>
                    <span style={{color:'#64748b',fontSize:11,marginLeft:6}}>{c.email}</span>
                  </div>
                ))}
              </div>
              <p style={{marginTop:6,fontSize:11,color:'#94a3b8'}}>Click a row to auto-fill</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
