// Keep a copy of the app on the phone (see sw.js), so the help page opens even offline.
// A file of its own, not inline in the pages: the Content-Security-Policy runs only script files from this site.
if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/sw.js");
}
