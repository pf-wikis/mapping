import { Position } from 'geojson';
import turfDistance from '@turf/distance';
import turfMidpoint from '@turf/midpoint';
import { Map as MapLibreMap } from 'maplibre-gl';

/**
 * Travel methods with speeds (km/day)
 */
export const TRAVEL_METHODS = {
  foot: {
    name: 'On Foot',
    icon: '🚶',
    landSpeed: 30,    // 30 km/day walking
    waterSpeed: 0,    // Can't walk on water
    requiresBoat: true
  },
  horse: {
    name: 'Horseback',
    icon: '🐴',
    landSpeed: 60,    // 60 km/day on horse
    waterSpeed: 0,
    requiresBoat: true
  },
  wagon: {
    name: 'Wagon/Cart',
    icon: '🛒',
    landSpeed: 40,    // 40 km/day with wagon
    waterSpeed: 0,
    requiresBoat: true
  },
  ship: {
    name: 'Ship/Boat',
    icon: '⛵',
    landSpeed: 0,     // Can't sail on land
    waterSpeed: 150   // 150 km/day sailing
  },
  mixed: {
    name: 'Mixed (Walk + Ferry)',
    icon: '🚶⛵',
    landSpeed: 30,
    waterSpeed: 150   // Ferry for water crossings
  }
} as const;

export type TravelMethod = keyof typeof TRAVEL_METHODS;

/**
 * Path segment (continuous land or water section)
 */
export interface PathSegment {
  type: 'land' | 'water';
  coordinates: Position[];
  distance: number; // km
}

/**
 * Route result with terrain-aware segments
 */
export interface RouteResult {
  path: Position[];           // Full path coordinates
  segments: PathSegment[];    // Broken down by terrain type
  totalDistance: number;      // Total distance in km
  landDistance: number;       // Distance on land
  waterDistance: number;      // Distance on water
  success: boolean;
  message?: string;
}

/**
 * Travel time breakdown
 */
export interface TravelTime {
  totalDays: number;
  landDays: number;
  waterDays: number;
  method: TravelMethod;
  description: string;
}

/**
 * Terrain cache entry
 */
interface TerrainCache {
  coord: Position;
  terrainType: 'land' | 'water';
  timestamp: number;
}

/**
 * Simplified pathfinder using geodesic paths with terrain detection
 */
export class Pathfinder {
  private map: MapLibreMap;
  private readonly SAMPLE_INTERVAL = 0.5; // km between samples (much denser for coastal accuracy)
  private readonly CACHE_DURATION = 60000; // Cache terrain data for 1 minute
  private readonly MAX_CACHE_SIZE = 1000; // Maximum cache entries
  private terrainCache: Map<string, TerrainCache> = new Map();

  constructor(map: MapLibreMap) {
    this.map = map;
    this.initializeMapStateMonitoring();
  }

  /**
   * Initialize map state monitoring
   */
  private initializeMapStateMonitoring(): void {
    // Wait for map to be fully loaded
    if (this.map.loaded()) {
      console.log('Map already loaded - initializing Pathfinder');
      this.onMapLoaded();
    } else {
      console.log('Waiting for map to load...');
      this.map.once('load', () => {
        console.log('Map load event fired - initializing Pathfinder');
        this.onMapLoaded();
      });
    }
  }

  /**
   * Called when map is fully loaded
   */
  private onMapLoaded(): void {
    // Additional checks for PMTiles source readiness
    this.checkSourceReadiness();
  }

  /**
   * Check if PMTiles source is ready
   */
  private checkSourceReadiness(): void {
    const source = this.map.getSource('golarion');
    if (source) {
      console.log('PMTiles source found:', source);
      // Some sources might need additional time to load tiles
      setTimeout(() => {
        console.log('PMTiles source should be ready now');
      }, 2000);
    } else {
      console.warn('PMTiles source not found');
    }
  }

  /**
   * Check if map is ready for terrain queries
   */
  private isMapReady(): boolean {
    try {
      // Check if map is loaded
      if (!this.map.loaded()) {
        console.warn('Map not loaded');
        return false;
      }

      // Check if map has style
      if (!this.map.getStyle()) {
        console.warn('Map style not loaded');
        return false;
      }

      // Check if PMTiles source exists
      const source = this.map.getSource('golarion');
      if (!source) {
        console.warn('PMTiles source not available');
        return false;
      }

      // Check if we can get bounds (basic functionality test)
      const bounds = this.map.getBounds();
      if (!bounds) {
        console.warn('Cannot get map bounds');
        return false;
      }

      console.log('Map is ready for terrain queries');
      return true;
    } catch (error) {
      console.warn('Error checking map readiness:', error);
      return false;
    }
  }

  /**
   * Wait for map to be ready with timeout
   */
  private async waitForMapReady(timeoutMs: number = 10000): Promise<boolean> {
    const startTime = Date.now();

    while (Date.now() - startTime < timeoutMs) {
      if (this.isMapReady()) {
        return true;
      }
      console.log('Waiting for map to be ready...');
      await new Promise(resolve => setTimeout(resolve, 500));
    }

    console.error('Map readiness timeout after', timeoutMs, 'ms');
    return false;
  }

  /**
   * Find route from start to end
   */
  async findRoute(start: Position, end: Position): Promise<RouteResult> {
    console.log('=== ROUTE CALCULATION STARTED ===');
    console.log('Start:', start);
    console.log('End:', end);

    // Wait for map to be ready before starting terrain detection
    console.log('Checking map readiness...');
    const mapReady = await this.waitForMapReady(15000); // 15 second timeout

    if (!mapReady) {
      console.error('Map not ready for route calculation');
      return this.createFallbackRoute(start, end);
    }

    console.log('Map is ready - proceeding with route calculation');

    // Create geodesic path
    const path = this.createGeodesicPath(start, end);
    console.log('Generated path with', path.length, 'points');

    // Sample terrain along path
    const segments = await this.detectTerrainSegments(path);

    // Calculate distances
    let totalDistance = 0;
    let landDistance = 0;
    let waterDistance = 0;

    for (const segment of segments) {
      totalDistance += segment.distance;
      if (segment.type === 'land') {
        landDistance += segment.distance;
      } else {
        waterDistance += segment.distance;
      }
    }

    // Log comprehensive route summary
    console.log('=== ROUTE CALCULATION COMPLETE ===');
    console.log(`📍 Total Distance: ${totalDistance.toFixed(2)} km`);
    console.log(`🌍 Land Distance: ${landDistance.toFixed(2)} km (${((landDistance / totalDistance) * 100).toFixed(1)}%)`);
    console.log(`💧 Water Distance: ${waterDistance.toFixed(2)} km (${((waterDistance / totalDistance) * 100).toFixed(1)}%)`);
    console.log(`📊 Segments: ${segments.length} total`);
    segments.forEach((seg, idx) => {
      console.log(`  Segment ${idx + 1}: ${seg.type.toUpperCase()} - ${seg.distance.toFixed(2)} km (${seg.coordinates.length} points)`);
    });
    console.log('==================================');

    return {
      path,
      segments,
      totalDistance,
      landDistance,
      waterDistance,
      success: true
    };
  }

  /**
   * Create geodesic (great circle) path between two points
   */
  private createGeodesicPath(start: Position, end: Position): Position[] {
    const path: Position[] = [start];
    const totalDistance = turfDistance(start, end);

    // Sample every N km along the path
    const numSamples = Math.ceil(totalDistance / this.SAMPLE_INTERVAL);

    // Recursively add midpoints for smooth geodesic curve
    this.addGeodesicPoints(path, start, end);

    path.push(end);
    return path;
  }

  /**
   * Recursively add points along geodesic
   */
  private addGeodesicPoints(path: Position[], a: Position, b: Position): void {
    const distance = turfDistance(a, b);

    // If segment is short enough, stop recursing
    if (distance < this.SAMPLE_INTERVAL) {
      return;
    }

    const mid = turfMidpoint(a, b).geometry.coordinates as Position;

    // Add midpoint
    this.addGeodesicPoints(path, a, mid);
    path.push(mid);
    this.addGeodesicPoints(path, mid, b);
  }

  /**
   * Detect terrain type (land vs water) along path
   */
  private async detectTerrainSegments(path: Position[]): Promise<PathSegment[]> {
    const segments: PathSegment[] = [];
    let currentSegment: PathSegment | null = null;

    for (let i = 0; i < path.length; i++) {
      const coord = path[i];
      const terrainType = await this.getTerrainType(coord);

      if (!currentSegment || currentSegment.type !== terrainType) {
        // Start new segment
        if (currentSegment) {
          // Calculate distance for the completed segment
          if (currentSegment.coordinates.length > 1) {
            for (let j = 1; j < currentSegment.coordinates.length; j++) {
              const segmentDistance = turfDistance(
                currentSegment.coordinates[j - 1],
                currentSegment.coordinates[j]
              );
              currentSegment.distance += segmentDistance;
            }
          }
          segments.push(currentSegment);
        }

        currentSegment = {
          type: terrainType,
          coordinates: [coord],
          distance: 0
        };
      } else {
        // Continue current segment
        currentSegment.coordinates.push(coord);
      }
    }

    // Add final segment and calculate its distance
    if (currentSegment) {
      if (currentSegment.coordinates.length > 1) {
        for (let j = 1; j < currentSegment.coordinates.length; j++) {
          const segmentDistance = turfDistance(
            currentSegment.coordinates[j - 1],
            currentSegment.coordinates[j]
          );
          currentSegment.distance += segmentDistance;
        }
      }
      segments.push(currentSegment);
    }

    return segments;
  }

  /**
   * Get cache key for a coordinate
   */
  private getCacheKey(coord: Position): string {
    // Round coordinates to 4 decimal places for consistent cache keys
    const [lng, lat] = coord;
    return `${Math.round(lng * 10000) / 10000},${Math.round(lat * 10000) / 10000}`;
  }

  /**
   * Clean expired entries from terrain cache
   */
  private cleanCache(): void {
    const now = Date.now();
    const keysToDelete: string[] = [];

    for (const [key, entry] of this.terrainCache.entries()) {
      if (now - entry.timestamp > this.CACHE_DURATION) {
        keysToDelete.push(key);
      }
    }

    keysToDelete.forEach(key => this.terrainCache.delete(key));

    // If cache is still too large, remove oldest entries
    if (this.terrainCache.size > this.MAX_CACHE_SIZE) {
      const entries = Array.from(this.terrainCache.entries());
      entries.sort((a, b) => a[1].timestamp - b[1].timestamp);
      const toDelete = entries.slice(0, this.terrainCache.size - this.MAX_CACHE_SIZE);
      toDelete.forEach(([key]) => this.terrainCache.delete(key));
    }
  }

  /**
   * Get all available layers in the map for debugging
   */
  private getAvailableLayers(): void {
    const style = this.map.getStyle();
    if (!style || !style.layers) {
      console.warn('No map style available');
      return;
    }

    const layers = style.layers.map(layer => ({
      id: layer.id,
      type: layer.type,
      sourceLayer: layer['source-layer'],
      source: layer.source
    }));

    console.log('=== AVAILABLE MAP LAYERS ===');
    console.log(layers);
    console.log('=========================');
  }

  /**
   * Check if coordinate is within map bounds
   */
  private isCoordinateWithinBounds(coord: Position): boolean {
    try {
      const bounds = this.map.getBounds();
      const [lng, lat] = coord;
      return lng >= bounds.getWest() && lng <= bounds.getEast() &&
             lat >= bounds.getSouth() && lat <= bounds.getNorth();
    } catch (error) {
      console.warn('Error checking bounds:', error);
      return false;
    }
  }

  /**
   * Test coordinate projections
   */
  private testCoordinateProjections(coord: Position): void {
    try {
      const [lng, lat] = coord;
      const projected = this.map.project([lng, lat]);
      const unprojected = this.map.unproject([projected.x, projected.y]);

      console.log('=== COORDINATE PROJECTION TEST ===');
      console.log('Original:', coord);
      console.log('Projected:', { x: projected.x, y: projected.y });
      console.log('Unprojected:', [unprojected.lng, unprojected.lat]);
      console.log('In bounds:', this.isCoordinateWithinBounds(coord));
      console.log('===================================');
    } catch (error) {
      console.warn('Error testing projections:', error);
    }
  }

  /**
   * Determine terrain type at a specific coordinate using map data
   */
  private async getTerrainType(coord: Position): Promise<'land' | 'water'> {
    const cacheKey = this.getCacheKey(coord);

    // Check cache first
    const cached = this.terrainCache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < this.CACHE_DURATION) {
      return cached.terrainType;
    }

    // Clean cache periodically
    if (this.terrainCache.size > this.MAX_CACHE_SIZE * 0.8) {
      this.cleanCache();
    }

    // Quick map state check before each query
    if (!this.isMapReady()) {
      console.warn('Map not ready during terrain query, using fallback');
      return this.getTerrainTypeCoordinateFallback(coord);
    }

    try {
      const [lng, lat] = coord;

      // Log layer information and coordinate tests (only once per session)
      if (this.terrainCache.size === 0) {
        this.getAvailableLayers();
        this.testCoordinateProjections(coord);
      }

      console.log(`Querying coordinate [${lng.toFixed(4)}, ${lat.toFixed(4)}]:`, {
        inBounds: this.isCoordinateWithinBounds(coord),
        cacheKey
      });

      // Query the map at the specific coordinate
      const point = this.map.project([lng, lat]);

      // Try multiple query methods to find terrain data
      let allFeatures = this.map.queryRenderedFeatures([point.x, point.y]);

      // If rendered features query fails, try source features query
      if (allFeatures.length === 0) {
        console.log('Rendered features query empty, trying source features query...');
        try {
          allFeatures = this.map.querySourceFeatures('golarion', {
            sourceLayer: 'geometry'
          });
          console.log(`Source features query returned ${allFeatures.length} features`);
        } catch (error) {
          console.warn('Source features query failed:', error);
        }
      }

      // Try broader source query if specific layer fails
      if (allFeatures.length === 0) {
        console.log('Specific source layer query empty, trying broader query...');
        try {
          allFeatures = this.map.querySourceFeatures('golarion');
          console.log(`Broad source features query returned ${allFeatures.length} features`);
        } catch (error) {
          console.warn('Broad source features query failed:', error);
        }
      }

      // Debug logging - check what we're finding
      console.log(`Querying coordinate [${lng.toFixed(4)}, ${lat.toFixed(4)}]:`, {
        totalFeatures: allFeatures.length,
        point: { x: point.x, y: point.y },
        layers: allFeatures.map(f => ({
          id: f.layer?.id,
          type: f.layer?.type,
          sourceLayer: f.layer?.['source-layer'],
          hasProperties: !!f.properties,
          properties: f.properties ? {
            hasColor: 'color' in f.properties,
            color: f.properties.color,
            keys: Object.keys(f.properties)
          } : null
        })),
        detailedFeatures: allFeatures.map(f => ({
          layerId: f.layer?.id,
          layerType: f.layer?.type,
          sourceLayer: f.layer?.['source-layer'],
          geometryType: f.geometry?.type,
          properties: f.properties,
          isLandCandidate: f.layer?.type === 'fill' &&
                          f.layer?.['source-layer'] === 'geometry' &&
                          f.properties?.color !== undefined
        }))
      });

      // Analyze terrain using comprehensive feature analysis
      let terrainType = 'land'; // Default to land (most of the map is land)
      let foundGeometryFeature = false;
      let isSourceFeatures = allFeatures.length > 0 && !allFeatures[0].layer;

      console.log(`Processing ${allFeatures.length} features (${isSourceFeatures ? 'source' : 'rendered'})`);

      // For source features, we need spatial filtering, not just feature collection
      if (isSourceFeatures) {
        // Source features are ALL features in the dataset - we need to find which one contains our coordinate
        terrainType = this.findTerrainAtCoordinateFromSourceFeatures(allFeatures, [lng, lat]);
      } else {
        // Rendered features - process as before (these are already spatially filtered)
        for (const feature of allFeatures) {
          // Rendered features filtering - check layer properties
          // Note: We're in the rendered features path, so we check feature.layer
          const isGeometryFeature = feature.layer?.type === 'fill' &&
                           feature.layer?.['source-layer'] === 'geometry' &&
                           feature.properties?.color !== undefined;

          if (isGeometryFeature) {
            foundGeometryFeature = true;

            // Create a unified feature object for analysis
            const unifiedFeature = {
              ...feature,
              layer: feature.layer || { id: feature.sourceLayer, type: 'fill', 'source-layer': feature.sourceLayer },
              sourceLayer: feature.sourceLayer || feature.layer?.['source-layer']
            };

            // Use comprehensive terrain analysis
            const detectedTerrain = this.getTerrainTypeFromFeature(unifiedFeature);

            // Update terrain type (prioritize land over water for mixed areas)
            if (detectedTerrain === 'land') {
              terrainType = 'land';
              console.log('Terrain classified as: LAND (comprehensive analysis)');
              break; // Found land, no need to check further
            } else if (detectedTerrain === 'water') {
              terrainType = 'water';
              console.log('Terrain classified as: WATER (comprehensive analysis)');
            } else {
              // For other terrain types (mountain, forest, etc.), treat as land for now
              terrainType = 'land';
              console.log(`Terrain classified as: ${detectedTerrain} (treating as land for routing)`);
              break;
            }
          }
        }
      }

      // If no geometry features found, default to land (use fallback for accuracy)
      if (!foundGeometryFeature) {
        console.warn('⚠️ No geometry features found at coordinate - defaulting to land. Consider using fallback detection.');
        terrainType = 'land'; // Default to land instead of water
      }

      
      console.log(`Terrain type determined: ${terrainType}`);

      // Cache the result
      this.terrainCache.set(cacheKey, {
        coord: [...coord],
        terrainType,
        timestamp: Date.now()
      });

      return terrainType;

    } catch (error) {
      console.warn('Map query failed, falling back to coordinate detection:', error);

      // Fallback to coordinate-based detection if map query fails
      return this.getTerrainTypeCoordinateFallback(coord);
    }
  }

  /**
   * Fallback terrain detection using coordinate-based approach
   */
  private getTerrainTypeCoordinateFallback(coord: Position): 'land' | 'water' {
    const [lng, lat] = coord;

    // Inner Sea (between Avistan and Garund) - IMPROVED for Absalom-Escadar route
    // This is a large sea between the continents, should be much broader
    const innerSea = lng > 5 && lng < 35 && lat > 35 && lat < 50;

    // Expand Inner Sea bounds to cover more water between islands
    const innerSeaExpanded = lng > 3 && lng < 38 && lat > 33 && lat < 52;

    // Specific water channels between known islands/cities
    const absalomChannel = lng > 18 && lng < 25 && lat > 40 && lat < 45; // Around Absalom area
    // Escadar is actually at [-0.9841, 31.9528] which should be LAND (island city)
    const escadarLandZone = (lng > -2 && lng < 0.5) && (lat > 31.5 && lat < 32.5); // Escadar island area
    const escadarApproach = (lng > -1.5 && lng < -0.5) && (lat > 31 && lat < 32.5); // Approaches to Escadar island

    // Arcadian Ocean (west of Avistan/Garund)
    const arcadianOcean = lng < -180 || lng > 180;

    // Obari Ocean (east of Garund)
    const obariOcean = lng > 60 && lng < 100 && lat > -20 && lat < 40;

    // Embaral Ocean (far east)
    const embaralOcean = lng > 140 || lng < -140;

    // Antarkos Ocean (far south)
    const antarkosOcean = lat < -60;

    // Steaming Sea (north of Garund)
    const steamingSea = lng > 15 && lng < 45 && lat > 20 && lat < 35;

    // Fever Sea (west of Inner Sea)
    const feverSea = lng > -5 && lng < 10 && lat > 38 && lat < 48;

    // If in any major water body, it's water - but exclude known land zones
    if ((innerSeaExpanded || absalomChannel || escadarApproach ||
        arcadianOcean || obariOcean || embaralOcean ||
        antarkosOcean || steamingSea || feverSea) &&
        !escadarLandZone) { // Don't classify Escadar zone as water
      console.log(`Coordinate fallback detected water: [${lng.toFixed(4)}, ${lat.toFixed(4)}]`);
      return 'water';
    }

    console.log(`Coordinate fallback detected land: [${lng.toFixed(4)}, ${lat.toFixed(4)}]`);
    // Default to land
    return 'land';
  }

  /**
   * Validate if a coordinate should be land based on city type and geography
   */
  private async validateCityCoordinate(coord: Position, cityName: string): Promise<{
    isPlausible: boolean;
    actualTerrain: 'land' | 'water';
    reasoning: string;
  }> {
    const terrain = await this.getTerrainType(coord);
    const [lng, lat] = coord;

    // Known island/coastal cities that might legitimately be at water's edge
    const knownCoastalOrIslandCities = [
      'absalom', 'escadar', 'westcrown', 'koria', 'illmagvi',
      'otari', 'cassomir', 'boldheart', 'diobel'
    ].map(name => name.toLowerCase());

    const isKnownCoastal = knownCoastalOrIslandCities.includes(cityName.toLowerCase());

    const reasoning = terrain === 'land'
      ? `Detected land at coordinate [${lng.toFixed(4)}, ${lat.toFixed(4)}]`
      : isKnownCoastal
        ? `Detected water at coordinate [${lng.toFixed(4)}, ${lat.toFixed(4)}] - plausible for coastal/island city ${cityName}`
        : `Detected water at coordinate [${lng.toFixed(4)}, ${lat.toFixed(4)}] - unusual for city ${cityName} (should be land)`;

    const isPlausible = terrain === 'land' || isKnownCoastal;

    console.log(`City coordinate validation for ${cityName}:`, {
      coordinate: [lng.toFixed(4), lat.toFixed(4)],
      terrain,
      isKnownCoastal,
      isPlausible,
      reasoning
    });

    return {
      isPlausible,
      actualTerrain: terrain,
      reasoning
    };
  }

  /**
   * Calculate travel time for a route with a specific travel method
   */
  calculateTravelTime(route: RouteResult, method: TravelMethod = 'mixed'): TravelTime {
    const travelMethod = TRAVEL_METHODS[method];

    let landDays = 0;
    let waterDays = 0;

    // Calculate land travel time
    if (route.landDistance > 0) {
      if (travelMethod.landSpeed > 0) {
        landDays = route.landDistance / travelMethod.landSpeed;
      } else if (travelMethod.requiresBoat) {
        // Can't travel on land with this method
        return {
          totalDays: Infinity,
          landDays: 0,
          waterDays: 0,
          method,
          description: `${travelMethod.name} cannot travel on land`
        };
      }
    }

    // Calculate water travel time
    if (route.waterDistance > 0) {
      if (travelMethod.waterSpeed > 0) {
        waterDays = route.waterDistance / travelMethod.waterSpeed;
      } else {
        // Can't travel on water without boat
        return {
          totalDays: Infinity,
          landDays: 0,
          waterDays: 0,
          method,
          description: `${travelMethod.name} cannot cross water (requires ferry)`
        };
      }
    }

    const totalDays = landDays + waterDays;

    // Create description
    let description = `${Math.ceil(totalDays)} days total`;
    if (landDays > 0 && waterDays > 0) {
      description += ` (${Math.ceil(landDays)} days on land, ${Math.ceil(waterDays)} days on water)`;
    } else if (landDays > 0) {
      description += ` (all on land)`;
    } else if (waterDays > 0) {
      description += ` (all on water)`;
    }

    return {
      totalDays,
      landDays,
      waterDays,
      method,
      description
    };
  }

  /**
   * Interactive terrain testing - test terrain at clicked coordinates
   */
  async testTerrainAtCoordinate(lng: number, lat: number): Promise<void> {
    const coord: Position = [lng, lat];

    console.log(`=== TESTING TERRAIN AT [${lng.toFixed(6)}, ${lat.toFixed(6)}] ===`);

    // Test coordinate bounds
    console.log('Bounds check:', this.isCoordinateWithinBounds(coord));

    // Test projections
    this.testCoordinateProjections(coord);

    // Get available layers
    this.getAvailableLayers();

    // Test terrain detection with detailed logging
    const point = this.map.project(coord);
    console.log('Screen coordinates:', { x: point.x, y: point.y });

    // Query all layers at this point
    const allFeatures = this.map.queryRenderedFeatures([point.x, point.y]);
    console.log('All features found:', allFeatures.length);

    // Group features by layer
    const featuresByLayer: Record<string, any[]> = {};
    allFeatures.forEach(feature => {
      const layerId = feature.layer?.id || 'unknown';
      if (!featuresByLayer[layerId]) {
        featuresByLayer[layerId] = [];
      }
      featuresByLayer[layerId].push(feature);
    });

    console.log('Features by layer:', featuresByLayer);

    // Test our terrain detection
    const terrainType = await this.getTerrainType(coord);
    console.log('Terrain type result:', terrainType);

    // Test individual layer queries with detailed analysis
    const layers = this.map.getStyle()?.layers || [];
    for (const layer of layers) {
      try {
        const layerFeatures = this.map.queryRenderedFeatures([point.x, point.y], {
          layers: [layer.id]
        });
        if (layerFeatures.length > 0) {
          console.log(`\n--- Layer "${layer.id}" (${layerFeatures.length} features) ---`);
          layerFeatures.forEach((feature, index) => {
            console.log(`Feature ${index + 1}:`, {
              id: feature.id,
              type: feature.type,
              source: feature.source,
              'source-layer': feature.sourceLayer,
              properties: feature.properties,
              geometry: feature.geometry?.type,
              layer: {
                id: feature.layer?.id,
                type: feature.layer?.type,
                paint: feature.layer?.paint,
                layout: feature.layer?.layout
              }
            });
          });
        }
      } catch (error) {
        console.warn(`Error querying layer "${layer.id}":`, error);
      }
    }

    console.log('====================================');
  }

  /**
   * Get comprehensive terrain type from feature properties
   * Analyzes all available properties to determine terrain type
   */
  private getTerrainTypeFromFeature(feature: any): string {
    // Get all available properties that might indicate terrain type
    const allProperties = feature.properties || {};
    const featureColor = allProperties.color;
    const sourceLayer = feature.sourceLayer || feature.layer?.['source-layer'];
    const layerId = feature.layer?.id;

    console.log('=== COMPREHENSIVE TERRAIN ANALYSIS ===');
    console.log('All feature properties:', allProperties);
    console.log('Source layer:', sourceLayer);
    console.log('Layer ID:', layerId);
    console.log('Feature color:', featureColor);

    // List all property keys to understand available data
    console.log('Available property keys:', Object.keys(allProperties));

    // Check for explicit terrain type properties
    const possibleTypeFields = ['type', 'class', 'kind', 'terrain', 'category', 'landuse', 'natural'];
    for (const field of possibleTypeFields) {
      if (allProperties[field]) {
        console.log(`Found terrain type in field "${field}":`, allProperties[field]);
      }
    }

    // Method 1: Check explicit terrain type properties
    if (allProperties.type) {
      const terrainType = this.normalizeTerrainType(allProperties.type);
      if (terrainType !== 'unknown') {
        console.log(`Terrain determined from type property: ${terrainType}`);
        return terrainType;
      }
    }

    if (allProperties.class) {
      const terrainType = this.normalizeTerrainType(allProperties.class);
      if (terrainType !== 'unknown') {
        console.log(`Terrain determined from class property: ${terrainType}`);
        return terrainType;
      }
    }

    // Method 2: Check source layer for terrain indication
    if (sourceLayer) {
      const terrainType = this.getTerrainFromSourceLayer(sourceLayer);
      if (terrainType !== 'unknown') {
        console.log(`Terrain determined from source layer: ${terrainType}`);
        return terrainType;
      }
    }

    // Method 3: Use color detection as fallback (existing logic)
    if (featureColor) {
      const terrainType = this.getTerrainFromColor(featureColor);
      if (terrainType !== 'unknown') {
        console.log(`Terrain determined from color: ${terrainType}`);
        return terrainType;
      }
    }

    // Default fallback - when all detection methods fail, assume land
    console.warn(`⚠️ Could not determine terrain type from feature properties. Defaulting to LAND as conservative estimate.`);
    console.log('Feature had:', {
      hasColor: !!featureColor,
      color: featureColor,
      hasSourceLayer: !!sourceLayer,
      sourceLayer,
      allProperties: Object.keys(allProperties)
    });
    return 'land';
  }

  /**
   * Normalize terrain type names to standard values
   */
  private normalizeTerrainType(type: string): string {
    if (!type || typeof type !== 'string') return 'unknown';

    const normalized = type.toLowerCase().trim();

    // Water types
    if (['water', 'ocean', 'sea', 'lake', 'river'].includes(normalized)) {
      return 'water';
    }

    // Land types
    if (['land', 'ground', 'terrain'].includes(normalized)) {
      return 'land';
    }

    // Mountain types
    if (['mountain', 'mountains', 'hill', 'hills', 'peak', 'peaks'].includes(normalized)) {
      return 'mountain';
    }

    // Forest types
    if (['forest', 'forests', 'wood', 'woods', 'jungle', 'tree', 'trees'].includes(normalized)) {
      return 'forest';
    }

    // Desert types
    if (['desert', 'deserts', 'sand', 'dune'].includes(normalized)) {
      return 'desert';
    }

    // Swamp types
    if (['swamp', 'swamps', 'marsh', 'wetland', 'bog'].includes(normalized)) {
      return 'swamp';
    }

    // Ice types
    if (['ice', 'snow', 'glacier', 'tundra', 'frozen'].includes(normalized)) {
      return 'ice';
    }

    return 'unknown';
  }

  /**
   * Get terrain type from source layer name
   */
  private getTerrainFromSourceLayer(sourceLayer: string): string {
    if (!sourceLayer || typeof sourceLayer !== 'string') return 'unknown';

    const layer = sourceLayer.toLowerCase();

    if (layer.includes('water') || layer.includes('ocean') || layer.includes('sea')) {
      return 'water';
    }
    if (layer.includes('land') || layer.includes('terrain')) {
      return 'land';
    }
    if (layer.includes('mountain')) {
      return 'mountain';
    }
    if (layer.includes('forest')) {
      return 'forest';
    }
    if (layer.includes('desert')) {
      return 'desert';
    }
    if (layer.includes('swamp') || layer.includes('marsh')) {
      return 'swamp';
    }
    if (layer.includes('ice') || layer.includes('snow')) {
      return 'ice';
    }
    if (layer.includes('river')) {
      return 'river';
    }

    return 'unknown';
  }

  /**
   * Find terrain at coordinate using reliable rendered features
   */
  private findTerrainAtCoordinateFromSourceFeatures(features: any[], coordinate: [number, number]): 'land' | 'water' {
    console.log(`=== COMPREHENSIVE TERRAIN DETECTION for [${coordinate[0].toFixed(6)}, ${coordinate[1].toFixed(6)}] ===`);

    // Use rendered features query - already spatially filtered and reliable
    try {
      const point = this.map.project(coordinate);
      const renderedFeatures = this.map.queryRenderedFeatures([point.x, point.y]);

      console.log(`Found ${renderedFeatures.length} rendered features at coordinate`);

      // Log ALL features found for debugging
      renderedFeatures.forEach((feature, index) => {
        console.log(`Feature ${index + 1}:`, {
          layerId: feature.layer?.id,
          layerType: feature.layer?.type,
          sourceLayer: feature.layer?.['source-layer'],
          geometryType: feature.geometry?.type,
          hasProperties: !!feature.properties,
          allPropertyKeys: feature.properties ? Object.keys(feature.properties) : [],
          color: feature.properties?.color,
          colorType: typeof feature.properties?.color
        });
      });

      // Look for features with color properties at this exact coordinate
      for (const feature of renderedFeatures) {
        if (feature.properties?.color !== undefined) {
          const terrainType = this.getTerrainFromColor(feature.properties.color);

          if (terrainType === 'water') {
            console.log(`💧 WATER terrain detected: color=${feature.properties.color} → ${terrainType}`);
            return 'water';
          } else if (terrainType !== 'unknown') {
            console.log(`🌍 LAND terrain detected: color=${feature.properties.color} → ${terrainType}`);
            return 'land';
          } else {
            console.log(`❓ Unknown terrain: color=${feature.properties.color} → ${terrainType}`);
          }
        }
      }

      console.log('⚠️ No colored features found at coordinate, using fallback detection');
    } catch (error) {
      console.warn('❌ Rendered features query failed:', error);
    }

    // Fallback to coordinate-based detection
    const fallbackResult = this.getTerrainTypeCoordinateFallback(coordinate);
    console.log(`🔄 Fallback result: ${fallbackResult}`);
    return fallbackResult;
  }

  
  
  /**
   * Quick heuristic to determine if coordinate is in a likely water region
   */
  private isLikelyWaterRegion(coordinate: [number, number]): boolean {
    const [lng, lat] = coordinate;

    // Simple heuristic based on known water regions
    // Inner Sea area between continents
    if (lng > 5 && lng < 35 && lat > 35 && lat < 50) {
      return true;
    }

    // Coastal areas
    if (Math.abs(lng) < 50 && Math.abs(lat) < 60) {
      // Near coastlines - more likely to have water features
      return Math.random() > 0.7; // 30% chance for coastal areas
    }

    return false;
  }

  /**
   * Check if a coordinate is within a feature's geometry
   */
  private coordinateInFeature(coordinate: [number, number], feature: any): boolean {
    if (!feature.geometry) return false;

    // Simple bounding box check for Polygon geometries
    if (feature.geometry.type === 'Polygon' && feature.geometry.coordinates) {
      const coords = feature.geometry.coordinates[0]; // Outer ring
      let minX = Infinity, maxX = -Infinity;
      let minY = Infinity, maxY = -Infinity;

      for (const point of coords) {
        minX = Math.min(minX, point[0]);
        maxX = Math.max(maxX, point[0]);
        minY = Math.min(minY, point[1]);
        maxY = Math.max(maxY, point[1]);
      }

      const [lng, lat] = coordinate;
      return lng >= minX && lng <= maxX && lat >= minY && lat <= maxY;
    }

    // Simple bounding box check for MultiPolygon geometries
    if (feature.geometry.type === 'MultiPolygon' && feature.geometry.coordinates) {
      for (const polygon of feature.geometry.coordinates) {
        const coords = polygon[0]; // Outer ring of each polygon
        let minX = Infinity, maxX = -Infinity;
        let minY = Infinity, maxY = -Infinity;

        for (const point of coords) {
          minX = Math.min(minX, point[0]);
          maxX = Math.max(maxX, point[0]);
          minY = Math.min(minY, point[1]);
          maxY = Math.max(maxY, point[1]);
        }

        const [lng, lat] = coordinate;
        if (lng >= minX && lng <= maxX && lat >= minY && lat <= maxY) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Find nearest terrain type when coordinate is not inside any feature
   */
  private findNearestTerrainType(features: any[], coordinate: [number, number]): 'land' | 'water' {
    // Simple heuristic: find the feature with the smallest bounding box that contains our coordinate
    // For now, we'll use the coordinate-based fallback as a reasonable approximation
    return this.getTerrainTypeCoordinateFallback(coordinate);
  }

  /**
   * Get terrain type from color (supporting multiple color formats)
   */
  private getTerrainFromColor(color: string | number | any): string {
    if (!color) return 'unknown';

    // Convert color to normalized hex string for comparison
    const normalizedColor = this.normalizeColor(color);

    // ACTUAL PF2e color codes from mapping-data repository
    const colorMap: { [key: string]: string } = {
      '#8AB4F8': 'water',    // Waters, rivers, shallow-waters (RGB: 138, 180, 248)
      '#F8F1E1': 'land',     // Land, continents (RGB: 248, 241, 225)
      '#BBE2C6': 'forest',   // Forests (RGB: 187, 226, 198)
      '#DED8B8': 'mountain', // Mountains (RGB: 222, 212, 184)
      '#FFF7BE': 'desert',   // Deserts (RGB: 255, 247, 190)
      '#B7C5BC': 'swamp',    // Swamps (RGB: 183, 197, 188)
      '#FFFFFF': 'ice',      // Ice (transparent white simplified to white)
    };

    const terrainType = colorMap[normalizedColor];

    if (terrainType) {
      console.log(`✓ Color ${color} → ${normalizedColor} mapped to terrain: ${terrainType}`);
      return terrainType;
    }

    // Unknown color - log for debugging and default to land
    console.warn(`⚠️ Unknown color: ${color} → ${normalizedColor} (not in color map). Known colors: ${Object.keys(colorMap).join(', ')}`);
    return 'unknown';
  }

  /**
   * Normalize color to hex string (handles RGB, rgba, number formats)
   */
  private normalizeColor(color: string | number | any): string {
    try {
      // If it's already a hex string, just normalize
      if (typeof color === 'string') {
        const upperColor = color.toUpperCase().trim();

        // Handle hex format
        if (upperColor.startsWith('#')) {
          return upperColor;
        }

        // Handle rgb() format
        if (upperColor.startsWith('RGB(')) {
          const matches = upperColor.match(/\d+/g);
          if (matches && matches.length >= 3) {
            const r = parseInt(matches[0]);
            const g = parseInt(matches[1]);
            const b = parseInt(matches[2]);
            return this.rgbToHex(r, g, b);
          }
        }

        // Handle rgba() format
        if (upperColor.startsWith('RGBA(')) {
          const matches = upperColor.match(/\d+/g);
          if (matches && matches.length >= 3) {
            const r = parseInt(matches[0]);
            const g = parseInt(matches[1]);
            const b = parseInt(matches[2]);
            return this.rgbToHex(r, g, b);
          }
        }

        return upperColor;
      }

      // If it's a number, convert to hex
      if (typeof color === 'number') {
        return '#' + color.toString(16).padStart(6, '0').toUpperCase();
      }

      // If it's an object with r,g,b properties
      if (typeof color === 'object' && color.r !== undefined && color.g !== undefined && color.b !== undefined) {
        return this.rgbToHex(color.r, color.g, color.b);
      }

      return String(color).toUpperCase();
    } catch (error) {
      console.warn('Error normalizing color:', color, error);
      return String(color).toUpperCase();
    }
  }

  /**
   * Convert RGB values to hex string
   */
  private rgbToHex(r: number, g: number, b: number): string {
    const toHex = (n: number) => {
      const hex = Math.max(0, Math.min(255, Math.round(n))).toString(16);
      return hex.padStart(2, '0').toUpperCase();
    };
    return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
  }

  /**
   * Create fallback route when map queries fail
   */
  private createFallbackRoute(start: Position, end: Position): RouteResult {
    console.log('Creating fallback route due to map unavailability');

    // Calculate straight-line distance using Turf.js
    const totalDistance = turfDistance(start, end);

    // Use coordinate-based detection as fallback (existing logic)
    const startTerrain = this.getTerrainTypeCoordinateFallback(start);
    const endTerrain = this.getTerrainTypeCoordinateFallback(end);

    // Default assumption: mixed terrain based on coordinate heuristics
    const waterRatio = this.estimateWaterRatio(start, end);
    const waterDistance = totalDistance * waterRatio;
    const landDistance = totalDistance * (1 - waterRatio);

    console.log(`Fallback route: ${totalDistance.toFixed(2)}km total, ${landDistance.toFixed(2)}km land, ${waterDistance.toFixed(2)}km water`);

    return {
      success: true,
      path: [start, end],
      segments: [
        {
          type: 'land',
          coordinates: [start, end],
          distance: landDistance
        },
        {
          type: 'water',
          coordinates: [start, end],
          distance: waterDistance
        }
      ],
      totalDistance,
      landDistance,
      waterDistance,
      message: 'Route calculated using fallback terrain detection (map queries unavailable)'
    };
  }

  /**
   * Estimate water ratio based on coordinate analysis
   */
  private estimateWaterRatio(start: Position, end: Position): number {
    // Simple heuristic: if both endpoints are near known land areas, assume less water
    // This is a rough fallback - you could make this more sophisticated
    const [startLng, startLat] = start;
    const [endLng, endLat] = end;

    // Known major land areas (very rough approximation)
    const isNearMajorLand = (lng: number, lat: number) => {
      // Absalom area, major cities, etc. would be considered land-heavy
      return Math.abs(lng) < 5 && lat > 25 && lat < 45; // Rough approximation
    };

    const startIsLand = isNearMajorLand(startLng, startLat);
    const endIsLand = isNearMajorLand(endLng, endLat);

    if (startIsLand && endIsLand) {
      return 0.3; // Assume 30% water for land-to-land routes
    } else {
      return 0.7; // Assume 70% water for other routes
    }
  }

  /**
   * Calculate travel times for all methods
   */
  calculateAllTravelTimes(route: RouteResult): TravelTime[] {
    const methods: TravelMethod[] = ['foot', 'horse', 'wagon', 'ship', 'mixed'];

    return methods
      .map(method => this.calculateTravelTime(route, method))
      .filter(time => time.totalDays < Infinity) // Filter out impossible routes
      .sort((a, b) => a.totalDays - b.totalDays); // Sort by fastest first
  }
}
