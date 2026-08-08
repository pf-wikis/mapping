// Copies the built module into the local Foundry data directory as a REAL
// directory. A junction is not usable: classic-level (compendium packs) fails
// its manifest renames through a Windows directory junction, so the pack DB
// silently never loads. This copy also mirrors the release zip layout exactly.
import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const target = join(
  process.env.LOCALAPPDATA ?? join(homedir(), "AppData", "Local"),
  "FoundryVTT",
  "Data",
  "modules",
  "golarion-map-scenes"
);

const SHIP = ["module.json", "LICENSE", "README.md", "dist", "assets", "lang", "templates", "packs"];

mkdirSync(target, { recursive: true });
for (const entry of SHIP) {
  const src = join(root, entry);
  if (!existsSync(src)) {
    console.warn(`skip missing: ${entry}`);
    continue;
  }
  rmSync(join(target, entry), { recursive: true, force: true });
  cpSync(src, join(target, entry), { recursive: true });
  console.log(`copied ${entry}`);
}

// The picker's local tile mirror (~209 MB) is optional and synced only when
// absent; delete the target's map-data manually to force a refresh.
const mirrorSrc = join(root, "map-data");
const mirrorDst = join(target, "map-data");
if (existsSync(mirrorSrc) && !existsSync(mirrorDst)) {
  cpSync(mirrorSrc, mirrorDst, { recursive: true });
  console.log("copied map-data (first deploy)");
}
console.log(`deployed to ${target}`);
