const API = 'http://localhost:4000';
let isLogin = true;

function toggleForm() {
    isLogin = !isLogin;
    document.getElementById('form-title').innerText = isLogin ? 'Welcome back' : 'Create account';
    document.getElementById('form-sub').innerText = isLogin ? 'Sign in to your account' : 'Register to get started';
    document.getElementById('btn-text').innerText = isLogin ? 'Sign In' : 'Register';
    document.getElementById('name-field').style.display = isLogin ? 'none' : 'block';
    document.getElementById('toggle-text').innerHTML = isLogin
        ? `Don't have an account? <span onclick="toggleForm()">Create one</span>`
        : `Already have an account? <span onclick="toggleForm()">Sign in</span>`;
    setMessage('', '');
}

function setMessage(msg, type) {
    const el = document.getElementById('message');
    el.innerText = msg;
    el.className = type;
}

function togglePassword() {
    const pw = document.getElementById('password');
    const icon = document.getElementById('eyeIcon');
    if (pw.type === 'password') {
        pw.type = 'text';
        icon.className = 'fas fa-eye-slash toggle-pw';
    } else {
        pw.type = 'password';
        icon.className = 'fas fa-eye toggle-pw';
    }
}

async function handleSubmit() {
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value.trim();
    const name = document.getElementById('name').value.trim();

    if (!email || !password) { setMessage('Please fill all fields', 'error'); return; }
    if (!isLogin && !name) { setMessage('Please enter your name', 'error'); return; }

    const btn = document.getElementById('submit-btn');
    btn.disabled = true;
    document.getElementById('btn-text').innerText = isLogin ? 'Signing in...' : 'Registering...';

    try {
        const endpoint = isLogin ? '/login' : '/register';
        const body = isLogin ? { email, password } : { name, email, password };

        const res = await fetch(API + endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await res.json();

        if (data.success) {
            setMessage(data.message, 'success');
            if (isLogin) setTimeout(() => window.location.href = '#dashboard', 1500);
        } else {
            setMessage(data.message, 'error');
        }
    } catch {
        setMessage('Cannot connect to server. Make sure backend is running.', 'error');
    }

    btn.disabled = false;
    document.getElementById('btn-text').innerText = isLogin ? 'Sign In' : 'Register';
}

async function socialLogin(provider) {
    setMessage(`Connecting to ${provider}...`, '');
    try {
        // In production this would use OAuth — for demo we simulate with a prompt
        const email = prompt(`Enter your ${provider} email:`);
        if (!email) { setMessage('', ''); return; }
        const name = email.split('@')[0];

        const res = await fetch(API + '/social-login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, provider })
        });
        const data = await res.json();
        setMessage(data.message, data.success ? 'success' : 'error');
    } catch {
        setMessage('Cannot connect to server. Make sure backend is running.', 'error');
    }
}
