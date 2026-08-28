export interface SceneDataOptions {
  name: string;
  width: number;
  height: number;
  imagePath: string;
  gridType: "gridless" | "square";
  /** Grid square size in image pixels (square grids). */
  gridSize: number;
  /** Distance in miles covered by one grid square (or 100px when gridless). */
  gridDistanceMiles: number;
}

/**
 * Build a Scene document source compatible with the running Foundry version.
 * v14 removed Scene.background: the image now lives on an entry in the new
 * `levels` embedded collection. v13 keeps the flat background.src field.
 */
export function buildSceneData(opts: SceneDataOptions): Record<string, unknown> {
  const grid =
    opts.gridType === "square"
      ? {
          type: CONST.GRID_TYPES.SQUARE,
          size: opts.gridSize,
          distance: Number(opts.gridDistanceMiles.toPrecision(3)),
          units: "mi"
        }
      : {
          type: CONST.GRID_TYPES.GRIDLESS,
          size: 100,
          distance: Number(opts.gridDistanceMiles.toPrecision(3)),
          units: "mi"
        };

  const base: Record<string, unknown> = {
    name: opts.name,
    width: opts.width,
    height: opts.height,
    padding: 0,
    grid,
    tokenVision: false,
    fog: { exploration: false },
    navigation: true
  };

  if (game.release.generation >= 14) {
    base.levels = [
      {
        _id: foundry.utils.randomID(16),
        name: opts.name,
        index: 0,
        background: { src: opts.imagePath }
      }
    ];
  } else {
    base.background = { src: opts.imagePath };
  }
  return base;
}
