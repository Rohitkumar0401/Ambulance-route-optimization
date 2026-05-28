/**
 * Road Scoring & Flagging Module
 * - Scores roads based on quality, terrain, traffic
 * - Flags roads that exceed thresholds
 * - Threshold Check System
 */
const db = require('../../config/database');

// Thresholds for flagging
const THRESHOLDS = {
  roadQuality:       { warn: 1.5, critical: 2.0 },  // multiplier (higher = worse)
  congestionLevel:   { warn: 0.6, critical: 0.85 },
  averageSpeed:      { warn: 20,  critical: 10 },    // km/h (lower = worse)
  incidentCount:     { warn: 3,   critical: 6 },
  compositeScore:    { warn: 60,  critical: 80 }     // 0-100 (higher = worse)
};

// Calculate composite road score (0-100, higher = worse condition)
function calculateRoadScore(data) {
  const {
    roadQuality      = 1.0,   // 1.0 = perfect, 2.0 = very bad
    terrainDifficulty = 1.0,
    congestionLevel  = 0.0,   // 0-1
    averageSpeed     = 60,    // km/h
    incidentCount    = 0,
    weatherFactor    = 1.0
  } = data;

  // Normalize each factor to 0-100
  const qualityScore    = Math.min(((roadQuality - 1.0) / 1.5) * 100, 100);
  const terrainScore    = Math.min(((terrainDifficulty - 1.0) / 1.5) * 100, 100);
  const congestionScore = congestionLevel * 100;
  const speedScore      = Math.max(0, ((60 - averageSpeed) / 60) * 100);
  const incidentScore   = Math.min((incidentCount / 10) * 100, 100);
  const weatherScore    = Math.min(((weatherFactor - 1.0) / 1.0) * 100, 100);

  // Weighted composite
  const composite = (
    qualityScore    * 0.30 +
    terrainScore    * 0.15 +
    congestionScore * 0.25 +
    speedScore      * 0.15 +
    incidentScore   * 0.10 +
    weatherScore    * 0.05
  );

  return Math.round(composite);
}

function getFlagStatus(score, data) {
  if (score >= THRESHOLDS.compositeScore.critical ||
      (data.congestionLevel || 0) >= THRESHOLDS.congestionLevel.critical ||
      (data.averageSpeed || 60) <= THRESHOLDS.averageSpeed.critical) {
    return 'critical';
  }
  if (score >= THRESHOLDS.compositeScore.warn ||
      (data.congestionLevel || 0) >= THRESHOLDS.congestionLevel.warn ||
      (data.averageSpeed || 60) <= THRESHOLDS.averageSpeed.warn) {
    return 'warning';
  }
  return 'good';
}

// GET /api/road-scoring/all
exports.getAllRoadScores = async (req, res) => {
  try {
    const [roads] = await db.query(`
      SELECT rs.*, 
             td.congestion_level, td.average_speed,
             rc.condition_type
      FROM road_scores rs
      LEFT JOIN (
        SELECT road_id, AVG(congestion_level) as congestion_level, AVG(average_speed) as average_speed
        FROM traffic_data
        WHERE timestamp > DATE_SUB(NOW(), INTERVAL 1 HOUR)
        GROUP BY road_id
      ) td ON rs.road_id = td.road_id
      LEFT JOIN road_conditions rc ON rs.road_id = rc.road_id
      ORDER BY rs.composite_score DESC
    `);
    res.json({ success: true, data: roads, thresholds: THRESHOLDS });
  } catch (error) {
    console.error('Get road scores error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

// GET /api/road-scoring/flagged
exports.getFlaggedRoads = async (req, res) => {
  try {
    const [roads] = await db.query(`
      SELECT * FROM road_scores 
      WHERE flag_status IN ('warning', 'critical')
      ORDER BY composite_score DESC, updated_at DESC
    `);
    res.json({ success: true, data: roads, count: roads.length });
  } catch (error) {
    console.error('Get flagged roads error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

// POST /api/road-scoring/score
exports.scoreRoad = async (req, res) => {
  try {
    const {
      roadId, roadName, latitude, longitude,
      roadQuality, terrainDifficulty, congestionLevel,
      averageSpeed, incidentCount, weatherFactor, reportedBy
    } = req.body;

    if (!roadId || !roadName) {
      return res.status(400).json({ success: false, error: 'roadId and roadName are required' });
    }

    const scoreData = { roadQuality, terrainDifficulty, congestionLevel, averageSpeed, incidentCount, weatherFactor };
    const compositeScore = calculateRoadScore(scoreData);
    const flagStatus = getFlagStatus(compositeScore, scoreData);

    // Upsert road score
    await db.query(`
      INSERT INTO road_scores 
        (road_id, road_name, latitude, longitude, road_quality, terrain_difficulty,
         congestion_level, average_speed, incident_count, weather_factor,
         composite_score, flag_status, reported_by, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
      ON DUPLICATE KEY UPDATE
        road_name=VALUES(road_name), latitude=VALUES(latitude), longitude=VALUES(longitude),
        road_quality=VALUES(road_quality), terrain_difficulty=VALUES(terrain_difficulty),
        congestion_level=VALUES(congestion_level), average_speed=VALUES(average_speed),
        incident_count=VALUES(incident_count), weather_factor=VALUES(weather_factor),
        composite_score=VALUES(composite_score), flag_status=VALUES(flag_status),
        reported_by=VALUES(reported_by), updated_at=NOW()
    `, [
      roadId, roadName, latitude || null, longitude || null,
      roadQuality || 1.0, terrainDifficulty || 1.0,
      congestionLevel || 0.0, averageSpeed || 60,
      incidentCount || 0, weatherFactor || 1.0,
      compositeScore, flagStatus, reportedBy || 'system'
    ]);

    // Auto-create alert if critical
    if (flagStatus === 'critical') {
      await db.query(`
        INSERT INTO alerts (road_id, road_name, alert_type, severity, message, status)
        VALUES (?, ?, 'road_condition', 'critical', ?, 'active')
        ON DUPLICATE KEY UPDATE status='active', updated_at=NOW()
      `, [roadId, roadName, `Critical road condition detected on ${roadName}. Score: ${compositeScore}/100`]);
    }

    res.json({
      success: true,
      roadId,
      compositeScore,
      flagStatus,
      breakdown: {
        qualityScore:    Math.round(((roadQuality || 1.0) - 1.0) / 1.5 * 100),
        congestionScore: Math.round((congestionLevel || 0) * 100),
        speedScore:      Math.round(Math.max(0, (60 - (averageSpeed || 60)) / 60 * 100))
      }
    });
  } catch (error) {
    console.error('Score road error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

// POST /api/road-scoring/threshold-check
exports.thresholdCheck = async (req, res) => {
  try {
    const { roadId } = req.body;
    if (!roadId) return res.status(400).json({ success: false, error: 'roadId is required' });

    const [rows] = await db.query('SELECT * FROM road_scores WHERE road_id = ?', [roadId]);
    if (rows.length === 0) return res.status(404).json({ success: false, error: 'Road not found' });

    const road = rows[0];
    const violations = [];

    if ((road.road_quality || 1.0) >= THRESHOLDS.roadQuality.critical)
      violations.push({ field: 'roadQuality', value: road.road_quality, threshold: THRESHOLDS.roadQuality.critical, level: 'critical' });
    else if ((road.road_quality || 1.0) >= THRESHOLDS.roadQuality.warn)
      violations.push({ field: 'roadQuality', value: road.road_quality, threshold: THRESHOLDS.roadQuality.warn, level: 'warning' });

    if ((road.congestion_level || 0) >= THRESHOLDS.congestionLevel.critical)
      violations.push({ field: 'congestionLevel', value: road.congestion_level, threshold: THRESHOLDS.congestionLevel.critical, level: 'critical' });
    else if ((road.congestion_level || 0) >= THRESHOLDS.congestionLevel.warn)
      violations.push({ field: 'congestionLevel', value: road.congestion_level, threshold: THRESHOLDS.congestionLevel.warn, level: 'warning' });

    if ((road.average_speed || 60) <= THRESHOLDS.averageSpeed.critical)
      violations.push({ field: 'averageSpeed', value: road.average_speed, threshold: THRESHOLDS.averageSpeed.critical, level: 'critical' });
    else if ((road.average_speed || 60) <= THRESHOLDS.averageSpeed.warn)
      violations.push({ field: 'averageSpeed', value: road.average_speed, threshold: THRESHOLDS.averageSpeed.warn, level: 'warning' });

    res.json({
      success: true,
      roadId,
      compositeScore: road.composite_score,
      flagStatus: road.flag_status,
      violations,
      thresholds: THRESHOLDS,
      requiresAction: violations.some(v => v.level === 'critical')
    });
  } catch (error) {
    console.error('Threshold check error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

// GET /api/road-scoring/stats
exports.getRoadStats = async (req, res) => {
  try {
    const [stats] = await db.query(`
      SELECT 
        COUNT(*) as total,
        SUM(flag_status = 'good') as good,
        SUM(flag_status = 'warning') as warning,
        SUM(flag_status = 'critical') as critical,
        AVG(composite_score) as avgScore,
        MAX(composite_score) as maxScore
      FROM road_scores
    `);
    res.json({ success: true, data: stats[0] });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};
