class HospitalSearch {
  binarySearch(hospitals, targetName) {
    let left = 0;
    let right = hospitals.length - 1;

    while (left <= right) {
      const mid = Math.floor((left + right) / 2);
      
      if (hospitals[mid].name === targetName) {
        return hospitals[mid];
      }
      
      if (hospitals[mid].name < targetName) {
        left = mid + 1;
      } else {
        right = mid - 1;
      }
    }
    
    return null;
  }

  findNearestHospitals(userLocation, hospitals, count = 5) {
    // Input validation
    if (!userLocation || !userLocation.latitude || !userLocation.longitude) {
      throw new Error('Valid user location with latitude and longitude is required');
    }

    if (!hospitals || hospitals.length === 0) {
      return [];
    }

    // Ensure count is positive
    const validCount = Math.max(1, Math.min(count, hospitals.length));

    const hospitalsWithDistance = hospitals.map(hospital => ({
      ...hospital,
      distance: this.calculateDistance(userLocation, {
        latitude: hospital.latitude,
        longitude: hospital.longitude
      })
    }));

    // Sort by distance using Quick Sort
    return this.quickSort(hospitalsWithDistance, 0, hospitalsWithDistance.length - 1)
      .slice(0, validCount);
  }

  quickSort(arr, low, high) {
    if (low < high) {
      const pi = this.partition(arr, low, high);
      this.quickSort(arr, low, pi - 1);
      this.quickSort(arr, pi + 1, high);
    }
    return arr;
  }

  partition(arr, low, high) {
    const pivot = arr[high].distance;
    let i = low - 1;

    for (let j = low; j < high; j++) {
      if (arr[j].distance < pivot) {
        i++;
        [arr[i], arr[j]] = [arr[j], arr[i]];
      }
    }
    [arr[i + 1], arr[high]] = [arr[high], arr[i + 1]];
    return i + 1;
  }

  calculateDistance(point1, point2) {
    const R = 6371; // Earth's radius in km
    const dLat = this.toRad(point2.latitude - point1.latitude);
    const dLon = this.toRad(point2.longitude - point1.longitude);
    
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(this.toRad(point1.latitude)) * Math.cos(this.toRad(point2.latitude)) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  toRad(degrees) {
    return degrees * (Math.PI / 180);
  }
}

module.exports = new HospitalSearch();
