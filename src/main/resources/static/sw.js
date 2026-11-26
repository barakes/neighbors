// The service worker: keeps a copy of the app on the phone.
// Most important: the help page opens even with no internet at all.
//
// Change VERSION whenever any of the files below change, so phones replace their old copy.
const VERSION = "small-steps-v4";

const FILES = [
    "/",
    "/index.html",
    "/style.css",
    "/app.js",
    "/help.html",
    "/help.css",
    "/ladder.html",
    "/ladder.css",
    "/ladder.js",
    "/sw-register.js",
    "/manifest.json",
    "/icons/icon-192.png",
    "/icons/icon-512.png",
    "/icons/icon-180.png",
];

// First visit: save every file.
self.addEventListener("install", (event) => {
    event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(FILES)));
    self.skipWaiting();
});

// A new version took over: delete the copies the old one saved.
self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys().then((names) =>
            Promise.all(names.filter((name) => name !== VERSION).map((name) => caches.delete(name)))
        )
    );
    self.clients.claim();
});

// Every request: try the internet first, so people always see the newest version.
// No internet (or the server is asleep and doesn't answer): use the saved copy.
self.addEventListener("fetch", (event) => {
    const request = event.request;
    const url = new URL(request.url);
    // Only the app's own pages and files. Never the API: someone's ladder must not be kept
    // in a copy that another person on the same phone could open.
    if (request.method !== "GET" || url.origin !== location.origin || url.pathname.startsWith("/api/")) {
        return;
    }
    event.respondWith(
        fetch(request)
            .then((response) => {
                const copy = response.clone();
                caches.open(VERSION).then((cache) => cache.put(request, copy));
                return response;
            })
            .catch(() => caches.match(request, { ignoreSearch: true }))
    );
});
