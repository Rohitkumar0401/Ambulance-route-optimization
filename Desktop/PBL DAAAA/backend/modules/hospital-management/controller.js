const db = require('../../config/database');
const HospitalSearch = require('./models/HospitalSearch');

exports.getAllHospitals = async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM hospitals ORDER BY name');
    res.json({ success: true, data: rows });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.searchHospital = async (req, res) => {
  try {
    const { name } = req.query;
    const [hospitals] = await db.query('SELECT * FROM hospitals ORDER BY name');
    const result = HospitalSearch.binarySearch(hospitals, name);
    
    if (result) {
      res.json({ success: true, data: result });
    } else {
      res.status(404).json({ success: false, message: 'Hospital not found' });
    }
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.getNearestHospitals = async (req, res) => {
  try {
    const { latitude, longitude, count = 5 } = req.query;
    const [hospitals] = await db.query('SELECT * FROM hospitals');
    
    const nearest = HospitalSearch.findNearestHospitals(
      { latitude: parseFloat(latitude), longitude: parseFloat(longitude) },
      hospitals,
      parseInt(count)
    );
    
    res.json({ success: true, data: nearest });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.addHospital = async (req, res) => {
  try {
    const { name, address, latitude, longitude, contact, facilities } = req.body;
    
    // Input validation
    if (!name || !address || !latitude || !longitude) {
      return res.status(400).json({ 
        success: false, 
        error: 'Name, address, latitude, and longitude are required' 
      });
    }

    // Validate coordinates
    if (latitude < -90 || latitude > 90) {
      return res.status(400).json({ 
        success: false, 
        error: 'Invalid latitude. Must be between -90 and 90' 
      });
    }

    if (longitude < -180 || longitude > 180) {
      return res.status(400).json({ 
        success: false, 
        error: 'Invalid longitude. Must be between -180 and 180' 
      });
    }

    // Check for duplicate hospital
    const [existing] = await db.query(
      'SELECT id FROM hospitals WHERE name = ? AND latitude = ? AND longitude = ?',
      [name, latitude, longitude]
    );

    if (existing.length > 0) {
      return res.status(409).json({ 
        success: false, 
        error: 'Hospital with same name and location already exists' 
      });
    }

    const [result] = await db.query(
      'INSERT INTO hospitals (name, address, latitude, longitude, contact, facilities) VALUES (?, ?, ?, ?, ?, ?)',
      [name, address, latitude, longitude, contact || null, JSON.stringify(facilities || [])]
    );
    res.json({ success: true, hospitalId: result.insertId });
  } catch (error) {
    console.error('Hospital addition error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};
