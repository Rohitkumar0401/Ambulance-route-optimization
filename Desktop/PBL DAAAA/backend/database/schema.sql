CREATE DATABASE IF NOT EXISTS ambulance_optimization;
USE ambulance_optimization;

-- Users table
CREATE TABLE IF NOT EXISTS users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  role ENUM('admin', 'dispatcher', 'driver', 'user') DEFAULT 'user',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Hospitals table
CREATE TABLE IF NOT EXISTS hospitals (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  address TEXT NOT NULL,
  latitude DECIMAL(10, 8) NOT NULL,
  longitude DECIMAL(11, 8) NOT NULL,
  contact VARCHAR(20),
  facilities JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Emergency requests table
CREATE TABLE IF NOT EXISTS emergency_requests (
  id INT PRIMARY KEY AUTO_INCREMENT,
  patient_name VARCHAR(100),
  location JSON NOT NULL,
  severity ENUM('critical', 'high', 'medium', 'low') NOT NULL,
  contact VARCHAR(20) NOT NULL,
  description TEXT,
  status ENUM('pending', 'assigned', 'in_progress', 'completed', 'cancelled') DEFAULT 'pending',
  ambulance_id INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Traffic data table
CREATE TABLE IF NOT EXISTS traffic_data (
  id INT PRIMARY KEY AUTO_INCREMENT,
  road_id VARCHAR(50) NOT NULL,
  congestion_level DECIMAL(3, 2),
  average_speed DECIMAL(5, 2),
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_road_id (road_id),
  INDEX idx_timestamp (timestamp)
);

-- Road conditions table
CREATE TABLE IF NOT EXISTS road_conditions (
  id INT PRIMARY KEY AUTO_INCREMENT,
  road_id VARCHAR(50) NOT NULL,
  condition_type ENUM('good', 'fair', 'poor', 'closed') NOT NULL,
  description TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Roadblocks table
CREATE TABLE IF NOT EXISTS roadblocks (
  id INT PRIMARY KEY AUTO_INCREMENT,
  road_id VARCHAR(50) NOT NULL,
  location JSON NOT NULL,
  severity ENUM('minor', 'moderate', 'severe') NOT NULL,
  description TEXT,
  status ENUM('active', 'resolved') DEFAULT 'active',
  reported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  resolved_at TIMESTAMP NULL
);

-- Routes table
CREATE TABLE IF NOT EXISTS routes (
  id INT PRIMARY KEY AUTO_INCREMENT,
  start_location JSON NOT NULL,
  end_location JSON NOT NULL,
  path JSON NOT NULL,
  distance DECIMAL(10, 2),
  estimated_time INT,
  algorithm_used VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Road Scores table (Road Scoring & Flagging Module)
CREATE TABLE IF NOT EXISTS road_scores (
  id INT PRIMARY KEY AUTO_INCREMENT,
  road_id VARCHAR(100) UNIQUE NOT NULL,
  road_name VARCHAR(200) NOT NULL,
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  road_quality DECIMAL(4, 2) DEFAULT 1.0,
  terrain_difficulty DECIMAL(4, 2) DEFAULT 1.0,
  congestion_level DECIMAL(3, 2) DEFAULT 0.0,
  average_speed DECIMAL(5, 2) DEFAULT 60.0,
  incident_count INT DEFAULT 0,
  weather_factor DECIMAL(4, 2) DEFAULT 1.0,
  composite_score INT DEFAULT 0,
  flag_status ENUM('good', 'warning', 'critical') DEFAULT 'good',
  reported_by VARCHAR(100) DEFAULT 'system',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_flag_status (flag_status),
  INDEX idx_composite_score (composite_score)
);

-- Activity log table
CREATE TABLE IF NOT EXISTS activity_log (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT,
  action VARCHAR(100) NOT NULL,
  details TEXT,
  ip_address VARCHAR(45),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_action (action),
  INDEX idx_created_at (created_at)
);

-- Alerts table (Alert & Government Reporting Module)
CREATE TABLE IF NOT EXISTS alerts (
  id INT PRIMARY KEY AUTO_INCREMENT,
  road_id VARCHAR(100),
  road_name VARCHAR(200),
  alert_type ENUM('road_condition', 'traffic', 'emergency', 'weather', 'infrastructure') NOT NULL,
  severity ENUM('low', 'medium', 'high', 'critical') NOT NULL,
  message TEXT NOT NULL,
  latitude DECIMAL(10, 8),
  longitude DECIMAL(11, 8),
  status ENUM('active', 'acknowledged', 'resolved') DEFAULT 'active',
  acknowledged_by VARCHAR(100),
  acknowledged_at TIMESTAMP NULL,
  resolved_at TIMESTAMP NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_severity (severity),
  INDEX idx_created_at (created_at)
);
