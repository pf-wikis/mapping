/** @type {import('vite').UserConfig} */
export default {
  base: '',
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