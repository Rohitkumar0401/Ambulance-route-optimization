class RequestQueue {
  constructor() {
    this.queue = [];
  }

  enqueue(request) {
    // FIFO queue with priority based on severity
    this.queue.push(request);
    this.sortBySeverity();
  }

  dequeue() {
    if (this.isEmpty()) {
      return null;
    }
    return this.queue.shift();
  }

  peek() {
    return this.queue[0] || null;
  }

  isEmpty() {
    return this.queue.length === 0;
  }

  size() {
    return this.queue.length;
  }

  sortBySeverity() {
    // Sort by severity (critical > high > medium > low) and then by timestamp
    const severityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
    
    this.queue.sort((a, b) => {
      const severityDiff = severityOrder[a.severity] - severityOrder[b.severity];
      if (severityDiff !== 0) return severityDiff;
      return a.timestamp - b.timestamp;
    });
  }

  getAll() {
    return [...this.queue];
  }
}

module.exports = new RequestQueue();
