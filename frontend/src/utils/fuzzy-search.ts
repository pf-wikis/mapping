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
  bbox: [number, number]|[number, number, number, number]; // [minLng, minLat, maxLng, maxLat] or [lng, lat]
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
  private fuse: Fuse<SearchResult>;
  private flattenedEntries: SearchResult[] = [];

  constructor(searchIndex: SearchCategory[], fuse: Fuse<SearchResult>, flattenedEntries: SearchResult[]) {
    this.searchIndex = searchIndex;
    this.fuse = fuse;
    this.flattenedEntries = flattenedEntries;
  }

  static lazyInit = (fn: () => Promise<any>): (() => Promise<any>) => {
    let prom: Promise<any> | undefined = undefined;
    return (): Promise<any> => prom = (prom || fn());
  }

  /**
   * Load and initialize the search index
   */
  static get: ()=>Promise<FuzzySearch> = FuzzySearch.lazyInit(async () => {
    try {
      let response = await fetch('./search.json?v=' + import.meta.env.VITE_DATA_HASH);

      if (!response.ok) {
        throw new Error(`Failed to load search index: ${response.statusText}`);
      }
      
      let searchIndex = await response.json() as SearchCategory[];

      // Flatten entries and add category information
      let flattenedEntries = searchIndex.flatMap(cat =>
        cat.entries.map(entry => ({
          ...entry,
          category: cat.category
        }))
      );

      // Initialize Fuse.js for fuzzy matching
      let fuse = new Fuse<SearchResult>(flattenedEntries, {
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

      console.log(`Search index loaded: ${flattenedEntries.length} entries`);
      return new FuzzySearch(searchIndex, fuse, flattenedEntries);
    } catch (error) {
      console.error('Failed to load search index:', error);
      throw error;
    }
  });

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
    this.history = this.history.filter(item => item.label !== result.label || item.category !== result.category);

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