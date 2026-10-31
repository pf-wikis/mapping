export const MODULE_ID = "golarion-map-scenes";

// Default points at the PathfinderWiki-hosted map. That host serves no CORS
// headers, so browsers block it from a Foundry origin; users should point this
// at a CORS-enabled mirror (see README) or a copy inside their Foundry data.
export const DEFAULT_HOST = "https://map.pathfinderwiki.com";

/**
 * Settings-menu shim: the "button" in module settings that opens the
 * Adventure importer (scenes + gazetteer journals, ids preserved). Must be a
 * real ApplicationV2 subclass or v14 rejects the menu registration; its
 * render() just redirects to the adventure sheet and never shows a window.
 */
function buildImportMenuClass(): any {
  return class GolarionImportMenu extends foundry.applications.api.ApplicationV2 {
    async render(): Promise<any> {
      const pack = game.packs.get(`${MODULE_ID}.golarion-adventure`);
      const docs = pack ? await pack.getDocuments() : [];
      if (!docs.length) {
        ui.notifications.error("Golarion Maps: adventure pack not found.");
        return this;
      }
      // Chunked import: Adventure#import() sends the whole content set in a
      // few giant socket batches, which silently hangs at this size (92
      // scenes, ~5k notes, journal pages with article text). Small batches
      // with keepId preserve the same in-place-upgrade semantics.
      const src = docs[0].toObject();
      ui.notifications.info("Golarion Maps: importing… this takes a minute.");
      try {
        const folders = src.folders.filter((f: any) => !game.folders.has(f._id));
        if (folders.length) await Folder.createDocuments(folders, { keepId: true });
        const upSc: any[] = [];
        const newSc: any[] = [];
        for (const s of src.scenes) (game.scenes.has(s._id) ? upSc : newSc).push(s);
        for (let i = 0; i < newSc.length; i += 8)
          await Scene.createDocuments(newSc.slice(i, i + 8), { keepId: true });
        for (let i = 0; i < upSc.length; i += 8)
          await Scene.updateDocuments(upSc.slice(i, i + 8));
        const upJ: any[] = [];
        const newJ: any[] = [];
        for (const j of src.journal) (game.journal.has(j._id) ? upJ : newJ).push(j);
        for (let i = 0; i < newJ.length; i += 4)
          await JournalEntry.createDocuments(newJ.slice(i, i + 4), { keepId: true });
        for (let i = 0; i < upJ.length; i += 4)
          await JournalEntry.updateDocuments(upJ.slice(i, i + 4));
        ui.notifications.info(
          `Golarion Maps: imported ${src.scenes.length} scenes and ${src.journal.length} journals.`
        );
      } catch (e: any) {
        console.error(`${MODULE_ID} |`, e);
        ui.notifications.error(`Golarion Maps import failed: ${e.message}`);
      }
      return this;
    }
  };
}

export function registerSettings(): void {
  game.settings.registerMenu(MODULE_ID, "importAll", {
    name: "GOLARIONMAPS.Settings.ImportAll.Name",
    label: "GOLARIONMAPS.Settings.ImportAll.Label",
    hint: "GOLARIONMAPS.Settings.ImportAll.Hint",
    icon: "fa-solid fa-earth-europe",
    type: buildImportMenuClass(),
    restricted: true
  });
  game.settings.register(MODULE_ID, "enablePicker", {
    name: "GOLARIONMAPS.Settings.EnablePicker.Name",
    hint: "GOLARIONMAPS.Settings.EnablePicker.Hint",
    scope: "world",
    config: true,
    type: Boolean,
    default: false,
    requiresReload: true
  });
  game.settings.register(MODULE_ID, "tilesHost", {
    name: "GOLARIONMAPS.Settings.TilesHost.Name",
    hint: "GOLARIONMAPS.Settings.TilesHost.Hint",
    scope: "world",
    config: true,
    type: String,
    default: DEFAULT_HOST
  });
}

/**
 * Resolve the configured map-data endpoint to an absolute URL with no
 * trailing slash. A bare path (e.g. "modules/golarion-map-scenes/map-data")
 * is resolved against the Foundry origin so self-hosted copies work.
 */
export function tilesHost(): string {
  let h = String(game.settings.get(MODULE_ID, "tilesHost") ?? DEFAULT_HOST).trim();
  if (!h) h = DEFAULT_HOST;
  if (!/^https?:\/\//i.test(h)) {
    h = new URL(h.replace(/^\//, ""), window.location.origin).toString();
  }
  return h.replace(/\/+$/, "");
}
