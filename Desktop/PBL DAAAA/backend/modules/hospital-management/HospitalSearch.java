package hospital;

import java.util.List;
import java.util.ArrayList;

/**
 * HospitalSearch.java
 * Search and sort algorithms for hospitals.
 * Direct Java conversion of models/HospitalSearch.js
 *
 * JS source: HospitalSearch.js
 *   - binarySearch(hospitals, targetName)
 *   - findNearestHospitals(userLocation, hospitals, count)
 *   - quickSort(arr, low, high)
 *   - partition(arr, low, high)
 *   - calculateDistance(point1, point2)   → moved to Hospital.distanceTo()
 */
public class HospitalSearch {

    // ── Binary Search by name ─────────────────────────────────────────────────
    // Mirrors JS binarySearch(hospitals, targetName)
    //
    // JS:
    //   let left = 0, right = hospitals.length - 1;
    //   while (left <= right) {
    //     const mid = Math.floor((left + right) / 2);
    //     if (hospitals[mid].name === targetName) return hospitals[mid];
    //     if (hospitals[mid].name < targetName)   left  = mid + 1;
    //     else                                    right = mid - 1;
    //   }
    //   return null;
    //
    // NOTE: list must be sorted by name (ORDER BY name in SQL query).
    // Falls back to partial-match scan if exact match not found.
    public static Hospital binarySearch(List<Hospital> sorted, String targetName) {
        if (targetName == null || targetName.isEmpty()) return null;

        String target = targetName.toLowerCase();
        int lo = 0, hi = sorted.size() - 1;

        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = sorted.get(mid).name.toLowerCase().compareTo(target);
            if      (cmp == 0) return sorted.get(mid);   // exact match
            else if (cmp < 0)  lo = mid + 1;
            else               hi = mid - 1;
        }

        // Fallback: partial match (case-insensitive contains)
        for (Hospital h : sorted) {
            if (h.name.toLowerCase().contains(target)) return h;
        }
        return null;
    }

    // ── Find N nearest hospitals ──────────────────────────────────────────────
    // Mirrors JS findNearestHospitals(userLocation, hospitals, count)
    //
    // JS:
    //   const hospitalsWithDistance = hospitals.map(h => ({
    //     ...h,
    //     distance: this.calculateDistance(userLocation, { lat: h.latitude, lon: h.longitude })
    //   }));
    //   return this.quickSort(hospitalsWithDistance, 0, len-1).slice(0, validCount);
    public static List<Hospital> findNearestHospitals(double userLat, double userLon,
                                                       List<Hospital> hospitals, int count) {
        if (hospitals == null || hospitals.isEmpty()) return new ArrayList<>();

        int validCount = Math.max(1, Math.min(count, hospitals.size()));

        // Attach distance to each hospital (mirrors JS map step)
        List<Hospital> withDist = new ArrayList<>();
        for (Hospital h : hospitals) {
            Hospital copy = new Hospital(h.id, h.name, h.address,
                                         h.latitude, h.longitude,
                                         h.contact, h.facilities,
                                         h.totalBeds, h.availableBeds,
                                         h.isAvailable, h.operatingHours, h.rating);
            copy.distance = copy.distanceTo(userLat, userLon);
            withDist.add(copy);
        }

        // Sort by distance using Quick Sort (mirrors JS quickSort)
        quickSort(withDist, 0, withDist.size() - 1);

        return withDist.subList(0, validCount);
    }

    // ── Quick Sort (in-place, sorts by distance ascending) ───────────────────
    // Mirrors JS quickSort(arr, low, high)
    //
    // JS:
    //   if (low < high) {
    //     const pi = this.partition(arr, low, high);
    //     this.quickSort(arr, low, pi - 1);
    //     this.quickSort(arr, pi + 1, high);
    //   }
    public static void quickSort(List<Hospital> arr, int low, int high) {
        if (low < high) {
            int pi = partition(arr, low, high);
            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }

    // ── Partition (Lomuto scheme) ─────────────────────────────────────────────
    // Mirrors JS partition(arr, low, high)
    //
    // JS:
    //   const pivot = arr[high].distance;
    //   let i = low - 1;
    //   for (let j = low; j < high; j++) {
    //     if (arr[j].distance < pivot) { i++; swap(arr[i], arr[j]); }
    //   }
    //   swap(arr[i+1], arr[high]);
    //   return i + 1;
    private static int partition(List<Hospital> arr, int low, int high) {
        double pivot = arr.get(high).distance;
        int    i     = low - 1;

        for (int j = low; j < high; j++) {
            if (arr.get(j).distance < pivot) {
                i++;
                Hospital tmp = arr.get(i);
                arr.set(i, arr.get(j));
                arr.set(j, tmp);
            }
        }
        // Place pivot in correct position
        Hospital tmp = arr.get(i + 1);
        arr.set(i + 1, arr.get(high));
        arr.set(high, tmp);
        return i + 1;
    }
}
