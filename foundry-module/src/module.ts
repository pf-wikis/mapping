import "maplibre-gl/dist/maplibre-gl.css";
import "../styles/module.css";

import { bakeBase, bakeLabels, bakeRegion, generateScenes, makeThumb } from "./api";
import { buildPickerClass } from "./apps/picker";
import { buildSceneData } from "./scene-data";
import { registerSettings } from "./settings";

let PickerClass: any = null;
let pickerInstance: any = null;

Hooks.once("init", () => {
  registerSettings();
});

Hooks.once("ready", () => {
  // Built lazily so foundry.applications.api exists before we subclass it.
  PickerClass = buildPickerClass();
  // Programmatic API: used by the compendium generation tooling and available
  // to macros/other modules.
  const mod = game.modules.get("golarion-map-scenes");
  if (mod) mod.api = { bakeRegion, bakeBase, bakeLabels, generateScenes, buildSceneData, makeThumb };
});

// Note pins link to gazetteer journal pages (installed via the Adventure).
// When the journal isn't in the world — e.g. a scene imported individually —
// fall back to opening the pin's PathfinderWiki article instead.
Hooks.on("activateNote", (note: any) => {
  const doc = note?.document;
  const url = doc?.getFlag?.("golarion-map-scenes", "wikiUrl");
  if (!url || !/^https:\/\/pathfinderwiki\.com\//.test(url)) return true;
  if (doc.entryId && doc.entry) return true;
  window.open(url, "_blank", "noopener");
  return false;
});

Hooks.on("renderSceneDirectory", (_app: any, html: any) => {
  const el: HTMLElement = html instanceof HTMLElement ? html : html[0];
  if (!el || el.querySelector(".golarion-maps-open")) return;
  if (!game.user?.isGM) return;
  // Compendium-first: the live picker is a power feature, off by default.
  if (!game.settings.get("golarion-map-scenes", "enablePicker")) return;
  const footer = el.querySelector(".directory-footer") ?? el;
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "golarion-maps-open";
  btn.innerHTML = `<i class="fa-solid fa-earth-europe"></i> ${game.i18n.localize(
    "GOLARIONMAPS.OpenPicker"
  )}`;
  btn.addEventListener("click", () => {
    if (!PickerClass) PickerClass = buildPickerClass();
    pickerInstance ??= new PickerClass();
    pickerInstance.render({ force: true });
  });
  footer.append(btn);
});
