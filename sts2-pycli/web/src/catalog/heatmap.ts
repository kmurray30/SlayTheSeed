/**
 * Clustered heatmap for the card-synergy catalog.
 *
 * Renders a card×card matrix where cell (i,j) = metric value for the pair,
 * rows/columns ordered by hierarchical clustering (from catalog.card_order)
 * so synergistic cards land in contiguous diagonal blocks.
 *
 * Interaction:
 *   - Hover cell → tooltip with both cards' text + metric values
 *   - Hover row/col label → that card's text + top-5 partners
 *   - Click row/col label (or cell) → highlight that card's full row+column
 *   - N selector (40/60/100/all) → restrict to top-N by frequency
 *   - Metric toggle → PMI / W−L / Econ S / Combat S
 *   - Color legend in the top-left corner area
 */

import type { Catalog, CatalogNode, CatalogEdge, HeatmapMetric } from "./data";
import {
  buildEdgeLookup,
  pairKey,
  topNByFrequency,
  divergingColor,
  topPartners,
} from "./data";

export interface CardTextEntry {
  title: string;
  description: string;
}

// ── Layout constants ──────────────────────────────────────────────────────────
const LABEL_WIDTH  = 140;  // px reserved on the left for row labels
const LABEL_HEIGHT = 140;  // px reserved on top for rotated col labels
const LEGEND_WIDTH = 60;   // px for the color scale legend on the right
const MIN_CELL     = 6;    // px minimum cell size
const DEFAULT_N    = 60;   // default top-N cards shown

const METRIC_LABELS: Record<HeatmapMetric, string> = {
  pmi: "PMI",
  econ_S: "Econ S",
  combat_S: "Combat S",
};

// ── Helper ────────────────────────────────────────────────────────────────────

/** Escape HTML special characters so catalog strings can be safely embedded in innerHTML. */
function esc(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function resolvedLabel(id: string, cardText: Record<string, CardTextEntry>, nodeById: Map<string, CatalogNode>): string {
  return cardText[id]?.title ?? nodeById.get(id)?.label ?? id;
}

/** Compute symmetric domain for diverging scale from the visible cells. */
function computeDomain(
  cards: string[],
  edgeLookup: Map<string, CatalogEdge>,
  metric: HeatmapMetric,
): number {
  let maxAbs = 0;
  for (let i = 0; i < cards.length; i++) {
    for (let j = i + 1; j < cards.length; j++) {
      const edge = edgeLookup.get(pairKey(cards[i], cards[j]));
      if (!edge) continue;
      const v = edge[metric] as number | null;
      if (v !== null && Number.isFinite(v)) {
        maxAbs = Math.max(maxAbs, Math.abs(v));
      }
    }
  }
  return maxAbs > 0 ? maxAbs : 1;
}

// ── Main render function ──────────────────────────────────────────────────────

export function renderHeatmap(
  wrap: HTMLElement,
  catalog: Catalog,
  cardText: Record<string, CardTextEntry> = {},
): () => void {
  wrap.innerHTML = "";

  const nodeById = new Map(catalog.nodes.map((n) => [n.id, n]));
  const edgeLookup = buildEdgeLookup(catalog.edges);
  const cardOrder = catalog.card_order ?? [];

  // ── State ─────────────────────────────────────────────────────────────────
  let currentN: number = Math.min(DEFAULT_N, cardOrder.length);
  let currentMetric: HeatmapMetric = "pmi";
  let selectedCard: string | null = null;

  // ── Controls bar ──────────────────────────────────────────────────────────
  const controls = document.createElement("div");
  controls.style.cssText = `
    display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center;
    padding: 0.4rem 0.7rem; background: #1a1a30; border-bottom: 1px solid #333;
    font-size: 0.82rem; color: #bbb;
  `;

  // N selector
  const nLabel = document.createElement("span");
  nLabel.textContent = "Show:";
  controls.appendChild(nLabel);

  const nSel = document.createElement("select");
  nSel.style.cssText = "background:#252548; color:#ddd; border:1px solid #555; border-radius:3px; padding:2px 4px; font-size:0.82rem; cursor:pointer;";
  const nOptions = [40, 60, 100, Math.max(1, cardOrder.length)] as const;
  const nLabels  = ["Top 40", "Top 60", "Top 100", `All (${cardOrder.length})`];
  nOptions.forEach((v, i) => {
    const opt = document.createElement("option");
    opt.value = String(v);
    opt.textContent = nLabels[i];
    if (v === currentN || (i === nOptions.length - 1 && currentN >= cardOrder.length)) {
      opt.selected = true;
    }
    nSel.appendChild(opt);
  });
  controls.appendChild(nSel);

  // Metric selector
  const metricLabel = document.createElement("span");
  metricLabel.textContent = "Metric:";
  metricLabel.style.marginLeft = "0.6rem";
  controls.appendChild(metricLabel);

  const metricSel = document.createElement("select");
  metricSel.style.cssText = "background:#252548; color:#ddd; border:1px solid #555; border-radius:3px; padding:2px 4px; font-size:0.82rem; cursor:pointer;";
  (["pmi", "econ_S", "combat_S"] as HeatmapMetric[]).forEach((m) => {
    const opt = document.createElement("option");
    opt.value = m;
    opt.textContent = METRIC_LABELS[m];
    if (m === currentMetric) opt.selected = true;
    metricSel.appendChild(opt);
  });
  controls.appendChild(metricSel);

  // Hint text
  const hint = document.createElement("span");
  hint.style.cssText = "margin-left:auto; color:#666; font-size:0.75rem;";
  hint.textContent = "click a label or cell to pin row+col · hover for details";
  controls.appendChild(hint);

  wrap.appendChild(controls);

  // ── Canvas container ──────────────────────────────────────────────────────
  const canvasWrap = document.createElement("div");
  canvasWrap.style.cssText = "position: relative; overflow: auto; width: 100%;";
  wrap.appendChild(canvasWrap);

  // ── Tooltip ───────────────────────────────────────────────────────────────
  const tooltip = document.createElement("div");
  tooltip.style.cssText = `
    position: fixed; pointer-events: none; display: none; z-index: 9999;
    background: rgba(20,20,30,0.95); color: #eee; border: 1px solid #555;
    border-radius: 6px; padding: 8px 12px; font-size: 12px;
    max-width: 280px; line-height: 1.5;
  `;
  document.body.appendChild(tooltip);

  function showTooltip(html: string, x: number, y: number): void {
    tooltip.innerHTML = html;
    tooltip.style.display = "block";
    // Prevent overflow off right/bottom edge
    const tw = tooltip.offsetWidth || 280;
    const left = Math.min(x + 14, window.innerWidth - tw - 10);
    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${Math.max(4, y - 10)}px`;
  }
  function hideTooltip(): void { tooltip.style.display = "none"; }

  function cardHtml(id: string): string {
    const entry = cardText[id];
    const label = resolvedLabel(id, cardText, nodeById);
    let h = `<strong>${esc(label)}</strong>`;
    if (entry?.description) {
      h += `<br><span style="color:#aaa;font-size:11px">${esc(entry.description).replace(/\n/g, "<br>")}</span>`;
    }
    return h;
  }

  function cellTooltipHtml(a: string, b: string, edge: CatalogEdge | undefined): string {
    let h = cardHtml(a) + `<hr style="border:none;border-top:1px solid #333;margin:5px 0">` + cardHtml(b);
    if (edge) {
      h += `<hr style="border:none;border-top:1px solid #333;margin:5px 0">`;
      h += `<span style="color:#888;font-size:11px">`;
      h += `PMI: <b style="color:#e8c98a">${edge.pmi.toFixed(3)}</b>`;
      if (edge.econ_S !== null) h += ` · Econ S: <b style="color:#e8c98a">${edge.econ_S.toFixed(3)}</b>`;
      if (edge.combat_S !== null) h += ` · Combat S: <b style="color:#e8c98a">${edge.combat_S.toFixed(3)}</b>`;
      h += ` · <span style="color:#999">${esc(edge.quadrant)}</span>`;
      h += `</span>`;
    } else {
      h += `<br><span style="color:#666;font-size:11px">no data (below floor)</span>`;
    }
    return h;
  }

  function labelTooltipHtml(id: string): string {
    let h = cardHtml(id);
    const partners = topPartners(catalog, id, 5);
    if (partners.length > 0) {
      h += `<hr style="border:none;border-top:1px solid #333;margin:5px 0">`;
      h += `<span style="color:#888;font-size:11px">Top partners (PMI):</span><br>`;
      for (const p of partners) {
        const pl = resolvedLabel(p.partnerId, cardText, nodeById);
        h += `· ${esc(pl)} <span style="color:#6af">${p.pmi.toFixed(2)}</span><br>`;
      }
    }
    return h;
  }

  // ── Render the heatmap ────────────────────────────────────────────────────
  function render(): void {
    canvasWrap.innerHTML = "";

    if (cardOrder.length === 0) {
      const msg = document.createElement("p");
      msg.style.cssText = "padding:1rem; color:#888;";
      msg.textContent = "No card_order in catalog — regenerate with order-catalog.";
      canvasWrap.appendChild(msg);
      return;
    }

    const n = Math.min(currentN, cardOrder.length);
    const cards = topNByFrequency(cardOrder, nodeById, n);
    const nCards = cards.length;

    if (nCards === 0) {
      const msg = document.createElement("p");
      msg.style.cssText = "padding:1rem; color:#888;";
      msg.textContent = "No signal cards to show.";
      canvasWrap.appendChild(msg);
      return;
    }

    const domain = computeDomain(cards, edgeLookup, currentMetric);

    // Compute cell size to fit the available width
    const availableWidth = (canvasWrap.parentElement?.clientWidth ?? 900) - LABEL_WIDTH - LEGEND_WIDTH - 20;
    const cellSize = Math.max(MIN_CELL, Math.floor(availableWidth / nCards));
    const gridW = nCards * cellSize;
    const gridH = nCards * cellSize;
    const totalW = LABEL_WIDTH + gridW + LEGEND_WIDTH + 4;
    const totalH = LABEL_HEIGHT + gridH;

    // Use canvas for the cells, SVG/HTML for labels
    const container = document.createElement("div");
    container.style.cssText = `position: relative; width: ${totalW}px; min-height: ${totalH}px;`;
    canvasWrap.appendChild(container);

    // ── Canvas for cells ──────────────────────────────────────────────────
    const canvas = document.createElement("canvas");
    canvas.width = gridW;
    canvas.height = gridH;
    canvas.style.cssText = `
      position: absolute;
      left: ${LABEL_WIDTH}px;
      top: ${LABEL_HEIGHT}px;
      image-rendering: pixelated;
      cursor: crosshair;
    `;
    container.appendChild(canvas);

    const ctx = canvas.getContext("2d")!;

    // Draw all cells
    function drawCells(highlightId: string | null): void {
      ctx.clearRect(0, 0, gridW, gridH);
      for (let i = 0; i < nCards; i++) {
        for (let j = 0; j < nCards; j++) {
          const a = cards[i];
          const b = cards[j];
          if (i === j) {
            // Diagonal — neutral/dark
            ctx.fillStyle = "#1a1a2e";
            ctx.fillRect(j * cellSize, i * cellSize, cellSize, cellSize);
            continue;
          }
          const edge = edgeLookup.get(pairKey(a, b));
          const isHighlighted = highlightId !== null && (a === highlightId || b === highlightId);
          const isRowHighlighted = highlightId !== null && a === highlightId;
          const isColHighlighted = highlightId !== null && b === highlightId;

          let color: string;
          if (!edge) {
            // Below floor: very dark neutral
            color = highlightId !== null && !isHighlighted ? "#111120" : "#1e1e38";
          } else {
            const rawValue = edge[currentMetric] as number | null;
            if (rawValue === null) {
              color = highlightId !== null && !isHighlighted ? "#111120" : "#1e1e38";
            } else {
              color = divergingColor(rawValue, domain);
              if (highlightId !== null && !isHighlighted) {
                // Dim non-highlighted cells
                color = dimColor(color, 0.25);
              }
            }
          }

          ctx.fillStyle = color;
          ctx.fillRect(j * cellSize, i * cellSize, cellSize, cellSize);

          // Highlight border for pinned card's row+col
          if (isRowHighlighted || isColHighlighted) {
            ctx.strokeStyle = "rgba(255,220,100,0.8)";
            ctx.lineWidth = 1;
            ctx.strokeRect(j * cellSize + 0.5, i * cellSize + 0.5, cellSize - 1, cellSize - 1);
          }
        }
      }
      // Grid lines (only when cells are big enough)
      if (cellSize >= 8) {
        ctx.strokeStyle = "rgba(0,0,0,0.25)";
        ctx.lineWidth = 0.5;
        for (let i = 0; i <= nCards; i++) {
          ctx.beginPath();
          ctx.moveTo(i * cellSize, 0);
          ctx.lineTo(i * cellSize, gridH);
          ctx.stroke();
          ctx.beginPath();
          ctx.moveTo(0, i * cellSize);
          ctx.lineTo(gridW, i * cellSize);
          ctx.stroke();
        }
      }
    }

    drawCells(selectedCard);

    // Dim utility: parse an rgb() or hsl() string and return a dimmed version
    function dimColor(cssColor: string, alpha: number): string {
      // We only produce rgb(...) from divergingColor; known static strings are #1e1e38 / #1a1a2e
      if (cssColor.startsWith("rgb(")) {
        const m = cssColor.match(/rgb\((\d+),(\d+),(\d+)\)/);
        if (m) {
          return `rgba(${m[1]},${m[2]},${m[3]},${alpha})`;
        }
      }
      return cssColor;
    }

    // ── Row labels (left side) ────────────────────────────────────────────
    const rowLabelDiv = document.createElement("div");
    rowLabelDiv.style.cssText = `
      position: absolute; left: 0; top: ${LABEL_HEIGHT}px;
      width: ${LABEL_WIDTH}px; height: ${gridH}px;
    `;
    container.appendChild(rowLabelDiv);

    cards.forEach((id, i) => {
      const label = resolvedLabel(id, cardText, nodeById);
      const el = document.createElement("div");
      el.textContent = label;
      el.dataset.cardId = id;
      el.style.cssText = `
        position: absolute;
        top: ${i * cellSize}px;
        left: 0; right: 4px;
        height: ${cellSize}px;
        line-height: ${cellSize}px;
        font-size: ${Math.max(8, Math.min(13, cellSize - 1))}px;
        text-align: right;
        padding-right: 6px;
        color: #ccc;
        cursor: pointer;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        user-select: none;
        transition: color 0.1s;
      `;
      if (selectedCard === id) {
        el.style.color = "#e8c98a";
        el.style.fontWeight = "bold";
      }
      el.addEventListener("mouseenter", (e) => {
        if (selectedCard !== id) el.style.color = "#e8d88a";
        showTooltip(labelTooltipHtml(id), (e as MouseEvent).clientX, (e as MouseEvent).clientY);
      });
      el.addEventListener("mousemove", (e) => {
        showTooltip(labelTooltipHtml(id), (e as MouseEvent).clientX, (e as MouseEvent).clientY);
      });
      el.addEventListener("mouseleave", () => {
        hideTooltip();
        if (selectedCard !== id) el.style.color = "#ccc";
      });
      el.addEventListener("click", () => {
        selectedCard = selectedCard === id ? null : id;
        render();
      });
      rowLabelDiv.appendChild(el);
    });

    // ── Column labels (top, rotated) ───────────────────────────────────────
    const colLabelDiv = document.createElement("div");
    colLabelDiv.style.cssText = `
      position: absolute; left: ${LABEL_WIDTH}px; top: 0;
      width: ${gridW}px; height: ${LABEL_HEIGHT}px;
      overflow: hidden;
    `;
    container.appendChild(colLabelDiv);

    cards.forEach((id, j) => {
      const label = resolvedLabel(id, cardText, nodeById);
      const el = document.createElement("div");
      el.textContent = label;
      el.dataset.cardId = id;
      el.style.cssText = `
        position: absolute;
        left: ${j * cellSize + cellSize / 2}px;
        bottom: 4px;
        transform-origin: bottom left;
        transform: rotate(-55deg);
        font-size: ${Math.max(8, Math.min(13, cellSize - 1))}px;
        color: #ccc;
        cursor: pointer;
        white-space: nowrap;
        user-select: none;
        transition: color 0.1s;
      `;
      if (selectedCard === id) {
        el.style.color = "#e8c98a";
        el.style.fontWeight = "bold";
      }
      el.addEventListener("mouseenter", (e) => {
        if (selectedCard !== id) el.style.color = "#e8d88a";
        showTooltip(labelTooltipHtml(id), (e as MouseEvent).clientX, (e as MouseEvent).clientY);
      });
      el.addEventListener("mousemove", (e) => {
        showTooltip(labelTooltipHtml(id), (e as MouseEvent).clientX, (e as MouseEvent).clientY);
      });
      el.addEventListener("mouseleave", () => {
        hideTooltip();
        if (selectedCard !== id) el.style.color = "#ccc";
      });
      el.addEventListener("click", () => {
        selectedCard = selectedCard === id ? null : id;
        render();
      });
      colLabelDiv.appendChild(el);
    });

    // ── Canvas mouse events ────────────────────────────────────────────────
    canvas.addEventListener("mousemove", (e) => {
      const rect = canvas.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const j = Math.floor(mx / cellSize);
      const i = Math.floor(my / cellSize);
      if (i < 0 || i >= nCards || j < 0 || j >= nCards || i === j) {
        hideTooltip();
        return;
      }
      const a = cards[i];
      const b = cards[j];
      const edge = edgeLookup.get(pairKey(a, b));
      showTooltip(cellTooltipHtml(a, b, edge), e.clientX, e.clientY);
    });
    canvas.addEventListener("mouseleave", hideTooltip);
    canvas.addEventListener("click", (e) => {
      const rect = canvas.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;
      const j = Math.floor(mx / cellSize);
      const i = Math.floor(my / cellSize);
      if (i < 0 || i >= nCards || j < 0 || j >= nCards || i === j) return;
      const a = cards[i]; // row card
      // Toggle selection on the row card (clicking the cell highlights its row+col)
      selectedCard = selectedCard === a ? null : a;
      drawCells(selectedCard);
      // Update label colors by re-rendering label divs
      rowLabelDiv.querySelectorAll<HTMLDivElement>("[data-card-id]").forEach((el) => {
        const cid = el.dataset.cardId!;
        el.style.color = cid === selectedCard ? "#e8c98a" : "#ccc";
        el.style.fontWeight = cid === selectedCard ? "bold" : "normal";
      });
      colLabelDiv.querySelectorAll<HTMLDivElement>("[data-card-id]").forEach((el) => {
        const cid = el.dataset.cardId!;
        el.style.color = cid === selectedCard ? "#e8c98a" : "#ccc";
        el.style.fontWeight = cid === selectedCard ? "bold" : "normal";
      });
    });

    // ── Color legend ───────────────────────────────────────────────────────
    const legendDiv = document.createElement("div");
    legendDiv.style.cssText = `
      position: absolute;
      left: ${LABEL_WIDTH + gridW + 8}px;
      top: ${LABEL_HEIGHT}px;
      width: ${LEGEND_WIDTH - 8}px;
      height: ${Math.min(gridH, 220)}px;
    `;
    container.appendChild(legendDiv);

    const legendCanvas = document.createElement("canvas");
    const lh = Math.min(gridH, 220);
    legendCanvas.width = 16;
    legendCanvas.height = lh;
    legendCanvas.style.cssText = "width:16px; display:block; margin-bottom:2px;";
    const lctx = legendCanvas.getContext("2d")!;
    for (let py = 0; py < lh; py++) {
      const t = 1 - (py / (lh - 1)); // 1 at top (positive) → 0 at bottom (negative)
      const v = (t * 2 - 1) * domain; // [-domain, +domain]
      lctx.fillStyle = divergingColor(v, domain);
      lctx.fillRect(0, py, 16, 1);
    }
    legendDiv.appendChild(legendCanvas);

    const legendTop = document.createElement("div");
    legendTop.style.cssText = "font-size:10px; color:#888; margin-bottom:2px;";
    legendTop.textContent = `+${domain.toFixed(2)}`;

    const legendBot = document.createElement("div");
    legendBot.style.cssText = "font-size:10px; color:#888; margin-top:2px;";
    legendBot.textContent = `−${domain.toFixed(2)}`;

    const legendMid = document.createElement("div");
    legendMid.style.cssText = "font-size:10px; color:#888; position:absolute; top:" + (lh / 2 - 6) + "px; left:20px;";
    legendMid.textContent = "0";

    legendDiv.appendChild(legendTop);
    legendDiv.appendChild(legendCanvas);
    legendDiv.appendChild(legendBot);
    legendDiv.appendChild(legendMid);

    const legendTitle = document.createElement("div");
    legendTitle.style.cssText = "font-size:10px; color:#666; margin-top:6px; word-wrap:break-word;";
    legendTitle.textContent = METRIC_LABELS[currentMetric];
    legendDiv.appendChild(legendTitle);

    // ── "Not computed" notice ─────────────────────────────────────────────
    const allNull = catalog.edges.every((e) => (e[currentMetric] as number | null) === null);
    if (allNull && (currentMetric === "econ_S" || currentMetric === "combat_S")) {
      const notice = document.createElement("div");
      notice.style.cssText = "padding:0.4rem 0.7rem; font-size:0.78rem; color:#aa7; background:#1a1a20; border-top:1px solid #333;";
      notice.textContent = `${METRIC_LABELS[currentMetric]}: not computed in this catalog.`;
      wrap.appendChild(notice);
    }
  }

  // ── Wire controls ─────────────────────────────────────────────────────────
  nSel.addEventListener("change", () => {
    currentN = parseInt(nSel.value, 10);
    render();
  });
  metricSel.addEventListener("change", () => {
    currentMetric = metricSel.value as HeatmapMetric;
    render();
  });

  // Initial render
  render();

  // ── Cleanup ───────────────────────────────────────────────────────────────
  return () => {
    wrap.innerHTML = "";
    tooltip.remove();
  };
}
