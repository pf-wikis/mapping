import { defineConfig, ResolvedConfig, UserConfig } from 'vite';
import { compression, defineAlgorithm } from 'vite-plugin-compression2'
import style from './src/ml-style/style.ts'
import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec'

const jsonModule = 'virtual:style';
const resolvedJsonModule = '\0'+jsonModule;

export default defineConfig(({ command, mode, isSsrBuild, isPreview }):UserConfig => {
  console.log('Mode: ', mode);

  let config:ResolvedConfig;
  const host = mode=='development'?'http://localhost:5173':'https://map.pathfinderwiki.com';
  const dataHash = Math.floor(Date.now() / 1000);
  
  return {
    define: {
      HOST: JSON.stringify(host),
      BUILD_DATA_HASH: dataHash
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
            let compiledStyle = style(
              host,
              dataHash
            );
            for(let e of validateStyleMin(compiledStyle)) {
              console.error(`Style validation error: ${e.message} at line ${e.line}`);
            }
            return `export default ${JSON.stringify(compiledStyle)}`
          }
        }
      },
      compression({
        include: /\..*$/i,
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
      sourcemap: mode=='development'?'inline':false,
      modulePreload: {
        polyfill: false
      },
      chunkSizeWarningLimit: 2048
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
        '/sprites': 'https://map.pathfinderwiki.com',
        //'/golarion.pmtiles': 'https://map.pathfinderwiki.com',
        '/search.json': 'https://map.pathfinderwiki.com',
      }
    }
  };
})
