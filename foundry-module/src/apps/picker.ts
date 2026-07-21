import { Map as MLMap, NavigationControl, ScaleControl } from "maplibre-gl";
import { bakeViewport } from "../bake";
import { computeScale } from "../scale";
import { buildSceneData } from "../scene-data";
import { MODULE_ID } from "../settings";
import { loadStyle } from "../style";

const api = () => foundry.applications.api;

export function buildPickerClass(): any {
  const { ApplicationV2, HandlebarsApplicationMixin } = api();

  return class GolarionMapPicker extends HandlebarsApplicationMixin(ApplicationV2) {
    #map: any = null;

    static DEFAULT_OPTIONS = {
      id: "golarion-map-picker",
      classes: ["golarion-maps"],
      window: {
        title: "GOLARIONMAPS.PickerTitle",
        icon: "fa-solid fa-earth-europe",
        resizable: true
      },
      position: { width: 1000, height: 760 },
      actions: {
        create: async function (this: any) {
          await this.createScene();
        }
      }
    };

    static PARTS = {
      main: { template: `modules/${MODULE_ID}/templates/picker.hbs` }
    };

    async _prepareContext(): Promise<object> {
      return { sceneName: "Golarion" };
    }

    _onRender(): void {
      if (this.#map) return;
      const container = this.element.querySelector(".gm-map");
      if (!container) return;
      loadStyle()
        .then((style) => {
          this.#map = new MLMap({
            container,
            style,
            center: [0, 30],
            zoom: 2,
            attributionControl: false,
            canvasContextAttributes: { preserveDrawingBuffer: false }
          });
          (globalThis as any).__golarionMapsDebug = { app: this, map: this.#map };
          this.#map.on("error", (e: any) =>
            console.warn(`${MODULE_ID} | map error:`, e?.error?.message ?? e)
          );
          this.#map.dragRotate.disable();
          this.#map.touchZoomRotate.disableRotation();
          this.#map.addControl(new NavigationControl({ showCompass: false }));
          this.#map.addControl(new ScaleControl({ unit: "imperial" }));
          this.#map.on("moveend", () => this.#updateReadout());
          this.#map.on("load", () => this.#updateReadout());
          this.element
            .querySelectorAll("select[name=resFactor], input[name=gridSize], select[name=gridType]")
            .forEach((el: Element) =>
              el.addEventListener("change", () => this.#updateReadout())
            );
        })
        .catch((err: Error) => {
          console.error(`${MODULE_ID} |`, err);
          ui.notifications.error(err.message);
        });
    }

    _onClose(): void {
      try {
        this.#map?.remove();
      } catch {
        /* ignore */
      }
      this.#map = null;
    }

    #formValues() {
      const el = this.element;
      const num = (sel: string, fallback: number) => {
        const v = parseFloat((el.querySelector(sel) as HTMLInputElement)?.value);
        return Number.isFinite(v) ? v : fallback;
      };
      return {
        sceneName:
          (el.querySelector("input[name=sceneName]") as HTMLInputElement)?.value?.trim() ||
          "Golarion",
        resFactor: num("select[name=resFactor]", 2),
        gridType:
          (el.querySelector("select[name=gridType]") as HTMLSelectElement)?.value ?? "gridless",
        gridSize: Math.max(20, Math.round(num("input[name=gridSize]", 100)))
      };
    }

    #currentScale() {
      if (!this.#map) return null;
      const { resFactor, gridSize } = this.#formValues();
      const canvasEl = this.#map.getContainer();
      return computeScale({
        latDeg: this.#map.getCenter().lat,
        zoom: this.#map.getZoom(),
        cssWidth: canvasEl.clientWidth,
        cssHeight: canvasEl.clientHeight,
        resFactor,
        gridSizePx: gridSize
      });
    }

    #updateReadout(): void {
      const readout = this.element?.querySelector(".gm-readout");
      const scale = this.#currentScale();
      if (!readout || !scale) return;
      const { resFactor } = this.#formValues();
      const canvasEl = this.#map.getContainer();
      const w = Math.round(canvasEl.clientWidth * resFactor);
      const h = Math.round(canvasEl.clientHeight * resFactor);
      const fmt = (n: number) =>
        n >= 100 ? Math.round(n).toLocaleString() : n.toPrecision(3);
      readout.textContent = game.i18n.format("GOLARIONMAPS.Readout", {
        width: w,
        height: h,
        sceneMiles: `${fmt(scale.sceneWidthMiles)} × ${fmt(scale.sceneHeightMiles)}`,
        gridMiles: fmt(scale.gridDistanceMiles)
      });
    }

    async createScene(): Promise<void> {
      if (!this.#map) return;
      const { sceneName, resFactor, gridType, gridSize } = this.#formValues();
      const scale = this.#currentScale();
      const status = this.element.querySelector(".gm-status");
      const setStatus = (key: string) => {
        if (status) status.textContent = game.i18n.localize(key);
      };
      const btn = this.element.querySelector("button[data-action=create]") as HTMLButtonElement;
      if (btn) btn.disabled = true;

      try {
        setStatus("GOLARIONMAPS.Status.Rendering");
        const container = this.#map.getContainer();
        const style = await loadStyle();
        const blob = await bakeViewport({
          style,
          center: this.#map.getCenter(),
          zoom: this.#map.getZoom(),
          width: container.clientWidth,
          height: container.clientHeight,
          pixelRatio: resFactor
        });

        setStatus("GOLARIONMAPS.Status.Uploading");
        const FP = foundry.applications.apps.FilePicker.implementation;
        const dir = "golarion-maps";
        await FP.createDirectory("data", dir).catch(() => {});
        const slug = sceneName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
        const file = new File([blob], `${slug || "golarion"}-${Date.now()}.webp`, {
          type: "image/webp"
        });
        const uploaded = await FP.upload("data", dir, file, {}, { notify: false });
        if (!uploaded?.path) throw new Error("Upload failed");

        setStatus("GOLARIONMAPS.Status.CreatingScene");
        const width = Math.round(container.clientWidth * resFactor);
        const height = Math.round(container.clientHeight * resFactor);
        const gridDistanceMiles =
          gridType === "square"
            ? scale!.gridDistanceMiles
            : (100 * scale!.metersPerImagePixel) / 1609.344;
        const scene = await Scene.create(
          buildSceneData({
            name: sceneName,
            width,
            height,
            imagePath: uploaded.path,
            gridType: gridType as "gridless" | "square",
            gridSize,
            gridDistanceMiles
          })
        );

        setStatus("GOLARIONMAPS.Status.Done");
        ui.notifications.info(
          game.i18n.format("GOLARIONMAPS.SceneCreated", { name: scene.name })
        );
        scene.view();
      } catch (err: any) {
        console.error(`${MODULE_ID} |`, err);
        ui.notifications.error(`Golarion Maps: ${err.message}`);
        setStatus("GOLARIONMAPS.Status.Failed");
      } finally {
        if (btn) btn.disabled = false;
      }
    }
  };
}
