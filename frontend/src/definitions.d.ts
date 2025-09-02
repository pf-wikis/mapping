declare module 'pure-context-menu';
declare module 'virtual:style' {
    import { StyleSpecification } from 'maplibre-gl';
    const value: StyleSpecification;
    export default value;
}

interface ImportMetaEnv {
    readonly VITE_DATA_HASH: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
  