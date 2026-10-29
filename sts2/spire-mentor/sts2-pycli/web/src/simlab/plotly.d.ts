// plotly.js-dist-min ships no bundled types; reuse the @types/plotly.js
// definitions (the dist-min build exposes the same runtime API).
declare module "plotly.js-dist-min" {
  export * from "plotly.js";
  import Plotly from "plotly.js";
  export default Plotly;
}
