import { Map as MapLibreMap, MapGeoJSONFeature } from 'maplibre-gl';
import { RoadGraph } from '../utils/graph-builder';
import { Position, LineString, MultiLineString } from 'geojson';

/**
 * RoadNetworkExtractor extracts road/path features from vector tiles
 * and builds a graph for pathfinding
 */
export class RoadNetworkExtractor {
  private map: MapLibreMap;
  private roadGraph: RoadGraph;
  private loaded: boolean = false;
  private loading: Promise<void> | null = null;

  // Road source layers to query
  // These may need to be adjusted based on actual vector tile structure
  private readonly ROAD_LAYERS = [
    'line-labels', // Line features (roads, rivers, etc.)
    'geometry',    // Might contain road geometry
  ];

  // Road type filters
  private readonly ROAD_TYPES = [
    'road',
    'path',
    'trail',
    'highway',
    'street',
  ];

  constructor(map: MapLibreMap) {
    this.map = map;
    this.roadGraph = new RoadGraph();
  }

  /**
   * Load road network from vector tiles
   * This queries the map for road features and builds the graph
   */
  async load(): Promise<void> {
    if (this.loaded) {
      return;
    }

    if (this.loading) {
      return this.loading;
    }

    this.loading = new Promise<void>((resolve, reject) => {
      // Wait for map to load
      if (!this.map.loaded()) {
        this.map.once('load', () => {
          this.extractRoads()
            .then(() => {
              this.loaded = true;
              resolve();
            })
            .catch(reject);
        });
      } else {
        this.extractRoads()
          .then(() => {
            this.loaded = true;
            resolve();
          })
          .catch(reject);
      }
    });

    return this.loading;
  }

  /**
   * Extract roads from vector tiles
   */
  private async extractRoads(): Promise<void> {
    console.log('Starting road network extraction...');

    // Get map bounds
    const bounds = this.map.getBounds();
    const bbox = [
      [bounds.getWest(), bounds.getSouth()],
      [bounds.getWest(), bounds.getNorth()],
      [bounds.getEast(), bounds.getNorth()],
      [bounds.getEast(), bounds.getSouth()],
      [bounds.getWest(), bounds.getSouth()]
    ];

    let totalFeatures = 0;
    let roadFeatures = 0;

    // Query each potential road layer
    for (const sourceLayer of this.ROAD_LAYERS) {
      try {
        // Find layer IDs that use this source layer
        const layerIds = this.map.getStyle().layers
          ?.filter(layer =>
            'source-layer' in layer &&
            layer['source-layer'] === sourceLayer &&
            layer.type === 'line'
          )
          .map(layer => layer.id);

        if (!layerIds || layerIds.length === 0) {
          continue;
        }

        for (const layerId of layerIds) {
          // Query features from this layer
          const features = this.map.querySourceFeatures('golarion', {
            sourceLayer: sourceLayer
          });

          totalFeatures += features.length;

          for (const feature of features) {
            if (this.isRoadFeature(feature)) {
              this.addFeatureToGraph(feature);
              roadFeatures++;
            }
          }
        }
      } catch (error) {
        console.warn(`Error querying layer ${sourceLayer}:`, error);
      }
    }

    const stats = this.roadGraph.getStats();
    console.log(`Road network extraction complete:`);
    console.log(`  - Total features queried: ${totalFeatures}`);
    console.log(`  - Road features found: ${roadFeatures}`);
    console.log(`  - Graph nodes: ${stats.nodes}`);
    console.log(`  - Graph edges: ${stats.edges}`);
    console.log(`  - Avg node degree: ${stats.avgDegree.toFixed(2)}`);

    // Cache the graph in IndexedDB
    await this.cacheGraph();
  }

  /**
   * Check if a feature is a road
   */
  private isRoadFeature(feature: MapGeoJSONFeature): boolean {
    if (!feature.geometry || feature.geometry.type !== 'LineString') {
      return false;
    }

    // Check properties for road indicators
    const props = feature.properties;
    if (!props) {
      return false;
    }

    // Check for road type property
    const type = (props.type || props.class || props.fclass || '').toLowerCase();

    // Check if it matches any road types
    return this.ROAD_TYPES.some(roadType => type.includes(roadType));
  }

  /**
   * Add a road feature to the graph
   */
  private addFeatureToGraph(feature: MapGeoJSONFeature): void {
    const geometry = feature.geometry;

    if (geometry.type === 'LineString') {
      this.addLineString(geometry.coordinates as Position[], feature.properties);
    } else if (geometry.type === 'MultiLineString') {
      const multiLine = geometry as MultiLineString;
      for (const line of multiLine.coordinates) {
        this.addLineString(line as Position[], feature.properties);
      }
    }
  }

  /**
   * Add a LineString to the graph
   */
  private addLineString(coordinates: Position[], properties: any): void {
    if (coordinates.length < 2) {
      return;
    }

    const roadType = this.getRoadType(properties);

    // Add edges between consecutive points
    for (let i = 0; i < coordinates.length - 1; i++) {
      const from = coordinates[i];
      const to = coordinates[i + 1];

      // For better visualization, include intermediate points
      const segment = coordinates.slice(i, i + 2);

      this.roadGraph.addRoadSegment(from, to, roadType, segment);
    }
  }

  /**
   * Determine road type from properties
   */
  private getRoadType(properties: any): string | undefined {
    if (!properties) {
      return undefined;
    }

    const type = (properties.type || properties.class || properties.fclass || '').toLowerCase();

    if (type.includes('highway')) return 'highway';
    if (type.includes('major')) return 'major_road';
    if (type.includes('minor')) return 'minor_road';
    if (type.includes('path')) return 'path';
    if (type.includes('trail')) return 'trail';

    return undefined;
  }

  /**
   * Get the road graph
   */
  getGraph(): RoadGraph {
    return this.roadGraph;
  }

  /**
   * Check if road network is loaded
   */
  isLoaded(): boolean {
    return this.loaded;
  }

  /**
   * Cache graph to IndexedDB
   */
  private async cacheGraph(): Promise<void> {
    if (!window.indexedDB) {
      return;
    }

    try {
      const db = await this.openDatabase();
      const transaction = db.transaction(['roadGraph'], 'readwrite');
      const store = transaction.objectStore('roadGraph');

      const graphData = {
        id: 'main',
        data: this.roadGraph.toJSON(),
        timestamp: Date.now()
      };

      store.put(graphData);

      await new Promise((resolve, reject) => {
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error);
      });

      db.close();
      console.log('Road graph cached to IndexedDB');
    } catch (error) {
      console.warn('Failed to cache road graph:', error);
    }
  }

  /**
   * Load graph from cache
   */
  async loadFromCache(): Promise<boolean> {
    if (!window.indexedDB) {
      return false;
    }

    try {
      const db = await this.openDatabase();
      const transaction = db.transaction(['roadGraph'], 'readonly');
      const store = transaction.objectStore('roadGraph');
      const request = store.get('main');

      const graphData = await new Promise<any>((resolve, reject) => {
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      });

      db.close();

      if (graphData && graphData.data) {
        // Check if cache is recent (less than 7 days old)
        const age = Date.now() - graphData.timestamp;
        const maxAge = 7 * 24 * 60 * 60 * 1000; // 7 days

        if (age < maxAge) {
          this.roadGraph = RoadGraph.fromJSON(graphData.data);
          this.loaded = true;
          console.log('Road graph loaded from cache');
          return true;
        } else {
          console.log('Cached road graph is too old, will rebuild');
        }
      }

      return false;
    } catch (error) {
      console.warn('Failed to load road graph from cache:', error);
      return false;
    }
  }

  /**
   * Open IndexedDB database
   */
  private openDatabase(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open('GolarionRoadNetwork', 1);

      request.onerror = () => reject(request.error);
      request.onsuccess = () => resolve(request.result);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains('roadGraph')) {
          db.createObjectStore('roadGraph', { keyPath: 'id' });
        }
      };
    });
  }

  /**
   * Clear cached graph
   */
  async clearCache(): Promise<void> {
    if (!window.indexedDB) {
      return;
    }

    try {
      const db = await this.openDatabase();
      const transaction = db.transaction(['roadGraph'], 'readwrite');
      const store = transaction.objectStore('roadGraph');
      store.delete('main');

      await new Promise<void>((resolve, reject) => {
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => reject(transaction.error);
      });

      db.close();
      console.log('Road graph cache cleared');
    } catch (error) {
      console.warn('Failed to clear road graph cache:', error);
    }
  }
}
