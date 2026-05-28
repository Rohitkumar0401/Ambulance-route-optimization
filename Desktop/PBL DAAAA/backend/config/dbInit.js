const mysql = require('mysql2/promise');
const fs    = require('fs');
const path  = require('path');
require('dotenv').config();

async function initializeDatabase() {
  let connection;
  try {
    connection = await mysql.createConnection({
      host:     process.env.DB_HOST     || 'localhost',
      user:     process.env.DB_USER     || 'root',
      password: process.env.DB_PASSWORD || '',
      multipleStatements: true
    });

    console.log('✅ Connected to MySQL server');

    const dbName = process.env.DB_NAME || 'ambulance_optimization';
    await connection.query(`CREATE DATABASE IF NOT EXISTS \`${dbName}\``);
    await connection.query(`USE \`${dbName}\``);
    console.log(`✅ Database '${dbName}' ready`);

    // Execute schema statement by statement
    const schemaPath = path.join(__dirname, '../database/schema.sql');
    const schema = fs.readFileSync(schemaPath, 'utf8');

    // Remove comments and split on semicolons
    const cleaned = schema
      .replace(/--[^\n]*/g, '')   // remove line comments
      .replace(/\/\*[\s\S]*?\*\//g, ''); // remove block comments

    const statements = cleaned
      .split(';')
      .map(s => s.trim())
      .filter(s => s.length > 5); // skip empty/trivial

    for (const stmt of statements) {
      try {
        await connection.query(stmt);
      } catch (e) {
        // Ignore "already exists" errors
        if (!e.message.includes('already exists') && !e.message.includes('Duplicate')) {
          throw e;
        }
      }
    }
    console.log('✅ Schema initialized');

    await insertSampleData(connection);
    console.log('✅ Database initialization complete!');
  } catch (error) {
    console.error('❌ Database initialization error:', error.message);
    process.exit(1);
  } finally {
    if (connection) await connection.end();
  }
}

async function insertSampleData(connection) {
  // Hospitals
  const [hCount] = await connection.query('SELECT COUNT(*) as c FROM hospitals');
  if (hCount[0].c === 0) {
    await connection.query(`
      INSERT INTO hospitals (name, address, latitude, longitude, contact, facilities) VALUES
      ('City General Hospital',    '123 Main St, City Center',    28.6139, 77.2090, '+91-1234567890', '["Emergency","ICU","Surgery","Trauma Center"]'),
      ('Rural Health Center',      '456 Village Rd, Remote Area', 28.5355, 77.3910, '+91-0987654321', '["Emergency","Basic Care","Maternity"]'),
      ('District Medical College', '789 College Rd, District HQ', 28.7041, 77.1025, '+91-1122334455', '["Emergency","ICU","Surgery","Trauma Center","Neurology"]'),
      ('North City Hospital',      '12 North Ave, Sector 5',      28.6800, 77.1500, '+91-9988776655', '["Emergency","ICU","Cardiology"]'),
      ('South Care Clinic',        '34 South Rd, Sector 12',      28.5700, 77.2500, '+91-8877665544', '["Emergency","Basic Care","Pediatrics"]')
    `);
    console.log('  → Sample hospitals inserted');
  }

  // Admin user
  const [uCount] = await connection.query('SELECT COUNT(*) as c FROM users');
  if (uCount[0].c === 0) {
    const bcrypt = require('bcrypt');
    const hash = await bcrypt.hash('admin123', 10);
    await connection.query(
      'INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)',
      ['admin', 'admin@ambulance.com', hash, 'admin']
    );
    console.log('  → Admin user created (admin@ambulance.com / admin123)');
  }

  // Road scores sample data
  const [rCount] = await connection.query('SELECT COUNT(*) as c FROM road_scores');
  if (rCount[0].c === 0) {
    await connection.query(`
      INSERT INTO road_scores (road_id, road_name, latitude, longitude, road_quality, terrain_difficulty, congestion_level, average_speed, incident_count, weather_factor, composite_score, flag_status) VALUES
      ('RD001', 'NH-48 Main Highway',        28.6200, 77.2100, 1.1, 1.0, 0.30, 55, 1, 1.0, 18,  'good'),
      ('RD002', 'Village Road Sector-7',     28.5400, 77.3800, 1.8, 1.4, 0.20, 30, 3, 1.1, 62,  'warning'),
      ('RD003', 'Mountain Pass Route',       28.4900, 77.4500, 2.0, 1.9, 0.10, 15, 5, 1.3, 85,  'critical'),
      ('RD004', 'City Ring Road',            28.6500, 77.1800, 1.2, 1.0, 0.70, 25, 2, 1.0, 58,  'warning'),
      ('RD005', 'District Highway NH-58',    28.7100, 77.0900, 1.1, 1.1, 0.25, 50, 0, 1.0, 20,  'good'),
      ('RD006', 'Rural Dirt Track',          28.5100, 77.4200, 2.1, 1.7, 0.05, 12, 7, 1.2, 90,  'critical'),
      ('RD007', 'Expressway Bypass',         28.6300, 77.2400, 1.0, 1.0, 0.40, 80, 0, 1.0, 15,  'good'),
      ('RD008', 'Flood-Prone Road Sector-3', 28.5800, 77.3200, 1.6, 1.3, 0.50, 20, 4, 1.5, 72,  'critical')
    `);
    console.log('  → Sample road scores inserted');
  }

  // Alerts sample data
  const [aCount] = await connection.query('SELECT COUNT(*) as c FROM alerts');
  if (aCount[0].c === 0) {
    await connection.query(`
      INSERT INTO alerts (road_id, road_name, alert_type, severity, message, status) VALUES
      ('RD003', 'Mountain Pass Route',       'road_condition',  'critical', 'Critical road condition: Mountain Pass Route has composite score 85/100. Immediate repair required.',          'active'),
      ('RD006', 'Rural Dirt Track',          'road_condition',  'critical', 'Critical road condition: Rural Dirt Track has composite score 90/100. Road is nearly impassable.',            'active'),
      ('RD004', 'City Ring Road',            'traffic',         'high',     'Heavy congestion on City Ring Road. Congestion level 70%. Average speed reduced to 25 km/h.',                'active'),
      ('RD008', 'Flood-Prone Road Sector-3', 'weather',         'critical', 'Flood risk on Sector-3 Road. Weather factor 1.5x. Avoid during monsoon.',                                   'active'),
      ('RD002', 'Village Road Sector-7',     'infrastructure',  'medium',   'Village Road Sector-7 requires maintenance. Road quality degraded to 1.8x normal.',                         'acknowledged'),
      (NULL,    NULL,                         'emergency',       'high',     'Multiple emergency requests in remote area. Response time exceeding threshold.',                             'active')
    `);
    console.log('  → Sample alerts inserted');
  }

  // Sample emergency requests
  const [eCount] = await connection.query('SELECT COUNT(*) as c FROM emergency_requests');
  if (eCount[0].c === 0) {
    await connection.query(`
      INSERT INTO emergency_requests (patient_name, location, severity, contact, description, status) VALUES
      ('Rajesh Kumar',  '{"latitude":28.6139,"longitude":77.2090}', 'critical', '+91-9876543210', 'Cardiac arrest, needs immediate attention', 'completed'),
      ('Priya Sharma',  '{"latitude":28.5355,"longitude":77.3910}', 'high',     '+91-8765432109', 'Road accident, multiple injuries',          'in_progress'),
      ('Amit Singh',    '{"latitude":28.7041,"longitude":77.1025}', 'medium',   '+91-7654321098', 'Severe fever, remote location',             'pending'),
      ('Sunita Devi',   '{"latitude":28.5800,"longitude":77.3200}', 'critical', '+91-6543210987', 'Pregnancy emergency, flood-prone area',     'assigned'),
      ('Mohan Lal',     '{"latitude":28.6500,"longitude":77.1800}', 'low',      '+91-5432109876', 'Minor injury, needs transport',             'pending')
    `);
    console.log('  → Sample emergency requests inserted');
  }
}

if (require.main === module) {
  initializeDatabase();
}

module.exports = { initializeDatabase };
