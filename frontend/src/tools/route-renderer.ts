import { Map as MapLibreMap, LngLatBoundsLike, Marker } from 'maplibre-gl';
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
}

/**
 * Interface for route storage management
 */
interface RouteStorage {
  routes: SavedRoute[];
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

    const source = this.map.getSource(this.routeSourceId);
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
      font-size: 24px;
      line-height: 1;
      text-shadow: 0 0 3px white, 0 0 5px white;
    `;
    el.textContent = '⛵';
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
    panel.className = 'route-info-panel';
    panel.style.cssText = `
      position: absolute;
      bottom: 20px;
      left: 20px;
      background: white;
      padding: 20px;
      border-radius: 12px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
      min-width: 320px;
      max-width: 450px;
      max-height: 80vh;
      overflow-y: auto;
      z-index: 1000;
    `;

    // Add custom scrollbar styling
    const style = document.createElement('style');
    style.textContent = `
      .route-info-panel::-webkit-scrollbar {
        width: 8px;
      }
      .route-info-panel::-webkit-scrollbar-track {
        background: #f1f1f1;
        border-radius: 4px;
      }
      .route-info-panel::-webkit-scrollbar-thumb {
        background: #888;
        border-radius: 4px;
      }
      .route-info-panel::-webkit-scrollbar-thumb:hover {
        background: #555;
      }
    `;
    if (!document.getElementById('route-panel-styles')) {
      style.id = 'route-panel-styles';
      document.head.appendChild(style);
    }

    const title = document.createElement('h3');
    title.textContent = 'Route Information';
    title.style.cssText = 'margin: 0 0 12px 0; font-size: 16px; color: #202124; font-weight: 600;';

    const totalDist = document.createElement('div');
    totalDist.innerHTML = `📏 <strong>Total Distance:</strong> ${route.totalDistance.toFixed(1)} km (${(route.totalDistance * 0.621371).toFixed(1)} mi)`;
    totalDist.style.cssText = 'margin: 8px 0; font-size: 14px; color: #5f6368;';

    panel.appendChild(title);
    panel.appendChild(totalDist);

    // Terrain breakdown - calculate distances for each water type
    const terrainDistances = {
      land: 0,
      river: 0,
      'shallow-water': 0,
      'deep-water': 0
    };

    route.segments.forEach(segment => {
      if (segment.type in terrainDistances) {
        terrainDistances[segment.type as keyof typeof terrainDistances] += segment.distance;
      }
    });

    const terrainDiv = document.createElement('div');
    terrainDiv.style.cssText = 'margin: 8px 0; padding: 8px; background: #f8f9fa; border-radius: 4px;';

    if (terrainDistances.land > 0) {
      const landDiv = document.createElement('div');
      landDiv.innerHTML = `🏔️ Land: ${terrainDistances.land.toFixed(1)} km`;
      landDiv.style.cssText = 'font-size: 13px; color: #8B4513; margin: 4px 0; font-weight: 500;';
      terrainDiv.appendChild(landDiv);
    }

    if (terrainDistances.river > 0) {
      const riverDiv = document.createElement('div');
      riverDiv.innerHTML = `🏞️ River: ${terrainDistances.river.toFixed(1)} km`;
      riverDiv.style.cssText = 'font-size: 13px; color: #64ADEF; margin: 4px 0;';
      terrainDiv.appendChild(riverDiv);
    }

    if (terrainDistances['shallow-water'] > 0) {
      const shallowDiv = document.createElement('div');
      shallowDiv.innerHTML = `🌊 Shallow Water: ${terrainDistances['shallow-water'].toFixed(1)} km`;
      shallowDiv.style.cssText = 'font-size: 13px; color: #4A9EFF; margin: 4px 0;';
      terrainDiv.appendChild(shallowDiv);
    }

    if (terrainDistances['deep-water'] > 0) {
      const deepDiv = document.createElement('div');
      deepDiv.innerHTML = `🌊 Deep Ocean: ${terrainDistances['deep-water'].toFixed(1)} km`;
      deepDiv.style.cssText = 'font-size: 13px; color: #2E5C99; margin: 4px 0;';
      terrainDiv.appendChild(deepDiv);
    }

    if (terrainDiv.childNodes.length > 0) {
      panel.appendChild(terrainDiv);
    }

    // Travel times
    if (travelTimes.length > 0) {
      const timesTitle = document.createElement('div');
      timesTitle.textContent = '⏱️ Travel Time:';
      timesTitle.style.cssText = 'margin: 12px 0 8px 0; font-size: 14px; font-weight: 600; color: #202124;';
      panel.appendChild(timesTitle);

      travelTimes.slice(0, 4).forEach(time => {
        const method = TRAVEL_METHODS[time.method];
        const timeDiv = document.createElement('div');
        timeDiv.style.cssText = 'margin: 4px 0; font-size: 13px; color: #5f6368; padding-left: 8px; display: flex; align-items: center; gap: 8px;';

        // Create SVG icon instead of using emoji
        const icon = this.createTravelIcon(method.icon);
        icon.alt = method.name;
        icon.style.cssText = `
          width: 18px;
          height: 18px;
          object-fit: contain;
          filter: drop-shadow(0 1px 2px rgba(0,0,0,0.15));
          flex-shrink: 0;
        `;

        const textSpan = document.createElement('span');
        textSpan.innerHTML = `<strong>${method.name}:</strong> ${time.description}`;

        timeDiv.appendChild(icon);
        timeDiv.appendChild(textSpan);
        panel.appendChild(timeDiv);
      });
    }

    // Segment Details Section (Collapsible)
    const segmentsSection = document.createElement('div');
    segmentsSection.style.cssText = 'margin: 16px 0 12px 0;';

    const segmentsHeader = document.createElement('div');
    segmentsHeader.style.cssText = `
      font-size: 14px;
      font-weight: 600;
      color: #202124;
      cursor: pointer;
      padding: 10px 12px;
      background: #f1f3f4;
      border-radius: 6px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      user-select: none;
      transition: background 0.2s;
    `;
    segmentsHeader.innerHTML = `<span>📋 Segment Details (${route.segments.length})</span><span id="toggle-arrow">▼</span>`;
    segmentsHeader.onmouseover = () => {
      segmentsHeader.style.background = '#e8eaed';
    };
    segmentsHeader.onmouseout = () => {
      segmentsHeader.style.background = '#f1f3f4';
    };

    const segmentsContent = document.createElement('div');
    segmentsContent.id = 'segments-content';
    segmentsContent.style.cssText = 'margin-top: 12px;';

    // Toggle functionality
    let isExpanded = true;
    segmentsHeader.onclick = () => {
      isExpanded = !isExpanded;
      segmentsContent.style.display = isExpanded ? 'block' : 'none';
      const arrow = document.getElementById('toggle-arrow');
      if (arrow) arrow.textContent = isExpanded ? '▼' : '▶';
    };

    // Add individual segment cards
    const selectedMethod = travelTimes.length > 0 ? travelTimes[0].method : 'mixed';
    const method = TRAVEL_METHODS[selectedMethod];

    route.segments.forEach((segment, index) => {
      const segmentCard = document.createElement('div');
      segmentCard.style.cssText = `
        margin: 8px 0;
        padding: 12px;
        background: white;
        border-left: 4px solid ${this.getTerrainColor(segment.type)};
        border-radius: 6px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.08);
      `;

      const segmentTitle = document.createElement('div');
      segmentTitle.style.cssText = 'font-weight: 600; margin-bottom: 8px; font-size: 13px; color: #202124;';
      segmentTitle.innerHTML = `Segment ${index + 1}: ${this.getTerrainIcon(segment.type)} ${this.getTerrainLabel(segment.type)}`;

      const segmentDistance = document.createElement('div');
      segmentDistance.style.cssText = 'font-size: 12px; color: #5f6368; margin: 4px 0;';
      segmentDistance.innerHTML = `📏 Distance: <strong>${segment.distance.toFixed(1)} km</strong> (${(segment.distance * 0.621371).toFixed(1)} mi)`;

      const segmentTime = document.createElement('div');
      segmentTime.style.cssText = 'font-size: 12px; color: #5f6368; margin: 4px 0;';
      const timeInDays = this.calculateSegmentTime(segment, method);
      const timeStr = timeInDays === Infinity ? 'Impassable' :
                      timeInDays < 1 ? `${(timeInDays * 24).toFixed(1)} hours` :
                      `${timeInDays.toFixed(1)} days`;
      segmentTime.innerHTML = `⏱️ Time: <strong>${timeStr}</strong> (${method.name})`;

      segmentCard.appendChild(segmentTitle);
      segmentCard.appendChild(segmentDistance);
      segmentCard.appendChild(segmentTime);

      segmentsContent.appendChild(segmentCard);
    });

    segmentsSection.appendChild(segmentsHeader);
    segmentsSection.appendChild(segmentsContent);
    panel.appendChild(segmentsSection);

    const closeBtn = document.createElement('button');
    closeBtn.textContent = 'Close';
    closeBtn.style.cssText = `
      margin-top: 12px;
      padding: 8px 16px;
      background: #1976D2;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
      width: 100%;
    `;
    closeBtn.onmouseover = () => {
      closeBtn.style.background = '#1565C0';
    };
    closeBtn.onmouseout = () => {
      closeBtn.style.background = '#1976D2';
    };
    closeBtn.onclick = () => panel.remove();

    panel.appendChild(closeBtn);

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
    panel.style.cssText = `
      position: absolute;
      bottom: 80px;
      right: 10px;
      background: white;
      border: 2px solid #ccc;
      border-radius: 8px;
      padding: 15px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      z-index: 1000;
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
      const name = prompt('Enter route name:') || `Route ${new Date().toLocaleDateString()}`;
      const description = prompt('Enter route description (optional):') || '';
      if (this.saveRoute(name, description, this.currentTravelMethod)) {
        this.showSavedRoutesPanel(); // Refresh the panel
        alert('Route saved successfully!');
      }
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
            alert('Route loaded successfully!');
          }
        });
      });

      routesList.querySelectorAll('.delete-route-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          const routeId = (e.target as HTMLElement).getAttribute('data-route-id');
          if (routeId && confirm('Are you sure you want to delete this route?')) {
            if (this.deleteSavedRoute(routeId)) {
              this.showSavedRoutesPanel(); // Refresh panel
              alert('Route deleted successfully!');
            }
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
}
