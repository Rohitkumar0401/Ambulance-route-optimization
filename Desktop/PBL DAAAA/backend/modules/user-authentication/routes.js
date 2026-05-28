const express = require('express');
const router  = express.Router();
const ctrl    = require('./controller');
const { verifyToken, adminOnly } = require('./middleware/auth');

// Public
router.post('/register', ctrl.register);
router.post('/login',    ctrl.login);

// Authenticated
router.get('/me', verifyToken, ctrl.getMe);

// Admin only
router.get('/users',              verifyToken, adminOnly, ctrl.getAllUsers);
router.post('/users',             verifyToken, adminOnly, ctrl.adminCreateUser);
router.patch('/users/:id/role',   verifyToken, adminOnly, ctrl.updateUserRole);
router.delete('/users/:id',       verifyToken, adminOnly, ctrl.deleteUser);
router.get('/activity-log',       verifyToken, adminOnly, ctrl.getActivityLog);

module.exports = router;
