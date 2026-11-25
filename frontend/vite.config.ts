import { defineConfig, type ResolvedConfig, type UserConfig } from 'vite';
import { compression, defineAlgorithm } from 'vite-plugin-compression2'
import sourcemaps from 'rolldown-plugin-sourcemaps'
import style from './src/ml-style/style.ts'
import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec'
import fs from 'node:fs'
import path from 'node:path'

const jsonModule = 'virtual:style';
const resolvedJsonModule = '\0'+jsonModule;

export default defineConfig(({ command, mode, isSsrBuild, isPreview }):UserConfig => {
  console.log('Mode: ', mode);

  let config:ResolvedConfig;
  // Development may run on any port (Vite increments if 5173 is busy). Use
  // origin-relative URLs so the assets are fetched from the server that served
  // the app instead of a hard-coded localhost port.
  const host = mode=='development'?'':'https://map.pathfinderwiki.com';
  const dataHash = mode=='development'?'latest':Math.floor(Date.now() / 1000);
  const compiledStyle = style(host, dataHash);
  
  let assetNames = {
    entryFileNames: `${dataHash}/assets/[name]-[hash].js`,
    chunkFileNames:  `${dataHash}/assets/[name]-[hash].js`,
    assetFileNames:  `${dataHash}/assets/[name]-[hash].[ext]`,
  };

  return {
    define: {
      HOST: JSON.stringify(host),
      BUILD_DATA_HASH: dataHash==='latest'?JSON.stringify(dataHash):dataHash
    },
    // The build copies `public/` into `dist/<dataHash>/` itself (see
    // `copy-public`), so Vite must not also copy it to the dist root. In dev
    // we do want Vite's static handler, because it knows how to serve range
    // requests for the PMTiles file.
    publicDir: command === 'serve' && !isPreview ? 'public' : false,
    optimizeDeps: {
      // Dev pre-bundles dependencies with Rolldown as well; without this the
      // dev maps still point at the minified dependency bundles.
      rolldownOptions: {
        plugins: [sourcemaps()]
      }
    },
    plugins: [
      {
        name: 'compile-style',
        resolveId(id) {
          if (id === jsonModule) {
            return resolvedJsonModule
          }
        },
        configResolved(resolved) {
          config = resolved;
        },
        load(id) {
          if (id === resolvedJsonModule) {
            for(let e of validateStyleMin(compiledStyle)) {
              console.error(`Style validation error: ${e.message} at line ${e.line}`);
            }
            return `export default ${JSON.stringify(compiledStyle)}`
          }
        },
        generateBundle(_, bundle) {
          // Emit generated file
          this.emitFile({
            type: "asset",
            fileName: `${dataHash}/style.json`,
            source: JSON.stringify(compiledStyle),
          });
        }
      },
      {
        // A production build copies `public/` into `dist/${dataHash}/`, so the
        // app always requests data as `/${dataHash}/…`. The dev server serves
        // `public/` at the root, so rewrite those requests to the matching
        // file. Paths configured in `server.proxy` are skipped so they still
        // reach the proxy middleware (which runs after plugin middlewares).
        name: 'serve-public-under-data-hash',
        apply: 'serve',
        configureServer(server) {
          const publicDir = server.config.publicDir;
          const prefix = `/${dataHash}/`;
          const proxied = Object.keys(server.config.server.proxy ?? {});
          server.middlewares.use((req, res, next) => {
            const url = req.url;
            if (!url || !url.startsWith(prefix)) return next();
            const pathname = url.split('?')[0];
            if (proxied.some(p => pathname.startsWith(p))) return next();
            let rel: string;
            try {
              rel = decodeURIComponent(pathname.slice(prefix.length));
            } catch {
              return next();
            }
            const file = path.resolve(publicDir, rel);
            if (!file.startsWith(publicDir + path.sep)) return next();
            try {
              if (!fs.statSync(file).isFile()) return next();
            } catch {
              return next();
            }
            req.url = `/${rel}${url.slice(pathname.length)}`;
            next();
          });
        }
      },
      {
        name: 'copy-public',
        generateBundle(_, bundle) {
          // Emit generated file
          this.emitFile({
            type: "asset",
            fileName: `latest.json`,
            source: JSON.stringify(dataHash),
          });
        },
        closeBundle() {
          fs.cpSync(
            path.resolve('public'),
            path.resolve(`dist/${dataHash}`),
            { recursive: true }
          )
        }
      },
      compression({
        include: /\.(?!pmtiles)[^.]*$/i,
        threshold: 0,
        algorithms: [
          defineAlgorithm('gzip', { level: 9 }),
          defineAlgorithm('brotliCompress'),
          defineAlgorithm('zstandard'),
        ]
      })
    ],
    build: {
      target: 'esnext',
      sourcemap: true,
      modulePreload: {
        polyfill: false
      },
      chunkSizeWarningLimit: 2048,
      rolldownOptions: {
        output: assetNames
      }
    },
    worker: {
      // The worker bundle is built separately; it needs its own instance or
      // maplibre's worker map still points at the minified bundle.
      plugins: () => [sourcemaps()],
      rolldownOptions: {
        output: assetNames
      }
    },
    resolve: {
      alias: {
        '~bootstrap': 'node_modules/bootstrap',
      }
    },
    server: {
      port: 5173,
      proxy: {
        // Use remote data from production map
        '/latest/sprites': 'https://map.pathfinderwiki.com',
        //'/latest/golarion.pmtiles': 'https://map.pathfinderwiki.com',
        '/latest/search.json': 'https://map.pathfinderwiki.com',
      }
    }
  };
})
