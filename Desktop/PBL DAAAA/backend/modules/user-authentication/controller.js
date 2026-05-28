const bcrypt = require('bcrypt');
const jwt    = require('jsonwebtoken');
const db     = require('../../config/database');

// ── Register ──────────────────────────────────────────────────────────────────
exports.register = async (req, res) => {
  try {
    const { username, email, password, role } = req.body;

    if (!username || !email || !password)
      return res.status(400).json({ success: false, error: 'Username, email and password are required' });

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
      return res.status(400).json({ success: false, error: 'Invalid email format' });

    if (password.length < 8)
      return res.status(400).json({ success: false, error: 'Password must be at least 8 characters' });

    // Only admin can create admin/dispatcher accounts via API
    // Public registration always creates 'user' role
    const allowedRoles = ['user', 'driver'];
    const assignedRole = allowedRoles.includes(role) ? role : 'user';

    const [existing] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
    if (existing.length > 0)
      return res.status(409).json({ success: false, error: 'Email already registered' });

    const hash = await bcrypt.hash(password, 10);
    const [result] = await db.query(
      'INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)',
      [username, email, hash, assignedRole]
    );

    res.json({ success: true, userId: result.insertId, message: 'Account created successfully' });
  } catch (err) {
    console.error('Register error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── Login ─────────────────────────────────────────────────────────────────────
exports.login = async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password)
      return res.status(400).json({ success: false, error: 'Email and password are required' });

    const [rows] = await db.query('SELECT * FROM users WHERE email = ?', [email]);
    if (rows.length === 0)
      return res.status(401).json({ success: false, error: 'Invalid email or password' });

    const user = rows[0];
    const valid = await bcrypt.compare(password, user.password);
    if (!valid)
      return res.status(401).json({ success: false, error: 'Invalid email or password' });

    const token = jwt.sign(
      { userId: user.id, email: user.email, role: user.role, username: user.username },
      process.env.JWT_SECRET,
      { expiresIn: '24h' }
    );

    // Log the login
    await db.query(
      'INSERT INTO activity_log (user_id, action, details) VALUES (?, ?, ?)',
      [user.id, 'login', `User ${user.username} logged in`]
    ).catch(() => {}); // non-fatal

    res.json({
      success: true,
      token,
      user: { id: user.id, username: user.username, email: user.email, role: user.role }
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── Get current user ──────────────────────────────────────────────────────────
exports.getMe = async (req, res) => {
  try {
    const [rows] = await db.query(
      'SELECT id, username, email, role, created_at FROM users WHERE id = ?',
      [req.user.userId]
    );
    if (rows.length === 0)
      return res.status(404).json({ success: false, error: 'User not found' });
    res.json({ success: true, user: rows[0] });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── List all users (admin only) ───────────────────────────────────────────────
exports.getAllUsers = async (req, res) => {
  try {
    const [rows] = await db.query(
      'SELECT id, username, email, role, created_at FROM users ORDER BY created_at DESC'
    );
    res.json({ success: true, data: rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── Update user role (admin only) ─────────────────────────────────────────────
exports.updateUserRole = async (req, res) => {
  try {
    const { id } = req.params;
    const { role } = req.body;

    const validRoles = ['admin', 'dispatcher', 'driver', 'user'];
    if (!validRoles.includes(role))
      return res.status(400).json({ success: false, error: `Role must be one of: ${validRoles.join(', ')}` });

    // Prevent demoting yourself
    if (parseInt(id) === req.user.userId)
      return res.status(400).json({ success: false, error: 'Cannot change your own role' });

    const [existing] = await db.query('SELECT id FROM users WHERE id = ?', [id]);
    if (existing.length === 0)
      return res.status(404).json({ success: false, error: 'User not found' });

    await db.query('UPDATE users SET role = ? WHERE id = ?', [role, id]);

    await db.query(
      'INSERT INTO activity_log (user_id, action, details) VALUES (?, ?, ?)',
      [req.user.userId, 'role_change', `Changed user #${id} role to ${role}`]
    ).catch(() => {});

    res.json({ success: true, message: 'Role updated' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── Delete user (admin only) ──────────────────────────────────────────────────
exports.deleteUser = async (req, res) => {
  try {
    const { id } = req.params;

    if (parseInt(id) === req.user.userId)
      return res.status(400).json({ success: false, error: 'Cannot delete your own account' });

    const [existing] = await db.query('SELECT id, username FROM users WHERE id = ?', [id]);
    if (existing.length === 0)
      return res.status(404).json({ success: false, error: 'User not found' });

    await db.query('DELETE FROM users WHERE id = ?', [id]);

    await db.query(
      'INSERT INTO activity_log (user_id, action, details) VALUES (?, ?, ?)',
      [req.user.userId, 'delete_user', `Deleted user ${existing[0].username} (#${id})`]
    ).catch(() => {});

    res.json({ success: true, message: 'User deleted' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── Activity log (admin only) ─────────────────────────────────────────────────
exports.getActivityLog = async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT al.*, u.username, u.email, u.role
      FROM activity_log al
      LEFT JOIN users u ON al.user_id = u.id
      ORDER BY al.created_at DESC
      LIMIT 100
    `);
    res.json({ success: true, data: rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
};

// ── Admin create user (can assign any role) ───────────────────────────────────
exports.adminCreateUser = async (req, res) => {
  try {
    const { username, email, password, role } = req.body;

    if (!username || !email || !password)
      return res.status(400).json({ success: false, error: 'Username, email and password are required' });

    if (password.length < 8)
      return res.status(400).json({ success: false, error: 'Password must be at least 8 characters' });

    const validRoles = ['admin', 'dispatcher', 'driver', 'user'];
    const assignedRole = validRoles.includes(role) ? role : 'user';

    const [existing] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
    if (existing.length > 0)
      return res.status(409).json({ success: false, error: 'Email already registered' });

    const hash = await bcrypt.hash(password, 10);
    const [result] = await db.query(
      'INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)',
      [username, email, hash, assignedRole]
    );

    await db.query(
      'INSERT INTO activity_log (user_id, action, details) VALUES (?, ?, ?)',
      [req.user.userId, 'create_user', `Admin created user ${username} with role ${assignedRole}`]
    ).catch(() => {});

    res.json({ success: true, userId: result.insertId, message: 'User created' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
};
