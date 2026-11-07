const CACHE = "taskflow-static-v6";
const ASSETS = [
  "/offline.html",
  "/css/app.css",
  "/css/planner.css",
  "/css/focus.css",
  "/js/theme.js",
  "/js/app.js",
  "/js/focus.js",
  "/js/vendor/lucide.min.js",
  "/fonts/inter-latin.woff2",
  "/fonts/inter-cyrillic.woff2",
  "/icons/logo.svg",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  "/icons/icon-maskable-512.png",
  "/manifest.webmanifest",
];
self.addEventListener("install", (event) => {
  const requests = ASSETS.map((asset) => {
    const url = new URL(asset, self.location.origin);
    return new Request(url, { cache: "reload" });
  });

  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(requests))
      .then(() => self.skipWaiting()),
  );
});
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter(
              (key) => key.startsWith("taskflow-static-") && key !== CACHE,
            )
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});
self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  if (request.mode === "navigate") {
    // Authenticated HTML and CSRF tokens must never enter a shared cache.
    event.respondWith(
      fetch(request).catch(() => caches.match("/offline.html")),
    );
    return;
  }
  if (!ASSETS.includes(url.pathname) || url.search) return;
  event.respondWith(
    caches.open(CACHE).then(async (cache) => {
      const cached = await cache.match(request);
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok && response.type === "basic")
        await cache.put(request, response.clone());
      return response;
    }),
  );
});
