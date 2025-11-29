import Fuse from 'fuse.js';

/**
 * Search index structure from search.json
 */
export interface SearchCategory {
  category: string;
  entries: SearchEntry[];
}

export interface SearchEntry {
  label: string;
  bbox: number[]; // [minLng, minLat, maxLng, maxLat] or [lng, lat]
  areaM2?: number;
}

export interface SearchResult extends SearchEntry {
  category: string;
  score?: number;
}

/**
 * FuzzySearch class handles loading, caching, and searching the location index
 */
export class FuzzySearch {
  private searchIndex: SearchCategory[] = [];
  private fuse: Fuse<SearchResult> | null = null;
  private flattenedEntries: SearchResult[] = [];
  private loaded: boolean = false;

  constructor() {}

  /**
   * Load and initialize the search index
   */
  async load(): Promise<void> {
    if (this.loaded) {
      return;
    }

    try {
      // Try uncompressed version first (more reliable)
      let response = await fetch('./search.json');

      if (!response.ok) {
        // Fall back to compressed version
        console.log('Trying compressed search index...');
        response = await fetch('./search.json.gz');

        if (!response.ok) {
          throw new Error(`Failed to load search index: ${response.statusText}`);
        }

        // Decompress using DecompressionStream API
        const decompressedStream = response.body!.pipeThrough(
          new DecompressionStream('gzip')
        );

        const decompressedResponse = new Response(decompressedStream);
        this.searchIndex = await decompressedResponse.json();
      } else {
        // Uncompressed version
        this.searchIndex = await response.json();
      }

      // Flatten entries and add category information
      this.flattenedEntries = this.searchIndex.flatMap(cat =>
        cat.entries.map(entry => ({
          ...entry,
          category: cat.category
        }))
      );

      // Initialize Fuse.js for fuzzy matching
      this.fuse = new Fuse(this.flattenedEntries, {
        keys: [
          {
            name: 'label',
            weight: 2
          },
          {
            name: 'category',
            weight: 0.5
          }
        ],
        threshold: 0.4,
        distance: 100,
        minMatchCharLength: 2,
        includeScore: true,
        useExtendedSearch: false,
        ignoreLocation: true
      });

      this.loaded = true;
      console.log(`Search index loaded: ${this.flattenedEntries.length} entries`);
    } catch (error) {
      console.error('Failed to load search index:', error);
      throw error;
    }
  }

  /**
   * Search for locations matching the query
   * @param query Search string
   * @param options Search options
   * @returns Array of search results sorted by relevance
   */
  search(
    query: string,
    options: {
      categories?: string[];
      limit?: number;
    } = {}
  ): SearchResult[] {
    if (!this.fuse || !this.loaded) {
      console.warn('Search index not loaded');
      return [];
    }

    if (!query || query.trim().length < 2) {
      return [];
    }

    // Perform fuzzy search
    let results = this.fuse.search(query);

    // Filter by categories if specified
    if (options.categories && options.categories.length > 0) {
      results = results.filter(result =>
        options.categories!.includes(result.item.category)
      );
    }

    // Apply limit
    const limit = options.limit || 10;
    const limitedResults = results.slice(0, limit);

    // Return results with scores
    return limitedResults.map(result => ({
      ...result.item,
      score: result.score
    }));
  }

  /**
   * Get all categories in the search index
   */
  getCategories(): string[] {
    return this.searchIndex.map(cat => cat.category);
  }

  /**
   * Get entries for a specific category
   */
  getCategory(category: string): SearchEntry[] {
    const cat = this.searchIndex.find(c => c.category === category);
    return cat ? cat.entries : [];
  }

  /**
   * Check if search index is loaded
   */
  isLoaded(): boolean {
    return this.loaded;
  }

  /**
   * Get total number of searchable entries
   */
  getEntryCount(): number {
    return this.flattenedEntries.length;
  }
}

/**
 * Search history management
 */
export class SearchHistory {
  private static readonly STORAGE_KEY = 'golarion-search-history';
  private static readonly MAX_HISTORY = 20;
  private history: SearchResult[] = [];

  constructor() {
    this.load();
  }

  /**
   * Load search history from localStorage
   */
  private load(): void {
    try {
      const stored = localStorage.getItem(SearchHistory.STORAGE_KEY);
      if (stored) {
        this.history = JSON.parse(stored);
      }
    } catch (error) {
      console.warn('Failed to load search history:', error);
      this.history = [];
    }
  }

  /**
   * Save search history to localStorage
   */
  private save(): void {
    try {
      localStorage.setItem(
        SearchHistory.STORAGE_KEY,
        JSON.stringify(this.history)
      );
    } catch (error) {
      console.warn('Failed to save search history:', error);
    }
  }

  /**
   * Add a search result to history
   */
  add(result: SearchResult): void {
    // Remove duplicate if exists
    this.history = this.history.filter(item => item.label !== result.label);

    // Add to beginning
    this.history.unshift(result);

    // Limit size
    if (this.history.length > SearchHistory.MAX_HISTORY) {
      this.history = this.history.slice(0, SearchHistory.MAX_HISTORY);
    }

    this.save();
  }

  /**
   * Get search history
   */
  get(limit?: number): SearchResult[] {
    return limit ? this.history.slice(0, limit) : this.history;
  }

  /**
   * Clear search history
   */
  clear(): void {
    this.history = [];
    this.save();
  }

  /**
   * Remove a specific item from history
   */
  remove(label: string): void {
    this.history = this.history.filter(item => item.label !== label);
    this.save();
  }
}
