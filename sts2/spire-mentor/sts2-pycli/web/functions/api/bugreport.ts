// `/api/bugreport` — see _bugreport.ts (Pages skips `_`-prefixed files as routes).
import type { Env, PagesFunction } from "../env";
import { handlePost, handleProbe } from "./_bugreport";

export const onRequestGet: PagesFunction<Env> = () => handleProbe();
export const onRequestPost: PagesFunction<Env> = ({ request, env }) => handlePost(request, env);
