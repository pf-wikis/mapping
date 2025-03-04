import { compression } from 'vite-plugin-compression2'

/** @type {import('vite').UserConfig} */
export default {
  base: '',
  plugins: [compression({
    include: /\.(js|mjs|json|css|html|pbf)$/i,
    threshold: 0,
    compressionOptions: {
      level: 9
    }
  })],
  build: {
    target: 'esnext',
    sourcemap: true,
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
    proxy: {
      //uncomment to use remote data
      //'/sprites': 'https://map.pathfinderwiki.com',
      //'/golarion.pmtiles': 'https://map.pathfinderwiki.com',
    }
  }
}