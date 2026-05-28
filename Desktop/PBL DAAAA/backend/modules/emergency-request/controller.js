const db = require('../../config/database');
const RequestQueue = require('./models/RequestQueue');

exports.createEmergencyRequest = async (req, res) => {
  try {
    const { patientName, location, severity, contact, description } = req.body;
    
    // Input validation
    if (!location || !severity || !contact) {
      return res.status(400).json({ 
        success: false, 
        error: 'Location, severity, and contact are required fields' 
      });
    }

    // Validate severity level
    const validSeverities = ['critical', 'high', 'medium', 'low'];
    if (!validSeverities.includes(severity)) {
      return res.status(400).json({ 
        success: false, 
        error: 'Invalid severity level. Must be: critical, high, medium, or low' 
      });
    }

    // Validate contact format (basic phone validation)
    const phoneRegex = /^[0-9+\-\s()]{10,20}$/;
    if (!phoneRegex.test(contact)) {
      return res.status(400).json({ 
        success: false, 
        error: 'Invalid contact number format' 
      });
    }
    
    const [result] = await db.query(
      'INSERT INTO emergency_requests (patient_name, location, severity, contact, description, status) VALUES (?, ?, ?, ?, ?, ?)',
      [patientName || 'Unknown', JSON.stringify(location), severity, contact, description || '', 'pending']
    );
    
    const requestId = result.insertId;
    RequestQueue.enqueue({ id: requestId, severity, timestamp: Date.now() });
    
    res.json({ success: true, requestId, message: 'Emergency request created' });
  } catch (error) {
    console.error('Emergency request creation error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.getNextRequest = async (req, res) => {
  try {
    const nextRequest = RequestQueue.dequeue();
    
    if (!nextRequest) {
      return res.json({ success: true, message: 'No pending requests' });
    }
    
    const [rows] = await db.query('SELECT * FROM emergency_requests WHERE id = ?', [nextRequest.id]);
    res.json({ success: true, data: rows[0] });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.updateRequestStatus = async (req, res) => {
  try {
    const { requestId, status, ambulanceId } = req.body;
    
    // Input validation
    if (!requestId || !status) {
      return res.status(400).json({ 
        success: false, 
        error: 'Request ID and status are required' 
      });
    }

    // Validate status
    const validStatuses = ['pending', 'assigned', 'in_progress', 'completed', 'cancelled'];
    if (!validStatuses.includes(status)) {
      return res.status(400).json({ 
        success: false, 
        error: 'Invalid status. Must be: pending, assigned, in_progress, completed, or cancelled' 
      });
    }

    // Check if request exists
    const [existing] = await db.query('SELECT id FROM emergency_requests WHERE id = ?', [requestId]);
    if (existing.length === 0) {
      return res.status(404).json({ 
        success: false, 
        error: 'Emergency request not found' 
      });
    }
    
    await db.query(
      'UPDATE emergency_requests SET status = ?, ambulance_id = ?, updated_at = NOW() WHERE id = ?',
      [status, ambulanceId || null, requestId]
    );
    
    res.json({ success: true, message: 'Request status updated' });
  } catch (error) {
    console.error('Status update error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
};

exports.getAllRequests = async (req, res) => {
  try {
    const { status } = req.query;
    let query = 'SELECT * FROM emergency_requests';
    const params = [];
    
    if (status) {
      query += ' WHERE status = ?';
      params.push(status);
    }
    
    query += ' ORDER BY created_at DESC';
    const [rows] = await db.query(query, params);
    
    res.json({ success: true, data: rows });
  } catch (error) {
    res.status(500).json({ success: false, error: error.message });
  }
};
