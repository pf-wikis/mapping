// Minimal ambient declarations for the Foundry VTT globals this module touches.
// We intentionally do not depend on a community types package; the surface we
// use is tiny and stable across v13/v14.
declare const Hooks: any;
declare const game: any;
declare const foundry: any;
declare const Scene: any;
declare const ui: any;
declare const CONST: any;

declare module "maplibre-gl/dist/maplibre-gl.css" {}
