(() => {
  const panel = document.querySelector(".focus-panel[data-focus-user]");
  if (!panel) return;

  const duration = 25 * 60 * 1000;
  const key = `taskflow-focus:${panel.dataset.focusUser}`;
  const time = panel.querySelector("[data-focus-time]");
  const status = panel.querySelector("[data-focus-status]");
  const progress = panel.querySelector("[data-focus-progress]");
  const target = panel.querySelector("[data-focus-target]");
  const toggle = panel.querySelector("[data-focus-toggle]");
  const reset = panel.querySelector("[data-focus-reset]");
  const targetKind = target.tagName === "SELECT" ? "task" : "goal";

  let session = readSession();

  function readSession() {
    try {
      const saved = JSON.parse(localStorage.getItem(key));
      if (saved && Number.isFinite(saved.remaining)) {
        return {
          remaining: Math.min(duration, Math.max(0, saved.remaining)),
          endsAt: Number.isFinite(saved.endsAt) ? saved.endsAt : null,
          target: saved.targetKind === targetKind && typeof saved.target === "string"
            ? saved.target.slice(0, 160)
            : "",
        };
      }
    } catch {
      // The timer can run without storage.
    }
    return { remaining: duration, endsAt: null, target: "" };
  }

  function saveSession() {
    try {
      localStorage.setItem(key, JSON.stringify({ ...session, targetKind }));
    } catch {
      // The current page still has the session in memory.
    }
  }

  function remainingTime() {
    if (session.endsAt === null) return session.remaining;
    return Math.min(duration, Math.max(0, session.endsAt - Date.now()));
  }

  function formatTime(milliseconds) {
    const seconds = Math.ceil(milliseconds / 1000);
    const minutesPart = String(Math.floor(seconds / 60)).padStart(2, "0");
    const secondsPart = String(seconds % 60).padStart(2, "0");
    return `${minutesPart}:${secondsPart}`;
  }

  function render() {
    const remaining = remainingTime();
    const running = session.endsAt !== null && remaining > 0;
    const finished = remaining === 0;
    const paused = !running && !finished && remaining < duration;
    const message = running ? "Сессия идёт"
      : paused ? "На паузе"
      : finished ? "Сессия завершена"
      : "Готов к запуску";

    time.textContent = formatTime(remaining);
    progress.value = (duration - remaining) / 1000;
    if (status.textContent !== message) status.textContent = message;
    toggle.textContent = running ? "Пауза" : paused ? "Продолжить" : "Начать";
    target.disabled = running || paused;
    reset.disabled = !running && remaining === duration;
  }

  function restoreTarget() {
    target.value = session.target;
    if (targetKind === "task" && !target.value) target.value = "";
  }

  toggle.addEventListener("click", () => {
    const remaining = remainingTime();
    if (session.endsAt !== null && remaining > 0) {
      session.remaining = remaining;
      session.endsAt = null;
    } else {
      session.remaining = remaining || duration;
      session.endsAt = Date.now() + session.remaining;
      session.target = target.value.trim();
    }
    saveSession();
    render();
  });

  reset.addEventListener("click", () => {
    session.remaining = duration;
    session.endsAt = null;
    saveSession();
    render();
  });

  target.addEventListener("input", () => {
    session.target = target.value.trim();
    saveSession();
  });

  window.addEventListener("storage", (event) => {
    if (event.key !== key) return;
    session = readSession();
    restoreTarget();
    render();
  });

  restoreTarget();
  render();
  panel.hidden = false;
  window.setInterval(render, 1000);
  document.addEventListener("visibilitychange", render);
})();
