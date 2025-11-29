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

  // Waypoint editing properties
  private waypoints: Position[] = []; // User-added waypoints between start and end
  private waypointMarkers: Marker[] = []; // Visual handles for waypoints
  private isWaypointEditingEnabled: boolean = false;
  private hoveredWaypointIndex: number | null = null;
  private draggedWaypointIndex: number | null = null;
  private isDraggingWaypoint: boolean = false;
  private isUpdatingWaypoint: boolean = false; // Guard flag to prevent re-entrant update calls
  private waypointClickHandler: ((e: any) => void) | null = null;
  private waypointHoverHandler: ((e: any) => void) | null = null;
  private waypointDragHandlers: {
    mousedown: (e: any) => void;
    mousemove: (e: any) => void;
    mouseup: (e: any) => void;
  } | null = null;

  // Panel dragging properties
  private isDraggingPanel: boolean = false;
  private panelDragOffset: { x: number; y: number } = { x: 0, y: 0 };
  private currentPanelPosition: { right: number; bottom: number } = { right: 20, bottom: 20 };
  private currentPanel: HTMLElement | null = null;
  private panelDragHandlers: {
    mousedown: (e: MouseEvent) => void;
    mousemove: (e: MouseEvent) => void;
    mouseup: (e: MouseEvent) => void;
  } | null = null;

  // Cache for route segments to avoid recalculating unchanged segments
  private cachedSegments: { start: Position, end: Position, result: RouteResult }[] = [];

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
    this.cachedSegments = [];
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

    // Header (draggable)
    const header = document.createElement('div');
    header.className = 'golarion-route-header draggable-header';
    header.style.cursor = 'grab';
    header.title = 'Drag to reposition panel';

    // Drag handle icon
    const dragHandle = document.createElement('div');
    dragHandle.className = 'drag-handle';
    dragHandle.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" style="opacity: 0.5;">
        <circle cx="4" cy="4" r="1.5"/>
        <circle cx="4" cy="8" r="1.5"/>
        <circle cx="4" cy="12" r="1.5"/>
        <circle cx="8" cy="4" r="1.5"/>
        <circle cx="8" cy="8" r="1.5"/>
        <circle cx="8" cy="12" r="1.5"/>
      </svg>
    `;

    const title = document.createElement('h3');
    title.textContent = 'Route Details';

    const closeBtn = document.createElement('button');
    closeBtn.className = 'golarion-close-btn';
    closeBtn.innerHTML = '×';
    closeBtn.onclick = () => {
      this.disablePanelDragging();
      panel.remove();
    };

    header.appendChild(dragHandle);
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

    // Waypoint Editing Toggle Button
    const waypointSection = document.createElement('div');
    waypointSection.className = 'golarion-waypoint-section';

    const waypointButton = document.createElement('button');
    waypointButton.className = 'golarion-btn golarion-waypoint-toggle';
    waypointButton.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 16 16" style="vertical-align: middle; margin-right: 6px;">
        <circle cx="8" cy="8" r="4" fill="currentColor"/>
      </svg>
      Edit Waypoints
    `;

    // Auto-create waypoints button
    const autoWaypointButton = document.createElement('button');
    autoWaypointButton.className = 'golarion-btn golarion-auto-waypoint-btn';
    autoWaypointButton.style.marginLeft = '8px';
    autoWaypointButton.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 16 16" style="vertical-align: middle; margin-right: 4px;">
        <path d="M8 2v12M2 8h12" stroke="currentColor" stroke-width="2" fill="none"/>
      </svg>
      Auto 25km
    `;
    autoWaypointButton.title = 'Automatically create waypoints every 25km along the route';
    autoWaypointButton.onclick = async () => {
      if (!this.isWaypointEditingEnabled) {
        this.enableWaypointEditing();
        waypointButton.classList.add('active');
        waypointButton.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 16 16" style="vertical-align: middle; margin-right: 6px;">
            <circle cx="8" cy="8" r="4" fill="#4CAF50"/>
          </svg>
          Editing Waypoints
        `;
        waypointHelp.style.display = 'block';
      }
      await this.autoCreateWaypoints(25);
    };

    const waypointHelp = document.createElement('div');
    waypointHelp.className = 'golarion-waypoint-help';
    waypointHelp.style.display = 'none';
    waypointHelp.innerHTML = `
      <div style="font-size: 12px; color: #666; margin-top: 8px; line-height: 1.4;">
        <strong>How to use:</strong><br>
        • Waypoint handles are now always visible (semi-transparent circles)<br>
        • Click directly on waypoint handles and drag to reposition<br>
        • Click on path to manually add more waypoints<br>
        • Right-click on waypoints to delete them<br>
        • Use "Auto 25km" to automatically place waypoints every 25km
      </div>
    `;

    // Toggle waypoint editing on button click (combined with help text toggle)
    waypointButton.onclick = () => {
      if (this.isWaypointEditingEnabled) {
        this.disableWaypointEditing();
        waypointButton.classList.remove('active');
        waypointButton.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 16 16" style="vertical-align: middle; margin-right: 6px;">
            <circle cx="8" cy="8" r="4" fill="currentColor"/>
          </svg>
          Edit Waypoints
        `;
        waypointHelp.style.display = 'none';
      } else {
        this.enableWaypointEditing();
        waypointButton.classList.add('active');
        waypointButton.innerHTML = `
          <svg width="16" height="16" viewBox="0 0 16 16" style="vertical-align: middle; margin-right: 6px;">
            <circle cx="8" cy="8" r="4" fill="#4CAF50"/>
          </svg>
          Editing Waypoints
        `;
        waypointHelp.style.display = 'block';
      }
    };

    const buttonContainer = document.createElement('div');
    buttonContainer.style.display = 'flex';
    buttonContainer.style.alignItems = 'center';
    buttonContainer.appendChild(waypointButton);
    buttonContainer.appendChild(autoWaypointButton);

    waypointSection.appendChild(buttonContainer);
    waypointSection.appendChild(waypointHelp);
    panel.appendChild(waypointSection);

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

    // Position panel in bottom-right corner (no collision with search control which is top-left)
    panel.style.position = "absolute";
    panel.style.right = `${this.currentPanelPosition.right}px`;
    panel.style.bottom = `${this.currentPanelPosition.bottom}px`;
    panel.style.left = 'auto'; // Disable left positioning

    // Store reference to panel for dragging
    this.currentPanel = panel;

    // NOTE: Panel dragging will be enabled after the panel is added to DOM
    // Call enablePanelDraggingForCurrentPanel() after appending to map container

    return panel;
  }

  /**
   * Create travel icon element (renders emoji)
   */
  private createTravelIcon(iconChar: string, className: string = 'travel-mode-icon'): HTMLElement {
    const iconEl = document.createElement('div');
    iconEl.textContent = iconChar;
    iconEl.className = className;
    iconEl.style.cssText = `
      width: 24px;
      height: 24px;
      font-size: 20px;
      line-height: 24px;
      text-align: center;
      vertical-align: middle;
      display: inline-block;
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
      bottom: ${bottomPosition}px;
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
   * Find the nearest point on the current route path to a given map position
   * Used for detecting clicks on the path line
   */
  private findNearestPointOnPath(lngLat: { lng: number; lat: number }, thresholdPixels: number = 15): {
    position: Position;
    insertIndex: number;
    distance: number;
  } | null {
    if (!this.currentRoute || !this.currentRoute.path || this.currentRoute.path.length < 2) {
      return null;
    }

    const clickPoint = this.map.project([lngLat.lng, lngLat.lat]);
    let nearestPoint: Position | null = null;
    let nearestDistance = Infinity;
    let nearestIndex = -1;

    // Check each segment of the path
    for (let i = 0; i < this.currentRoute.path.length - 1; i++) {
      const start = this.currentRoute.path[i];
      const end = this.currentRoute.path[i + 1];

      const startPoint = this.map.project(start);
      const endPoint = this.map.project(end);

      // Find closest point on line segment
      const segmentLengthSquared = Math.pow(endPoint.x - startPoint.x, 2) + Math.pow(endPoint.y - startPoint.y, 2);

      if (segmentLengthSquared === 0) {
        // Degenerate segment (start === end)
        const distance = Math.hypot(clickPoint.x - startPoint.x, clickPoint.y - startPoint.y);
        if (distance < nearestDistance) {
          nearestDistance = distance;
          nearestPoint = start;
          nearestIndex = i + 1;
        }
        continue;
      }

      // Project click point onto line segment
      const t = Math.max(0, Math.min(1,
        ((clickPoint.x - startPoint.x) * (endPoint.x - startPoint.x) +
          (clickPoint.y - startPoint.y) * (endPoint.y - startPoint.y)) / segmentLengthSquared
      ));

      const projectedX = startPoint.x + t * (endPoint.x - startPoint.x);
      const projectedY = startPoint.y + t * (endPoint.y - startPoint.y);
      const distance = Math.hypot(clickPoint.x - projectedX, clickPoint.y - projectedY);

      if (distance < nearestDistance) {
        nearestDistance = distance;
        // Convert back to lng/lat
        const projectedLngLat = this.map.unproject([projectedX, projectedY]);
        nearestPoint = [projectedLngLat.lng, projectedLngLat.lat];
        nearestIndex = i + 1; // Insert after this segment
      }
    }

    if (nearestPoint && nearestDistance <= thresholdPixels) {
      return {
        position: nearestPoint,
        insertIndex: nearestIndex,
        distance: nearestDistance
      };
    }

    return null;
  }

  /**
   * Find the nearest existing waypoint to a given map position
   * Used for detecting hover and clicks on waypoint markers
   */
  private findNearestWaypoint(lngLat: { lng: number; lat: number }, thresholdPixels: number = 20): {
    waypointIndex: number;
    position: Position;
    distance: number;
  } | null {
    if (this.waypoints.length === 0) {
      return null;
    }

    const clickPoint = this.map.project([lngLat.lng, lngLat.lat]);
    let nearestIndex = -1;
    let nearestDistance = Infinity;

    for (let i = 0; i < this.waypoints.length; i++) {
      const waypoint = this.waypoints[i];
      const waypointPoint = this.map.project(waypoint);
      const distance = Math.hypot(clickPoint.x - waypointPoint.x, clickPoint.y - waypointPoint.y);

      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestIndex = i;
      }
    }

    if (nearestIndex >= 0 && nearestDistance <= thresholdPixels) {
      return {
        waypointIndex: nearestIndex,
        position: this.waypoints[nearestIndex],
        distance: nearestDistance
      };
    }

    return null;
  }

  /**
   * Start dragging a waypoint
   */
  private startDraggingWaypoint(index: number, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();

    console.log(`[WAYPOINT DRAG] Started dragging waypoint ${index} - Release mouse to recalculate route`);

    this.isDraggingWaypoint = true;
    this.draggedWaypointIndex = index;
    this.map.getCanvas().style.cursor = 'grabbing';
    this.map.dragPan.disable(); // Prevent map panning while dragging

    // Update the marker element cursor
    const markerEl = this.waypointMarkers[index]?.getElement();
    if (markerEl) {
      markerEl.classList.add('hovered');
      markerEl.style.cursor = 'grabbing';
      markerEl.style.opacity = '1';
    }

    let moveCount = 0;

    // Add global mousemove and mouseup handlers for dragging
    const mousemoveHandler = (e: MouseEvent) => {
      if (this.isDraggingWaypoint && this.draggedWaypointIndex !== null) {
        moveCount++;
        const lngLat = this.map.unproject([e.clientX, e.clientY]);
        const newPosition: Position = [lngLat.lng, lngLat.lat];
        this.waypoints[this.draggedWaypointIndex] = newPosition;

        // Debug log every 10th move to avoid spam
        if (moveCount % 10 === 0) {
          console.log(`[WAYPOINT DRAG] Dragging to [${lngLat.lng.toFixed(4)}, ${lngLat.lat.toFixed(4)}] (move ${moveCount})`);
        }

        // Update marker position
        if (this.waypointMarkers[this.draggedWaypointIndex]) {
          this.waypointMarkers[this.draggedWaypointIndex].setLngLat(newPosition);
        }
      }
    };

    const mouseupHandler = async (e: MouseEvent) => {
      console.log(`[WAYPOINT DRAG] Mouse released - finalizing waypoint position and recalculating route`);

      // CRITICAL: Remove handlers FIRST to prevent recursive calls during async operation
      document.removeEventListener('mousemove', mousemoveHandler);
      document.removeEventListener('mouseup', mouseupHandler);
      window.removeEventListener('blur', blurHandler);
      document.removeEventListener('mouseleave', mouseleaveHandler);
      console.log(`[WAYPOINT DRAG] Handlers cleaned up - starting update`);

      if (this.isDraggingWaypoint && this.draggedWaypointIndex !== null) {
        const lngLat = this.map.unproject([e.clientX, e.clientY]);
        const newPosition: Position = [lngLat.lng, lngLat.lat];

        console.log(`[WAYPOINT DRAG] Final position: [${lngLat.lng.toFixed(4)}, ${lngLat.lat.toFixed(4)}]`);
        console.log(`[WAYPOINT DRAG] Starting route recalculation...`);

        await this.updateWaypoint(this.draggedWaypointIndex, newPosition);

        console.log(`[WAYPOINT DRAG] Route recalculation complete!`);

        // Reset state
        const markerEl = this.waypointMarkers[this.draggedWaypointIndex]?.getElement();
        if (markerEl) {
          markerEl.classList.remove('hovered');
          markerEl.style.cursor = 'grab';
        }

        this.isDraggingWaypoint = false;
        this.draggedWaypointIndex = null;
        this.map.getCanvas().style.cursor = '';
        this.map.dragPan.enable(); // Re-enable map panning
      }

      console.log(`[WAYPOINT DRAG] Drag operation ended`);
    };

    // Handle window blur (alt-tab) - end drag operation
    const blurHandler = () => {
      console.log('[WAYPOINT DRAG] Window lost focus (alt-tab) - ending drag operation');
      mouseupHandler(new MouseEvent('mouseup'));
    };

    // Handle mouse leaving the document - end drag operation
    const mouseleaveHandler = (e: MouseEvent) => {
      if (e.target === document.documentElement || e.relatedTarget === null) {
        console.log('[WAYPOINT DRAG] Mouse left window - ending drag operation');
        mouseupHandler(e);
      }
    };

    // Attach temporary global handlers
    document.addEventListener('mousemove', mousemoveHandler);
    document.addEventListener('mouseup', mouseupHandler);
    window.addEventListener('blur', blurHandler, { once: true });
    document.addEventListener('mouseleave', mouseleaveHandler);
  }

  /**
   * Create a visual marker for a waypoint
   */
  private createWaypointMarker(position: Position, index: number): Marker {
    const el = document.createElement('div');
    el.className = 'waypoint-handle';
    el.style.width = '16px';
    el.style.height = '16px';
    el.style.borderRadius = '50%';
    el.style.backgroundColor = 'white';
    el.style.border = '2px solid #4CAF50';
    el.style.cursor = 'grab';
    el.style.opacity = '0.6'; // Semi-visible by default for better discoverability
    el.style.transition = 'opacity 0.2s, transform 0.2s, box-shadow 0.2s';
    el.style.pointerEvents = 'auto';
    el.style.boxShadow = '0 2px 4px rgba(0,0,0,0.3)';
    el.style.zIndex = '1000';
    el.style.transformOrigin = 'center center'; // Critical: prevents position shift when scaling
    el.dataset.waypointIndex = index.toString();

    // Add direct event listeners to the marker element to prevent map interference
    el.addEventListener('mousedown', (e) => {
      e.stopPropagation(); // Prevent map from receiving the event
      this.startDraggingWaypoint(index, e);
    });

    el.addEventListener('mouseenter', () => {
      el.classList.add('hovered');
      el.style.opacity = '1';
      el.style.boxShadow = '0 0 8px rgba(76, 175, 80, 0.8)';
      el.style.cursor = 'grab';
    });

    el.addEventListener('mouseleave', () => {
      if (!this.isDraggingWaypoint || this.draggedWaypointIndex !== index) {
        el.classList.remove('hovered');
        el.style.opacity = '0.6';
        el.style.boxShadow = '0 2px 4px rgba(0,0,0,0.3)';
      }
    });

    const marker = new Marker({ element: el, draggable: false })
      .setLngLat(position)
      .addTo(this.map);

    return marker;
  }

  /**
   * Update all waypoint marker positions and visibility
   */
  private updateWaypointMarkers(): void {
    // Remove old markers
    this.waypointMarkers.forEach(marker => marker.remove());
    this.waypointMarkers = [];

    // Create new markers for each waypoint
    this.waypoints.forEach((waypoint, index) => {
      const marker = this.createWaypointMarker(waypoint, index);
      this.waypointMarkers.push(marker);
    });
  }

  /**
   * Add a new waypoint at the given position and recalculate the route
   * @param position The position to add the waypoint at
   * @param index Optional index to insert the waypoint at. If not provided, appends to end.
   */
  private async addWaypoint(position: Position, index?: number): Promise<void> {
    if (typeof index === 'number' && index >= 0 && index <= this.waypoints.length) {
      this.waypoints.splice(index, 0, position);
    } else {
      this.waypoints.push(position);
    }
    this.updateWaypointMarkers();
    await this.recalculateRouteWithWaypoints();
  }

  /**
   * Remove a waypoint and recalculate the route
   */
  private async removeWaypoint(index: number): Promise<void> {
    if (index < 0 || index >= this.waypoints.length) {
      return;
    }

    this.waypoints.splice(index, 1);
    this.updateWaypointMarkers();
    await this.recalculateRouteWithWaypoints();
  }

  /**
   * Update a waypoint position and recalculate the route
   */
  private async updateWaypoint(index: number, newPosition: Position): Promise<void> {
    // Guard against re-entrant calls to prevent infinite loops
    if (this.isUpdatingWaypoint) {
      console.warn(`[WAYPOINT UPDATE] Already updating waypoint - ignoring recursive call to prevent infinite loop`);
      return;
    }

    this.isUpdatingWaypoint = true;
    try {
      console.log(`[WAYPOINT UPDATE] Updating waypoint ${index} to position [${newPosition[0].toFixed(4)}, ${newPosition[1].toFixed(4)}]`);

      if (index < 0 || index >= this.waypoints.length) {
        console.warn(`[WAYPOINT UPDATE] Invalid index ${index}, waypoints length: ${this.waypoints.length}`);
        return;
      }

      this.waypoints[index] = newPosition;
      this.updateWaypointMarkers();

      console.log(`[WAYPOINT UPDATE] Calling recalculateRouteWithWaypoints...`);
      await this.recalculateRouteWithWaypoints();
      console.log(`[WAYPOINT UPDATE] Complete`);
    } finally {
      this.isUpdatingWaypoint = false;
    }
  }

  /**
   * Auto-create waypoints at regular intervals along the route
   * @param intervalKm Interval in kilometers (default: 25km)
   */
  public async autoCreateWaypoints(intervalKm: number = 25): Promise<void> {
    if (!this.currentRoute || !this.currentRoute.segments) {
      console.warn('No route available to auto-create waypoints');
      return;
    }

    // Clear existing waypoints and cache
    this.waypoints = [];
    const newCachedSegments: { start: Position, end: Position, result: RouteResult }[] = [];

    const intervalMeters = intervalKm * 1000;
    let accumulatedDistance = 0;
    let nextWaypointDistance = intervalMeters;

    // Current segment being built (between two waypoints)
    let currentSegmentResult: RouteResult = {
      path: [],
      segments: [],
      totalDistance: 0,
      landDistance: 0,
      waterDistance: 0,
      success: true
    };

    let currentSegmentStart = this.currentRoute.path[0];
    let currentTerrainSegment: any = null;

    // Helper to finalize the current route segment and push to cache
    const finalizeRouteSegment = (endPoint: Position) => {
      // Add the final point to path
      currentSegmentResult.path.push(endPoint);

      // Finish current terrain segment if exists
      if (currentTerrainSegment) {
        currentTerrainSegment.coordinates.push(endPoint);

        // Recalculate distance for this terrain segment
        let segDist = 0;
        for (let k = 0; k < currentTerrainSegment.coordinates.length - 1; k++) {
          segDist += this.calculateDistance(currentTerrainSegment.coordinates[k], currentTerrainSegment.coordinates[k + 1]);
        }
        currentTerrainSegment.distance = segDist / 1000; // to km

        currentSegmentResult.segments.push(currentTerrainSegment);

        if (currentTerrainSegment.type === 'land') {
          currentSegmentResult.landDistance += currentTerrainSegment.distance;
        } else {
          currentSegmentResult.waterDistance += currentTerrainSegment.distance;
        }
        currentSegmentResult.totalDistance += currentTerrainSegment.distance;
      }

      // Push to cache
      newCachedSegments.push({
        start: currentSegmentStart,
        end: endPoint,
        result: JSON.parse(JSON.stringify(currentSegmentResult)) // Deep copy
      });

      // Reset for next segment
      currentSegmentStart = endPoint;
      currentSegmentResult = {
        path: [endPoint], // Start with the split point
        segments: [],
        totalDistance: 0,
        landDistance: 0,
        waterDistance: 0,
        success: true
      };
      currentTerrainSegment = null;
    };

    // Iterate through all terrain segments of the original route
    for (const segment of this.currentRoute.segments) {
      // Start a new terrain segment for the sub-route
      currentTerrainSegment = {
        type: segment.type,
        coordinates: [segment.coordinates[0]],
        distance: 0
      };

      // If this is the very first segment of the route, add start point to path
      if (currentSegmentResult.path.length === 0) {
        currentSegmentResult.path.push(segment.coordinates[0]);
      }

      for (let i = 0; i < segment.coordinates.length - 1; i++) {
        const p1 = segment.coordinates[i];
        const p2 = segment.coordinates[i + 1];
        const dist = this.calculateDistance(p1, p2);

        // Check if we cross a waypoint threshold
        if (accumulatedDistance + dist >= nextWaypointDistance) {
          let currentP1 = p1;
          let currentDistToP2 = dist; // Remaining distance to p2

          // Handle potentially multiple waypoints on a single long edge
          while (accumulatedDistance + currentDistToP2 >= nextWaypointDistance) {
            const distToSplit = nextWaypointDistance - accumulatedDistance;
            const ratio = distToSplit / currentDistToP2;

            // Interpolate split point
            const splitLng = currentP1[0] + (p2[0] - currentP1[0]) * ratio;
            const splitLat = currentP1[1] + (p2[1] - currentP1[1]) * ratio;
            const splitPoint: Position = [splitLng, splitLat];

            // Add split point to current structures
            if (currentTerrainSegment) {
              currentTerrainSegment.coordinates.push(splitPoint);
            }
            currentSegmentResult.path.push(splitPoint);

            // Add waypoint
            this.waypoints.push(splitPoint);

            // Finalize the current route segment ending at splitPoint
            // Note: finalizeRouteSegment will add splitPoint to the END of the current segment
            // But we just added it above. 
            // Actually, finalizeRouteSegment expects to add the end point.
            // Let's adjust: remove the push above and let finalize handle it?
            // No, finalize adds it to the NEW segment start too.
            // Let's stick to the helper logic:
            // We need to close the current terrain segment first.

            // Recalculate distance for this terrain segment piece
            let segDist = 0;
            if (currentTerrainSegment) {
              for (let k = 0; k < currentTerrainSegment.coordinates.length - 1; k++) {
                segDist += this.calculateDistance(currentTerrainSegment.coordinates[k], currentTerrainSegment.coordinates[k + 1]);
              }
              currentTerrainSegment.distance = segDist / 1000;
              currentSegmentResult.segments.push(currentTerrainSegment);

              if (currentTerrainSegment.type === 'land') {
                currentSegmentResult.landDistance += currentTerrainSegment.distance;
              } else {
                currentSegmentResult.waterDistance += currentTerrainSegment.distance;
              }
              currentSegmentResult.totalDistance += currentTerrainSegment.distance;
            }

            // Push to cache
            newCachedSegments.push({
              start: currentSegmentStart,
              end: splitPoint,
              result: JSON.parse(JSON.stringify(currentSegmentResult))
            });

            // Reset for next segment
            currentSegmentStart = splitPoint;
            currentSegmentResult = {
              path: [splitPoint],
              segments: [],
              totalDistance: 0,
              landDistance: 0,
              waterDistance: 0,
              success: true
            };

            // Start new terrain segment (same type) starting at splitPoint
            currentTerrainSegment = {
              type: segment.type,
              coordinates: [splitPoint],
              distance: 0
            };

            // Update counters
            accumulatedDistance = nextWaypointDistance;
            nextWaypointDistance += intervalMeters;

            currentP1 = splitPoint;
            currentDistToP2 -= distToSplit;
          }

          // Continue with the rest of the edge
          accumulatedDistance += currentDistToP2;
          if (currentTerrainSegment) {
            currentTerrainSegment.coordinates.push(p2);
          }
          currentSegmentResult.path.push(p2);

        } else {
          accumulatedDistance += dist;
          if (currentTerrainSegment) {
            currentTerrainSegment.coordinates.push(p2);
          }
          currentSegmentResult.path.push(p2);
        }
      }

      // Finished processing this original segment's coordinates
      // Push the current terrain segment to the result (if it has content and we aren't at the very end of route)
      // Note: The loop above adds points. We need to finalize the terrain segment.
      if (currentTerrainSegment && currentTerrainSegment.coordinates.length > 1) {
        // Calculate distance
        let segDist = 0;
        for (let k = 0; k < currentTerrainSegment.coordinates.length - 1; k++) {
          segDist += this.calculateDistance(currentTerrainSegment.coordinates[k], currentTerrainSegment.coordinates[k + 1]);
        }
        currentTerrainSegment.distance = segDist / 1000;

        currentSegmentResult.segments.push(currentTerrainSegment);
        if (currentTerrainSegment.type === 'land') {
          currentSegmentResult.landDistance += currentTerrainSegment.distance;
        } else {
          currentSegmentResult.waterDistance += currentTerrainSegment.distance;
        }
        currentSegmentResult.totalDistance += currentTerrainSegment.distance;

        // Reset for next original segment
        currentTerrainSegment = null;
      }
    }

    // Finalize the last route segment (from last waypoint to end)
    const endPoint = this.currentRoute.path[this.currentRoute.path.length - 1];

    newCachedSegments.push({
      start: currentSegmentStart,
      end: endPoint,
      result: currentSegmentResult
    });

    this.cachedSegments = newCachedSegments;
    console.log(`Auto-created ${this.waypoints.length} waypoints and populated cache with ${this.cachedSegments.length} segments`);

    this.updateWaypointMarkers();
  }

  /**
   * Calculate distance between two positions in meters using Haversine formula
   */
  private calculateDistance(pos1: Position, pos2: Position): number {
    const R = 6371000; // Earth's radius in meters
    const lat1 = pos1[1] * Math.PI / 180;
    const lat2 = pos2[1] * Math.PI / 180;
    const deltaLat = (pos2[1] - pos1[1]) * Math.PI / 180;
    const deltaLng = (pos2[0] - pos1[0]) * Math.PI / 180;

    const a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
      Math.cos(lat1) * Math.cos(lat2) *
      Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  /**
   * Recalculate the route with current waypoints
   * Routes will be calculated between: start -> waypoint1 -> waypoint2 -> ... -> end
   */
  private async recalculateRouteWithWaypoints(): Promise<void> {
    console.log(`[ROUTE RECALC] Starting recalculation with ${this.waypoints.length} waypoints`);

    if (!this.currentRoute || !this.startMarker || !this.endMarker) {
      console.warn(`[ROUTE RECALC] Cannot recalculate - missing required data`);
      return;
    }

    const startPos: Position = [this.startMarker.getLngLat().lng, this.startMarker.getLngLat().lat];
    const endPos: Position = [this.endMarker.getLngLat().lng, this.endMarker.getLngLat().lat];

    // Build list of all points to route through: start, waypoints, end
    const allPoints: Position[] = [startPos, ...this.waypoints, endPos];

    console.log(`[ROUTE RECALC] Calculating ${allPoints.length - 1} route segments`);

    // Calculate route segments between consecutive points
    const segments: any[] = [];
    let totalPath: Position[] = [];
    let totalDistance = 0;
    let landDistance = 0;
    let waterDistance = 0;

    // New cache for this calculation
    const newCachedSegments: { start: Position, end: Position, result: RouteResult }[] = [];

    for (let i = 0; i < allPoints.length - 1; i++) {
      const segmentStart = allPoints[i];
      const segmentEnd = allPoints[i + 1];

      // Check cache first
      const cached = this.findCachedSegment(segmentStart, segmentEnd);

      let result: RouteResult;

      if (cached) {
        console.log(`[ROUTE RECALC] Segment ${i + 1}/${allPoints.length - 1}: Using cached result`);
        result = cached;
      } else {
        console.log(`[ROUTE RECALC] Segment ${i + 1}/${allPoints.length - 1}: Calculating new route...`);
        result = await this.pathfinder.findRoute(segmentStart, segmentEnd);
      }

      if (result.success) {
        // Add to new cache
        newCachedSegments.push({
          start: segmentStart,
          end: segmentEnd,
          result: result
        });

        // Merge segment data
        segments.push(...result.segments);
        if (i > 0) {
          // Skip first point to avoid duplicates
          totalPath.push(...result.path.slice(1));
        } else {
          totalPath.push(...result.path);
        }
        totalDistance += result.totalDistance;
        landDistance += result.landDistance;
        waterDistance += result.waterDistance;
      } else {
        console.warn(`Failed to calculate route segment from waypoint ${i} to ${i + 1}`);
      }
    }

    // Update cache
    this.cachedSegments = newCachedSegments;

    // Update current route with merged data
    this.currentRoute = {
      path: totalPath,
      segments: segments,
      totalDistance,
      landDistance,
      waterDistance,
      success: totalPath.length > 0
    };

    console.log(`[ROUTE RECALC] Route recalculation complete:`, {
      totalDistance: totalDistance.toFixed(2) + ' km',
      segments: segments.length,
      pathPoints: totalPath.length,
      cachedSegments: this.cachedSegments.length
    });

    // Re-render the route
    this.displayRoute(this.currentRoute);
  }

  /**
   * Find a cached segment result for the given start and end points
   */
  private findCachedSegment(start: Position, end: Position): RouteResult | null {
    for (const cached of this.cachedSegments) {
      if (this.arePositionsEqual(start, cached.start) &&
        this.arePositionsEqual(end, cached.end)) {
        return cached.result;
      }
    }
    return null;
  }

  /**
   * Check if two positions are equal (within a small epsilon)
   */
  private arePositionsEqual(p1: Position, p2: Position): boolean {
    const epsilon = 0.000001; // Very strict equality for cache hits
    return Math.abs(p1[0] - p2[0]) < epsilon &&
      Math.abs(p1[1] - p2[1]) < epsilon;
  }

  /**
   * Determine the correct waypoint insertion index based on the path index
   */
  private getWaypointIndexForPathIndex(pathIndex: number): number {
    // Strategy 1: Use cachedSegments if available (most accurate)
    if (this.cachedSegments.length > 0) {
      let accumulatedLength = 0;

      for (let k = 0; k < this.cachedSegments.length; k++) {
        const segment = this.cachedSegments[k];
        const segLen = segment.result.path.length;

        // First segment contributes full length, others contribute length - 1 (shared start)
        const contribution = (k === 0) ? segLen : segLen - 1;

        // The valid start indices for this segment are [accumulated, accumulated + contribution - 2]
        const limit = accumulatedLength + contribution - 1;

        if (pathIndex < limit) {
          return k;
        }

        accumulatedLength += contribution;
      }

      return this.waypoints.length;
    }

    // Strategy 2: Fallback - find waypoints in the path (for loaded routes)
    if (this.waypoints.length === 0) return 0;

    let lastWaypointPathIndex = 0;

    for (let i = 0; i < this.waypoints.length; i++) {
      const wp = this.waypoints[i];
      let foundIndex = -1;

      // Search for waypoint in path
      for (let j = lastWaypointPathIndex; j < this.currentRoute!.path.length; j++) {
        if (this.arePositionsEqual(this.currentRoute!.path[j], wp)) {
          foundIndex = j;
          break;
        }
      }

      if (foundIndex !== -1) {
        // If pathIndex is before this waypoint, it belongs to segment i
        if (pathIndex < foundIndex) {
          return i;
        }
        lastWaypointPathIndex = foundIndex;
      }
    }

    // If we are here, it's in the last segment
    return this.waypoints.length;
  }

  /**
   * Enable waypoint editing mode
   * Allows clicking on path to add waypoints and dragging waypoints to reposition
   */
  public enableWaypointEditing(): void {
    if (this.isWaypointEditingEnabled) {
      return;
    }

    this.isWaypointEditingEnabled = true;

    // Click handler for adding waypoints
    const routeLayers = [
      this.landLayerId,
      this.riverLayerId,
      this.shallowWaterLayerId,
      this.lowSeaLayerId,
      this.deepSeaLayerId,
      this.waterLayerId,
      this.deepWaterLayerId
    ];

    this.waypointClickHandler = (e: any) => {
      // Check if we're clicking near an existing waypoint first (increased threshold)
      const nearestWaypoint = this.findNearestWaypoint(e.lngLat, 60);
      if (nearestWaypoint) {
        // Don't add a new waypoint if clicking on existing one
        return;
      }

      // Check if click is on the path (increased threshold for better UX)
      const nearestPath = this.findNearestPointOnPath(e.lngLat, 20);
      if (nearestPath) {
        e.preventDefault();
        // nearestPath.insertIndex is i + 1 (where i is the start of the line segment).
        // We want the index of the start of the line segment.
        const pathStartIndex = nearestPath.insertIndex - 1;
        const insertIndex = this.getWaypointIndexForPathIndex(pathStartIndex);
        this.addWaypoint(nearestPath.position, insertIndex);
      }
    };

    routeLayers.forEach(layerId => {
      if (this.map.getLayer(layerId)) {
        this.map.on('click', layerId, this.waypointClickHandler);
      }
    });

    // Right-click handler for deleting waypoints (uses increased threshold)
    this.map.on('contextmenu', (e: any) => {
      const nearestWaypoint = this.findNearestWaypoint(e.lngLat, 60);
      if (nearestWaypoint) {
        e.preventDefault();
        this.removeWaypoint(nearestWaypoint.waypointIndex);
      }
    });

    console.log('Waypoint editing enabled. Click on path to add waypoints, drag handles to reposition, right-click to delete.');
  }

  /**
   * Disable waypoint editing mode
   */
  public disableWaypointEditing(): void {
    if (!this.isWaypointEditingEnabled) {
      return;
    }

    this.isWaypointEditingEnabled = false;

    // Remove click handler
    if (this.waypointClickHandler) {
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
          this.map.off('click', layerId, this.waypointClickHandler!);
        }
      });

      this.waypointClickHandler = null;
    }

    // Remove hover handler
    if (this.waypointHoverHandler) {
      this.map.off('mousemove', this.waypointHoverHandler);
      this.waypointHoverHandler = null;
    }

    // Remove drag handlers
    if (this.waypointDragHandlers) {
      this.map.off('mousedown', this.waypointDragHandlers.mousedown);
      this.map.off('mousemove', this.waypointDragHandlers.mousemove);
      this.map.off('mouseup', this.waypointDragHandlers.mouseup);
      this.waypointDragHandlers = null;
    }

    // Hide all waypoint markers
    this.waypointMarkers.forEach(marker => {
      const el = marker.getElement();
      el.style.opacity = '0';
    });

    // Reset state
    this.hoveredWaypointIndex = null;
    this.draggedWaypointIndex = null;
    this.isDraggingWaypoint = false;
    this.map.getCanvas().style.cursor = '';
    this.map.dragPan.enable();

    console.log('Waypoint editing disabled.');
  }

  /**
   * Clear all waypoints and remove markers
   */
  public clearWaypoints(): void {
    this.waypoints = [];
    this.waypointMarkers.forEach(marker => marker.remove());
    this.waypointMarkers = [];
  }

  /**
   * Enable panel dragging for the current panel (call after panel is added to DOM)
   * This must be called AFTER the panel has been appended to the map container
   */
  public enablePanelDraggingForCurrentPanel(): void {
    if (this.currentPanel && document.contains(this.currentPanel)) {
      this.enablePanelDragging(this.currentPanel);
    } else {
      console.warn('Cannot enable panel dragging: panel not in DOM or not available');
    }
  }

  /**
   * Enable dragging functionality for route info panel
   */
  private enablePanelDragging(panel: HTMLElement): void {
    // Verify panel is in DOM before attaching event listeners
    if (!document.contains(panel)) {
      console.warn('Panel not in DOM, cannot enable dragging');
      return;
    }

    const header = panel.querySelector('.golarion-route-header') as HTMLElement;
    if (!header) {
      console.warn('Header not found in panel, cannot enable dragging');
      return;
    }

    const mousedownHandler = (e: MouseEvent) => {
      // Only allow dragging from header (not close button)
      if ((e.target as HTMLElement).closest('.golarion-close-btn')) {
        return;
      }

      e.preventDefault();
      this.isDraggingPanel = true;

      // Calculate offset from mouse to panel's current position
      const panelRect = panel.getBoundingClientRect();
      this.panelDragOffset = {
        x: panelRect.right - e.clientX,
        y: panelRect.bottom - e.clientY
      };

      header.style.cursor = 'grabbing';
      panel.classList.add('dragging');
    };

    const mousemoveHandler = (e: MouseEvent) => {
      if (!this.isDraggingPanel) return;

      e.preventDefault();

      // Calculate new position (right/bottom based)
      const newRight = window.innerWidth - (e.clientX + this.panelDragOffset.x);
      const newBottom = window.innerHeight - (e.clientY + this.panelDragOffset.y);

      // Constrain to viewport boundaries
      const panelRect = panel.getBoundingClientRect();
      const panelWidth = panelRect.width;
      const panelHeight = panelRect.height;

      const constrainedRight = Math.max(0, Math.min(newRight, window.innerWidth - panelWidth));
      const constrainedBottom = Math.max(0, Math.min(newBottom, window.innerHeight - panelHeight));

      // Update panel position
      panel.style.right = `${constrainedRight}px`;
      panel.style.bottom = `${constrainedBottom}px`;

      // Store current position
      this.currentPanelPosition = {
        right: constrainedRight,
        bottom: constrainedBottom
      };
    };

    const mouseupHandler = () => {
      if (!this.isDraggingPanel) return;

      this.isDraggingPanel = false;
      header.style.cursor = 'grab';
      panel.classList.remove('dragging');
    };

    // Store handlers for cleanup
    this.panelDragHandlers = {
      mousedown: mousedownHandler,
      mousemove: mousemoveHandler,
      mouseup: mouseupHandler
    };

    // Attach event listeners
    header.addEventListener('mousedown', mousedownHandler);
    document.addEventListener('mousemove', mousemoveHandler);
    document.addEventListener('mouseup', mouseupHandler);
  }

  /**
   * Disable dragging functionality and cleanup
   */
  private disablePanelDragging(): void {
    if (!this.panelDragHandlers || !this.currentPanel) return;

    const header = this.currentPanel.querySelector('.golarion-route-header') as HTMLElement;
    if (header) {
      header.removeEventListener('mousedown', this.panelDragHandlers.mousedown);
    }

    document.removeEventListener('mousemove', this.panelDragHandlers.mousemove);
    document.removeEventListener('mouseup', this.panelDragHandlers.mouseup);

    this.panelDragHandlers = null;
    this.isDraggingPanel = false;
    this.currentPanel = null;
  }

  /**
   * Simple collision detection for panel positioning
   */
}
