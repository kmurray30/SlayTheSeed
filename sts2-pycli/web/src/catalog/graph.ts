import * as d3 from "d3";
import type { Catalog, CatalogNode } from "./data";
import { normToPixel, hueToColor, topPartners } from "./data";

export interface CardTextEntry {
  title: string;
  description: string;
}

// ── Layout constants ──────────────────────────────────────────────────────────
const PAD = 40; // SVG padding on each side

/** Map a node's normalized [0,1] x to SVG pixel x (with padding). */
function toSvgX(x: number, width: number): number {
  return normToPixel(x, PAD, width - PAD);
}
/** Map a node's normalized [0,1] y to SVG pixel y (with padding). */
function toSvgY(y: number, height: number): number {
  return normToPixel(y, PAD, height - PAD);
}

function nodeRadius(frequency: number): number {
  return Math.min(22, Math.max(4, 4 + frequency * 18));
}

// Top-N by frequency shown as always-visible labels at base zoom
const ALWAYS_LABEL_N = 40;
// Zoom threshold above which ALL labels are shown
const ZOOM_LABEL_THRESHOLD = 2.5;

/**
 * Render a UMAP scatter plot of catalog nodes.
 *
 * - Uses precomputed x/y from UMAP (no force simulation).
 * - Labels: always-on for top-40 by frequency; all labels at zoom ≥ 2.5×.
 * - Hover: shows rich tooltip (card title + description + top-5 partners).
 * - Click: draws faint partner lines to top-K; click elsewhere clears.
 * - Color: emergent hsl from node.hue; fallback grey for nodes without embedding.
 *
 * @param svg - The SVGSVGElement to render into (cleared first).
 * @param catalog - Full catalog (nodes with optional x/y/hue, edges for partners).
 * @param width - SVG client width.
 * @param height - SVG client height.
 * @param cardText - Optional resolved card text map.
 * @returns Cleanup function (removes SVG children; call before re-rendering).
 */
export function renderGraph(
  svg: SVGSVGElement,
  catalog: Catalog,
  width: number,
  height: number,
  cardText: Record<string, CardTextEntry> = {},
): () => void {
  const root = d3.select(svg);
  root.selectAll("*").remove();

  // Filter to nodes that have UMAP coords
  const embeddedNodes = catalog.nodes.filter(
    (n): n is CatalogNode & { x: number; y: number } =>
      typeof n.x === "number" && typeof n.y === "number",
  );
  const fallbackNodes = catalog.nodes.filter(
    (n) => typeof n.x !== "number" || typeof n.y !== "number",
  );

  if (embeddedNodes.length === 0) {
    root
      .append("text")
      .attr("x", width / 2)
      .attr("y", height / 2)
      .attr("text-anchor", "middle")
      .attr("fill", "#888")
      .text("No UMAP coordinates in catalog — re-run with embed-catalog.");
    return () => root.selectAll("*").remove();
  }

  // Sort to identify always-labeled nodes (top-N by frequency)
  const sortedByFreq = [...embeddedNodes].sort((a, b) => b.frequency - a.frequency);
  const alwaysLabelSet = new Set(sortedByFreq.slice(0, ALWAYS_LABEL_N).map((n) => n.id));

  // ── Zoom container ────────────────────────────────────────────────────────
  const container = root.append("g").attr("class", "scatter-root");

  // Partner-line group (below nodes)
  const lineG = container.append("g").attr("class", "partner-lines");
  // Node group
  const nodeG = container.append("g").attr("class", "nodes");
  // Label group (above nodes)
  const labelG = container.append("g").attr("class", "labels");

  // ── Tooltip ───────────────────────────────────────────────────────────────
  const tooltipSel = d3
    .select("body")
    .selectAll<HTMLDivElement, unknown>(".scatter-tooltip")
    .data([null]);
  const tooltip = tooltipSel
    .enter()
    .append("div")
    .attr("class", "scatter-tooltip")
    .merge(tooltipSel)
    .style("position", "fixed")
    .style("pointer-events", "none")
    .style("background", "rgba(20,20,30,0.93)")
    .style("color", "#eee")
    .style("border", "1px solid #555")
    .style("border-radius", "6px")
    .style("padding", "8px 12px")
    .style("font-size", "12px")
    .style("max-width", "260px")
    .style("line-height", "1.5")
    .style("z-index", "9999")
    .style("display", "none");

  function showTooltip(event: MouseEvent, node: CatalogNode): void {
    const entry = cardText[node.id];
    const partners = topPartners(catalog, node.id, 5);
    let html = `<strong>${entry?.title ?? node.label}</strong>`;
    if (entry?.description) {
      html += `<br><span style="color:#aaa">${entry.description.replace(/\n/g, "<br>")}</span>`;
    }
    if (partners.length > 0) {
      html += `<hr style="border:none;border-top:1px solid #444;margin:6px 0">`;
      html += `<span style="color:#888;font-size:11px">Top partners (PMI):</span><br>`;
      for (const p of partners) {
        const pLabel = cardText[p.partnerId]?.title ?? p.partnerLabel;
        html += `• ${pLabel} <span style="color:#6af">${p.pmi.toFixed(2)}</span><br>`;
      }
    }
    tooltip
      .html(html)
      .style("display", "block")
      .style("left", `${event.clientX + 14}px`)
      .style("top", `${event.clientY - 10}px`);
  }

  function hideTooltip(): void {
    tooltip.style("display", "none");
  }

  // ── Click state ───────────────────────────────────────────────────────────
  let selectedId: string | null = null;
  const nodeById = new Map(catalog.nodes.map((n) => [n.id, n]));

  function drawPartnerLines(cardId: string): void {
    lineG.selectAll("*").remove();
    const partners = topPartners(catalog, cardId, 10);
    const src = nodeById.get(cardId);
    if (!src || typeof src.x !== "number") return;
    const sx = toSvgX(src.x, width);
    const sy = toSvgY(src.y!, height);
    for (const p of partners) {
      const tgt = nodeById.get(p.partnerId);
      if (!tgt || typeof tgt.x !== "number") continue;
      lineG
        .append("line")
        .attr("x1", sx)
        .attr("y1", sy)
        .attr("x2", toSvgX(tgt.x, width))
        .attr("y2", toSvgY(tgt.y!, height))
        .attr("stroke", "#6af")
        .attr("stroke-width", 1)
        .attr("stroke-opacity", 0.4);
    }
  }

  function clearPartnerLines(): void {
    lineG.selectAll("*").remove();
    selectedId = null;
  }

  // Click on SVG background → clear
  root.on("click", (event: MouseEvent) => {
    if ((event.target as Element) === svg) {
      clearPartnerLines();
    }
  });

  // ── Render nodes ──────────────────────────────────────────────────────────
  const nodeSel = nodeG
    .selectAll<SVGCircleElement, CatalogNode>("circle")
    .data(embeddedNodes)
    .join("circle")
    .attr("cx", (d) => toSvgX(d.x, width))
    .attr("cy", (d) => toSvgY(d.y, height))
    .attr("r", (d) => nodeRadius(d.frequency))
    .attr("fill", (d) =>
      d.hue != null ? hueToColor(d.hue) : "#6688aa",
    )
    .attr("fill-opacity", 0.82)
    .attr("stroke", "#fff")
    .attr("stroke-width", 0.8)
    .attr("cursor", "pointer")
    .on("mouseenter", (event: MouseEvent, d) => {
      showTooltip(event, d);
      d3.select(event.currentTarget as SVGCircleElement)
        .attr("stroke", "#fff")
        .attr("stroke-width", 2.5)
        .attr("fill-opacity", 1);
    })
    .on("mousemove", (event: MouseEvent, d) => showTooltip(event, d))
    .on("mouseleave", () => {
      hideTooltip();
      nodeSel.attr("stroke-width", 0.8).attr("fill-opacity", 0.82);
    })
    .on("click", (event: MouseEvent, d) => {
      event.stopPropagation();
      if (selectedId === d.id) {
        clearPartnerLines();
      } else {
        selectedId = d.id;
        drawPartnerLines(d.id);
      }
    });

  // Fallback: note for nodes without coords
  if (fallbackNodes.length > 0) {
    root
      .append("text")
      .attr("x", PAD)
      .attr("y", height - 8)
      .attr("fill", "#666")
      .attr("font-size", "10px")
      .text(`${fallbackNodes.length} card(s) without UMAP coords omitted`);
  }

  // ── Render labels ─────────────────────────────────────────────────────────
  let currentZoomK = 1;

  function renderLabels(k: number): void {
    const showAll = k >= ZOOM_LABEL_THRESHOLD;
    labelG
      .selectAll<SVGTextElement, CatalogNode>("text")
      .data(embeddedNodes)
      .join("text")
      .attr("x", (d) => toSvgX(d.x, width))
      .attr("y", (d) => toSvgY(d.y, height) + nodeRadius(d.frequency) + 9)
      .attr("text-anchor", "middle")
      .attr("font-size", `${Math.max(7, Math.min(11, 8 / k))}px`)
      .attr("fill", "#ddd")
      .attr("pointer-events", "none")
      .style("display", (d) => (showAll || alwaysLabelSet.has(d.id) ? "block" : "none"))
      .text((d) => (cardText[d.id]?.title ?? d.label));
  }

  renderLabels(1);

  // ── D3 zoom ───────────────────────────────────────────────────────────────
  const zoom = d3
    .zoom<SVGSVGElement, unknown>()
    .scaleExtent([0.5, 12])
    .on("zoom", (event: d3.D3ZoomEvent<SVGSVGElement, unknown>) => {
      container.attr("transform", event.transform.toString());
      currentZoomK = event.transform.k;
      renderLabels(currentZoomK);
    });

  root.call(zoom);

  return () => {
    root.selectAll("*").remove();
    tooltip.style("display", "none");
    root.on(".zoom", null);
    root.on("click", null);
  };
}
