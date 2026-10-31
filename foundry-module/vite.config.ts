import { copyFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";

// maplibre-gl v6 spawns its worker via new URL("./maplibre-gl-worker.mjs",
// import.meta.url) at runtime, so the worker chunk (and the shared chunk it
// imports) must sit next to our bundle in dist/.
const MAPLIBRE_RUNTIME_FILES = [
  "maplibre-gl-worker.mjs",
  "maplibre-gl-worker.mjs.map",
  "maplibre-gl-shared.mjs",
  "maplibre-gl-shared.mjs.map"
];

export default defineConfig({
  plugins: [
    {
      name: "copy-maplibre-worker",
      closeBundle() {
        for (const f of MAPLIBRE_RUNTIME_FILES) {
          copyFileSync(
            resolve("node_modules/maplibre-gl/dist", f),
            resolve("dist", f)
          );
        }
      }
    }
  ],
  build: {
    lib: {
      entry: "src/module.ts",
      formats: ["es"],
      fileName: () => "module.js"
    },
    outDir: "dist",
    emptyOutDir: true,
    sourcemap: true,
    target: "esnext",
    cssCodeSplit: false,
    chunkSizeWarningLimit: 4096,
    rollupOptions: {
      output: {
        assetFileNames: (assetInfo) =>
          assetInfo.names?.some((n) => n.endsWith(".css"))
            ? "style.css"
            : "assets/[name][extname]"
      }
    }
  },
  worker: {
    format: "es"
  }
});
