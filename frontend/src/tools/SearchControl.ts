import { IControl, Map, Popup, LngLatBoundsLike } from 'maplibre-gl';
import { FuzzySearch, SearchHistory, SearchResult } from '../utils/fuzzy-search';
import { GolarionMap } from './GolarionMap';

export default class SearchControl implements IControl {
  private map: GolarionMap;
  private searchHistory: SearchHistory;
  private container: HTMLElement;
  private searchInput: HTMLInputElement | null = null;
  private resultsContainer: HTMLElement | null = null;
  private selectedIndex: number = -1;
  private currentResults: SearchResult[] = [];
  private categoryFilter: string | null = null;
  private popup: Popup | null = null;
  private lastSearchResult: SearchResult | null = null;

  constructor(map: GolarionMap) {
    this.map = map;
    this.searchHistory = new SearchHistory();

    // Create main container
    this.container = document.createElement('div');
    this.container.className = 'golarion-search-control';
  }

  onAdd(map: Map): HTMLElement {
    // Create search UI
    this.createSearchUI();

    return this.container;
  }

  private createSearchUI(): void {
    if (!this.container) return;

    const wrapper = document.createElement('div');
    wrapper.className = 'golarion-search-wrapper';

    this.searchInput = document.createElement('input');
    this.searchInput.autocomplete = "off";
    this.searchInput.type = 'text';
    this.searchInput.name = 'search';
    this.searchInput.className = 'golarion-search-input';
    this.searchInput.placeholder = 'Search…';
    this.searchInput.addEventListener('input', () => this.handleSearch());
    this.searchInput.addEventListener('keydown', (e) => this.handleKeyDown(e));
    this.searchInput.addEventListener('focus', () => this.handleFocus());
    this.searchInput.addEventListener('blur', () => this.handleBlur());

    // Results container
    this.resultsContainer = document.createElement('div');
    this.resultsContainer.className = 'golarion-search-results';

    wrapper.appendChild(this.searchInput);

    this.container!.appendChild(wrapper);
    this.container!.appendChild(this.resultsContainer);
  }

  private handleBlur(): void {
    // Delay hiding results to allow click events to fire
    setTimeout(() => {
      this.hideResults();
    }, 200);
  }

  private handleSearch(point?: 'A' | 'B'): void {
    let query = this.searchInput!.value;

    if (query.trim().length < 2) {
      // Show recent searches
      const recentSearches = this.searchHistory.get(5);
      this.currentResults = recentSearches;
      this.displayResults();
      return;
    }

    // Perform search
    FuzzySearch.get().then((fuzzySearch) => {
      const results = fuzzySearch.search(query, {
        categories: this.categoryFilter ? [this.categoryFilter] : undefined,
        limit: 10
      });

      this.currentResults = results;
      this.selectedIndex = -1;
      this.displayResults();
    }).catch((error) => {
      console.error('Failed to load search index:', error);
      this.showError('Search unavailable');
    });
    
  }

  private handleFocus(): void {
    const recentSearches = this.searchHistory.get(5);
    if (recentSearches.length > 0) {
        this.currentResults = recentSearches;
        this.displayResults();
    }
  }


  private handleKeyDown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.selectedIndex = Math.min(this.selectedIndex + 1, this.currentResults.length - 1);
        this.updateSelection();
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.selectedIndex = Math.max(this.selectedIndex - 1, -1);
        this.updateSelection();
        break;
      case 'Enter':
        event.preventDefault();
        if (this.selectedIndex >= 0 && this.selectedIndex < this.currentResults.length) {
          this.selectResult(this.currentResults[this.selectedIndex]);
        }
        break;
      case 'Escape':
        event.preventDefault();
        this.hideResults();
        break;
    }
  }

  private displayResults(): void {
    if (!this.resultsContainer) return;

    this.resultsContainer.innerHTML = '';

    if (this.currentResults.length === 0) {
      this.resultsContainer.style.display = 'none';
      return;
    }

    this.currentResults.forEach((result, index) => {
      const item = document.createElement('div');
      item.className = 'golarion-search-result-item';
      if (index === this.selectedIndex) {
        item.classList.add('selected');
      }

      const title = document.createElement('div');
      title.className = 'golarion-result-title';
      title.textContent = result.label;

      const category = document.createElement('div');
      category.className = 'golarion-result-category';
      category.textContent = result.category;

      item.appendChild(title);
      item.appendChild(category);

      item.addEventListener('click', () => {
        this.selectResult(result);
      });

      this.resultsContainer!.appendChild(item);
    });

    this.resultsContainer.style.display = 'block';
  }

  private updateSelection(): void {
    if (!this.resultsContainer) return;

    const items = this.resultsContainer.querySelectorAll('.golarion-search-result-item');
    items.forEach((item, index) => {
      if (index === this.selectedIndex) {
        item.classList.add('selected');
        item.scrollIntoView({ block: 'nearest' });
      } else {
        item.classList.remove('selected');
      }
    });
  }

  private selectResult(result: SearchResult): void {
    // Add to history
    this.searchHistory.add(result);


    // Single search mode - navigate to location
    this.lastSearchResult = result; // Save for potential directions mode
    this.navigateToResult(result);
    this.searchInput!.value = result.label;

    this.hideResults();
  }

  private navigateToResult(result: SearchResult): void {
    const bbox = result.bbox;

    if (bbox.length === 2) {
      // Point location [lng, lat]
      this.map.map.flyTo({
        center: [bbox[0], bbox[1]],
        zoom: 7,
        duration: 2000
      });
    } else if (bbox.length === 4) {
      // Bounding box [minLng, minLat, maxLng, maxLat]
      this.map.map.fitBounds(bbox as LngLatBoundsLike, {
        padding: 50,
        duration: 2000
      });
    }
  }

  private getCoordinatesFromResult(result: SearchResult): [number, number] | null {
    const bbox = result.bbox;

    if (bbox.length === 2) {
      // Point location [lng, lat]
      return [bbox[0], bbox[1]];
    } else if (bbox.length === 4) {
      // Bounding box [minLng, minLat, maxLng, maxLat] - use center
      const centerLng = (bbox[0] + bbox[2]) / 2;
      const centerLat = (bbox[1] + bbox[3]) / 2;
      return [centerLng, centerLat];
    }

    return null;
  }

  private hideResults(): void {
    if (this.resultsContainer) {
      this.resultsContainer.style.display = 'none';
    }
    this.selectedIndex = -1;
  }

  private showError(message: string): void {
    if (!this.container) return;

    const error = document.createElement('div');
    error.className = 'golarion-search-error';
    error.textContent = message;
    this.container.appendChild(error);

    setTimeout(() => {
      error.remove();
    }, 3000);
  }

  onRemove(map: Map): void {
    if (this.popup) {
      this.popup.remove();
    }
    if (this.container && this.container.parentNode) {
      this.container.parentNode.removeChild(this.container);
    }
  }
}