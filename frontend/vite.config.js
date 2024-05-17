import viteCompression from 'vite-plugin-compression';

/** @type {import('vite').UserConfig} */
export default {
  base: '',
  plugins: [viteCompression({
    filter: /\.(js|mjs|json|css|html|pbf)$/i,
    threshold: 0,
    compressionOptions: {
      level: 9
    }
  })],
  build: {
    sourcemap: true,
    modulePreload: {
      polyfill: false
    }
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