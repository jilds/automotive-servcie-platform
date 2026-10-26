// Apply the theme the visitor picked before the first paint (data-theme on <html>); without a choice the page
// follows prefers-color-scheme through CSS light-dark(). A file of its own, not an inline script, so the
// Content-Security-Policy can stay at script-src 'self' (same pattern as frontend/public/theme-init.js).
(function () {
  var mode = null;
  try {
    mode = localStorage.getItem('asp.site.theme');
  } catch (e) {}
  if (mode === 'light' || mode === 'dark') {
    document.documentElement.setAttribute('data-theme', mode);
  }
})();
