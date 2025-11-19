import { IControl, Map, Popup, LngLatBoundsLike } from 'maplibre-gl';
import { FuzzySearch, SearchHistory, SearchResult } from '../utils/fuzzy-search';
import { GolarionMap } from './GolarionMap';
import { Pathfinder } from './pathfinder';
import { RouteRenderer } from './route-renderer';

export default class SearchControl implements IControl {
  private map: GolarionMap;
  private fuzzySearch: FuzzySearch;
  private searchHistory: SearchHistory;
  private pathfinder: Pathfinder;
  private routeRenderer: RouteRenderer;
  private container: HTMLElement | null = null;
  private searchInput: HTMLInputElement | null = null;
  private resultsContainer: HTMLElement | null = null;
  private selectedIndex: number = -1;
  private currentResults: SearchResult[] = [];
  private directionsMode: boolean = false;
  private pointAInput: HTMLInputElement | null = null;
  private pointBInput: HTMLInputElement | null = null;
  private pointA: SearchResult | null = null;
  private pointB: SearchResult | null = null;
  private activeInput: 'A' | 'B' | null = null;
  private categoryFilter: string | null = null;
  private popup: Popup | null = null;
  private routeInfoPanel: HTMLElement | null = null;
  private lastSearchResult: SearchResult | null = null;

  constructor(map: GolarionMap) {
    this.map = map;
    this.fuzzySearch = new FuzzySearch();
    this.searchHistory = new SearchHistory();
    this.pathfinder = new Pathfinder(map.map);
    this.routeRenderer = new RouteRenderer(map.map);

    // Expose pathfinder globally for debugging
    (window as any).pathfinder = this.pathfinder;
  }

  onAdd(map: Map): HTMLElement {
    // Create main container
    this.container = document.createElement('div');
    this.container.className = 'golarion-search-control';

    // Initialize search index
    this.fuzzySearch.load().then(() => {
      console.log('Search index loaded successfully');
    }).catch((error) => {
      console.error('Failed to load search index:', error);
      this.showError('Search unavailable');
    });

    // Create search UI
    this.createSearchUI();

    return this.container;
  }

  private createSearchUI(): void {
    if (!this.container) return;

    const wrapper = document.createElement('div');
    wrapper.className = 'golarion-search-wrapper';

    // Directions toggle button
    const directionsToggle = document.createElement('button');
    directionsToggle.className = 'golarion-directions-toggle';
    directionsToggle.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
        <path d="M8 0l3 3-3 3V4H5v3L2 4l3-3V0h3zm0 16l-3-3 3-3v2h3V9l3 3-3 3v-1H8v1z"/>
      </svg>
    `;
    directionsToggle.title = 'Get Directions';
    directionsToggle.addEventListener('click', () => this.toggleDirectionsMode());

    // Single search mode
    const singleSearchDiv = document.createElement('div');
    singleSearchDiv.className = 'golarion-search-single';

    this.searchInput = document.createElement('input');
    this.searchInput.type = 'text';
    this.searchInput.className = 'golarion-search-input';
    this.searchInput.placeholder = 'Search locations...';
    this.searchInput.addEventListener('input', () => this.handleSearch());
    this.searchInput.addEventListener('keydown', (e) => this.handleKeyDown(e));
    this.searchInput.addEventListener('focus', () => this.handleFocus());
    this.searchInput.addEventListener('blur', () => this.handleBlur());

    const searchIcon = document.createElement('span');
    searchIcon.className = 'golarion-search-icon';
    searchIcon.innerHTML = '🔍';

    singleSearchDiv.appendChild(searchIcon);
    singleSearchDiv.appendChild(this.searchInput);

    // Directions mode (Point A and Point B)
    const directionsDiv = document.createElement('div');
    directionsDiv.className = 'golarion-directions-inputs';
    directionsDiv.style.display = 'none';

    // Point A input
    const pointAWrapper = document.createElement('div');
    pointAWrapper.className = 'golarion-point-wrapper';

    const pointALabel = document.createElement('span');
    pointALabel.className = 'golarion-point-label';
    pointALabel.textContent = 'A';

    this.pointAInput = document.createElement('input');
    this.pointAInput.type = 'text';
    this.pointAInput.className = 'golarion-point-input';
    this.pointAInput.placeholder = 'Start location';
    this.pointAInput.addEventListener('input', () => this.handleSearch('A'));
    this.pointAInput.addEventListener('keydown', (e) => this.handleKeyDown(e, 'A'));
    this.pointAInput.addEventListener('focus', () => this.handleFocus('A'));
    this.pointAInput.addEventListener('blur', () => this.handleBlur());

    pointAWrapper.appendChild(pointALabel);
    pointAWrapper.appendChild(this.pointAInput);

    // Point B input
    const pointBWrapper = document.createElement('div');
    pointBWrapper.className = 'golarion-point-wrapper';

    const pointBLabel = document.createElement('span');
    pointBLabel.className = 'golarion-point-label';
    pointBLabel.textContent = 'B';

    this.pointBInput = document.createElement('input');
    this.pointBInput.type = 'text';
    this.pointBInput.className = 'golarion-point-input';
    this.pointBInput.placeholder = 'Destination';
    this.pointBInput.addEventListener('input', () => this.handleSearch('B'));
    this.pointBInput.addEventListener('keydown', (e) => this.handleKeyDown(e, 'B'));
    this.pointBInput.addEventListener('focus', () => this.handleFocus('B'));
    this.pointBInput.addEventListener('blur', () => this.handleBlur());

    pointBWrapper.appendChild(pointBLabel);
    pointBWrapper.appendChild(this.pointBInput);

    // Swap button
    const swapButton = document.createElement('button');
    swapButton.className = 'golarion-swap-button';
    swapButton.innerHTML = '⇅';
    swapButton.title = 'Swap start and destination';
    swapButton.addEventListener('click', () => this.swapPoints());

    // Clear route button
    const clearButton = document.createElement('button');
    clearButton.className = 'golarion-clear-button';
    clearButton.textContent = 'Clear';
    clearButton.addEventListener('click', () => this.clearRoute());

    // Saved routes button
    const savedRoutesButton = document.createElement('button');
    savedRoutesButton.className = 'golarion-saved-routes-button';
    savedRoutesButton.innerHTML = '💾';
    savedRoutesButton.title = 'Manage Saved Routes';
    savedRoutesButton.style.cssText = `
      background: none;
      border: none;
      padding: 8px;
      cursor: pointer;
      font-size: 14px;
      border-radius: 3px;
      transition: background-color 0.2s;
    `;
    savedRoutesButton.addEventListener('click', () => this.routeRenderer.showSavedRoutesPanel());
    savedRoutesButton.addEventListener('mouseover', () => {
      savedRoutesButton.style.backgroundColor = '#f0f0f0';
    });
    savedRoutesButton.addEventListener('mouseout', () => {
      savedRoutesButton.style.backgroundColor = 'transparent';
    });

    directionsDiv.appendChild(pointAWrapper);
    directionsDiv.appendChild(swapButton);
    directionsDiv.appendChild(pointBWrapper);
    directionsDiv.appendChild(clearButton);
    directionsDiv.appendChild(savedRoutesButton);

    // Results container
    this.resultsContainer = document.createElement('div');
    this.resultsContainer.className = 'golarion-search-results';
    this.resultsContainer.style.display = 'none';

    // Category filter
    const filterBar = document.createElement('div');
    filterBar.className = 'golarion-filter-bar';
    filterBar.style.display = 'none'; // Show when search is active

    wrapper.appendChild(directionsToggle);
    wrapper.appendChild(singleSearchDiv);
    wrapper.appendChild(directionsDiv);
    wrapper.appendChild(filterBar);
    wrapper.appendChild(this.resultsContainer);

    this.container!.appendChild(wrapper);
  }

  private toggleDirectionsMode(): void {
    this.directionsMode = !this.directionsMode;

    const singleSearch = this.container!.querySelector('.golarion-search-single') as HTMLElement;
    const directionsInputs = this.container!.querySelector('.golarion-directions-inputs') as HTMLElement;

    if (this.directionsMode) {
      // Entering directions mode
      singleSearch.style.display = 'none';
      directionsInputs.style.display = 'block';

      // Transfer last search result to Point A if available
      if (this.lastSearchResult) {
        this.pointA = this.lastSearchResult;
        if (this.pointAInput) {
          this.pointAInput.value = this.lastSearchResult.label;
        }
        // Focus on Point B since A is already filled
        setTimeout(() => this.pointBInput?.focus(), 100);
      } else {
        // No previous search, focus on Point A
        this.pointAInput?.focus();
      }
    } else {
      // Exiting directions mode
      singleSearch.style.display = 'flex';
      directionsInputs.style.display = 'none';
      this.searchInput?.focus();
      this.clearRoute();
    }

    this.hideResults();
  }

  private handleFocus(point?: 'A' | 'B'): void {
    if (this.directionsMode && point) {
      this.activeInput = point;
      // Show recent searches or all results
      if (this.fuzzySearch.isLoaded()) {
        const recentSearches = this.searchHistory.get(5);
        if (recentSearches.length > 0) {
          this.currentResults = recentSearches;
          this.displayResults();
        }
      }
    } else if (!this.directionsMode) {
      this.activeInput = null;
      const recentSearches = this.searchHistory.get(5);
      if (recentSearches.length > 0) {
        this.currentResults = recentSearches;
        this.displayResults();
      }
    }
  }

  private handleBlur(): void {
    // Delay hiding results to allow click events to fire
    setTimeout(() => {
      this.hideResults();
    }, 200);
  }

  private handleSearch(point?: 'A' | 'B'): void {
    let query: string;

    if (this.directionsMode && point) {
      this.activeInput = point;
      query = point === 'A' ? this.pointAInput!.value : this.pointBInput!.value;
    } else {
      query = this.searchInput!.value;
    }

    if (query.trim().length < 2) {
      // Show recent searches
      const recentSearches = this.searchHistory.get(5);
      this.currentResults = recentSearches;
      this.displayResults();
      return;
    }

    // Perform search
    const results = this.fuzzySearch.search(query, {
      categories: this.categoryFilter ? [this.categoryFilter] : undefined,
      limit: 10
    });

    this.currentResults = results;
    this.selectedIndex = -1;
    this.displayResults();
  }

  private handleKeyDown(event: KeyboardEvent, point?: 'A' | 'B'): void {
    if (this.directionsMode && point) {
      this.activeInput = point;
    }

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
      case 'Tab':
        if (this.directionsMode && point === 'A' && this.selectedIndex >= 0) {
          event.preventDefault();
          this.selectResult(this.currentResults[this.selectedIndex]);
          this.pointBInput?.focus();
        }
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

    if (this.directionsMode && this.activeInput) {
      // Set point A or B
      if (this.activeInput === 'A') {
        this.pointA = result;
        this.pointAInput!.value = result.label;
        // Focus on point B
        setTimeout(() => this.pointBInput?.focus(), 100);
      } else {
        this.pointB = result;
        this.pointBInput!.value = result.label;
      }

      // If both points are set, calculate route
      if (this.pointA && this.pointB) {
        this.calculateRoute();
      }
    } else {
      // Single search mode - navigate to location
      this.lastSearchResult = result; // Save for potential directions mode
      this.navigateToResult(result);
      this.searchInput!.value = result.label;
    }

    this.hideResults();
  }

  private navigateToResult(result: SearchResult): void {
    const bbox = result.bbox;

    if (bbox.length === 2) {
      // Point location [lng, lat]
      this.map.map.flyTo({
        center: [bbox[0], bbox[1]],
        zoom: 12,
        duration: 2000
      });

      // Show popup
      this.showPopup(result, [bbox[0], bbox[1]]);
    } else if (bbox.length === 4) {
      // Bounding box [minLng, minLat, maxLng, maxLat]
      this.map.map.fitBounds(bbox as LngLatBoundsLike, {
        padding: 50,
        duration: 2000
      });

      // Calculate center for popup
      const centerLng = (bbox[0] + bbox[2]) / 2;
      const centerLat = (bbox[1] + bbox[3]) / 2;
      this.showPopup(result, [centerLng, centerLat]);
    }
  }

  private showPopup(result: SearchResult, coordinates: [number, number]): void {
    // Remove existing popup
    if (this.popup) {
      this.popup.remove();
    }

    // Create popup content
    const content = document.createElement('div');
    content.className = 'golarion-search-popup';

    const title = document.createElement('h3');
    title.textContent = result.label;

    const category = document.createElement('p');
    category.textContent = `Category: ${result.category}`;

    content.appendChild(title);
    content.appendChild(category);

    // Create and show popup
    this.popup = new Popup({
      closeButton: true,
      closeOnClick: false
    })
      .setLngLat(coordinates)
      .setDOMContent(content)
      .addTo(this.map.map);
  }

  private async calculateRoute(): Promise<void> {
    if (!this.pointA || !this.pointB) return;

    console.log('Route requested from', this.pointA.label, 'to', this.pointB.label);

    // Get coordinates for start and end points
    const startCoords = this.getCoordinatesFromResult(this.pointA);
    const endCoords = this.getCoordinatesFromResult(this.pointB);

    if (!startCoords || !endCoords) {
      this.showError('Unable to determine coordinates for route');
      return;
    }

    try {
      // Validate city coordinates for geographic plausibility
      console.log('Validating start and end coordinates...');
      const startValidation = await this.pathfinder.validateCityCoordinate(startCoords, this.pointA!.label);
      const endValidation = await this.pathfinder.validateCityCoordinate(endCoords, this.pointB!.label);

      // Log validation results
      console.log('Route validation results:', {
        start: {
          city: this.pointA!.label,
          coordinate: startCoords,
          ...startValidation
        },
        end: {
          city: this.pointB!.label,
          coordinate: endCoords,
          ...endValidation
        }
      });

      // Calculate route using geodesic pathfinding with terrain detection
      const route = await this.pathfinder.findRoute(startCoords, endCoords);

      if (!route.success) {
        this.showError(route.message || 'Failed to find route');
        return;
      }

      // Display route on map with land/water styling
      this.routeRenderer.displayRoute(route, this.pointA.label, this.pointB.label);

      // Calculate travel times for all methods
      const travelTimes = this.pathfinder.calculateAllTravelTimes(route);

      // Set the fastest travel method for route saving
      if (travelTimes.length > 0) {
        this.routeRenderer.setTravelMethod(travelTimes[0].method);
      }

      // Show route info panel with travel time breakdown
      this.showRouteInfo(route, travelTimes);

      console.log('Route calculated successfully:', route);
      console.log('Travel times:', travelTimes);
    } catch (error) {
      console.error('Route calculation error:', error);
      this.showError('Error calculating route');
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

  private showRouteInfo(route: any, travelTimes: any[]): void {
    // Remove existing panel
    if (this.routeInfoPanel) {
      this.routeInfoPanel.remove();
    }

    // Create info panel
    this.routeInfoPanel = this.routeRenderer.showRouteInfo(route, travelTimes);

    // Add to map container
    const mapContainer = document.getElementById('map-container');
    if (mapContainer) {
      mapContainer.appendChild(this.routeInfoPanel);
    }
  }

  private swapPoints(): void {
    const tempPoint = this.pointA;
    const tempValue = this.pointAInput!.value;

    this.pointA = this.pointB;
    this.pointAInput!.value = this.pointBInput!.value;

    this.pointB = tempPoint;
    this.pointBInput!.value = tempValue;

    if (this.pointA && this.pointB) {
      this.calculateRoute();
    }
  }

  private clearRoute(): void {
    this.pointA = null;
    this.pointB = null;
    if (this.pointAInput) this.pointAInput.value = '';
    if (this.pointBInput) this.pointBInput.value = '';

    // Clear route visualization from map
    this.routeRenderer.clearRoute();

    // Clear route info panel
    if (this.routeInfoPanel) {
      this.routeInfoPanel.remove();
      this.routeInfoPanel = null;
    }
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
