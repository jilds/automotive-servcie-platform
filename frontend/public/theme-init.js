// Apply the saved theme before the first paint as Bootstrap's data-bs-theme (see shared/theme.service.ts).
// A file of its own, not an inline script, so the Content-Security-Policy can stay at script-src 'self'.
(function () {
  var mode = null;
  try {
    mode = localStorage.getItem('asp.theme');
  } catch (e) {}
  if (mode !== 'light' && mode !== 'dark') {
    mode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.setAttribute('data-bs-theme', mode);
})();
