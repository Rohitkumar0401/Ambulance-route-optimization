# Ambulance Route Optimization System

**Team Name:** Visitors

## Project Overview
An intelligent route optimization system for ambulances in remote areas, computing the fastest and safest routes by considering distance, traffic, road conditions, and roadblocks.

## Technology Stack
- **Backend:** C++, Node.js, Express.js, RESTful APIs
- **Frontend:** HTML, CSS, JavaScript, React.js
- **Database:** MySQL (Relational Database)
- **Authentication:** JWT (JSON Web Tokens)
- **Security:** bcrypt for password hashing

## Modules
1. **Route Optimization Module** (Dijkstra's/A*) - Rohit Kumar (Lead)
2. **Traffic & Road Condition Analysis Module** - Karan Singh
3. **Hospital & Location Management Module** - Rahul Singh
4. **Emergency Request Handling Module** - Kabeer Kandari
5. **User Authentication & Data Security Module** - Rohit Kumar

## Key Features
✅ Shortest & safest route computation using Dijkstra's/A* Algorithm  
✅ Dynamic rerouting based on traffic and roadblocks  
✅ Queue-based ambulance request handling (FIFO with priority)  
✅ Fast hospital search using Binary Search  
✅ Route ranking using Quick Sort/Merge Sort  
✅ Secure authentication with JWT  
✅ Input validation and error handling  
✅ RESTful API architecture  

## Prerequisites
- Node.js (v14 or higher)
- MySQL (v8.0 or higher)
- npm or yarn package manager
- Git

## Installation & Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd ambulance-route-optimization
```

### 2. Backend Setup

#### Install Backend Dependencies
```bash
npm install
```

#### Configure Environment Variables
Create a `.env` file in the root directory:
```bash
cp .env.example .env
```

Edit `.env` with your configuration:
```env
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=your_mysql_password
DB_NAME=ambulance_optimization
PORT=5000
JWT_SECRET=your_secure_jwt_secret_key_here
NODE_ENV=development
```

#### Initialize Database
```bash
npm run db:init
```

This will:
- Create the database
- Set up all tables
- Insert sample data
- Create admin user (email: admin@ambulance.com, password: admin123)

### 3. Frontend Setup

#### Install Frontend Dependencies
```bash
cd frontend
npm install
cd ..
```

## Running the Application

### Development Mode

#### Start Backend Server
```bash
npm run server
```
Backend will run on: http://localhost:5000

#### Start Frontend (in a new terminal)
```bash
npm run dev
```
Frontend will run on: http://localhost:3000

### Production Mode

#### Build Frontend
```bash
npm run build
```

#### Start Production Server
```bash
npm start
```

## Available Scripts

### Backend Scripts
- `npm run server` - Start development server
- `npm run db:init` - Initialize database with schema and sample data
- `npm start` - Start production server

### Frontend Scripts
- `npm run dev` - Start React development server
- `npm run build` - Build for production

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login

### Emergency Requests
- `POST /api/emergency/create` - Create emergency request
- `GET /api/emergency/next` - Get next request from queue
- `PUT /api/emergency/update-status` - Update request status
- `GET /api/emergency/all` - Get all requests

### Hospital Management
- `GET /api/hospitals` - Get all hospitals
- `GET /api/hospitals/search?name=<name>` - Search hospital by name
- `GET /api/hospitals/nearest?latitude=<lat>&longitude=<lng>&count=<n>` - Find nearest hospitals
- `POST /api/hospitals/add` - Add new hospital

### Route Optimization
- `POST /api/route-optimization/calculate` - Calculate optimal route
- `POST /api/route-optimization/reroute` - Dynamic rerouting

### Traffic Analysis
- `GET /api/traffic-analysis/traffic/:roadId` - Get traffic data
- `POST /api/traffic-analysis/traffic/update` - Update traffic condition
- `GET /api/traffic-analysis/road-conditions/:roadId` - Get road conditions
- `POST /api/traffic-analysis/roadblock/report` - Report roadblock

## Edge Cases & Security Features

### Input Validation
✅ Email format validation  
✅ Password strength requirements (min 8 characters)  
✅ Phone number format validation  
✅ Coordinate range validation (latitude: -90 to 90, longitude: -180 to 180)  
✅ Severity level validation  
✅ Status validation  
✅ Algorithm choice validation  

### Error Handling
✅ Database connection errors  
✅ Duplicate entry prevention  
✅ Invalid reference handling  
✅ JWT token expiration  
✅ 404 route not found  
✅ Global error handler  
✅ Request payload size limits  

### Security Features
✅ Password hashing with bcrypt (10 rounds)  
✅ JWT-based authentication  
✅ Role-based access control  
✅ SQL injection prevention (parameterized queries)  
✅ CORS configuration  
✅ Request logging  

### Data Integrity
✅ Duplicate hospital prevention  
✅ Duplicate user prevention  
✅ Request existence validation  
✅ Empty queue handling  
✅ Null/undefined checks  
✅ Same start-destination handling  

## Default Credentials
After running `npm run db:init`, use these credentials to login:

**Admin Account:**
- Email: `admin@ambulance.com`
- Password: `admin123`

⚠️ **Important:** Change the default password after first login in production!

## Database Schema

### Tables
- `users` - User authentication and roles
- `hospitals` - Hospital information and locations
- `emergency_requests` - Emergency request queue
- `traffic_data` - Real-time traffic information
- `road_conditions` - Road quality and status
- `roadblocks` - Active roadblock reports
- `routes` - Computed route history

## Project Structure
```
ambulance-route-optimization/
├── backend/
│   ├── config/
│   │   ├── database.js          # Database connection
│   │   └── dbInit.js            # Database initialization
│   ├── modules/
│   │   ├── route-optimization/  # Dijkstra's & A* algorithms
│   │   ├── traffic-analysis/    # Traffic monitoring
│   │   ├── hospital-management/ # Hospital search & ranking
│   │   ├── emergency-request/   # Request queue (FIFO)
│   │   └── user-authentication/ # JWT auth & security
│   ├── utils/
│   │   └── errorHandler.js      # Global error handling
│   ├── database/
│   │   └── schema.sql           # Database schema
│   └── server.js                # Express server
├── frontend/
│   ├── src/
│   │   ├── components/          # React components
│   │   ├── App.js
│   │   └── index.js
│   └── public/
├── .env.example                 # Environment template
├── package.json
└── README.md
```

## Troubleshooting

### Database Connection Issues
```bash
# Check MySQL is running
mysql --version

# Test connection
mysql -u root -p

# Grant privileges
GRANT ALL PRIVILEGES ON ambulance_optimization.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

### Port Already in Use
```bash
# Change PORT in .env file
PORT=5001
```

### Module Not Found Errors
```bash
# Reinstall dependencies
rm -rf node_modules package-lock.json
npm install
```

## Future Enhancements
- Real-time GPS tracking integration
- Mobile application (iOS/Android)
- WebSocket for live updates
- Advanced traffic prediction using ML
- Integration with Google Maps API
- SMS/Push notifications
- Multi-language support
- Analytics dashboard

## Team Members
- **Rohit Kumar** (Lead) - Route Optimization Module (Dijkstra's/A*) & User Authentication Module
- **Rahul Singh** - Hospital & Location Management Module
- **Karan Singh** - Traffic & Road Condition Analysis Module
- **Kabeer Kandari** - Emergency Request Handling Module

## License
MIT License

## Support
For issues and questions, please contact the development team.
