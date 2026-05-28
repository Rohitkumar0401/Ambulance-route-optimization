const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, requireRole } = require('../user-authentication/middleware/auth');

// Any authenticated user can create an emergency request
router.post('/create', verifyToken, ctrl.createEmergencyRequest);

// Drivers, dispatchers, and admins can fetch and act on requests
router.get('/next',           verifyToken, requireRole('admin', 'dispatcher', 'driver'), ctrl.getNextRequest);
router.post('/update-status', verifyToken, requireRole('admin', 'dispatcher', 'driver'), ctrl.updateRequestStatus);
router.put('/update-status',  verifyToken, requireRole('admin', 'dispatcher', 'driver'), ctrl.updateRequestStatus);

// Only admins and dispatchers can see all requests
router.get('/all', verifyToken, requireRole('admin', 'dispatcher'), ctrl.getAllRequests);

module.exports = router;
