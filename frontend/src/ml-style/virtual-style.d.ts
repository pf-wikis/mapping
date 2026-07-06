declare module "virtual:style" {
    const style: import('maplibre-gl').StyleSpecification & {
        state: import('./style').State;
    };
    export default style;
}
