/* Render mermaid code fences produced by the md-share macro. Runs client-side:
 * the server cannot produce SVG, so PDF/Word export keeps the code block. */
(function () {
  "use strict";

  // restore the AMD loader hidden by md-share-amd-guard.js
  if (window.__mdShareSavedDefine !== undefined) {
    window.define = window.__mdShareSavedDefine;
  }
  try {
    delete window.__mdShareSavedDefine;
  } catch (e) {
    /* ignore */
  }

  AJS.toInit(function () {
    var blocks = AJS.$(".md-share-markdown pre > code.language-mermaid");
    if (!blocks.length || typeof window.mermaid === "undefined") {
      return;
    }
    window.mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: "neutral" });
    blocks.each(function (index) {
      var codeElement = AJS.$(this);
      var code = codeElement.text();
      var container = AJS.$('<div class="md-share-mermaid"></div>');
      codeElement.closest("pre").replaceWith(container);
      try {
        window.mermaid.render("md-share-mermaid-" + index, code, function (svg) {
          container.html(svg);
        });
      } catch (error) {
        // invalid diagram — fall back to the original code block
        container.replaceWith(AJS.$("<pre></pre>").text(code));
      }
    });
  });
})();
