import type { IControl, LineLayerSpecification, Map } from "maplibre-gl";
import style from "virtual:style";
import { colors } from "../../gen/props-meta-golarion.ts";

const PANEL_ID = 'map-legend-panel';

function borderColor(layerId: string): string {
  const layer = style.layers.find((candidate) => candidate.id === layerId) as LineLayerSpecification | undefined;
  const color = layer?.paint?.['line-color'];
  return typeof color === 'string' ? color : 'currentColor';
}

function lineSample(color: string, variant: 'solid' | 'dash-long' | 'dash-short', width: string): string {
  return `<span class="map-legend-line map-legend-line--${variant}" style="--legend-line-color:${color};--legend-line-width:${width}" aria-hidden="true"></span>`;
}

function swatchSample(background: string): string {
  return `<span class="map-legend-swatch" style="background:${background}" aria-hidden="true"></span>`;
}

function row(sample: string, label: string): string {
  return `<div class="map-legend-row">${sample}<span>${label}</span></div>`;
}

/**
 * Legend panel for the full (non-embedded) map. The panel starts hidden and
 * is toggled by a button in the top-right toolbar.
 */
export default class LegendControl implements IControl {
  private container?: HTMLElement;
  private panel?: HTMLElement;
  private button?: HTMLButtonElement;

  onAdd(map: Map): HTMLElement {
    const container = document.createElement('div');
    container.className = 'maplibregl-ctrl maplibregl-ctrl-group';

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'map-legend-toggle';
    button.title = 'Show legend';
    button.style.padding = '5px';
    button.innerHTML = `
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
        stroke-width="1.8" stroke-linecap="round" aria-hidden="true" focusable="false">
        <path d="M9 6h11M9 12h11M9 18h11"/>
        <path d="M4 6h.01M4 12h.01M4 18h.01" stroke-width="2.6"/>
      </svg>`;
    button.addEventListener('click', () => this.toggle());
    container.appendChild(button);

    const panel = document.createElement('aside');
    panel.id = PANEL_ID;
    panel.className = 'map-legend';
    panel.hidden = true;
    panel.tabIndex = -1;
    panel.setAttribute('aria-label', 'Map legend');
    panel.innerHTML = `
      <div class="map-legend-header">
        <h2 class="map-legend-title">Map legend</h2>
        <button type="button" class="map-legend-close" aria-label="Close legend">&times;</button>
      </div>
      <div class="map-legend-body">
        <section class="map-legend-section">
          <h3>Terrain</h3>
          ${row(swatchSample(colors.waters), 'Water')}
          ${row(swatchSample(colors.land), 'Land')}
          ${row(swatchSample(colors.forests), 'Forest')}
          ${row(swatchSample(colors.hills), 'Hills')}
          ${row(swatchSample(colors.mountains), 'Mountains')}
          ${row(swatchSample(colors.deserts), 'Desert')}
          ${row(swatchSample(colors.swamps), 'Swamp')}
          ${row(swatchSample(colors.ice), 'Ice')}
        </section>
        <section class="map-legend-section">
          <h3>Borders</h3>
          ${row(lineSample(borderColor('borders-regions'), 'solid', '2px'), 'Region')}
          ${row(lineSample(borderColor('borders-subregions'), 'solid', '1px'), 'Subregion')}
          ${row(lineSample(borderColor('borders-nations'), 'solid', '2px'), 'Nation')}
          ${row(lineSample(borderColor('borders-provinces'), 'dash-long', '2px'), 'Province')}
          ${row(lineSample(borderColor('borders-districts'), 'dash-short', '2px'), 'District')}
        </section>
      </div>`;

    panel.querySelector('.map-legend-close')?.addEventListener('click', () => this.hide());
    map.getContainer().appendChild(panel);

    this.container = container;
    this.panel = panel;
    this.button = button;
    return container;
  }

  onRemove(): void {
    window.removeEventListener('keydown', this.handleKeyDown);
    this.panel?.remove();
    this.container?.remove();
    this.panel = undefined;
    this.container = undefined;
    this.button = undefined;
  }

  private toggle(): void {
    if (this.panel?.hidden) {
      this.show();
    } else {
      this.hide();
    }
  }

  private show(): void {
    if (!this.panel || !this.button) return;
    this.panel.hidden = false;
    this.button.setAttribute('aria-expanded', 'true');
    this.button.title = 'Hide legend';
    this.button.setAttribute('aria-label', 'Hide legend');
    window.addEventListener('keydown', this.handleKeyDown);
    this.panel.focus({ preventScroll: true });
  }

  private hide(): void {
    if (!this.panel || !this.button) return;
    this.panel.hidden = true;
    this.button.setAttribute('aria-expanded', 'false');
    this.button.title = 'Show legend';
    this.button.setAttribute('aria-label', 'Show legend');
    window.removeEventListener('keydown', this.handleKeyDown);
    this.button.focus({ preventScroll: true });
  }

  private handleKeyDown = (event: KeyboardEvent): void => {
    if (event.key === 'Escape' && !this.panel?.hidden) {
      this.hide();
    }
  };
}
