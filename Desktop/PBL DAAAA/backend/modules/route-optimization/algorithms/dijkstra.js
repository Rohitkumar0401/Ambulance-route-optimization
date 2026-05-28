/**
 * Dijkstra's Algorithm - Weighted Graph Implementation
 * Converted from Java (Dijkstra.java + Graph.java + MapFactory.java)
 * Supports: road quality, terrain difficulty, traffic factor weighting
 */

class MinHeap {
  constructor() {
    this.heap = [];
  }

  push(item) {
    this.heap.push(item);
    this._bubbleUp(this.heap.length - 1);
  }

  pop() {
    const top = this.heap[0];
    const last = this.heap.pop();
    if (this.heap.length > 0) {
      this.heap[0] = last;
      this._sinkDown(0);
    }
    return top;
  }

  isEmpty() {
    return this.heap.length === 0;
  }

  _bubbleUp(i) {
    while (i > 0) {
      const parent = Math.floor((i - 1) / 2);
      if (this.heap[parent].cost <= this.heap[i].cost) break;
      [this.heap[parent], this.heap[i]] = [this.heap[i], this.heap[parent]];
      i = parent;
    }
  }

  _sinkDown(i) {
    const n = this.heap.length;
    while (true) {
      let smallest = i;
      const l = 2 * i + 1, r = 2 * i + 2;
      if (l < n && this.heap[l].cost < this.heap[smallest].cost) smallest = l;
      if (r < n && this.heap[r].cost < this.heap[smallest].cost) smallest = r;
      if (smallest === i) break;
      [this.heap[smallest], this.heap[i]] = [this.heap[i], this.heap[smallest]];
      i = smallest;
    }
  }
}

class Graph {
  constructor() {
    this.nodes = new Map();   // id -> { lat, lon, type, label }
    this.adj   = new Map();   // id -> [{ to, weight, meta }]
  }

  addNode(id, data) {
    this.nodes.set(id, data);
    if (!this.adj.has(id)) this.adj.set(id, []);
  }

  // weight = base distance * roadQuality * terrainDifficulty * trafficFactor
  addEdge(u, v, baseDist, roadQuality = 1.0, terrainDifficulty = 1.0, trafficFactor = 1.0) {
    const w = baseDist * roadQuality * terrainDifficulty * trafficFactor;
    const meta = { baseDist, roadQuality, terrainDifficulty, trafficFactor };
    this.adj.get(u).push({ to: v, weight: w, meta });
    this.adj.get(v).push({ to: u, weight: w, meta });
  }

  neighbors(id) { return this.adj.get(id) || []; }
  getNode(id)   { return this.nodes.get(id); }
  allIds()      { return Array.from(this.nodes.keys()); }
}

// Haversine distance in km
function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// Build graph from real coordinates with waypoints (mirrors MapFactory.java logic)
function buildGraph(start, dest, hospitals = [], roadConditions = {}) {
  const g = new Graph();

  // Add anchor nodes
  g.addNode('start', { lat: start.latitude, lon: start.longitude, type: 'start', label: 'Start' });
  g.addNode('dest',  { lat: dest.latitude,  lon: dest.longitude,  type: 'dest',  label: 'Destination' });

  // Add hospital nodes
  hospitals.forEach((h, i) => {
    g.addNode(`h${i}`, { lat: parseFloat(h.latitude), lon: parseFloat(h.longitude), type: 'hospital', label: h.name });
  });

  // Generate realistic waypoints along the route (mirrors connectNearestNeighbours)
  const numWaypoints = 8;
  for (let i = 1; i <= numWaypoints; i++) {
    const t = i / (numWaypoints + 1);
    const lat = start.latitude  + (dest.latitude  - start.latitude)  * t + (Math.random() - 0.5) * 0.015;
    const lon = start.longitude + (dest.longitude - start.longitude) * t + (Math.random() - 0.5) * 0.015;
    g.addNode(`wp${i}`, { lat, lon, type: 'waypoint', label: `Waypoint ${i}` });
  }

  // Connect each node to its K nearest neighbours (K=4, mirrors Java MapFactory)
  const ids = g.allIds();
  ids.forEach(uid => {
    const u = g.getNode(uid);
    const sorted = ids
      .filter(vid => vid !== uid)
      .map(vid => {
        const v = g.getNode(vid);
        return { vid, dist: haversine(u.lat, u.lon, v.lat, v.lon) };
      })
      .sort((a, b) => a.dist - b.dist)
      .slice(0, 4);

    sorted.forEach(({ vid, dist }) => {
      // Avoid duplicate edges
      if (!g.neighbors(uid).some(e => e.to === vid)) {
        const cond = roadConditions[`${uid}_${vid}`] || roadConditions[`${vid}_${uid}`] || {};
        g.addEdge(uid, vid, dist,
          cond.roadQuality      || 1.0,
          cond.terrainDifficulty || 1.0,
          cond.trafficFactor    || 1.0
        );
      }
    });
  });

  return g;
}

// Core Dijkstra (mirrors Dijkstra.java dijkstra())
function dijkstra(graph, sourceId) {
  const dist = new Map();
  const prev = new Map();
  const visited = new Set();
  const pq = new MinHeap();

  graph.allIds().forEach(id => { dist.set(id, Infinity); prev.set(id, null); });
  dist.set(sourceId, 0);
  pq.push({ cost: 0, id: sourceId });

  while (!pq.isEmpty()) {
    const { cost, id: u } = pq.pop();
    if (visited.has(u)) continue;
    visited.add(u);

    for (const edge of graph.neighbors(u)) {
      const newCost = cost + edge.weight;
      if (newCost < dist.get(edge.to)) {
        dist.set(edge.to, newCost);
        prev.set(edge.to, u);
        pq.push({ cost: newCost, id: edge.to });
      }
    }
  }
  return { dist, prev };
}

// Reconstruct path (mirrors Dijkstra.java reconstructPath())
function reconstructPath(prev, source, target) {
  const path = [];
  let cur = target;
  const maxLen = prev.size + 2;

  while (cur !== null) {
    path.unshift(cur);
    if (cur === source) break;
    cur = prev.get(cur);
    if (path.length > maxLen) return []; // cycle guard
  }

  if (path.length === 0 || path[0] !== source) return [];
  return path;
}

// Main exported findPath function
exports.findPath = async (start, destination, trafficData = {}, roadConditions = {}, hospitals = []) => {
  if (!start || !destination) throw new Error('Start and destination are required');
  if (start.latitude == null || start.longitude == null) throw new Error('Start must have latitude and longitude');
  if (destination.latitude == null || destination.longitude == null) throw new Error('Destination must have latitude and longitude');

  // Same location
  if (start.latitude === destination.latitude && start.longitude === destination.longitude) {
    return { path: ['start'], pathCoordinates: [{ ...start, type: 'start', label: 'Start' }], distance: 0, estimatedTime: 0, steps: [], algorithm: 'dijkstra' };
  }

  const graph = buildGraph(start, destination, hospitals, roadConditions);
  const { dist, prev } = dijkstra(graph, 'start');

  if (dist.get('dest') === Infinity) throw new Error('No path found between start and destination');

  const pathIds = reconstructPath(prev, 'start', 'dest');
  if (pathIds.length === 0) throw new Error('Path reconstruction failed');

  const pathCoordinates = pathIds.map(id => {
    const n = graph.getNode(id);
    return { latitude: n.lat, longitude: n.lon, type: n.type, label: n.label };
  });

  const totalDistance = dist.get('dest');
  const estimatedTime = Math.round((totalDistance / 40) * 60); // 40 km/h avg

  // Step-by-step directions
  const steps = [];
  for (let i = 0; i < pathIds.length - 1; i++) {
    const a = graph.getNode(pathIds[i]);
    const b = graph.getNode(pathIds[i + 1]);
    steps.push({
      step: i + 1,
      from: a.label,
      to: b.label,
      distance: haversine(a.lat, a.lon, b.lat, b.lon).toFixed(2),
      fromCoords: { latitude: a.lat, longitude: a.lon },
      toCoords:   { latitude: b.lat, longitude: b.lon }
    });
  }

  return {
    path: pathIds,
    pathCoordinates,
    distance: totalDistance.toFixed(2),
    estimatedTime,
    steps,
    algorithm: 'dijkstra',
    nodesExplored: graph.allIds().length
  };
};

exports.Graph     = Graph;
exports.dijkstra  = dijkstra;
exports.haversine = haversine;
