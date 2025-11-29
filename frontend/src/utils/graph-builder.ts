import turfDistance from '@turf/distance';
import { Position } from 'geojson';

/**
 * Graph node representing an intersection or endpoint
 */
export interface GraphNode {
  id: string;
  coords: Position; // [lng, lat]
  edges: GraphEdge[];
}

/**
 * Graph edge representing a road segment
 */
export interface GraphEdge {
  to: string; // Node ID
  weight: number; // Cost (distance in km)
  roadType?: string; // Optional road type for cost calculation
  geometry?: Position[]; // Optional path geometry for visualization
}

/**
 * Road network graph for pathfinding
 */
export class RoadGraph {
  private nodes: Map<string, GraphNode> = new Map();
  private readonly SNAP_THRESHOLD = 0.0001; // ~11 meters at equator

  constructor() {}

  /**
   * Create a unique node ID from coordinates
   */
  private createNodeId(coords: Position): string {
    const lng = Math.round(coords[0] / this.SNAP_THRESHOLD) * this.SNAP_THRESHOLD;
    const lat = Math.round(coords[1] / this.SNAP_THRESHOLD) * this.SNAP_THRESHOLD;
    return `${lng.toFixed(6)},${lat.toFixed(6)}`;
  }

  /**
   * Add a node to the graph or get existing node
   */
  addNode(coords: Position): GraphNode {
    const id = this.createNodeId(coords);
    let node = this.nodes.get(id);

    if (!node) {
      node = {
        id,
        coords: [
          Math.round(coords[0] / this.SNAP_THRESHOLD) * this.SNAP_THRESHOLD,
          Math.round(coords[1] / this.SNAP_THRESHOLD) * this.SNAP_THRESHOLD
        ],
        edges: []
      };
      this.nodes.set(id, node);
    }

    return node;
  }

  /**
   * Add a road segment (bidirectional edge between two nodes)
   */
  addRoadSegment(
    from: Position,
    to: Position,
    roadType?: string,
    geometry?: Position[]
  ): void {
    const fromNode = this.addNode(from);
    const toNode = this.addNode(to);

    // Calculate distance
    const distance = turfDistance(from, to);

    // Calculate weight based on road type
    const weight = this.calculateWeight(distance, roadType);

    // Add bidirectional edges
    fromNode.edges.push({
      to: toNode.id,
      weight,
      roadType,
      geometry
    });

    toNode.edges.push({
      to: fromNode.id,
      weight,
      roadType,
      geometry: geometry ? [...geometry].reverse() : undefined
    });
  }

  /**
   * Calculate edge weight based on distance and road type
   * Lower weight = preferred route
   */
  private calculateWeight(distance: number, roadType?: string): number {
    let multiplier = 1.0;

    // Adjust weight based on road type
    // Prefer major roads over minor roads
    switch (roadType) {
      case 'highway':
      case 'major_road':
        multiplier = 0.8; // Prefer these routes
        break;
      case 'minor_road':
      case 'path':
        multiplier = 1.2; // Less preferred
        break;
      case 'trail':
        multiplier = 1.5; // Least preferred
        break;
      default:
        multiplier = 1.0;
    }

    return distance * multiplier;
  }

  /**
   * Get a node by coordinates (snapped to grid)
   */
  getNode(coords: Position): GraphNode | undefined {
    const id = this.createNodeId(coords);
    return this.nodes.get(id);
  }

  /**
   * Get a node by ID
   */
  getNodeById(id: string): GraphNode | undefined {
    return this.nodes.get(id);
  }

  /**
   * Find nearest node to given coordinates
   */
  findNearestNode(coords: Position, maxDistance: number = 1): GraphNode | null {
    let nearest: GraphNode | null = null;
    let minDistance = maxDistance;

    for (const node of this.nodes.values()) {
      const distance = turfDistance(coords, node.coords);
      if (distance < minDistance) {
        minDistance = distance;
        nearest = node;
      }
    }

    return nearest;
  }

  /**
   * Get all nodes in the graph
   */
  getAllNodes(): GraphNode[] {
    return Array.from(this.nodes.values());
  }

  /**
   * Get total number of nodes
   */
  getNodeCount(): number {
    return this.nodes.size;
  }

  /**
   * Get total number of edges
   */
  getEdgeCount(): number {
    let count = 0;
    for (const node of this.nodes.values()) {
      count += node.edges.length;
    }
    return count / 2; // Divide by 2 because edges are bidirectional
  }

  /**
   * Clear the graph
   */
  clear(): void {
    this.nodes.clear();
  }

  /**
   * Serialize graph to JSON for caching
   */
  toJSON(): string {
    const data = {
      nodes: Array.from(this.nodes.entries())
    };
    return JSON.stringify(data);
  }

  /**
   * Deserialize graph from JSON
   */
  static fromJSON(json: string): RoadGraph {
    const graph = new RoadGraph();
    const data = JSON.parse(json);

    for (const [id, node] of data.nodes) {
      graph.nodes.set(id, node);
    }

    return graph;
  }

  /**
   * Get graph statistics
   */
  getStats(): {
    nodes: number;
    edges: number;
    avgDegree: number;
  } {
    const nodes = this.getNodeCount();
    const edges = this.getEdgeCount();

    let totalDegree = 0;
    for (const node of this.nodes.values()) {
      totalDegree += node.edges.length;
    }

    return {
      nodes,
      edges,
      avgDegree: nodes > 0 ? totalDegree / nodes : 0
    };
  }
}

/**
 * Priority queue for A* algorithm
 */
export class PriorityQueue<T> {
  private items: Array<{ item: T; priority: number }> = [];

  /**
   * Add an item with priority
   */
  enqueue(item: T, priority: number): void {
    const element = { item, priority };

    // Find insertion point
    let inserted = false;
    for (let i = 0; i < this.items.length; i++) {
      if (priority < this.items[i].priority) {
        this.items.splice(i, 0, element);
        inserted = true;
        break;
      }
    }

    if (!inserted) {
      this.items.push(element);
    }
  }

  /**
   * Remove and return item with lowest priority
   */
  dequeue(): T | undefined {
    const element = this.items.shift();
    return element?.item;
  }

  /**
   * Check if queue is empty
   */
  isEmpty(): boolean {
    return this.items.length === 0;
  }

  /**
   * Get queue size
   */
  size(): number {
    return this.items.length;
  }

  /**
   * Clear the queue
   */
  clear(): void {
    this.items = [];
  }
}
