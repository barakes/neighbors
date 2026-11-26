// "שכנים" – a building at night. Every lit window is someone here right now.

// ---------------------------------------------------------------------------------------------
// All the words people see, in one place.
// These are waiting for the mental-health professional's review (questions 1-10 in
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

    hereButton: "אני כאן",
    hereNote: "אין צורך לדבר עם אף אחד. רק להיות.",

    groupsQuestion: "רוצה לעשות משהו קטן ביחד עם השכנים? במה להתחיל?",
    justBeing: "גם רק להיות כאן נחשב.",
    groupTitle: (name) => `${name}: מה מתאים לך עכשיו?`,
    back: "חזרה",

    doing: (task) => `עכשיו: ${task}`,
    nobodyElseBusy: "השכנים כאן איתך, גם אם הם רק נמצאים.",
    oneElseBusy: "עוד שכן אחד עושה משהו קטן ברגע זה",
    manyElseBusy: (n) => `עוד ${n} שכנים עושים משהו קטן ברגע זה`,
    doneButton: "עשיתי",
    changeTask: "לבחור משהו אחר",

    doneTitle: "גם זה נחשב.",
    doneNote: "החלון שלך זוהר עכשיו אצל כל השכנים.",
    again: "עוד משהו קטן?",
};

// The draft list of small tasks (questions 5-8). Up to 6 in each group.
const GROUPS = [
    { name: "גוף", hint: "מים, מקלחת, מתיחה",
        tasks: ["לשתות כוס מים", "להתקלח", "להתמתח שתי דקות", "לאכול משהו קטן", "להחליף בגדים", "לנשום עמוק חמש פעמים"] },
    { name: "בית", hint: "חלון, מיטה, כלים",
        tasks: ["לפתוח תריס", "לפתוח חלון", "לסדר את המיטה", "לשטוף כמה כלים", "להוציא את הזבל", "להכין כוס תה"] },
    { name: "חוץ", hint: "מרפסת, פינה, מכולת",
        tasks: ["לצאת למרפסת", "לעמוד בכניסה לבניין", "לבדוק את תיבת הדואר", "ללכת עד הפינה", "לקנות דבר אחד במכולת", "לשבת על ספסל"] },
    { name: "קשר", hint: "הודעה, שיחה",
        tasks: ["לענות להודעה אחת", "לשלוח \"מה נשמע\" למישהו", "להתקשר לבן משפחה", "לכתוב לחבר ותיק", "לשלוח תמונה למישהו"] },
];

const WINDOWS = 30; // the same as SEATS on the server
const GLOW_MS = 2500;

const $ = (id) => document.getElementById(id);

// What the screen shows. step: start, groups, tasks, doing or done.
const state = {
    step: "start",
    group: null,
    task: null,
    here: 0,      // how many pressed "I'm here", the reader included once they pressed
    busy: 0,      // how many are doing a task right now, the reader included
    seats: [],    // the lit windows
    mySeat: -1,   // the reader's window, once the server gave one
};

let socket = null;

// --- Building the screen once -------------------------------------------------------------------

const building = $("building");
const windows = [];
for (let i = 0; i < WINDOWS; i++) {
    const w = document.createElement("div");
    w.className = "window";
    building.insertBefore(w, building.querySelector(".door"));
    windows.push(w);
}

$("here-button").textContent = TEXTS.hereButton;
$("here-note").textContent = TEXTS.hereNote;
$("groups-question").textContent = TEXTS.groupsQuestion;
$("just-being").textContent = TEXTS.justBeing;
$("tasks-back").textContent = TEXTS.back;
$("done-button").textContent = TEXTS.doneButton;
$("change-task").textContent = TEXTS.changeTask;
$("done-title").textContent = TEXTS.doneTitle;
$("done-note").textContent = TEXTS.doneNote;
$("again-button").textContent = TEXTS.again;

GROUPS.forEach((group) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "choice group";
    const name = document.createElement("span");
    name.className = "choice-name";
    name.textContent = group.name;
    const hint = document.createElement("span");
    hint.className = "choice-hint";
    hint.textContent = group.hint;
    button.append(name, hint);
    button.addEventListener("click", () => {
        state.group = group;
        state.step = "tasks";
        render();
    });
    $("groups").append(button);
});

// --- Showing the state ---------------------------------------------------------------------------

function render() {
    const inside = state.step !== "start";

    // The building
    windows.forEach((w) => w.classList.remove("lit", "mine"));
    state.seats.forEach((seat) => windows[seat] && windows[seat].classList.add("lit"));
    if (state.mySeat >= 0) {
        windows[state.mySeat].classList.add("mine");
    }
    building.classList.toggle("compact", inside);

    // The lines at the top
    $("greeting").textContent = inside ? TEXTS.greetingAfter : TEXTS.greetingBefore;
    if (inside) {
        const others = Math.max(state.here - 1, 0);
        $("count").textContent = others === 0 ? TEXTS.firstWindow
            : others === 1 ? TEXTS.oneWithYou
                : TEXTS.manyWithYou(others);
    } else {
        $("count").textContent = state.here === 0 ? TEXTS.nobodyYet
            : state.here === 1 ? TEXTS.oneHere
                : TEXTS.manyHere(state.here);
    }

    // One panel at a time
    $("start-panel").hidden = state.step !== "start";
    $("groups-panel").hidden = state.step !== "groups";
    $("tasks-panel").hidden = state.step !== "tasks";
    $("doing-panel").hidden = state.step !== "doing";
    $("done-panel").hidden = state.step !== "done";

    if (state.step === "tasks") {
        renderTasks();
    }
    if (state.step === "doing") {
        const othersBusy = Math.max(state.busy - 1, 0);
        $("doing-line").textContent = TEXTS.doing(state.task);
        $("others-line").textContent = othersBusy === 0 ? TEXTS.nobodyElseBusy
            : othersBusy === 1 ? TEXTS.oneElseBusy
                : TEXTS.manyElseBusy(othersBusy);
    }
}

function renderTasks() {
    $("group-title").textContent = TEXTS.groupTitle(state.group.name);
    const list = $("tasks");
    list.replaceChildren();
    state.group.tasks.forEach((task) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "choice";
        button.textContent = task;
        button.addEventListener("click", () => {
            state.task = task;
            state.step = "doing";
            send("start");
            render();
        });
        list.append(button);
    });
}

// Someone finished something small: their window glows for a moment.
function glow(seat) {
    const w = windows[seat];
    if (!w) {
        return;
    }
    w.classList.add("glow");
    setTimeout(() => w.classList.remove("glow"), GLOW_MS);
}

// --- Talking to the server -----------------------------------------------------------------------

function send(word) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(word);
    }
}

function connect() {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${protocol}://${location.host}/ws/presence`);

    socket.onopen = () => {
        // After a dropped connection, the reader is still here (and maybe still doing something).
        if (state.step !== "start") {
            send("here");
        }
        if (state.step === "doing") {
            send("start");
        }
    };

    socket.onmessage = (event) => {
        const message = JSON.parse(event.data);
        if (message.type === "presence") {
            state.here = message.here;
            state.busy = message.busy;
            state.seats = message.seats;
            render();
        } else if (message.type === "you") {
            state.mySeat = message.seat;
            render();
        } else if (message.type === "done") {
            glow(message.seat);
        }
    };

    // The phone slept or the network changed: try again quietly.
    // The window may change after reconnecting; the server gives a new one.
    socket.onclose = () => {
        state.mySeat = -1;
        $("count").textContent = TEXTS.reconnecting;
        setTimeout(connect, 3000);
    };
}

// --- Buttons -------------------------------------------------------------------------------------

$("here-button").addEventListener("click", () => {
    state.step = "groups";
    send("here");
    render();
});

$("tasks-back").addEventListener("click", () => {
    state.step = "groups";
    render();
});

$("done-button").addEventListener("click", () => {
    state.step = "done";
    send("done");
    render();
});

$("change-task").addEventListener("click", () => {
    state.step = "groups";
    render();
});

$("again-button").addEventListener("click", () => {
    state.step = "groups";
    render();
});

$("greeting").textContent = TEXTS.greetingBefore;
$("count").textContent = TEXTS.connecting;
connect();