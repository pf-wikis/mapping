declare module 'pure-context-menu';
declare module 'mapbox-gl-draw-geodesic';

interface ImportMetaEnv {
    readonly VITE_MAX_ZOOM: string
    readonly VITE_DATA_PATH: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
  