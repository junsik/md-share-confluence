/* Confluence ships an AMD loader, so mermaid's UMD wrapper would register as an
 * anonymous AMD module instead of window.mermaid. Hide define while it loads;
 * md-share-mermaid.js restores it. Resource order in atlassian-plugin.xml matters. */
(function () {
  window.__mdShareSavedDefine = window.define;
  try {
    window.define = undefined;
  } catch (e) {
    /* ignore */
  }
})();
