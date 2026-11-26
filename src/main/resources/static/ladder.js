// "הסולם שלי": each person's own ladder of small steps, from inside the room to going out.
// The account exists only to keep the ladder. Everything the person writes is encrypted on the server.

// ---------------------------------------------------------------------------------------------
// All the words people see, in one place. Waiting for the mental-health professional's review
// (questions 14 and 15). Change them here only.
// ---------------------------------------------------------------------------------------------
const TEXTS = {
    titleSignIn: "הסולם שלי",
    titleLadder: "הסולם שלי",
    whyAccount: "כדי לשמור את הסולם שלך צריך חשבון. רק שם משתמש וסיסמה, בלי מייל ובלי טלפון. כל מה שתכתוב בסולם נשמר מוצפן.",
    usernameLabel: "שם משתמש",
    passwordLabel: "סיסמה (לפחות 8 תווים)",
    signIn: "כניסה",
    signUp: "יצירת חשבון",
    toSignUp: "אין לי חשבון עדיין",
    toSignIn: "כבר יש לי חשבון",

    ladderIntro: "צעדים קטנים, מבפנים החוצה. עולים כשמרגישים מוכנים, ואפשר תמיד לחזור צעד אחורה.",
    stepDone: "עשיתי",
    stepReached: "✓ נעשה",
    stepNumber: (n) => `שלב ${n}`,
    countsToo: "גם זה נחשב.",
    edit: "לשנות את הסולם",
    finishEdit: "סיום",
    up: "למעלה",
    down: "למטה",
    remove: "מחיקה",
    newStepLabel: "צעד משלך",
    add: "הוספה",

    accountName: (name) => `החשבון: ${name}`,
    logout: "יציאה מהחשבון",
    deleteStart: "למחוק את החשבון שלי",
    deleteWarning: "זה ימחק מיד את החשבון ואת הסולם, לתמיד. אי אפשר לבטל.",
    deleteYes: "כן, למחוק",
    deleteNo: "ביטול",
    deleted: "החשבון נמחק.",

    // Errors
    badDetails: "שם משתמש: 3 עד 30 אותיות או ספרות, בלי רווחים. סיסמה: לפחות 8 תווים.",
    nameTaken: "השם הזה כבר תפוס. אפשר לבחור שם אחר.",
    wrongLogin: "שם המשתמש או הסיסמה לא נכונים.",
    tooMany: "היו יותר מדי ניסיונות. אפשר לנסות שוב בעוד רבע שעה.",
    badStep: "צעד הוא עד 80 תווים, ובסולם יש עד 20 צעדים.",
    noConnection: "אין חיבור לשרת. אפשר לנסות שוב בעוד רגע.",
    somethingWrong: "משהו השתבש. אפשר לנסות שוב.",
};

const TOKEN_KEY = "neighbors.token";

const $ = (id) => document.getElementById(id);

const state = {
    signedIn: false,
    username: "",
    authMode: "signIn", // or "signUp"
    editing: false,
    steps: [],          // bottom of the ladder first, as the server sends them
};

// --- The token: kept only on this phone. Storage can be blocked (private mode); then it lasts until the page closes.

let memoryToken = null;

function loadToken() {
    try {
        return localStorage.getItem(TOKEN_KEY) || memoryToken;
    } catch {
        return memoryToken;
    }
}

function saveToken(token) {
    memoryToken = token;
    try {
        if (token) {
            localStorage.setItem(TOKEN_KEY, token);
        } else {
            localStorage.removeItem(TOKEN_KEY);
        }
    } catch {
        // Blocked: the memory copy is enough.
    }
}

// --- Talking to the server --------------------------------------------------------------------

async function api(method, path, body) {
    const headers = {};
    const token = loadToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }
    if (body !== undefined) {
        headers["Content-Type"] = "application/json";
    }
    let response;
    try {
        response = await fetch(path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw { status: 0 };
    }
    if (!response.ok) {
        throw { status: response.status };
    }
    return response.status === 204 ? null : response.json();
}

function say(text) {
    $("message").textContent = text;
}

// --- Showing the state ------------------------------------------------------------------------

function render() {
    $("title").textContent = state.signedIn ? TEXTS.titleLadder : TEXTS.titleSignIn;
    $("auth-panel").hidden = state.signedIn;
    $("ladder-panel").hidden = !state.signedIn;

    if (!state.signedIn) {
        $("auth-submit").textContent = state.authMode === "signIn" ? TEXTS.signIn : TEXTS.signUp;
        $("auth-switch").textContent = state.authMode === "signIn" ? TEXTS.toSignUp : TEXTS.toSignIn;
        $("password").autocomplete = state.authMode === "signIn" ? "current-password" : "new-password";
        return;
    }

    $("account-name").textContent = TEXTS.accountName(state.username);
    $("edit-toggle").textContent = state.editing ? TEXTS.finishEdit : TEXTS.edit;
    $("add-form").hidden = !state.editing;
    renderSteps();
}

function renderSteps() {
    const list = $("steps");
    list.replaceChildren();
    // The top of the ladder is shown first, like a real ladder.
    const topFirst = [...state.steps].reverse();
    topFirst.forEach((step) => {
        const index = state.steps.indexOf(step); // 0 = the first step, at the bottom
        const item = document.createElement("li");
        item.className = step.reached ? "step reached" : "step";

        const number = document.createElement("span");
        number.className = "step-number";
        number.textContent = step.reached ? "✓" : String(index + 1);
        number.setAttribute("aria-label", TEXTS.stepNumber(index + 1));
        item.append(number);

        if (state.editing) {
            item.append(editControls(step, index));
        } else {
            // textContent, never innerHTML: whatever someone writes is shown as text, never run as code.
            const text = document.createElement("span");
            text.className = "step-text";
            text.textContent = step.text;
            item.append(text);
            if (step.reached) {
                const done = document.createElement("span");
                done.className = "step-done";
                done.textContent = TEXTS.stepReached;
                item.append(done);
            } else {
                item.append(button(TEXTS.stepDone, "small-button primary", () => markReached(step)));
            }
        }
        list.append(item);
    });
}

function editControls(step, index) {
    const box = document.createDocumentFragment();
    const input = document.createElement("input");
    input.className = "field";
    input.maxLength = 80;
    input.value = step.text;
    input.setAttribute("aria-label", TEXTS.stepNumber(index + 1));
    input.addEventListener("change", () => editStep(step, input.value));
    box.append(input);

    const up = button(TEXTS.up, "small-button", () => move(index, +1));
    up.disabled = index === state.steps.length - 1;
    const down = button(TEXTS.down, "small-button", () => move(index, -1));
    down.disabled = index === 0;
    box.append(up, down, button(TEXTS.remove, "small-button", () => removeStep(step)));
    return box;
}

function button(text, className, onClick) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = className;
    b.textContent = text;
    b.addEventListener("click", onClick);
    return b;
}

// --- Actions ----------------------------------------------------------------------------------

function showError(error) {
    const messages = { 0: TEXTS.noConnection, 400: TEXTS.badDetails, 409: TEXTS.nameTaken, 429: TEXTS.tooMany };
    if (error.status === 401 && state.signedIn) {
        // The token expired or the account is gone: back to signing in.
        signedOut();
        return;
    }
    say(error.status === 401 ? TEXTS.wrongLogin : (messages[error.status] || TEXTS.somethingWrong));
}

async function loadLadder() {
    state.steps = await api("GET", "/api/ladder");
    render();
}

async function submitAuth(event) {
    event.preventDefault();
    say("");
    const path = state.authMode === "signIn" ? "/api/account/login" : "/api/account/register";
    try {
        const signedIn = await api("POST", path, { username: $("username").value, password: $("password").value });
        saveToken(signedIn.token);
        state.signedIn = true;
        state.username = signedIn.username;
        $("password").value = "";
        await loadLadder();
    } catch (error) {
        showError(error);
    }
}

async function markReached(step) {
    try {
        const updated = await api("POST", `/api/ladder/${step.id}/reached`);
        step.reached = updated.reached;
        say(TEXTS.countsToo);
        render();
    } catch (error) {
        showError(error);
    }
}

async function editStep(step, text) {
    try {
        const updated = await api("PUT", `/api/ladder/${step.id}`, { text });
        step.text = updated.text;
        say("");
    } catch (error) {
        say(error.status === 400 ? TEXTS.badStep : "");
        if (error.status !== 400) {
            showError(error);
        }
    }
}

async function move(index, direction) {
    const ids = state.steps.map((s) => s.id);
    [ids[index], ids[index + direction]] = [ids[index + direction], ids[index]];
    try {
        state.steps = await api("PUT", "/api/ladder/order", { ids });
        render();
    } catch (error) {
        showError(error);
    }
}

async function removeStep(step) {
    try {
        await api("DELETE", `/api/ladder/${step.id}`);
        state.steps = state.steps.filter((s) => s !== step);
        render();
    } catch (error) {
        showError(error);
    }
}

async function addStep(event) {
    event.preventDefault();
    const input = $("new-step");
    try {
        const added = await api("POST", "/api/ladder", { text: input.value });
        state.steps.push(added);
        input.value = "";
        say("");
        render();
    } catch (error) {
        say(error.status === 400 ? TEXTS.badStep : "");
        if (error.status !== 400) {
            showError(error);
        }
    }
}

function signedOut() {
    saveToken(null);
    state.signedIn = false;
    state.username = "";
    state.steps = [];
    state.editing = false;
    $("delete-confirm").hidden = true;
    render();
}

async function logout() {
    try {
        await api("POST", "/api/account/logout");
    } catch {
        // Signed out on this phone anyway.
    }
    say("");
    signedOut();
}

async function deleteAccount() {
    try {
        await api("DELETE", "/api/account");
        signedOut();
        say(TEXTS.deleted);
    } catch (error) {
        showError(error);
    }
}

// --- Start ------------------------------------------------------------------------------------

$("why-account").textContent = TEXTS.whyAccount;
$("username-label").textContent = TEXTS.usernameLabel;
$("password-label").textContent = TEXTS.passwordLabel;
$("ladder-intro").textContent = TEXTS.ladderIntro;
$("new-step-label").textContent = TEXTS.newStepLabel;
$("add-button").textContent = TEXTS.add;
$("logout").textContent = TEXTS.logout;
$("delete-start").textContent = TEXTS.deleteStart;
$("delete-warning").textContent = TEXTS.deleteWarning;
$("delete-yes").textContent = TEXTS.deleteYes;
$("delete-no").textContent = TEXTS.deleteNo;

$("auth-form").addEventListener("submit", submitAuth);
$("auth-switch").addEventListener("click", () => {
    state.authMode = state.authMode === "signIn" ? "signUp" : "signIn";
    say("");
    render();
});
$("add-form").addEventListener("submit", addStep);
$("edit-toggle").addEventListener("click", () => {
    state.editing = !state.editing;
    render();
});
$("logout").addEventListener("click", logout);
$("delete-start").addEventListener("click", () => {
    $("delete-confirm").hidden = false;
});
$("delete-no").addEventListener("click", () => {
    $("delete-confirm").hidden = true;
});
$("delete-yes").addEventListener("click", deleteAccount);

async function start() {
    if (!loadToken()) {
        render();
        return;
    }
    try {
        const me = await api("GET", "/api/account/me");
        state.signedIn = true;
        state.username = me.username;
        await loadLadder();
    } catch (error) {
        if (error.status === 401) {
            saveToken(null);
        } else {
            showError(error);
        }
        render();
    }
}

start();
