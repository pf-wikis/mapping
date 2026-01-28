import express, { Request, Response } from "express";
import mbgl, { RenderOptions } from '@maplibre/maplibre-gl-native';
import sharp from 'sharp';
import fs from 'fs';
import { toMercator, toWgs84 } from "@turf/projection";



const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());


var rawStyle = fs.readFileSync('../../mapping/frontend/dist/style.json', 'utf-8');
const style = JSON.parse(rawStyle.replaceAll('https://map.pathfinderwiki.com', 'file://../../mapping/frontend/dist'));
const pmtiles = {data: fs.readFileSync('../../mapping/frontend/dist/golarion.pmtiles')};
const sprites = {data: fs.readFileSync('../../mapping/frontend/dist/sprites/sprites.json')};
const spritesPng = {data: fs.readFileSync('../../mapping/frontend/dist/sprites/sprites.png')};

const ratio = 2;

function superClamp(value:number|undefined|string, min:number, max:number):number {
    if(value === undefined) return min;
    if(typeof value === 'string') {
        value = parseInt(value);
    }
    return Math.min(Math.max(value, min), max);
}


app.get("/tile.png", (req: Request, res: Response) => {
    if(!req.query.size || !req.query.bbox)
        return res.status(400).send();
    const targetSize = superClam(req.query.size, 20, 1024);
    let bbox = (req.query.bbox as string).split(',').map(Number.parseFloat) as number[];
    let zoom = req.query.zoom as string?Number.parseFloat((req.query.zoom as string)):7;

    var map = new mbgl.Map({ratio: ratio}/*{
        request: function(req, callback) {
            if(req.url === 'pmtiles://https://map.pathfinderwiki.com/golarion.pmtiles') {
                console.log(pmtiles.data.slice(0, 100).toString('hex')); // Log first 100 bytes for debugging
                callback(undefined, pmtiles);
            }
            else if(req.url === 'https://map.pathfinderwiki.com/sprites/sprites.json') {
                callback(undefined, sprites);
            }
            else if(req.url === 'https://map.pathfinderwiki.com/sprites/sprites.png') {
                callback(undefined, spritesPng);
            }
            else {
                console.log(`Request URL ${req.url} not found`)
                callback(new Error(`Request URL ${req.url} not found`));
            }
        }
    }*/);
    map.load(style);

    //let cam:CenterZoomBearing;
    let center: [number, number] = [0, 0];
    let aspect = 1;
    let targetWidth = targetSize;
    let targetHeight = targetSize;
    if (
        bbox.length === 4 &&
        bbox[0] !== undefined &&
        bbox[1] !== undefined &&
        bbox[2] !== undefined &&
        bbox[3] !== undefined
    ) {
        let [left, bottom] = toMercator([bbox[0], bbox[1]]);
        let [right, top] = toMercator([bbox[2], bbox[3]]);
        let height = top! - bottom!;
        let width = right! - left!;
        aspect = width/height;

        if(aspect > 1) {
            targetHeight = targetSize;
            targetWidth = Math.round(targetSize * aspect);
        } else {
            targetWidth = targetSize;
            targetHeight = Math.round(targetSize / aspect);
        }
        console.log(`Target dimensions: ${targetWidth}x${targetHeight}`);
        console.log(`Aspect ratio: ${aspect}`);
        
        zoom = Math.log2(targetWidth/(width*0.000053)*ratio);
        console.log(`Zoom level: ${zoom}`);
        center = toWgs84([(left!+right!) / 2, (top! + bottom!) / 2]);
    }
    /*else if(bbox.length==2) {
        cam = {center:bbox as [number, number], zoom:zoom};
    }
    else {
        cam = {center:[0,0], zoom:zoom}
    }*/
    //map.jumpTo(cam);

    const config = {
        zoom: zoom,
        width: Math.round(targetWidth/ratio),
        height: Math.round(targetHeight/ratio),
        center: center,
    };

    map.render(config, function(err, buffer) {
        if (err) throw err;

        map.release();

        var image = sharp(buffer, {
            raw: {
                width: config.width*ratio,
                height: config.height*ratio,
                channels: 4
            }
        });

        image.toFormat('png').toBuffer()
            .then(data => {
                res.set('Content-Type', 'image/png');
                res.send(data);
            })
            .catch(err => {
                console.error("Error processing image:", err);
                res.status(500).send("Internal Server Error");
            });
    });
});

app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});