/**
 * Alert & Government Reporting Module
 * - Active alerts for road conditions, emergencies
 * - Government report generation
 * - Alert acknowledgement and resolution
 */
const db = require('../../config/database');

// GET /api/alerts/active
exports.getActiveAlerts = async (req, res) => {
  try {
    const [alerts] = await db.query(`
      SELECT * FROM alerts 
      WHERE status = 'active'
      ORDER BY 
        CASE severity WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END,
        created_at DESC
    `);
    res.json({ success: true, data: alerts, count: alerts.length });
  } catch (error) {
    console.error('Get alerts error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

// GET /api/alerts/all
exports.getAllAlerts = async (req, res) => {
  try {
    const { status, severity, limit = 50 } = req.query;
    let query = 'SELECT * FROM alerts WHERE 1=1';
    const params = [];

    if (status) { query += ' AND status = ?'; params.push(status); }
    if (severity) { query += ' AND severity = ?'; params.push(severity); }
    query += ' ORDER BY created_at DESC LIMIT ?';
    params.push(parseInt(limit));

    const [alerts] = await db.query(query, params);
    res.json({ success: true, data: alerts });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

// POST /api/alerts/create
exports.createAlert = async (req, res) => {
  try {
    const { roadId, roadName, alertType, severity, message, latitude, longitude } = req.body;

    const validTypes = ['road_condition', 'traffic', 'emergency', 'weather', 'infrastructure'];
    const validSeverities = ['low', 'medium', 'high', 'critical'];

    if (!alertType || !validTypes.includes(alertType))
      return res.status(400).json({ success: false, error: `alertType must be one of: ${validTypes.join(', ')}` });
    if (!severity || !validSeverities.includes(severity))
      return res.status(400).json({ success: false, error: `severity must be one of: ${validSeverities.join(', ')}` });
    if (!message)
      return res.status(400).json({ success: false, error: 'message is required' });

    const [result] = await db.query(`
      INSERT INTO alerts (road_id, road_name, alert_type, severity, message, latitude, longitude, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, 'active')
    `, [roadId || null, roadName || null, alertType, severity, message, latitude || null, longitude || null]);

    res.json({ success: true, alertId: result.insertId, message: 'Alert created' });
  } catch (error) {
    console.error('Create alert error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

// PATCH /api/alerts/:id/acknowledge
exports.acknowledgeAlert = async (req, res) => {
  try {
    const { id } = req.params;
    const { acknowledgedBy } = req.body;

    const [existing] = await db.query('SELECT id FROM alerts WHERE id = ?', [id]);
    if (existing.length === 0) return res.status(404).json({ success: false, error: 'Alert not found' });

    await db.query(
      'UPDATE alerts SET status = ?, acknowledged_by = ?, acknowledged_at = NOW(), updated_at = NOW() WHERE id = ?',
      ['acknowledged', acknowledgedBy || 'system', id]
    );
    res.json({ success: true, message: 'Alert acknowledged' });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

// PATCH /api/alerts/:id/resolve
exports.resolveAlert = async (req, res) => {
  try {
    const { id } = req.params;
    const [existing] = await db.query('SELECT id FROM alerts WHERE id = ?', [id]);
    if (existing.length === 0) return res.status(404).json({ success: false, error: 'Alert not found' });

    await db.query(
      'UPDATE alerts SET status = ?, resolved_at = NOW(), updated_at = NOW() WHERE id = ?',
      ['resolved', id]
    );
    res.json({ success: true, message: 'Alert resolved' });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

// GET /api/alerts/government-report
exports.generateGovernmentReport = async (req, res) => {
  try {
    const { from, to } = req.query;
    const fromDate = from || new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const toDate   = to   || new Date().toISOString().split('T')[0];

    const [alertSummary] = await db.query(`
      SELECT 
        alert_type,
        severity,
        COUNT(*) as count,
        SUM(status = 'resolved') as resolved,
        SUM(status = 'active') as active
      FROM alerts
      WHERE DATE(created_at) BETWEEN ? AND ?
      GROUP BY alert_type, severity
      ORDER BY CASE severity WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END
    `, [fromDate, toDate]);

    const [criticalRoads] = await db.query(`
      SELECT road_id, road_name, composite_score, flag_status, updated_at
      FROM road_scores
      WHERE flag_status IN ('warning', 'critical')
      ORDER BY composite_score DESC
      LIMIT 20
    `);

    const [emergencySummary] = await db.query(`
      SELECT 
        severity,
        COUNT(*) as total,
        SUM(status = 'completed') as completed,
        SUM(status = 'pending') as pending
      FROM emergency_requests
      WHERE DATE(created_at) BETWEEN ? AND ?
      GROUP BY severity
    `, [fromDate, toDate]);

    const [routeStats] = await db.query(`
      SELECT COUNT(*) as totalRoutes, AVG(distance) as avgDistance, AVG(estimated_time) as avgTime
      FROM routes
      WHERE DATE(created_at) BETWEEN ? AND ?
    `, [fromDate, toDate]);

    const report = {
      generatedAt: new Date().toISOString(),
      period: { from: fromDate, to: toDate },
      title: 'Ambulance Route Optimization - Government Report',
      team: 'Team Visitors',
      summary: {
        totalAlerts:      alertSummary.reduce((s, r) => s + r.count, 0),
        criticalAlerts:   alertSummary.filter(r => r.severity === 'critical').reduce((s, r) => s + r.count, 0),
        flaggedRoads:     criticalRoads.length,
        totalEmergencies: emergencySummary.reduce((s, r) => s + r.total, 0),
        totalRoutes:      routeStats[0]?.totalRoutes || 0
      },
      alertBreakdown:    alertSummary,
      criticalRoads,
      emergencyBreakdown: emergencySummary,
      routeStatistics:   routeStats[0] || {},
      recommendations: generateRecommendations(criticalRoads, alertSummary)
    };

    res.json({ success: true, report });
  } catch (error) {
    console.error('Government report error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

function generateRecommendations(criticalRoads, alertSummary) {
  const recs = [];
  if (criticalRoads.filter(r => r.flag_status === 'critical').length > 0)
    recs.push('Immediate road repair required on critically flagged roads');
  if (alertSummary.filter(r => r.alert_type === 'traffic' && r.severity === 'critical').length > 0)
    recs.push('Traffic management intervention needed at high-congestion zones');
  if (criticalRoads.length > 5)
    recs.push('Infrastructure investment required — multiple roads below acceptable threshold');
  recs.push('Regular road condition monitoring recommended every 48 hours');
  recs.push('Coordinate with local authorities for emergency route clearance protocols');
  return recs;
}

// GET /api/alerts/stats
exports.getAlertStats = async (req, res) => {
  try {
    const [stats] = await db.query(`
      SELECT
        COUNT(*) as total,
        SUM(status = 'active') as active,
        SUM(status = 'acknowledged') as acknowledged,
        SUM(status = 'resolved') as resolved,
        SUM(severity = 'critical') as critical,
        SUM(severity = 'high') as high
      FROM alerts
    `);
    res.json({ success: true, data: stats[0] });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};
