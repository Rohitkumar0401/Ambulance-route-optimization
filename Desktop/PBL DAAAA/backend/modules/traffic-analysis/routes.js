const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, requireRole } = require('../user-authentication/middleware/auth');

// Any authenticated user can read traffic data and road conditions
router.get('/traffic/:roadId',          verifyToken, ctrl.getTrafficData);
router.get('/road-conditions/:roadId',  verifyToken, ctrl.getRoadConditions);

// Any authenticated user can report a roadblock (drivers, public, etc.)
router.post('/roadblock/report', verifyToken, ctrl.reportRoadblock);

// Only admins and dispatchers can update traffic conditions
router.post('/traffic/update', verifyToken, requireRole('admin', 'dispatcher'), ctrl.updateTrafficCondition);

module.exports = router;
