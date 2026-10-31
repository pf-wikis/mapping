# Golarion Map Scenes — Foundry VTT module

A Foundry Virtual Tabletop module that turns this project's map into
ready-to-use scenes: 297 faithful renders covering the world map, continents,
the Inner Sea meta-regions, nations, city regions, full city maps, and 200+
individual towns. Every scene has a correctly scaled miles-based grid
(50-mile hexes on meta-regions, 10-mile hexes on nations and city regions,
gridless cities), and location pins that open gazetteer journal pages linking
to PathfinderWiki articles.

This answers [#299](https://github.com/pf-wikis/mapping/issues/299). All
scene images are rendered directly from this project's map data with its own
style; the module contains no AI-generated content.

## How it fits this repo's pipeline

The module is designed so that **images are build artifacts, not committed
files**. `scripts/bake-headless.mjs` renders every scene from the map build
output (`golarion.pmtiles`, fonts, sprites, `search.json`) using MapLibre in
headless Chromium:

```sh
npm install && npx playwright install chromium
npm run build                     # vite -> dist/module.js
npm run bake -- --data <map-data> # renders assets/scenes + assets/thumbs
node scripts/build-packs.mjs      # compiles compendium packs (LevelDB)
```

`<map-data>` is this project's build output or a mirror made with
`npm run mirror`. A release job can therefore regenerate the whole module
from the current map with no rendered images in git; `samples/` holds three
example renders for illustration only.

Scene *documents* (dimensions, note pins, journals, folders) live in
`packs-src/` and are stable across map re-renders; they only need
regenerating when the gazetteer changes. That path currently runs inside a
Foundry GM session via the module's `generateScenes` API (documented in the
source); porting it into the headless baker is a natural follow-up.

At runtime the module also ships an optional live map picker (disabled by
default) that lets a GM pan/zoom the actual vector map inside Foundry and
bake any viewport into a scene at chosen resolution, using a configurable
data endpoint so this project's servers never pay for Foundry traffic.

## Licensing

- Module code: MIT (see `LICENSE`).
- Map content: uses trademarks and/or copyrights owned by Paizo Inc., used
  under [Paizo's Community Use Policy](https://paizo.com/licenses/communityuse).
  We are expressly prohibited from charging you to use or access this
  content. Not published, endorsed, or specifically approved by Paizo.
- Everything renders from this repository's data and style; credit for the
  map itself belongs to this project and the GIS work it builds on.
