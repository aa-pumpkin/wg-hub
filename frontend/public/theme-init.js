// Runs before first paint so dark mode does not flash. External file because the CSP forbids inline scripts.
;(function () {
  var theme = localStorage.getItem('wghub.theme')
  var dark = theme === 'dark' || (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
})()
