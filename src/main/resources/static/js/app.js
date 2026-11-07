(() => {
  function setupTheme() {
    const storageKey = "taskflow-theme";
    const systemTheme = matchMedia("(prefers-color-scheme: dark)");
    const page = document.documentElement;
    const themeColor = document.querySelector('meta[name="theme-color"]');
    const choices = document.querySelectorAll('input[name="theme"]');
    const status = document.querySelector("#theme-status");

    function readTheme() {
      try {
        return localStorage.getItem(storageKey) || "system";
      } catch {
        return "system";
      }
    }

    function applyTheme(preference) {
      const useDarkTheme = preference === "dark"
        || (preference === "system" && systemTheme.matches);

      page.dataset.theme = useDarkTheme ? "dark" : "light";
      themeColor?.setAttribute("content", useDarkTheme ? "#151E19" : "#F6F4EE");

      for (const choice of choices) {
        choice.checked = choice.value === preference;
      }
    }

    function saveTheme(preference) {
      applyTheme(preference);
      try {
        localStorage.setItem(storageKey, preference);
        if (status) status.textContent = "Настройка сохранена";
      } catch {
        if (status) status.textContent = "Тема изменена для этой страницы";
      }
    }

    for (const button of document.querySelectorAll(".theme-toggle")) {
      button.addEventListener("click", () => {
        saveTheme(page.dataset.theme === "dark" ? "light" : "dark");
      });
    }

    for (const choice of choices) {
      choice.addEventListener("change", () => saveTheme(choice.value));
    }

    systemTheme.addEventListener("change", () => applyTheme(readTheme()));
    window.addEventListener("storage", (event) => {
      if (event.key === storageKey) applyTheme(readTheme());
    });
    applyTheme(readTheme());
  }

  function setupQuickTask() {
    const dialog = document.querySelector("#quick-task");
    if (!dialog || typeof dialog.showModal !== "function") return;

    const form = dialog.querySelector("form");
    const dueDate = form.elements.namedItem("dueDate");

    function openTask(date = "") {
      form.reset();
      dueDate.value = date;
      dialog.showModal();
    }

    for (const link of document.querySelectorAll(".open-task")) {
      link.addEventListener("click", (event) => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;

        event.preventDefault();
        openTask(link.dataset.date);
      });
    }

    for (const button of dialog.querySelectorAll(".close-dialog")) {
      button.addEventListener("click", () => dialog.close());
    }

    dialog.addEventListener("click", (event) => {
      if (event.target !== dialog) return;

      const { left, right, top, bottom } = dialog.getBoundingClientRect();
      const outsideDialog = event.clientX < left || event.clientX > right
        || event.clientY < top || event.clientY > bottom;
      if (outsideDialog) dialog.close();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key.toLowerCase() !== "n" || dialog.open) return;
      if (event.ctrlKey || event.metaKey || event.altKey) return;

      const focused = document.activeElement;
      if (focused?.matches("input, textarea, select") || focused?.isContentEditable) return;

      event.preventDefault();
      openTask();
    });
  }

  function setupReminders() {
    const button = document.querySelector(".notification-toggle");
    const panel = document.querySelector("#reminders");
    if (!button || !panel) return;

    function setOpen(isOpen) {
      panel.hidden = !isOpen;
      button.setAttribute("aria-expanded", String(isOpen));
    }

    button.addEventListener("click", () => setOpen(panel.hidden));
    document.addEventListener("click", (event) => {
      if (!event.target.closest(".notification-wrap")) setOpen(false);
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") setOpen(false);
    });
  }

  async function toggleTask(form, button, connectionStatus) {
    if (button.disabled) return;
    button.disabled = true;

    try {
      // The form already contains the CSRF token added by Spring Security.
      const response = await fetch(form.action, {
        method: "POST",
        body: new URLSearchParams(new FormData(form)),
        credentials: "same-origin",
        headers: { "X-Requested-With": "fetch" },
      });
      const sentToLogin = new URL(response.url).pathname === "/login";
      if (!response.ok || sentToLogin) throw new Error("Task update failed");

      // Keep the current filter and page instead of following the form redirect.
      location.reload();
    } catch {
      button.disabled = false;
      if (connectionStatus) {
        connectionStatus.textContent =
          "Не удалось сохранить. Проверьте соединение или войдите заново.";
        connectionStatus.hidden = false;
      }
    }
  }

  function setupForms() {
    const filters = document.querySelector("#task-filters");
    if (filters) {
      for (const choice of filters.querySelectorAll('select, input[type="radio"]')) {
        choice.addEventListener("change", () => filters.requestSubmit());
      }
    }

    const connectionStatus = document.querySelector("#connection-status");
    for (const form of document.querySelectorAll("form")) {
      form.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return;

        const confirmation = form.dataset.confirm;
        if (confirmation && !window.confirm(confirmation)) {
          event.preventDefault();
          return;
        }

        if (form.matches(".toggle-task")) {
          const button = form.querySelector("button");
          if (button) {
            event.preventDefault();
            toggleTask(form, button, connectionStatus);
          }
          return;
        }

        const button = event.submitter;
        if (button && !button.name && form.checkValidity()) {
          button.disabled = true;
          setTimeout(() => { button.disabled = false; }, 8000);
        }
      });
    }
  }

  function setupConnectionStatus() {
    const status = document.querySelector("#connection-status");
    if (!status) return;

    function updateConnection() {
      status.hidden = navigator.onLine;
    }

    window.addEventListener("online", updateConnection);
    window.addEventListener("offline", updateConnection);
    updateConnection();
  }

  function setupInstallation() {
    const buttons = document.querySelectorAll(".install-app");
    let installPrompt;

    function showInstallButtons(visible) {
      for (const button of buttons) {
        button.hidden = !visible;
      }
    }

    if (buttons.length) {
      window.addEventListener("beforeinstallprompt", (event) => {
        event.preventDefault();
        installPrompt = event;
        showInstallButtons(true);
      });

      for (const button of buttons) {
        button.addEventListener("click", async () => {
          if (!installPrompt) return;

          const prompt = installPrompt;
          installPrompt = undefined;
          showInstallButtons(false);
          try {
            await prompt.prompt();
          } catch (error) {
            console.warn("Не удалось открыть окно установки TaskFlow", error);
          }
        });
      }

      window.addEventListener("appinstalled", () => {
        installPrompt = undefined;
        showInstallButtons(false);
      });
    }

    if (window.isSecureContext && "serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js", { updateViaCache: "none" })
        .catch((error) => console.warn("Не удалось включить офлайн-экран TaskFlow", error));
    }
  }

  window.lucide?.createIcons();
  setupTheme();
  setupQuickTask();
  setupReminders();
  setupForms();
  setupConnectionStatus();
  setupInstallation();

  for (const button of document.querySelectorAll(".dismiss-flash")) {
    button.addEventListener("click", () => button.closest(".flash")?.remove());
  }

  for (const button of document.querySelectorAll(".show-password")) {
    const input = button.closest(".password-field")?.querySelector("input");
    if (!input) continue;

    button.addEventListener("click", () => {
      const showPassword = input.type === "password";
      input.type = showPassword ? "text" : "password";
      button.setAttribute("aria-label", showPassword ? "Скрыть пароль" : "Показать пароль");
      button.setAttribute("aria-pressed", String(showPassword));
    });
  }
})();
