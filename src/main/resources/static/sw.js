// The service worker: keeps a copy of the app on the phone.
// Most important: the help page opens even with no internet at all.
//
// Change VERSION whenever any of the files below change, so phones replace their old copy.
const VERSION = "small-steps-v1";

const FILES = [
    "/",
    "/index.html",
    "/style.css",
    "/app.js",
    "/help.html",
    "/help.css",
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
    if (request.method !== "GET" || new URL(request.url).origin !== location.origin) {
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