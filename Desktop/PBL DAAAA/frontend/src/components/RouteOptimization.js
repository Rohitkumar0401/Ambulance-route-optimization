import React from 'react';

export default function RouteOptimization() {
  return (
    <div style={{ height: 'calc(100vh - 96px)' }}>
      <iframe
        title="MDVRP Dijkstra Simulation"
        src="/mdvrp.html"
        style={{ width: '100%', height: '100%', border: 'none', borderRadius: 10, background: '#f1f5f9' }}
      />
    </div>
  );
}
