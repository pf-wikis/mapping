import { StyleSpecification } from 'maplibre-gl';

declare module 'maplibre-gl/dist/maplibre-gl.css';
declare module '*.scss';


declare const __BUILD_DATE__: string

interface ImportMetaEnv {
    readonly VITE_DATA_HASH: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}

declare module 'virtual:style' {
    const value: StyleSpecification;
    export default value;
}

