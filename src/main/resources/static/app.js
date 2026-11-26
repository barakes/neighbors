// "שכנים" – the first screen: a building at night. Every lit window is someone here right now.

// ---------------------------------------------------------------------------------------------
// All the words people see, in one place.
// These texts are waiting for the mental-health professional's review (questions 1, 4, 9 and 10 in
// "שכנים – מה אני מבקש לבדוק"). Change them here only; nothing else in the code holds text.
// ---------------------------------------------------------------------------------------------
const TEXTS = {
    greetingBefore: "טוב שבאת",
    greetingAfter: "החלון שלך דלוק",
    connecting: "מתחבר...",
    reconnecting: "מתחבר מחדש...",

    // Before pressing "I'm here": how many are here.
    nobodyYet: "עוד אין כאן אף אחד. החלון שלך יכול להיות הראשון.",
    oneHere: "עכשיו ער כאן שכן אחד",
    manyHere: (n) => `עכשיו ערים כאן ${n} שכנים`,

    // After pressing: how many others are here with you.
    firstWindow: "החלון שלך הראשון שדולק עכשיו",
    oneWithYou: "עוד שכן אחד ער איתך",
    manyWithYou: (n) => `עוד ${n} שכנים ערים איתך`,
};

const WINDOWS = 30;
const MY_WINDOW = 17;

// The order windows light up in, so the building looks lived-in rather than filled row by row.
const LIGHT_ORDER = [12, 3, 24, 8, 20, 1, 15, 27, 6, 22, 11, 29, 4, 18, 26, 9, 0, 14, 23, 5,
    28, 10, 19, 2, 25, 7, 16, 21, 13];

const building = document.getElementById("building");
const greeting = document.getElementById("greeting");
const countLine = document.getElementById("count");
const hereButton = document.getElementById("here-button");
const hereNote = document.getElementById("here-note");

let here = 0;        // how many pressed "I'm here", the reader included once they pressed
let iAmHere = false; // whether the reader pressed
let socket = null;

// Build the windows once; after that only their classes change.
const windows = [];
for (let i = 0; i < WINDOWS; i++) {
    const w = document.createElement("div");
    w.className = "window";
    building.insertBefore(w, building.querySelector(".door"));
    windows.push(w);
}

function render() {
    const others = iAmHere ? Math.max(here - 1, 0) : here;

    windows.forEach((w) => w.classList.remove("lit", "mine"));
    if (iAmHere) {
        windows[MY_WINDOW].classList.add("mine");
    }
    LIGHT_ORDER.slice(0, Math.min(others, LIGHT_ORDER.length))
        .forEach((index) => windows[index].classList.add("lit"));

    greeting.textContent = iAmHere ? TEXTS.greetingAfter : TEXTS.greetingBefore;
    if (iAmHere) {
        countLine.textContent = others === 0 ? TEXTS.firstWindow
            : others === 1 ? TEXTS.oneWithYou
                : TEXTS.manyWithYou(others);
    } else {
        countLine.textContent = here === 0 ? TEXTS.nobodyYet
            : here === 1 ? TEXTS.oneHere
                : TEXTS.manyHere(here);
    }
    hereButton.hidden = iAmHere;
    hereNote.hidden = iAmHere;
}

function connect() {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${protocol}://${location.host}/ws/presence`);

    socket.onopen = () => {
        // After a dropped connection, the reader is still here: say so again.
        if (iAmHere) {
            socket.send("here");
        }
    };

    socket.onmessage = (event) => {
        const message = JSON.parse(event.data);
        if (message.type === "presence") {
            here = message.here;
            render();
        }
    };

    // The phone slept or the network changed: try again quietly.
    socket.onclose = () => {
        countLine.textContent = TEXTS.reconnecting;
        setTimeout(connect, 3000);
    };
}

hereButton.addEventListener("click", () => {
    iAmHere = true;
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send("here");
    }
    render();
});

countLine.textContent = TEXTS.connecting;
connect();