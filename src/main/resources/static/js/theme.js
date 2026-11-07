(() => {
  let preference = "system";
  try {
    preference = localStorage.getItem("taskflow-theme") || "system";
  } catch (_) {}
  const dark =
    preference === "dark" ||
    (preference === "system" &&
      matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
})();
