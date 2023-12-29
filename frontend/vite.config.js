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
      '/sprites': 'https://map.pathfinderwiki.com',
      '/golarion.pmtiles': 'https://map.pathfinderwiki.com',
    }
  }
}