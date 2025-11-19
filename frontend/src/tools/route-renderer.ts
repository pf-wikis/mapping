import { Map as MapLibreMap, LngLatBoundsLike, Marker } from 'maplibre-gl';
import { RouteResult, TravelTime, TRAVEL_METHODS } from './pathfinder';
import { Position } from 'geojson';

/**
 * RouteRenderer handles visualization of calculated routes on the map
 */
export class RouteRenderer {
  private map: MapLibreMap;
  private routeSourceId = 'route-source';
  private landLayerId = 'route-land-layer';
  private landOutlineLayerId = 'route-land-outline-layer';
  private waterLayerId = 'route-water-layer';
  private waterOutlineLayerId = 'route-water-outline-layer';
  private startMarker: Marker | null = null;
  private endMarker: Marker | null = null;
  private boatMarkers: Marker[] = [];
  private anchorMarkers: Marker[] = [];
  private currentRoute: RouteResult | null = null;

  constructor(map: MapLibreMap) {
    this.map = map;
    this.initializeLayers();
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

      // Water route outline
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
            'line-color': '#1E90FF',
            'line-width': 8,
            'line-opacity': 0.4
          }
        });
      }

      // Water route main line (dashed)
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
            'line-color': '#4169E1',
            'line-width': 4,
            'line-dasharray': [2, 2]
          }
        });
      }
    });
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
      padding: 16px;
      border-radius: 8px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.3);
      font-family: Arial, sans-serif;
      min-width: 280px;
      max-width: 400px;
      z-index: 1000;
    `;

    const title = document.createElement('h3');
    title.textContent = 'Route Information';
    title.style.cssText = 'margin: 0 0 12px 0; font-size: 16px; color: #202124; font-weight: 600;';

    const totalDist = document.createElement('div');
    totalDist.innerHTML = `📏 <strong>Total Distance:</strong> ${route.totalDistance.toFixed(1)} km (${(route.totalDistance * 0.621371).toFixed(1)} mi)`;
    totalDist.style.cssText = 'margin: 8px 0; font-size: 14px; color: #5f6368;';

    panel.appendChild(title);
    panel.appendChild(totalDist);

    // Terrain breakdown
    if (route.landDistance > 0 || route.waterDistance > 0) {
      const terrainDiv = document.createElement('div');
      terrainDiv.style.cssText = 'margin: 8px 0; padding: 8px; background: #f8f9fa; border-radius: 4px;';

      if (route.landDistance > 0) {
        const landDiv = document.createElement('div');
        landDiv.innerHTML = `🏔️ Land: ${route.landDistance.toFixed(1)} km`;
        landDiv.style.cssText = 'font-size: 13px; color: #8B4513; margin: 4px 0;';
        terrainDiv.appendChild(landDiv);
      }

      if (route.waterDistance > 0) {
        const waterDiv = document.createElement('div');
        waterDiv.innerHTML = `🌊 Water: ${route.waterDistance.toFixed(1)} km`;
        waterDiv.style.cssText = 'font-size: 13px; color: #1E90FF; margin: 4px 0;';
        terrainDiv.appendChild(waterDiv);
      }

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
        timeDiv.innerHTML = `${method.icon} <strong>${method.name}:</strong> ${time.description}`;
        timeDiv.style.cssText = 'margin: 4px 0; font-size: 13px; color: #5f6368; padding-left: 8px;';
        panel.appendChild(timeDiv);
      });
    }

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
}
