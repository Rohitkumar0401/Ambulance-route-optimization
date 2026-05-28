const jwt = require('jsonwebtoken');

// Verify JWT token
exports.verifyToken = (req, res, next) => {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : authHeader;

  if (!token)
    return res.status(401).json({ success: false, error: 'Authentication required' });

  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET);
    next();
  } catch (err) {
    const msg = err.name === 'TokenExpiredError' ? 'Session expired, please login again' : 'Invalid token';
    res.status(401).json({ success: false, error: msg });
  }
};

// Role-based access control
exports.requireRole = (...roles) => (req, res, next) => {
  if (!req.user)
    return res.status(401).json({ success: false, error: 'Authentication required' });
  if (!roles.includes(req.user.role))
    return res.status(403).json({ success: false, error: `Access denied. Required role: ${roles.join(' or ')}` });
  next();
};

// Shorthand guards
exports.adminOnly      = exports.requireRole('admin');
exports.adminOrDispatch = exports.requireRole('admin', 'dispatcher');
