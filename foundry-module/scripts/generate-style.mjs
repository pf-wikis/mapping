// Generates assets/style.json from pf-wikis/mapping's style source.
// The style is TypeScript code upstream (frontend/src/ml-style), evaluated at
// their build time by vite. We do the same: shallow-clone the repo, then run
// their pure style(host, hash) function via tsx with a __HOST__ placeholder
// the module substitutes at runtime.
import { execSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const cacheDir = join(root, ".cache");
const repoDir = join(cacheDir, "mapping");

if (!existsSync(repoDir)) {
  execSync(
    `git clone --depth 1 https://github.com/pf-wikis/mapping.git "${repoDir}"`,
    { stdio: "inherit" }
  );
} else {
  execSync(`git -C "${repoDir}" pull --ff-only`, { stdio: "inherit" });
}

execSync(`npx tsx "${join(root, "scripts", "dump-style.ts")}"`, {
  stdio: "inherit",
  cwd: root
});
console.log("Wrote assets/style.json");
