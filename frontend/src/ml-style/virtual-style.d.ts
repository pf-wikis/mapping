declare module "virtual:style" {
    const style: import('maplibre-gl').StyleSpecification & {sources:{golarion:{url:string}}};
    export default style;
}
