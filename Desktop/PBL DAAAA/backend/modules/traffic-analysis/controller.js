const db = require('../../config/database');

exports.getTrafficData = async (req, res) => {
  try {
    const { roadId } = req.params;
    const [rows] = await db.query('SELECT * FROM traffic_data WHERE road_id = ?', [roadId]);
    res.json({ success: true, data: rows });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.updateTrafficCondition = async (req, res) => {
  try {
    const { roadId, congestionLevel, averageSpeed, timestamp } = req.body;
    
    // Input validation
    if (!roadId) {
      return res.status(400).json({ 
        success: false, 
        error: 'Road ID is required' 
      });
    }

    // Validate congestion level (0-1 range)
    if (congestionLevel !== undefined && (congestionLevel < 0 || congestionLevel > 1)) {
      return res.status(400).json({ 
        success: false, 
        error: 'Congestion level must be between 0 and 1' 
      });
    }

    // Validate average speed (must be positive)
    if (averageSpeed !== undefined && averageSpeed < 0) {
      return res.status(400).json({ 
        success: false, 
        error: 'Average speed must be a positive number' 
      });
    }

    await db.query(
      'INSERT INTO traffic_data (road_id, congestion_level, average_speed, timestamp) VALUES (?, ?, ?, ?)',
      [roadId, congestionLevel || null, averageSpeed || null, timestamp || new Date()]
    );
    res.json({ success: true, message: 'Traffic data updated' });
  } catch (error) {
    console.error('Traffic update error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.getRoadConditions = async (req, res) => {
  try {
    const { roadId } = req.params;
    const [rows] = await db.query('SELECT * FROM road_conditions WHERE road_id = ?', [roadId]);
    res.json({ success: true, data: rows });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.reportRoadblock = async (req, res) => {
  try {
    const { roadId, location, severity, description } = req.body;
    await db.query(
      'INSERT INTO roadblocks (road_id, location, severity, description, status) VALUES (?, ?, ?, ?, ?)',
      [roadId, location, severity, description, 'active']
    );
    res.json({ success: true, message: 'Roadblock reported' });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};
