// `/api/bugreport/<id>` — see ../_bugreport.ts.
import type { Env, PagesFunction } from "../../env";
import { handleGet } from "../_bugreport";

export const onRequestGet: PagesFunction<Env, "id"> = ({ params, env }) =>
  handleGet(String(params.id), env);
