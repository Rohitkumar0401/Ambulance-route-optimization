const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, requireRole } = require('../user-authentication/middleware/auth');

// Any authenticated user can calculate and request reroutes
router.post('/calculate', verifyToken, ctrl.calculateOptimalRoute);
router.post('/reroute',   verifyToken, ctrl.dynamicReroute);

// Only admins and dispatchers can view route history
router.get('/history', verifyToken, requireRole('admin', 'dispatcher'), ctrl.getRouteHistory);

module.exports = router;
