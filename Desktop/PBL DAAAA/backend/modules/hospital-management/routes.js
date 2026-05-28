const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, requireRole } = require('../user-authentication/middleware/auth');

// Any authenticated user can view and search hospitals
router.get('/',        verifyToken, ctrl.getAllHospitals);
router.get('/search',  verifyToken, ctrl.searchHospital);
router.get('/nearest', verifyToken, ctrl.getNearestHospitals);

// Only admins can add hospitals
router.post('/add', verifyToken, requireRole('admin'), ctrl.addHospital);

module.exports = router;
