// vite.config.js

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
  }
}