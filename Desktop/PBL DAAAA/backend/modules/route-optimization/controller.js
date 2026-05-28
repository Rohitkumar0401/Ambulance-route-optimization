const dijkstraAlgorithm = require('./algorithms/dijkstra');
const aStarAlgorithm    = require('./algorithms/aStar');
const db                = require('../../config/database');

exports.calculateOptimalRoute = async (req, res) => {
  try {
    const { start, destination, algorithm = 'dijkstra', roadConditions = {} } = req.body;

    if (!start || !destination)
      return res.status(400).json({ success: false, error: 'Start and destination are required' });

    if (!['dijkstra', 'astar'].includes(algorithm))
      return res.status(400).json({ success: false, error: 'Invalid algorithm. Use "dijkstra" or "astar"' });

    if (start.latitude == null || start.longitude == null)
      return res.status(400).json({ success: false, error: 'Start must have latitude and longitude' });

    if (destination.latitude == null || destination.longitude == null)
      return res.status(400).json({ success: false, error: 'Destination must have latitude and longitude' });

    // Validate coordinate ranges
    if (start.latitude < -90 || start.latitude > 90 || destination.latitude < -90 || destination.latitude > 90)
      return res.status(400).json({ success: false, error: 'Latitude must be between -90 and 90' });

    if (start.longitude < -180 || start.longitude > 180 || destination.longitude < -180 || destination.longitude > 180)
      return res.status(400).json({ success: false, error: 'Longitude must be between -180 and 180' });

    // Fetch hospitals for graph building
    let hospitals = [];
    try {
      const [rows] = await db.query('SELECT * FROM hospitals');
      hospitals = rows;
    } catch (_) { /* non-fatal */ }

    // Fetch active road conditions from DB and merge with request
    let dbRoadConditions = {};
    try {
      const [scores] = await db.query('SELECT road_id, road_quality, terrain_difficulty, congestion_level, average_speed FROM road_scores');
      scores.forEach(s => {
        dbRoadConditions[s.road_id] = {
          roadQuality:       parseFloat(s.road_quality)       || 1.0,
          terrainDifficulty: parseFloat(s.terrain_difficulty) || 1.0,
          trafficFactor:     1 + (parseFloat(s.congestion_level) || 0)
        };
      });
    } catch (_) { /* non-fatal */ }

    const mergedConditions = { ...dbRoadConditions, ...roadConditions };

    const route = algorithm === 'astar'
      ? await aStarAlgorithm.findPath(start, destination, {}, mergedConditions, hospitals)
      : await dijkstraAlgorithm.findPath(start, destination, {}, mergedConditions, hospitals);

    // Persist route to DB
    try {
      await db.query(
        'INSERT INTO routes (start_location, end_location, path, distance, estimated_time, algorithm_used) VALUES (?, ?, ?, ?, ?, ?)',
        [JSON.stringify(start), JSON.stringify(destination), JSON.stringify(route.pathCoordinates), route.distance, route.estimatedTime, algorithm]
      );
    } catch (_) { /* non-fatal */ }

    res.json({ success: true, route });
  } catch (error) {
    console.error('Route calculation error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.dynamicReroute = async (req, res) => {
  try {
    const { currentLocation, destination, roadblocks = [] } = req.body;

    if (!currentLocation || !destination)
      return res.status(400).json({ success: false, error: 'Current location and destination are required' });

    // Build road conditions from roadblocks (mark as impassable)
    const roadConditions = {};
    roadblocks.forEach(rb => {
      if (rb.roadId) roadConditions[rb.roadId] = { roadQuality: 3.0, trafficFactor: 3.0 };
    });

    let hospitals = [];
    try {
      const [rows] = await db.query('SELECT * FROM hospitals');
      hospitals = rows;
    } catch (_) {}

    const route = await dijkstraAlgorithm.findPath(currentLocation, destination, {}, roadConditions, hospitals);
    res.json({ success: true, route });
  } catch (error) {
    console.error('Reroute error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.getRouteHistory = async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM routes ORDER BY created_at DESC LIMIT 20');
    res.json({ success: true, data: rows });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};
