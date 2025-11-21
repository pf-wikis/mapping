# How to Use Waypoint Editing

## Overview
The waypoint editing feature allows you to customize routes by adding, moving, and deleting waypoints directly on the map.

## Step-by-Step Guide

### 1. Calculate a Route
- Use the search panel to select two locations (Point A and Point B)
- Click "Calculate Route" to display the path on the map
- A route details panel will appear on the right side

### 2. Enable Waypoint Editing
- In the route details panel, look for the **"Edit Waypoints"** button
- It's located below the terrain profile, in a blue-tinted section
- Click the button to enable editing mode
- The button will turn green and show "Editing Waypoints"
- Instructions will appear below the button

### 3. Add Waypoints
- **Click directly on the route path** to add a new waypoint
- The waypoint is created at that location
- The route will automatically recalculate through the new waypoint

### 4. Move Waypoints
- **Hover your mouse near the route path** where you added a waypoint
- A circular white handle will appear
- **Click and drag the handle** to move the waypoint
- The route updates in real-time as you drag
- **Release the mouse button** to finalize the position
- The route automatically recalculates with terrain-aware pathfinding

### 5. Delete Waypoints
- Hover near a waypoint to reveal its handle
- **Right-click on the handle** to delete the waypoint
- The route automatically recalculates without that waypoint

### 6. Disable Waypoint Editing
- Click the green "Editing Waypoints" button again
- The button returns to white and shows "Edit Waypoints"
- Waypoint handles disappear
- Your waypoints remain in place

## Visual Guide

```
Step 1: Calculate Route
┌─────────────────────┐
│ Point A: Absalom    │
│ Point B: Magnimar   │
│ [Calculate Route]   │
└─────────────────────┘

Step 2: Enable Editing
┌─────────────────────┐
│ Terrain Profile     │
│ ▓▓▓░░░▓▓▓▓         │
│                     │
│ [Edit Waypoints]  ← Click here
└─────────────────────┘

Step 3: Add Waypoint
Map view:
  Point A ●───────● Point B
               ↑
            Click path

Result:
  Point A ●───●───● Point B
          waypoint

Step 4: Drag Waypoint
Hover near waypoint:
  ●───○───●  (handle appears)
       ↑

Click & drag:
  ●───┐
      │
      ○ ← dragging
      │
      └───●

Release:
  ●───┐
      └─○──● (route recalculates)
```

## Tips

- **Handles appear on hover**: Move your mouse close to waypoints to see them
- **Terrain-aware**: Routes recalculate using roads and avoiding water automatically
- **Multiple waypoints**: Add as many as you need to customize your route
- **No permanent changes**: Waypoints are temporary and not saved with routes (yet)
- **Works with all terrain**: Land, water, rivers - all supported

## Troubleshooting

**Can't see handles?**
- Make sure "Editing Waypoints" button is green (editing enabled)
- Hover closer to the waypoint location (within ~25 pixels)
- Try adding a new waypoint by clicking the path

**Route not recalculating?**
- Check browser console for errors (F12)
- Make sure you released the mouse button after dragging
- Try refreshing the page

**Waypoint not dragging?**
- Click and hold directly on or very near the handle
- Make sure you're in editing mode (green button)
- The handle must be visible before you can drag it

## Technical Notes

- Uses terrain-aware pathfinding (Pathfinder system)
- Routes calculated between: Start → Waypoint 1 → Waypoint 2 → ... → End
- Each segment respects travel method (walking, riding, etc.)
- Real-time position updates with final recalculation on mouse release
