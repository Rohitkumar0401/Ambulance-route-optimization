const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, requireRole } = require('../user-authentication/middleware/auth');

// Any authenticated user can view road scores and stats
router.get('/all',     verifyToken, ctrl.getAllRoadScores);
router.get('/flagged', verifyToken, ctrl.getFlaggedRoads);
router.get('/stats',   verifyToken, ctrl.getRoadStats);

// Only admins and dispatchers can score roads or run threshold checks
router.post('/score',           verifyToken, requireRole('admin', 'dispatcher'), ctrl.scoreRoad);
router.post('/threshold-check', verifyToken, requireRole('admin', 'dispatcher'), ctrl.thresholdCheck);

module.exports = router;
