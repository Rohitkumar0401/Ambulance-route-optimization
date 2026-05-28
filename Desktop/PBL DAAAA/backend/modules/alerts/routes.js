const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, requireRole } = require('../user-authentication/middleware/auth');

// Any authenticated user can view active alerts and stats
router.get('/active', verifyToken, ctrl.getActiveAlerts);
router.get('/stats',  verifyToken, ctrl.getAlertStats);

// Admins and dispatchers can view full list and government report
router.get('/all',               verifyToken, requireRole('admin', 'dispatcher'), ctrl.getAllAlerts);
router.get('/government-report', verifyToken, requireRole('admin', 'dispatcher'), ctrl.generateGovernmentReport);

// Admins and dispatchers can create and acknowledge alerts
router.post('/create',           verifyToken, requireRole('admin', 'dispatcher'), ctrl.createAlert);
router.patch('/:id/acknowledge', verifyToken, requireRole('admin', 'dispatcher'), ctrl.acknowledgeAlert);

// Only admins can resolve alerts
router.patch('/:id/resolve', verifyToken, requireRole('admin'), ctrl.resolveAlert);

module.exports = router;
