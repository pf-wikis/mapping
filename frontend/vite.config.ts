import { PluginOption, UserConfig } from 'vite';
import { compression, defineAlgorithm } from 'vite-plugin-compression2'
import style from './src/style.ts';
import generateFile from 'vite-plugin-generate-file';

/*
const comp = async function() {
  
}*/

const jsonModule = 'virtual:style';
const resolvedJsonModule = '\0'+jsonModule;
const compileStyle:PluginOption = {
  name: 'compile-style',
  resolveId(id) {
    if (id === jsonModule) {
      return resolvedJsonModule
    }
  },
  load(id) {
    if (id === resolvedJsonModule) {
      return `export default ${JSON.stringify(style)}`
    }
  }
}

const config:UserConfig = {
  base: '',
  plugins: [
    compileStyle,
    generateFile([{
      output: './style.json',
      data: style
    }]),
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

export default config;