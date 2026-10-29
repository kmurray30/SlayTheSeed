/**
 * The `#p=` codec on its own, importable by the workbench shell without
 * pulling the whole SimLab editor into its bundle.
 */
export function encodePositionHash(position: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(position));
  let bin = "";
  bytes.forEach((b) => {
    bin += String.fromCharCode(b);
  });
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
