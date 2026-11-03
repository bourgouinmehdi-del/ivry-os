// IVRY OS — proxy PRIM (Cloudflare Worker)
// La clé PRIM reste ici, dans un secret Cloudflare, jamais dans l'APK.
// Secrets à définir : PRIM_API_KEY (obligatoire), APP_TOKEN (recommandé).

const ALLOWED = ["/stop-monitoring", "/general-message"];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "GET" || !ALLOWED.includes(url.pathname)) {
      return new Response("Not found", { status: 404 });
    }
    if (env.APP_TOKEN && request.headers.get("x-ivry-token") !== env.APP_TOKEN) {
      return new Response("Forbidden", { status: 403 });
    }
    const target = "https://prim.iledefrance-mobilites.fr/marketplace" + url.pathname + url.search;
    const upstream = await fetch(target, {
      headers: { apikey: env.PRIM_API_KEY, accept: "application/json" },
      cf: { cacheTtl: 20, cacheEverything: true },
    });
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "content-type": "application/json; charset=utf-8" },
    });
  },
};
