import { Map as MapLibreMap, LngLatBoundsLike, Marker, GeoJSONSource } from 'maplibre-gl';
import { RouteResult, TravelTime, TRAVEL_METHODS, Pathfinder } from './pathfinder';
import { Position } from 'geojson';

/**
 * Interface for saved route data
 */
interface SavedRoute {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
  routeData: RouteResult;
  startPoint: Position;
  endPoint: Position;
  travelMethod: string;
  totalDistance: number;
  /**
   * Simple collision detection for panel positioning
   */
}

/**
 * Interface for route storage management
 */
interface RouteStorage {
  routes: SavedRoute[];
  /**
   * Simple collision detection for panel positioning
   */
}

/**
 * RouteRenderer handles visualization of calculated routes on the map
 */
export class RouteRenderer {
  private map: MapLibreMap;
  private routeSourceId = 'route-source';
  private landLayerId = 'route-land-layer';
  private landOutlineLayerId = 'route-land-outline-layer';
  // Water type layers
  private riverLayerId = 'route-river-layer';
  private riverOutlineLayerId = 'route-river-outline-layer';
  private shallowWaterLayerId = 'route-shallow-water-layer';
  private shallowWaterOutlineLayerId = 'route-shallow-water-outline-layer';
  private lowSeaLayerId = 'route-low-sea-layer';
  private lowSeaOutlineLayerId = 'route-low-sea-outline-layer';
  private deepSeaLayerId = 'route-deep-sea-layer';
  private deepSeaOutlineLayerId = 'route-deep-sea-outline-layer';
  // Unified water layer (for merged water segments)
  private waterLayerId = 'route-water-layer';
  private waterOutlineLayerId = 'route-water-outline-layer';
  // Legacy deep-water layer (kept for backward compatibility)
  private deepWaterLayerId = 'route-deep-water-layer';
  private deepWaterOutlineLayerId = 'route-deep-water-outline-layer';
  private startMarker: Marker | null = null;
  private endMarker: Marker | null = null;
  private boatMarkers: Marker[] = [];
  private anchorMarkers: Marker[] = [];
  private currentRoute: RouteResult | null = null;
  private currentTravelMethod: string = 'walking';
  private pathfinder: Pathfinder;

  // Segment selection properties
  private highlightedSegmentIndex: number | null = null;
  private segmentClickHandlers: ((e: any) => void)[] = [];
  private isSelectionModeEnabled: boolean = false;

  constructor(map: MapLibreMap) {
    this.map = map;
    this.pathfinder = new Pathfinder(map);
    this.initializeLayers();
  }

  /**
   * Set the current travel method for route saving
   */
  public setTravelMethod(method: string): void {
    this.currentTravelMethod = method;
  }

  /**
   * Get the current travel method
   */
  public getTravelMethod(): string {
    return this.currentTravelMethod;
  }

  /**
   * Initialize map layers for route display
   */
  private initializeLayers(): void {
    this.map.on('load', () => {
      // Add route source
      if (!this.map.getSource(this.routeSourceId)) {
        this.map.addSource(this.routeSourceId, {
          type: 'geojson',
          data: {
            type: 'FeatureCollection',
            features: []
          }
        });
      }

      // Land route outline
      if (!this.map.getLayer(this.landOutlineLayerId)) {
        this.map.addLayer({
          id: this.landOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'land'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#8B4513',
            'line-width': 8,
            'line-opacity': 0.4
          }
        });
      }

      // Land route main line
      if (!this.map.getLayer(this.landLayerId)) {
        this.map.addLayer({
          id: this.landLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'land'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#D2691E',
            'line-width': 4
          }
        });
      }

      // River route outline (light blue, improved visibility)
      if (!this.map.getLayer(this.riverOutlineLayerId)) {
        this.map.addLayer({
          id: this.riverOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'river'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#5BBFEF',
            'line-width': 7,
            'line-opacity': 0.6
          }
        });
      }

      // River route main line (light blue, fine dashes)
      if (!this.map.getLayer(this.riverLayerId)) {
        this.map.addLayer({
          id: this.riverLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'river'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#64ADEF',
            'line-width': 4,
            'line-dasharray': [1, 1]
          }
        });
      }

      // Shallow water route outline (medium blue, increased visibility)
      if (!this.map.getLayer(this.shallowWaterOutlineLayerId)) {
        this.map.addLayer({
          id: this.shallowWaterOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'shallow-water'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#3A8EDF',
            'line-width': 8,
            'line-opacity': 0.6
          }
        });
      }

      // Shallow water route main line (medium blue, distinctive dashes)
      if (!this.map.getLayer(this.shallowWaterLayerId)) {
        this.map.addLayer({
          id: this.shallowWaterLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'shallow-water'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#4A9EFF',
            'line-width': 5,
            'line-dasharray': [3, 2]
          }
        });
      }

      // Deep water route outline (dark blue, increased visibility)
      if (!this.map.getLayer(this.deepWaterOutlineLayerId)) {
        this.map.addLayer({
          id: this.deepWaterOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'deep-water'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#1E5C99',
            'line-width': 9,
            'line-opacity': 0.6
          }
        });
      }

      // Deep water route main line (dark blue, long dashes)
      if (!this.map.getLayer(this.deepWaterLayerId)) {
        this.map.addLayer({
          id: this.deepWaterLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'deep-water'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#2E5C99',
            'line-width': 6,
            'line-dasharray': [5, 3]
          }
        });
      }

      // Low sea route outline (medium-dark blue, more visible)
      if (!this.map.getLayer(this.lowSeaOutlineLayerId)) {
        this.map.addLayer({
          id: this.lowSeaOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'low-sea'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#2A6BB7',
            'line-width': 9,
            'line-opacity': 0.6
          }
        });
      }

      // Low sea route main line (medium blue, distinctive dashes)
      if (!this.map.getLayer(this.lowSeaLayerId)) {
        this.map.addLayer({
          id: this.lowSeaLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'low-sea'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#3A8BE7',
            'line-width': 6,
            'line-dasharray': [4, 2]
          }
        });
      }

      // Deep sea route outline (very dark blue, high contrast)
      if (!this.map.getLayer(this.deepSeaOutlineLayerId)) {
        this.map.addLayer({
          id: this.deepSeaOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'deep-sea'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#1A4A8A',
            'line-width': 10,
            'line-opacity': 0.7
          }
        });
      }

      // Deep sea route main line (dark blue, very distinctive dashes)
      if (!this.map.getLayer(this.deepSeaLayerId)) {
        this.map.addLayer({
          id: this.deepSeaLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'deep-sea'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#2A5A9A',
            'line-width': 7,
            'line-dasharray': [6, 2]
          }
        });
      }

      // Unified water route outline (for merged water segments)
      if (!this.map.getLayer(this.waterOutlineLayerId)) {
        this.map.addLayer({
          id: this.waterOutlineLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'water'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#2A6BB7',
            'line-width': 9,
            'line-opacity': 0.6
          }
        });
      }

      // Unified water route main line (for merged water segments)
      if (!this.map.getLayer(this.waterLayerId)) {
        this.map.addLayer({
          id: this.waterLayerId,
          type: 'line',
          source: this.routeSourceId,
          filter: ['==', ['get', 'terrain'], 'water'],
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': '#3A8BE7',
            'line-width': 6,
            'line-dasharray': [4, 2]
          }
        });
      }
    });

    // Initialize segment selection functionality
    this.initializeSegmentSelection();
  }

  /**
   * Initialize segment selection and highlighting
   */
  private initializeSegmentSelection(): void {
    // Enable segment selection by default
    this.enableSegmentSelection();
  }

  /**
   * Enable segment selection mode
   */
  public enableSegmentSelection(): void {
    if (this.isSelectionModeEnabled) return;

    this.isSelectionModeEnabled = true;

    // Add click handlers for all route layers
    const routeLayers = [
      this.landLayerId,
      this.riverLayerId,
      this.shallowWaterLayerId,
      this.lowSeaLayerId,
      this.deepSeaLayerId,
      this.waterLayerId,
      this.deepWaterLayerId
    ];

    routeLayers.forEach(layerId => {
      if (this.map.getLayer(layerId)) {
        const clickHandler = (e: any) => {
          // Check if the click is on a route feature
          const features = this.map.queryRenderedFeatures(e.point, {
            layers: [layerId]
          });

          if (features.length > 0 && features[0].properties && features[0].properties.terrain) {
            e.preventDefault(); // Prevent default map behavior
            this.handleSegmentClick(features[0]);
          }
        };

        this.map.on('click', layerId, clickHandler);
        this.segmentClickHandlers.push(clickHandler);
      }
    });
  }

  /**
   * Disable segment selection mode
   */
  public disableSegmentSelection(): void {
    if (!this.isSelectionModeEnabled) return;

    this.isSelectionModeEnabled = false;

    // Remove all click handlers
    this.segmentClickHandlers.forEach(handler => {
      this.map.off('click', undefined, handler);
    });

    this.segmentClickHandlers = [];
    this.clearSegmentHighlight();
  }

  /**
   * Handle segment click event
   */
  private handleSegmentClick(feature: any): void {
    if (!this.currentRoute || !this.currentRoute.segments) return;

    // Find which segment was clicked by comparing coordinates
    const clickedSegmentIndex = this.findSegmentIndex(feature);

    if (clickedSegmentIndex !== -1) {
      this.highlightSegment(clickedSegmentIndex);
    }
  }

  /**
   * Find segment index by comparing clicked feature coordinates
   */
  private findSegmentIndex(clickedFeature: any): number {
    if (!this.currentRoute || !this.currentRoute.segments) return -1;

    const clickedCoords = clickedFeature.geometry.coordinates;
    const tolerance = 0.0001; // Coordinate tolerance for matching

    for (let i = 0; i < this.currentRoute.segments.length; i++) {
      const segment = this.currentRoute.segments[i];
      const segmentCoords = segment.coordinates;

      // Check if any coordinate in the segment matches the clicked coordinate
      for (const coord of segmentCoords) {
        if (Math.abs(coord[0] - clickedCoords[0]) < tolerance &&
          Math.abs(coord[1] - clickedCoords[1]) < tolerance) {
          return i;
        }
      }
    }

    return -1;
  }

  /**
   * Highlight a specific segment
   */
  private highlightSegment(segmentIndex: number): void {
    // Clear previous highlight
    this.clearSegmentHighlight();

    this.highlightedSegmentIndex = segmentIndex;

    // Update layer properties to highlight the segment
    this.updateSegmentHighlight(segmentIndex, true);

    // Show segment details
    this.showSegmentDetails(segmentIndex);
  }

  /**
   * Clear segment highlight
   */
  public clearSegmentHighlight(): void {
    if (this.highlightedSegmentIndex !== null) {
      this.updateSegmentHighlight(this.highlightedSegmentIndex, false);
      this.highlightedSegmentIndex = null;
    }
  }

  /**
   * Update segment highlighting state
   */
  private updateSegmentHighlight(segmentIndex: number, isHighlighted: boolean): void {
    if (!this.currentRoute || !this.currentRoute.segments) return;

    const segment = this.currentRoute.segments[segmentIndex];
    const terrainType = this.getLayerTerrainType(segment.type);

    // Find the corresponding layer IDs
    const mainLayerId = this.getLayerId(terrainType, false);
    const outlineLayerId = this.getLayerId(terrainType, true);

    if (this.map.getLayer(mainLayerId)) {
      // Update paint properties for highlighting
      if (isHighlighted) {
        this.map.setPaintProperty(mainLayerId, 'line-width', 8); // Thicker line
        this.map.setPaintProperty(mainLayerId, 'line-opacity', 1.0); // Full opacity
      } else {
        // Reset to original width based on terrain type
        const originalWidth = this.getOriginalLineWidth(terrainType);
        this.map.setPaintProperty(mainLayerId, 'line-width', originalWidth);
        this.map.setPaintProperty(mainLayerId, 'line-opacity', 1.0);
      }
    }

    if (this.map.getLayer(outlineLayerId)) {
      if (isHighlighted) {
        this.map.setPaintProperty(outlineLayerId, 'line-width', 11); // Thicker outline
        this.map.setPaintProperty(outlineLayerId, 'line-opacity', 0.9); // Higher opacity
      } else {
        // Reset to original outline width
        const originalOutlineWidth = this.getOriginalOutlineWidth(terrainType);
        this.map.setPaintProperty(outlineLayerId, 'line-width', originalOutlineWidth);
        this.map.setPaintProperty(outlineLayerId, 'line-opacity', this.getOriginalOutlineOpacity(terrainType));
      }
    }
  }

  /**
   * Show segment details in console or UI
   */
  private showSegmentDetails(segmentIndex: number): void {
    if (!this.currentRoute || !this.currentRoute.segments) return;

    const segment = this.currentRoute.segments[segmentIndex];
    const terrainLabel = this.getTerrainLabel(segment.type);
    const segmentNumber = segmentIndex + 1;
    const totalSegments = this.currentRoute.segments.length;

    console.log(`=== SEGMENT ${segmentNumber} of ${totalSegments} ===`);
    console.log(`🏷️  Terrain: ${terrainLabel}`);
    console.log(`📏 Distance: ${segment.distance.toFixed(2)} km`);
    console.log(`📍 Points: ${segment.coordinates.length}`);
    console.log(`⏱️  Index: ${segmentIndex}`);
    console.log('=============================');
  }

  /**
   * Get terrain type for layer ID mapping
   */
  private getLayerTerrainType(terrainType: string): string {
    const typeMap: Record<string, string> = {
      'land': 'land',
      'river': 'river',
      'shallow-water': 'shallow-water',
      'low-sea': 'low-sea',
      'deep-sea': 'deep-sea',
      'water': 'water',
      'deep-water': 'deep-water'
    };
    return typeMap[terrainType] || terrainType;
  }

  /**
   * Get layer ID for terrain type
   */
  private getLayerId(terrainType: string, isOutline: boolean): string {
    const layerMap: Record<string, { main: string; outline: string }> = {
      'land': { main: this.landLayerId, outline: this.landOutlineLayerId },
      'river': { main: this.riverLayerId, outline: this.riverOutlineLayerId },
      'shallow-water': { main: this.shallowWaterLayerId, outline: this.shallowWaterOutlineLayerId },
      'low-sea': { main: this.lowSeaLayerId, outline: this.lowSeaOutlineLayerId },
      'deep-sea': { main: this.deepSeaLayerId, outline: this.deepSeaOutlineLayerId },
      'water': { main: this.waterLayerId, outline: this.waterOutlineLayerId },
      'deep-water': { main: this.deepWaterLayerId, outline: this.deepWaterOutlineLayerId }
    };

    const layer = layerMap[terrainType];
    return layer ? (isOutline ? layer.outline : layer.main) : '';
  }

  /**
   * Get original line width for terrain type
   */
  private getOriginalLineWidth(terrainType: string): number {
    const widthMap: Record<string, number> = {
      'land': 4,
      'river': 4,
      'shallow-water': 5,
      'low-sea': 6,
      'deep-sea': 7,
      'water': 6,
      'deep-water': 5
    };
    return widthMap[terrainType] || 4;
  }

  /**
   * Get original outline width for terrain type
   */
  private getOriginalOutlineWidth(terrainType: string): number {
    const widthMap: Record<string, number> = {
      'land': 7,
      'river': 7,
      'shallow-water': 8,
      'low-sea': 9,
      'deep-sea': 10,
      'water': 9,
      'deep-water': 8
    };
    return widthMap[terrainType] || 7;
  }

  /**
   * Get original outline opacity for terrain type
   */
  private getOriginalOutlineOpacity(terrainType: string): number {
    const opacityMap: Record<string, number> = {
      'land': 0.4,
      'river': 0.6,
      'shallow-water': 0.6,
      'low-sea': 0.6,
      'deep-sea': 0.6,
      'water': 0.6,
      'deep-water': 0.4
    };
    return opacityMap[terrainType] || 0.4;
  }

  /**
   * Get currently highlighted segment index
   */
  public getHighlightedSegmentIndex(): number | null {
    return this.highlightedSegmentIndex;
  }

  /**
   * Display a route on the map
   */
  displayRoute(route: RouteResult, startLabel?: string, endLabel?: string): void {
    if (!route.success) {
      console.warn('Cannot display invalid route');
      return;
    }

    this.currentRoute = route;

    // Update route lines
    this.updateRouteLines(route);

    // Add start/end markers
    const start = route.path[0];
    const end = route.path[route.path.length - 1];
    this.addMarkers(start, end, startLabel, endLabel);

    // Add boat icons on water segments
    this.addBoatMarkers(route);

    // Add anchor icons at land/water transitions
    this.addAnchorMarkers(route);

    // Fit map to route bounds
    this.fitToRoute(route.path);
  }

  /**
   * Update route lines on map with terrain-aware styling
   */
  private updateRouteLines(route: RouteResult): void {
    const features = route.segments.map(segment => ({
      type: 'Feature' as const,
      geometry: {
        type: 'LineString' as const,
        coordinates: segment.coordinates
      },
      properties: {
        terrain: segment.type,
        distance: segment.distance
      }
    }));

    const source = this.map.getSource(this.routeSourceId) as GeoJSONSource;
    if (source && source.type === 'geojson') {
      source.setData({
        type: 'FeatureCollection',
        features
      });
    }
  }

  /**
   * Add start and end markers
   */
  private addMarkers(start: Position, end: Position, startLabel?: string, endLabel?: string): void {
    // Remove existing markers
    this.clearMarkers();

    // Create start marker (green)
    const startEl = this.createMarkerElement('A', '#4CAF50', startLabel);
    this.startMarker = new Marker({ element: startEl })
      .setLngLat([start[0], start[1]])
      .addTo(this.map);

    // Create end marker (red)
    const endEl = this.createMarkerElement('B', '#F44336', endLabel);
    this.endMarker = new Marker({ element: endEl })
      .setLngLat([end[0], end[1]])
      .addTo(this.map);
  }

  /**
   * Add boat icons along water segments
   */
  private addBoatMarkers(route: RouteResult): void {
    // Clear existing boat markers
    this.boatMarkers.forEach(marker => marker.remove());
    this.boatMarkers = [];

    for (const segment of route.segments) {
      if (segment.type === 'water' && segment.distance > 50) {
        // Add boat icons every ~100km on water segments
        const numBoats = Math.floor(segment.distance / 100);
        const interval = segment.coordinates.length / (numBoats + 1);

        for (let i = 1; i <= numBoats; i++) {
          const index = Math.floor(i * interval);
          if (index < segment.coordinates.length) {
            const coord = segment.coordinates[index];
            const boatEl = this.createBoatIcon();
            const marker = new Marker({ element: boatEl })
              .setLngLat([coord[0], coord[1]])
              .addTo(this.map);
            this.boatMarkers.push(marker);
          }
        }
      }
    }
  }

  /**
   * Add anchor icons at land/water transitions
   */
  private addAnchorMarkers(route: RouteResult): void {
    // Clear existing anchor markers
    this.anchorMarkers.forEach(marker => marker.remove());
    this.anchorMarkers = [];

    if (route.segments.length < 2) return;

    for (let i = 0; i < route.segments.length - 1; i++) {
      const current = route.segments[i];
      const next = route.segments[i + 1];

      // Check for terrain transition
      if (current.type !== next.type) {
        // Get transition point (end of current segment / start of next)
        const coord = current.coordinates[current.coordinates.length - 1];
        const anchorEl = this.createAnchorIcon();
        const marker = new Marker({ element: anchorEl })
          .setLngLat([coord[0], coord[1]])
          .addTo(this.map);
        this.anchorMarkers.push(marker);
      }
    }
  }

  /**
   * Create a boat icon element
   */
  private createBoatIcon(): HTMLElement {
    const el = document.createElement('div');
    el.className = 'route-boat-icon';
    el.style.cssText = `
      width: 30px;
      height: 30px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      line-height: 1;
      text-shadow: 0 0 3px white, 0 0 5px white;
      user-select: none;
    `;
    el.textContent = '⛵';
    el.title = 'Sea route';
    return el;
  }

  /**
   * Create an anchor icon element
   */
  private createAnchorIcon(): HTMLElement {
    const el = document.createElement('div');
    el.className = 'route-anchor-icon';
    el.style.cssText = `
      font-size: 20px;
      line-height: 1;
      text-shadow: 0 0 3px white, 0 0 5px white;
    `;
    el.textContent = '⚓';
    el.title = 'Port / Land-Water transition';
    return el;
  }

  /**
   * Create a marker element
   */
  private createMarkerElement(label: string, color: string, title?: string): HTMLElement {
    const el = document.createElement('div');
    el.className = 'route-marker';
    el.style.cssText = `
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background-color: ${color};
      border: 3px solid white;
      color: white;
      font-weight: bold;
      font-size: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 4px rgba(0,0,0,0.3);
      cursor: pointer;
    `;
    el.textContent = label;

    if (title) {
      el.title = title;
    }

    return el;
  }

  /**
   * Fit map view to route
   */
  private fitToRoute(path: Position[], padding: number = 100): void {
    if (path.length === 0) return;

    // Calculate bounding box
    let minLng = path[0][0];
    let maxLng = path[0][0];
    let minLat = path[0][1];
    let maxLat = path[0][1];

    for (const coord of path) {
      minLng = Math.min(minLng, coord[0]);
      maxLng = Math.max(maxLng, coord[0]);
      minLat = Math.min(minLat, coord[1]);
      maxLat = Math.max(maxLat, coord[1]);
    }

    const bounds: LngLatBoundsLike = [
      [minLng, minLat],
      [maxLng, maxLat]
    ];

    this.map.fitBounds(bounds, {
      padding: padding,
      duration: 1500
    });
  }

  /**
   * Clear route from map
   */
  clearRoute(): void {
    // Clear route lines
    const source = this.map.getSource(this.routeSourceId);
    if (source && source.type === 'geojson') {
      source.setData({
        type: 'FeatureCollection',
        features: []
      });
    }

    // Clear all markers
    this.clearMarkers();

    this.currentRoute = null;
  }

  /**
   * Clear all markers
   */
  private clearMarkers(): void {
    if (this.startMarker) {
      this.startMarker.remove();
      this.startMarker = null;
    }

    if (this.endMarker) {
      this.endMarker.remove();
      this.endMarker = null;
    }

    this.boatMarkers.forEach(marker => marker.remove());
    this.boatMarkers = [];

    this.anchorMarkers.forEach(marker => marker.remove());
    this.anchorMarkers = [];
  }

  /**
   * Get current route
   */
  getCurrentRoute(): RouteResult | null {
    return this.currentRoute;
  }

  /**
   * Show route info panel with travel time breakdown
   */
  showRouteInfo(route: RouteResult, travelTimes: TravelTime[]): HTMLElement {
    const panel = document.createElement('div');
    panel.className = 'golarion-route-panel';

    // Header
    const header = document.createElement('div');
    header.className = 'golarion-route-header';

    const title = document.createElement('h3');
    title.textContent = 'Route Details';

    const closeBtn = document.createElement('button');
    closeBtn.className = 'golarion-close-btn';
    closeBtn.innerHTML = '×';
    closeBtn.onclick = () => panel.remove();

    header.appendChild(title);
    header.appendChild(closeBtn);
    panel.appendChild(header);

    // Stats Grid
    const statsGrid = document.createElement('div');
    statsGrid.className = 'golarion-stats-grid';

    // Distance Card
    const distCard = document.createElement('div');
    distCard.className = 'golarion-stat-card';
    distCard.innerHTML = `
      <div class="stat-icon">📏</div>
      <div class="stat-content">
        <div class="stat-label">Distance</div>
        <div class="stat-value">${route.totalDistance.toFixed(1)} km</div>
        <div class="stat-sub">${(route.totalDistance * 0.621371).toFixed(1)} mi</div>
      </div>
    `;
    statsGrid.appendChild(distCard);

    // Time Card (Best Time)
    if (travelTimes.length > 0) {
      const bestTime = travelTimes[0];
      const timeCard = document.createElement('div');
      timeCard.className = 'golarion-stat-card';
      timeCard.innerHTML = `
        <div class="stat-icon">⏱️</div>
        <div class="stat-content">
          <div class="stat-label">Est. Time</div>
          <div class="stat-value">${bestTime.description.split(' ')[0]} ${bestTime.description.split(' ')[1]}</div>
          <div class="stat-sub">${TRAVEL_METHODS[bestTime.method].name}</div>
        </div>
      `;
      statsGrid.appendChild(timeCard);
    }

    panel.appendChild(statsGrid);

    // Terrain Profile Bar
    const profileSection = document.createElement('div');
    profileSection.className = 'golarion-profile-section';

    const profileTitle = document.createElement('div');
    profileTitle.className = 'section-title';
    profileTitle.textContent = 'Terrain Profile';

    const profileBar = document.createElement('div');
    profileBar.className = 'golarion-terrain-bar';

    route.segments.forEach(segment => {
      const segmentBar = document.createElement('div');
      segmentBar.className = `terrain-segment ${segment.type}`;
      const width = (segment.distance / route.totalDistance) * 100;
      segmentBar.style.width = `${width}%`;
      segmentBar.title = `${this.getTerrainLabel(segment.type)}: ${segment.distance.toFixed(1)} km`;
      profileBar.appendChild(segmentBar);
    });

    profileSection.appendChild(profileTitle);
    profileSection.appendChild(profileBar);

    // Legend
    const legend = document.createElement('div');
    legend.className = 'golarion-terrain-legend';
    const terrainTypes = ['land', 'river', 'shallow-water', 'deep-water'];
    terrainTypes.forEach(type => {
      const item = document.createElement('div');
      item.className = 'legend-item';
      item.innerHTML = `<span class="dot ${type}"></span>${this.getTerrainLabel(type)}`;
      legend.appendChild(item);
    });
    profileSection.appendChild(legend);

    panel.appendChild(profileSection);

    // Route Segments Detail
    if (route.segments && route.segments.length > 0) {
      const segmentsSection = document.createElement('div');
      segmentsSection.className = 'golarion-segments-section';

      const segmentsTitle = document.createElement('div');
      segmentsTitle.className = 'section-title';
      segmentsTitle.textContent = `Route Segments (${route.segments.length} total)`;
      segmentsSection.appendChild(segmentsTitle);

      // Create scrollable container for segments
      const segmentsList = document.createElement('div');
      segmentsList.className = 'golarion-segments-list';

      let cumulativeDistance = 0;
      let cumulativeTime = 0;

      route.segments.forEach((segment, index) => {
        cumulativeDistance += segment.distance;

        const segmentRow = document.createElement('div');
        segmentRow.className = 'golarion-segment-row';
        segmentRow.dataset.segmentIndex = index.toString();

        // Make segment row clickable to highlight on map
        segmentRow.onclick = () => {
          this.highlightSegment(index);
        };

        // Segment number and terrain type
        const segmentHeader = document.createElement('div');
        segmentHeader.className = 'segment-header';

        const segmentNumber = document.createElement('span');
        segmentNumber.className = 'segment-number';
        segmentNumber.textContent = `${index + 1}.`;

        const terrainLabel = document.createElement('span');
        terrainLabel.className = `segment-terrain terrain-${segment.type}`;
        terrainLabel.textContent = this.getTerrainLabel(segment.type);

        segmentHeader.appendChild(segmentNumber);
        segmentHeader.appendChild(terrainLabel);

        // Segment distance
        const segmentDistance = document.createElement('div');
        segmentDistance.className = 'segment-distance';
        segmentDistance.textContent = `${segment.distance.toFixed(1)} km`;

        // Calculate travel time using the fastest method
        let segmentTime = 0;
        if (travelTimes.length > 0) {
          const fastestMethod = TRAVEL_METHODS[travelTimes[0].method];
          segmentTime = this.calculateSegmentTime(segment, fastestMethod);
          cumulativeTime += segmentTime;

          const segmentTimeDiv = document.createElement('div');
          segmentTimeDiv.className = 'segment-time';
          const hours = segmentTime * 24;
          if (hours < 1) {
            segmentTimeDiv.textContent = `${(hours * 60).toFixed(0)} min`;
          } else if (hours < 24) {
            segmentTimeDiv.textContent = `${hours.toFixed(1)} hrs`;
          } else {
            segmentTimeDiv.textContent = `${segmentTime.toFixed(1)} days`;
          }
          segmentRow.appendChild(segmentTimeDiv);
        }

        // Cumulative info (tooltip)
        segmentRow.title = `Cumulative: ${cumulativeDistance.toFixed(1)} km`;

        segmentRow.appendChild(segmentHeader);
        segmentRow.appendChild(segmentDistance);

        segmentsList.appendChild(segmentRow);
      });

      segmentsSection.appendChild(segmentsList);
      panel.appendChild(segmentsSection);
    }

    // Travel Options
    if (travelTimes.length > 0) {
      const optionsSection = document.createElement('div');
      optionsSection.className = 'golarion-options-section';

      const optionsTitle = document.createElement('div');
      optionsTitle.className = 'section-title';
      optionsTitle.textContent = 'Travel Options';
      optionsSection.appendChild(optionsTitle);

      travelTimes.slice(0, 3).forEach(time => {
        const method = TRAVEL_METHODS[time.method];
        const optionRow = document.createElement('div');
        optionRow.className = 'golarion-option-row';

        const iconDiv = document.createElement('div');
        iconDiv.className = 'option-icon';
        const icon = this.createTravelIcon(method.icon);
        icon.style.width = '100%';
        icon.style.height = '100%';
        icon.style.objectFit = 'contain';
        iconDiv.appendChild(icon);

        const detailsDiv = document.createElement('div');
        detailsDiv.className = 'option-details';
        detailsDiv.innerHTML = `
          <div class="option-name">${method.name}</div>
          <div class="option-time">${time.description}</div>
        `;

        optionRow.appendChild(iconDiv);
        optionRow.appendChild(detailsDiv);
        optionsSection.appendChild(optionRow);
      });

      panel.appendChild(optionsSection);
    }

    // Apply simple positioning with collision avoidance
    const searchControl = document.querySelector(".golarion-search-control");
    let leftPosition = 20;
    let bottomPosition = 80;
    
    // Simple collision check with search control
    if (searchControl) {
      const searchRect = searchControl.getBoundingClientRect();
      if (window.innerWidth - 420 < searchRect.right + 20) {
        leftPosition = searchRect.right + 20;
      }
    }
    
    panel.style.position = "absolute";
    panel.style.left = `${leftPosition}px`;
    panel.style.bottom = `${bottomPosition}px`;
    return panel;
  }

  /**
   * Create travel icon element
   */
  private createTravelIcon(iconPath: string, className: string = 'travel-mode-icon'): HTMLElement {
    const iconEl = document.createElement('img');
    iconEl.src = iconPath;
    iconEl.className = className;
    iconEl.alt = ''; // Will be set by calling code
    iconEl.style.cssText = `
      width: 20px;
      height: 20px;
      object-fit: contain;
      filter: drop-shadow(0 1px 2px rgba(0,0,0,0.2));
      vertical-align: middle;
    `;
    return iconEl;
  }

  /**
   * Helper methods for segment details
   */
  private getTerrainColor(terrainType: string): string {
    const colors: Record<string, string> = {
      'land': '#8B4513',        // Brown (Saddle Brown)
      'river': '#64ADEF',       // Light blue (Dodger Blue)
      'shallow-water': '#4A9EFF', // Light blue (Royal Blue)
      'low-sea': '#3A8BE7',     // Medium blue
      'deep-sea': '#2A5A9A',    // Dark blue
      'water': '#3A8BE7',       // Medium blue (for unified water segments)
      // Legacy support for backward compatibility
      'deep-water': '#2E5C99'   // Dark blue (legacy)
    };
    return colors[terrainType] || '#999';
  }

  private getTerrainIcon(terrainType: string): string {
    const icons: Record<string, string> = {
      'land': '🏔️',
      'river': '🏞️',
      'shallow-water': '🌊',
      'low-sea': '🌊',
      'deep-sea': '🌊',
      'water': '🌊',           // For unified water segments
      // Legacy support for backward compatibility
      'deep-water': '🌊'
    };
    return icons[terrainType] || '❓';
  }

  private getTerrainLabel(terrainType: string): string {
    const labels: Record<string, string> = {
      'land': 'Land',
      // All water types show as simple "Sea" for users
      'river': 'Sea',
      'shallow-water': 'Sea',
      'low-sea': 'Sea',
      'deep-sea': 'Sea',
      'water': 'Sea',           // For unified water segments
      // Legacy support for backward compatibility
      'deep-water': 'Sea'
    };
    return labels[terrainType] || terrainType;
  }

  private calculateSegmentTime(segment: any, method: any): number {
    if (segment.type === 'land') {
      return method.landSpeed > 0 ? segment.distance / method.landSpeed : Infinity;
    } else {
      return method.waterSpeed > 0 ? segment.distance / method.waterSpeed : Infinity;
    }
  }

  // ==================== ROUTE SAVING FUNCTIONALITY ====================

  /**
   * Save current route to localStorage
   */
  public saveRoute(name?: string, description?: string, travelMethod?: string): boolean {
    if (!this.currentRoute) {
      console.error('No route to save');
      return false;
    }

    try {
      // Generate a unique ID for the route
      const routeId = this.generateRouteId();

      // Extract start and end points from the route data
      let startPoint: Position = [0, 0];
      let endPoint: Position = [0, 0];

      if (this.currentRoute.path && this.currentRoute.path.length > 0) {
        // Get coordinates from the first and last points of the path
        startPoint = this.currentRoute.path[0];
        endPoint = this.currentRoute.path[this.currentRoute.path.length - 1];
      }

      const savedRoute: SavedRoute = {
        id: routeId,
        name: name || `Route ${new Date().toLocaleDateString()}`,
        description: description || '',
        createdAt: new Date().toISOString(),
        routeData: this.currentRoute,
        startPoint: startPoint,
        endPoint: endPoint,
        travelMethod: travelMethod || 'walking',
        totalDistance: this.currentRoute.totalDistance || 0
      };

      // Get existing routes from storage
      const storage = this.getRouteStorage();
      storage.routes.push(savedRoute);

      // Save to localStorage
      localStorage.setItem('pf2e_saved_routes', JSON.stringify(storage));

      console.log(`Route "${savedRoute.name}" saved successfully!`);
      console.log('Route details:', savedRoute);

      return true;
    } catch (error) {
      console.error('Error saving route:', error);
      return false;
    }
  }

  /**
   * Load all saved routes from localStorage
   */
  public loadSavedRoutes(): SavedRoute[] {
    try {
      const storage = this.getRouteStorage();
      return storage.routes;
    } catch (error) {
      console.error('Error loading saved routes:', error);
      return [];
    }
  }

  /**
   * Load and display a specific saved route
   */
  public loadSavedRoute(routeId: string): boolean {
    try {
      const storage = this.getRouteStorage();
      const route = storage.routes.find(r => r.id === routeId);

      if (!route) {
        console.error(`Route with ID ${routeId} not found`);
        return false;
      }

      // Display the loaded route
      this.displayRoute(route.routeData);

      // Calculate travel times for the loaded route
      const travelTimes = this.pathfinder.calculateAllTravelTimes(route.routeData);

      // Set the travel method from the saved route or fastest available
      if (travelTimes.length > 0) {
        // Use the saved travel method if it exists and is available, otherwise use fastest
        const savedMethod = travelTimes.find(t => t.method === route.travelMethod);
        this.currentTravelMethod = savedMethod ? route.travelMethod : travelTimes[0].method;
      }

      // Show route info panel with travel times
      const routeInfoPanel = this.showRouteInfo(route.routeData, travelTimes);

      // Add the route info panel to the map container
      const mapContainer = document.getElementById('map-container');
      if (mapContainer) {
        // Remove any existing route info panel first
        const existingPanel = mapContainer.querySelector('.route-info-panel');
        if (existingPanel) {
          existingPanel.remove();
        }
        mapContainer.appendChild(routeInfoPanel);
      }

      console.log(`Loaded route: ${route.name}`);
      console.log('Route details:', route);
      console.log('Travel times:', travelTimes);

      return true;
    } catch (error) {
      console.error('Error loading saved route:', error);
      return false;
    }
  }

  /**
   * Delete a saved route
   */
  public deleteSavedRoute(routeId: string): boolean {
    try {
      const storage = this.getRouteStorage();
      const routeIndex = storage.routes.findIndex(r => r.id === routeId);

      if (routeIndex === -1) {
        console.error(`Route with ID ${routeId} not found`);
        return false;
      }

      const deletedRoute = storage.routes[routeIndex];
      storage.routes.splice(routeIndex, 1);

      // Save updated storage
      localStorage.setItem('pf2e_saved_routes', JSON.stringify(storage));

      console.log(`Deleted route: ${deletedRoute.name}`);

      return true;
    } catch (error) {
      console.error('Error deleting saved route:', error);
      return false;
    }
  }

  /**
   * Show saved routes management panel
   */
  public showSavedRoutesPanel(): void {
    // Remove existing panel if present
    const existingPanel = document.getElementById('saved-routes-panel');
    if (existingPanel) {
      existingPanel.remove();
    }

    const panel = document.createElement('div');
    panel.id = 'saved-routes-panel';
    // Apply simple positioning for saved routes panel
    let leftPosition = window.innerWidth - 420;
    let bottomPosition = 80;
    
    // Ensure panel stays within screen bounds
    leftPosition = Math.max(20, Math.min(leftPosition, window.innerWidth - 420));
    
    panel.style.cssText = `
      position: absolute;
      left: ${leftPosition}px;
      bottom: ${bottomPosition}px`;
      border: 2px solid #ccc;
      border-radius: 8px;
      padding: 15px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      z-index: 200;
      max-width: 400px;
      max-height: 500px;
      overflow-y: auto;
      font-family: Arial, sans-serif;
    `;

    // Panel header
    const header = document.createElement('div');
    header.innerHTML = `
      <h3 style="margin: 0 0 15px 0; color: #333;">📍 Saved Routes</h3>
      <p style="margin: 0 0 15px 0; color: #666; font-size: 14px;">
        Save and load your custom routes locally
      </p>
    `;

    // Save current route button
    const saveCurrentBtn = document.createElement('button');
    saveCurrentBtn.textContent = '💾 Save Current Route';
    saveCurrentBtn.style.cssText = `
      width: 100%;
      padding: 10px;
      margin-bottom: 15px;
      background: #4CAF50;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      font-weight: bold;
    `;
    saveCurrentBtn.onmouseover = () => saveCurrentBtn.style.background = '#45a049';
    saveCurrentBtn.onmouseout = () => saveCurrentBtn.style.background = '#4CAF50';
    saveCurrentBtn.onclick = () => {
      this.showSaveRouteDialog((name, description) => {
        if (this.saveRoute(name, description, this.currentTravelMethod)) {
          this.showSavedRoutesPanel(); // Refresh the panel
          this.showToast('Route saved successfully!', 'success');
        } else {
          this.showToast('Failed to save route', 'error');
        }
      });
    };

    // Saved routes list
    const routesList = document.createElement('div');
    routesList.id = 'saved-routes-list';

    const savedRoutes = this.loadSavedRoutes();

    if (savedRoutes.length === 0) {
      routesList.innerHTML = '<p style="color: #999; font-style: italic;">No saved routes yet</p>';
    } else {
      savedRoutes.forEach(route => {
        const routeItem = document.createElement('div');
        routeItem.style.cssText = `
          border: 1px solid #ddd;
          border-radius: 4px;
          padding: 10px;
          margin-bottom: 10px;
          background: #f9f9f9;
        `;

        const createdDate = new Date(route.createdAt).toLocaleDateString();
        const distance = route.totalDistance.toFixed(1);

        routeItem.innerHTML = `
          <div style="font-weight: bold; color: #333; margin-bottom: 5px;">${route.name}</div>
          <div style="font-size: 12px; color: #666; margin-bottom: 8px; display: flex; align-items: center; gap: 4px;">
            ${route.description ? `${route.description}<br>` : ''}
            📅 ${createdDate} | 🛣️ ${distance}km
          </div>
          <div style="font-size: 12px; color: #666; margin-bottom: 8px; display: flex; align-items: center; gap: 4px;">
            <img src="${TRAVEL_METHODS[route.travelMethod as keyof typeof TRAVEL_METHODS]?.icon || '/icons/travel-foot.svg'}"
                 alt="${TRAVEL_METHODS[route.travelMethod as keyof typeof TRAVEL_METHODS]?.name || route.travelMethod}"
                 style="width: 14px; height: 14px; object-fit: contain;">
            ${TRAVEL_METHODS[route.travelMethod as keyof typeof TRAVEL_METHODS]?.name || route.travelMethod}
          </div>
          <div style="display: flex; gap: 5px;">
            <button class="load-route-btn" data-route-id="${route.id}"
                    style="flex: 1; padding: 5px; background: #2196F3; color: white;
                           border: none; border-radius: 3px; cursor: pointer; font-size: 12px;">
              📍 Load
            </button>
            <button class="delete-route-btn" data-route-id="${route.id}"
                    style="flex: 1; padding: 5px; background: #f44336; color: white;
                           border: none; border-radius: 3px; cursor: pointer; font-size: 12px;">
              🗑️ Delete
            </button>
          </div>
        `;

        routesList.appendChild(routeItem);
      });

      // Add event listeners for buttons
      routesList.querySelectorAll('.load-route-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const routeId = (e.target as HTMLElement).getAttribute('data-route-id');
          if (routeId && this.loadSavedRoute(routeId)) {
            this.showSavedRoutesPanel(); // Refresh panel
            this.showToast('Route loaded successfully!', 'success');
          }
        });
      });

      routesList.querySelectorAll('.delete-route-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const routeId = (e.target as HTMLElement).getAttribute('data-route-id');
          if (routeId) {
            this.showConfirmDialog('Are you sure you want to delete this route?', () => {
              if (this.deleteSavedRoute(routeId)) {
                this.showSavedRoutesPanel(); // Refresh panel
                this.showToast('Route deleted successfully!', 'success');
              } else {
                this.showToast('Failed to delete route', 'error');
              }
            });
          }
        });
      });
    }

    // Close button
    const closeBtn = document.createElement('button');
    closeBtn.textContent = '❌ Close';
    closeBtn.style.cssText = `
      width: 100%;
      padding: 8px;
      background: #666;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      margin-top: 15px;
    `;
    closeBtn.onclick = () => panel.remove();

    // Assemble panel
    panel.appendChild(header);
    panel.appendChild(saveCurrentBtn);
    panel.appendChild(routesList);
    panel.appendChild(closeBtn);

    // Add to map container
    this.map.getContainer().appendChild(panel);
  }

  /**
   * Private helper methods for route storage
   */
  private generateRouteId(): string {
    return 'route_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
  }

  private getRouteStorage(): RouteStorage {
    try {
      const stored = localStorage.getItem('pf2e_saved_routes');
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (error) {
      console.warn('Error reading saved routes from storage:', error);
    }

    return { routes: [] };
  }

  // ==================== CUSTOM UI DIALOGS ====================

  /**
   * Show toast notification
   */
  private showToast(message: string, type: 'success' | 'error' | 'info' = 'success'): void {
    const existingToast = document.getElementById('golarion-toast');
    if (existingToast) {
      existingToast.remove();
    }

    const toast = document.createElement('div');
    toast.id = 'golarion-toast';
    toast.className = `golarion-toast golarion-toast-${type}`;
    toast.textContent = message;

    document.body.appendChild(toast);

    // Trigger animation
    setTimeout(() => toast.classList.add('show'), 10);

    // Auto-dismiss after 3 seconds
    setTimeout(() => {
      toast.classList.remove('show');
      setTimeout(() => toast.remove(), 300);
    }, 3000);
  }

  /**
   * Show confirmation dialog
   */
  private showConfirmDialog(message: string, onConfirm: () => void, onCancel?: () => void): void {
    const overlay = document.createElement('div');
    overlay.className = 'golarion-modal-overlay';

    const dialog = document.createElement('div');
    dialog.className = 'golarion-confirm-dialog';

    const messageDiv = document.createElement('div');
    messageDiv.className = 'confirm-message';
    messageDiv.textContent = message;

    const buttonsDiv = document.createElement('div');
    buttonsDiv.className = 'confirm-buttons';

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'golarion-btn btn-secondary';
    cancelBtn.textContent = 'Cancel';
    cancelBtn.onclick = () => {
      overlay.remove();
      if (onCancel) onCancel();
    };

    const confirmBtn = document.createElement('button');
    confirmBtn.className = 'golarion-btn btn-danger';
    confirmBtn.textContent = 'Delete';
    confirmBtn.onclick = () => {
      overlay.remove();
      onConfirm();
    };

    buttonsDiv.appendChild(cancelBtn);
    buttonsDiv.appendChild(confirmBtn);

    dialog.appendChild(messageDiv);
    dialog.appendChild(buttonsDiv);
    overlay.appendChild(dialog);

    document.body.appendChild(overlay);

    // Trigger animation
    setTimeout(() => overlay.classList.add('show'), 10);
  }

  /**
   * Show save route dialog
   */
  private showSaveRouteDialog(onSave: (name: string, description: string) => void): void {
    const overlay = document.createElement('div');
    overlay.className = 'golarion-modal-overlay';

    const dialog = document.createElement('div');
    dialog.className = 'golarion-save-dialog';

    const title = document.createElement('h3');
    title.textContent = 'Save Route';
    title.style.marginTop = '0';

    const nameLabel = document.createElement('label');
    nameLabel.className = 'form-label';
    nameLabel.textContent = 'Route Name';

    const nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.className = 'form-input';
    nameInput.placeholder = 'e.g., Absalom to Almas';
    nameInput.value = `Route ${new Date().toLocaleDateString()}`;

    const descLabel = document.createElement('label');
    descLabel.className = 'form-label';
    descLabel.textContent = 'Description (Optional)';

    const descInput = document.createElement('textarea');
    descInput.className = 'form-textarea';
    descInput.placeholder = 'Add notes about this route...';
    descInput.rows = 3;

    const buttonsDiv = document.createElement('div');
    buttonsDiv.className = 'form-buttons';

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'golarion-btn btn-secondary';
    cancelBtn.textContent = 'Cancel';
    cancelBtn.onclick = () => overlay.remove();

    const saveBtn = document.createElement('button');
    saveBtn.className = 'golarion-btn btn-primary';
    saveBtn.textContent = 'Save';
    saveBtn.onclick = () => {
      const name = nameInput.value.trim() || `Route ${new Date().toLocaleDateString()}`;
      const description = descInput.value.trim();
      overlay.remove();
      onSave(name, description);
    };

    // Allow Enter key to submit
    nameInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        saveBtn.click();
      }
    });

    buttonsDiv.appendChild(cancelBtn);
    buttonsDiv.appendChild(saveBtn);

    dialog.appendChild(title);
    dialog.appendChild(nameLabel);
    dialog.appendChild(nameInput);
    dialog.appendChild(descLabel);
    dialog.appendChild(descInput);
    dialog.appendChild(buttonsDiv);
    overlay.appendChild(dialog);

    document.body.appendChild(overlay);

    // Trigger animation and focus input
    setTimeout(() => {
      overlay.classList.add('show');
      nameInput.focus();
      nameInput.select();
    }, 10);
  }
  /**
   * Simple collision detection for panel positioning
   */
}
