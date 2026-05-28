/**
 * A* Algorithm - Heuristic-based shortest path
 * Uses haversine distance as admissible heuristic
 */
const { Graph, dijkstra: runDijkstra, haversine } = require('./dijkstra');

class MinHeap {
  constructor() { this.heap = []; }
  push(item) { this.heap.push(item); this._up(this.heap.length - 1); }
  pop() {
    const top = this.heap[0];
    const last = this.heap.pop();
    if (this.heap.length > 0) { this.heap[0] = last; this._down(0); }
    return top;
  }
  isEmpty() { return this.heap.length === 0; }
  _up(i) {
    while (i > 0) {
      const p = Math.floor((i - 1) / 2);
      if (this.heap[p].f <= this.heap[i].f) break;
      [this.heap[p], this.heap[i]] = [this.heap[i], this.heap[p]];
      i = p;
    }
  }
  _down(i) {
    const n = this.heap.length;
    while (true) {
      let s = i, l = 2*i+1, r = 2*i+2;
      if (l < n && this.heap[l].f < this.heap[s].f) s = l;
      if (r < n && this.heap[r].f < this.heap[s].f) s = r;
      if (s === i) break;
      [this.heap[s], this.heap[i]] = [this.heap[i], this.heap[s]];
      i = s;
    }
  }
}

function buildGraph(start, dest, hospitals = [], roadConditions = {}) {
  const g = new Graph();
  g.addNode('start', { lat: start.latitude,  lon: start.longitude,  type: 'start',    label: 'Start' });
  g.addNode('dest',  { lat: dest.latitude,   lon: dest.longitude,   type: 'dest',     label: 'Destination' });

  hospitals.forEach((h, i) => {
    g.addNode(`h${i}`, { lat: parseFloat(h.latitude), lon: parseFloat(h.longitude), type: 'hospital', label: h.name });
  });

  const numWaypoints = 8;
  for (let i = 1; i <= numWaypoints; i++) {
    const t = i / (numWaypoints + 1);
    const lat = start.latitude  + (dest.latitude  - start.latitude)  * t + (Math.random() - 0.5) * 0.015;
    const lon = start.longitude + (dest.longitude - start.longitude) * t + (Math.random() - 0.5) * 0.015;
    g.addNode(`wp${i}`, { lat, lon, type: 'waypoint', label: `Waypoint ${i}` });
  }

  const ids = g.allIds();
  ids.forEach(uid => {
    const u = g.getNode(uid);
    ids.filter(v => v !== uid)
      .map(vid => ({ vid, dist: haversine(u.lat, u.lon, g.getNode(vid).lat, g.getNode(vid).lon) }))
      .sort((a, b) => a.dist - b.dist)
      .slice(0, 4)
      .forEach(({ vid, dist }) => {
        if (!g.neighbors(uid).some(e => e.to === vid)) {
          const cond = roadConditions[`${uid}_${vid}`] || roadConditions[`${vid}_${uid}`] || {};
          g.addEdge(uid, vid, dist, cond.roadQuality || 1.0, cond.terrainDifficulty || 1.0, cond.trafficFactor || 1.0);
        }
      });
  });
  return g;
}

function aStar(graph, sourceId, targetId) {
  const destNode = graph.getNode(targetId);
  const gScore = new Map();
  const prev   = new Map();
  const closed = new Set();
  const pq     = new MinHeap();

  graph.allIds().forEach(id => { gScore.set(id, Infinity); prev.set(id, null); });
  gScore.set(sourceId, 0);

  const h = (id) => {
    const n = graph.getNode(id);
    return haversine(n.lat, n.lon, destNode.lat, destNode.lon);
  };

  pq.push({ f: h(sourceId), g: 0, id: sourceId });

  while (!pq.isEmpty()) {
    const { g: gCur, id: u } = pq.pop();
    if (u === targetId) break;
    if (closed.has(u)) continue;
    closed.add(u);

    for (const edge of graph.neighbors(u)) {
      const newG = gCur + edge.weight;
      if (newG < gScore.get(edge.to)) {
        gScore.set(edge.to, newG);
        prev.set(edge.to, u);
        pq.push({ f: newG + h(edge.to), g: newG, id: edge.to });
      }
    }
  }

  // Reconstruct
  const path = [];
  let cur = targetId;
  const maxLen = prev.size + 2;
  while (cur !== null) {
    path.unshift(cur);
    if (cur === sourceId) break;
    cur = prev.get(cur);
    if (path.length > maxLen) return { path: [], dist: Infinity };
  }
  if (path[0] !== sourceId) return { path: [], dist: Infinity };
  return { path, dist: gScore.get(targetId) };
}

exports.findPath = async (start, destination, trafficData = {}, roadConditions = {}, hospitals = []) => {
  if (!start || !destination) throw new Error('Start and destination are required');

  if (start.latitude === destination.latitude && start.longitude === destination.longitude) {
    return { path: ['start'], pathCoordinates: [{ ...start, type: 'start', label: 'Start' }], distance: 0, estimatedTime: 0, steps: [], algorithm: 'astar' };
  }

  const graph = buildGraph(start, destination, hospitals, roadConditions);
  const { path: pathIds, dist: totalDistance } = aStar(graph, 'start', 'dest');

  if (pathIds.length === 0 || totalDistance === Infinity) throw new Error('No path found');

  const pathCoordinates = pathIds.map(id => {
    const n = graph.getNode(id);
    return { latitude: n.lat, longitude: n.lon, type: n.type, label: n.label };
  });

  const estimatedTime = Math.round((totalDistance / 40) * 60);

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
    algorithm: 'astar',
    nodesExplored: graph.allIds().length
  };
};
