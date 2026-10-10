// Mirrors the map data (pmtiles, sprites, fonts) into map-data/ for
// self-hosting: either served from inside a Foundry install (dev) or uploaded
// to object storage (release). Fonts come from the upstream clone
// (frontend/public/fonts is committed); pmtiles + sprites are downloaded from
// the live site.
import { execSync } from "node:child_process";
import { createWriteStream, existsSync, mkdirSync, statSync, cpSync } from "node:fs";
import { dirname, join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";

const HOST = "https://map.pathfinderwiki.com";
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const outDir = join(root, "map-data");
const repoDir = join(root, ".cache", "mapping");
mkdirSync(outDir, { recursive: true });

async function download(path, dest, { optional = false } = {}) {
  if (existsSync(dest) && statSync(dest).size > 0) {
    console.log(`skip (exists): ${path}`);
    return;
  }
  const res = await fetch(`${HOST}${path}`);
  if (!res.ok) {
    if (optional) {
      console.log(`skip (${res.status}): ${path}`);
      return;
    }
    throw new Error(`${path}: HTTP ${res.status}`);
  }
  mkdirSync(dirname(dest), { recursive: true });
  await pipeline(Readable.fromWeb(res.body), createWriteStream(dest));
  const mb = (statSync(dest).size / 1024 / 1024).toFixed(1);
  console.log(`downloaded: ${path} (${mb} MB)`);
}

// Fonts: copy from the upstream clone (created by generate-style.mjs).
if (!existsSync(repoDir)) {
  execSync(
    `git clone --depth 1 https://github.com/pf-wikis/mapping.git "${repoDir}"`,
    { stdio: "inherit" }
  );
}
cpSync(join(repoDir, "frontend", "public", "fonts"), join(outDir, "fonts"), {
  recursive: true
});
console.log("copied fonts from upstream clone");

for (const f of ["sprites.json", "sprites.png", "sprites@2x.json", "sprites@2x.png"]) {
  await download(`/sprites/${f}`, join(outDir, "sprites", f), { optional: true });
}

await download("/search.json", join(outDir, "search.json"), { optional: true });
await download("/golarion.pmtiles", join(outDir, "golarion.pmtiles"));
console.log("mirror complete:", outDir);
