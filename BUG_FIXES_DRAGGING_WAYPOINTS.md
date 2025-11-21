# Bug Fixes: Dragging and Waypoint Editing

## Issues Fixed

### Issue 1: Panel Not Draggable
**Problem:** The route info panel wasn't draggable even though drag code was implemented.

**Root Cause:** `enablePanelDragging()` was called BEFORE the panel was added to the DOM. Event listeners attached to elements not yet in the document tree don't work properly.

**Fix Applied:**
1. Removed premature `enablePanelDragging(panel)` call from `showRouteInfo()` (line 1257)
2. Added new public method `enablePanelDraggingForCurrentPanel()` (lines 2279-2289)
3. Updated `search.ts` to call this method AFTER appending panel to DOM (line 665)
4. Added DOM attachment safety checks in `enablePanelDragging()` (lines 2290-2300)

### Issue 2: Cannot Grab Path Waypoints
**Problem:** Clicking the "Edit Waypoints" button didn't enable waypoint editing.

**Root Cause:** The button's onclick handler was being overwritten due to reassignment logic (lines 1117-1121). The closure lost context when the handler was wrapped.

**Fix Applied:**
1. Moved `waypointHelp` element creation BEFORE button onclick assignment
2. Combined both click handlers into a single cohesive function
3. Removed the problematic handler reassignment pattern
4. Added guards for empty `waypointMarkers` array (line 2114 and 2134)

## Files Modified

### 1. `frontend/src/tools/route-renderer.ts`
**Changes:**
- **Line 1256-1258:** Removed premature `enablePanelDragging()` call, added comment
- **Lines 2279-2289:** Added `enablePanelDraggingForCurrentPanel()` public method
- **Lines 2290-2300:** Added DOM attachment verification in `enablePanelDragging()`
- **Lines 1080-1116:** Reorganized waypoint button onclick handler logic
- **Lines 2114, 2134:** Added guards for empty waypointMarkers array

### 2. `frontend/src/tools/search.ts`
**Changes:**
- **Line 665:** Added call to `enablePanelDraggingForCurrentPanel()` after DOM insertion

## How To Use

### Dragging the Panel

1. **Calculate any route** between two locations
2. **Route panel appears** in bottom-right corner
3. **Hover over panel header** - cursor changes to grab hand
4. **Click and drag the header** - panel moves smoothly
5. **Release** - panel stays in new position
6. **Position persists** for current session

**Visual Indicators:**
- Grab cursor (open hand) on header hover
- Grabbing cursor (closed hand) while dragging
- Blue glow shadow during drag
- 6-dot drag handle icon on left side of header

### Using Waypoint Editing

1. **Calculate a route** - Panel appears with route details
2. **Click "Edit Waypoints" button** - Button turns green
3. **Instructions appear** below button showing how to use
4. **Click on the route path** - Adds a waypoint at that location
5. **Hover near the path** - Circular handles appear at waypoints
6. **Click and drag a handle** - Repositions the waypoint
7. **Route recalculates automatically** with terrain-aware pathfinding
8. **Right-click a handle** - Deletes that waypoint

**Visual Indicators:**
- White button: Editing disabled
- Green button: Editing enabled
- Invisible handles until hover (within ~25 pixels)
- Handles scale up (1.3x) when directly hovered
- Other handles show at 50% opacity when nearby

## Technical Details

### Event Listener Timing
**Before:** Event listeners attached to panel before it exists in DOM
**After:** Listeners attached only after `document.contains(panel)` returns true

### Safety Checks Added
```typescript
// In enablePanelDragging()
if (!document.contains(panel)) {
  console.warn('Panel not in DOM, cannot enable dragging');
  return;
}

// In enableWaypointEditing() hover handler
if (nearestWaypoint && this.waypointMarkers.length > 0) {
  // ... safe to iterate
}
```

### Button Handler Pattern
**Before (Broken):**
```typescript
// First assignment
waypointButton.onclick = () => { /* toggle logic */ };

// Second assignment OVERWRITES first
const originalOnClick = waypointButton.onclick;
waypointButton.onclick = (e) => {
  originalOnClick!(e);  // May lose closure context
  // ... additional logic
};
```

**After (Fixed):**
```typescript
// Single combined handler
waypointButton.onclick = () => {
  if (this.isWaypointEditingEnabled) {
    this.disableWaypointEditing();
    // ... update UI
    waypointHelp.style.display = 'none';
  } else {
    this.enableWaypointEditing();
    // ... update UI
    waypointHelp.style.display = 'block';
  }
};
```

## Testing Results

✅ **Build successful** - No TypeScript or compilation errors
✅ **Panel draggable** - Cursor changes, smooth dragging works
✅ **Boundaries enforced** - Cannot drag outside viewport
✅ **Waypoint button functional** - Properly toggles editing mode
✅ **Safety checks working** - Console warnings if DOM not ready
✅ **Event handlers correct** - No overwriting or context loss

## Console Messages

When features are working correctly, you should see:
- "Waypoint editing enabled..." when clicking Edit Waypoints button
- "Waypoint editing disabled." when toggling off
- NO warnings about panel not in DOM
- NO warnings about headers not found

If you see warnings:
- "Panel not in DOM, cannot enable dragging" → Call `enablePanelDraggingForCurrentPanel()` after DOM insertion
- "Header not found in panel" → Check panel structure has `.golarion-route-header` element
- "Cannot enable panel dragging: panel not in DOM" → Verify panel was appended to map container

## Known Limitations

1. **Position persistence:** Panel position resets on page refresh (could add localStorage)
2. **Mobile touch:** Drag works with mouse only, not optimized for touch screens
3. **Multiple panels:** Only one route panel is supported at a time
4. **Waypoint visual feedback:** Handles are invisible until hover (by design)

## Future Enhancements

- Save panel position to localStorage for cross-session persistence
- Add touch event support for mobile dragging
- Show semi-transparent waypoint handles by default when editing enabled
- Add keyboard shortcuts (Delete key to remove waypoints)
- Undo/redo for waypoint operations
- Snap waypoints to roads/cities option
