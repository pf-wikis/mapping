# Waypoint Editing Feature

This document describes the new drag-and-drop waypoint editing feature for routes on the Golarion World Map.

## Overview

The waypoint editing feature allows users to:
- **Click on any path** to add a new waypoint node
- **Hover near waypoints** to reveal visual handles
- **Drag waypoints** to reposition them (with automatic terrain-aware pathfinding recalculation)
- **Right-click waypoints** to delete them

## Usage

### Enabling Waypoint Editing

To enable waypoint editing mode in your code:

```typescript
// Get the RouteRenderer instance
const routeRenderer = new RouteRenderer(map);

// Calculate and display a route
await routeRenderer.calculateAndDisplayRoute(startPosition, endPosition, 'walking');

// Enable waypoint editing mode
routeRenderer.enableWaypointEditing();
```

### User Interactions

Once enabled:

1. **Adding Waypoints**
   - Click anywhere on the displayed route path
   - A new waypoint will be created at that location
   - The route automatically recalculates through all waypoints

2. **Showing Waypoint Handles**
   - Move your mouse near the path
   - Circular handles appear at waypoint locations
   - The hovered handle scales up and becomes fully visible
   - Other nearby handles show at 50% opacity

3. **Dragging Waypoints**
   - Click and hold on a waypoint handle
   - Drag to a new location
   - The handle follows your mouse in real-time
   - Release to finalize the position
   - The route automatically recalculates with terrain-aware pathfinding

4. **Deleting Waypoints**
   - Right-click on a waypoint handle
   - The waypoint is removed
   - The route automatically recalculates

### Disabling Waypoint Editing

To disable waypoint editing mode:

```typescript
routeRenderer.disableWaypointEditing();
```

### Clearing All Waypoints

To remove all waypoints and reset to the original route:

```typescript
routeRenderer.clearWaypoints();
```

## Technical Details

### Route Calculation with Waypoints

When waypoints are present, the route is calculated as a series of segments:

```
start → waypoint1 → waypoint2 → ... → waypointN → end
```

Each segment uses terrain-aware pathfinding (following roads, avoiding water when appropriate, etc.) based on the selected travel method (walking, riding, etc.).

### No Conflicts with Existing Handlers

The waypoint editing system is designed to coexist with existing map interactions:

- **Segment selection** (existing left-click handler on routes) still works
- **Location popups** continue to function normally
- **Map panning** is temporarily disabled only while actively dragging a waypoint

### Event Handling

The system uses:
- `mousedown` + `mousemove` + `mouseup` for drag-and-drop
- `click` on route layers for adding waypoints
- `mousemove` globally for hover detection
- `contextmenu` for right-click deletion

### Visual Styling

Waypoint handles use CSS classes defined in `style.scss`:

```scss
.waypoint-handle {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background-color: white;
  border: 2px solid #333;
  opacity: 0; // Hidden by default
  cursor: grab;
  transition: opacity 0.2s, transform 0.2s;

  &:hover {
    background-color: #4CAF50; // Green on hover
    transform: scale(1.3);
  }

  &:active {
    cursor: grabbing;
  }
}
```

## Example: Complete Integration

Here's a complete example showing how to integrate waypoint editing into your application:

```typescript
import { Map as MapLibreMap } from 'maplibre-gl';
import { RouteRenderer } from './tools/route-renderer';

// Initialize map
const map = new MapLibreMap({
  container: 'map',
  // ... other map options
});

// Create route renderer
const routeRenderer = new RouteRenderer(map);

// Calculate a route
const startPos = [10.5, 42.3];  // [lng, lat]
const endPos = [11.2, 43.1];
await routeRenderer.calculateAndDisplayRoute(startPos, endPos, 'walking');

// Enable waypoint editing
routeRenderer.enableWaypointEditing();

// Optional: Add a button to toggle waypoint editing
const toggleButton = document.getElementById('toggle-waypoints');
let editingEnabled = true;

toggleButton.addEventListener('click', () => {
  if (editingEnabled) {
    routeRenderer.disableWaypointEditing();
    toggleButton.textContent = 'Enable Waypoint Editing';
  } else {
    routeRenderer.enableWaypointEditing();
    toggleButton.textContent = 'Disable Waypoint Editing';
  }
  editingEnabled = !editingEnabled;
});
```

## Browser Compatibility

The feature uses standard web APIs supported by all modern browsers:
- `MouseEvent` (mousedown, mousemove, mouseup)
- `MapLibre Marker` API
- `GeoJSON` features
- CSS transforms and transitions

Tested on:
- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)

## Performance Considerations

- Route recalculation happens asynchronously on waypoint updates
- Pathfinding uses optimized graph algorithms
- Marker positions update in real-time during drag without triggering route recalculation
- Full route recalculation only occurs on `mouseup` (drag complete)

## Future Enhancements

Potential improvements:
- Keyboard shortcuts (Delete key to remove selected waypoint)
- Undo/redo support for waypoint operations
- Snap-to-road option
- Save/load waypoints with routes
- Visual feedback during route recalculation (loading indicator)
