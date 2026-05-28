const express = require('express');
const cors    = require('cors');
require('dotenv').config();

const app  = express();
const PORT = process.env.PORT || 5001;

const { errorHandler, notFoundHandler } = require('./utils/errorHandler');

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

app.use((req, _res, next) => {
  console.log(`${new Date().toISOString()} [${req.method}] ${req.path}`);
  next();
});

// Route modules
const authRoutes              = require('./modules/user-authentication/routes');
const routeOptimizationRoutes = require('./modules/route-optimization/routes');
const trafficAnalysisRoutes   = require('./modules/traffic-analysis/routes');
const hospitalManagementRoutes = require('./modules/hospital-management/routes');
const emergencyRequestRoutes  = require('./modules/emergency-request/routes');
const roadScoringRoutes       = require('./modules/road-scoring/routes');
const alertRoutes             = require('./modules/alerts/routes');

// Public endpoints
app.get('/health', (_req, res) => res.json({ status: 'ok', timestamp: new Date().toISOString() }));
app.get('/',       (_req, res) => res.json({ message: 'Ambulance Route Optimization API v2.0', team: 'Team Visitors' }));

// Auth routes (public register/login + protected admin routes)
app.use('/api/auth', authRoutes);

// Routes — auth and role checks are applied per-route in each module
app.use('/api/route-optimization', routeOptimizationRoutes);
app.use('/api/traffic-analysis',   trafficAnalysisRoutes);
app.use('/api/hospitals',          hospitalManagementRoutes);
app.use('/api/emergency',          emergencyRequestRoutes);
app.use('/api/road-scoring',       roadScoringRoutes);
app.use('/api/alerts',             alertRoutes);

app.use(notFoundHandler);
app.use(errorHandler);

process.on('SIGTERM', () => server.close(() => console.log('Server closed')));

const server = app.listen(PORT, () => {
  console.log(`🚑 Server running on port ${PORT}`);
});
