/* md-share macro browser field: the built-in "attachment" parameter type never
 * populates its dropdown (long-standing Confluence bug), so we render our own
 * select filled with the current page's markdown attachments via REST. */
(function () {
  "use strict";

  function mdShareAttachmentField(param, options) {
    var paramDiv = AJS.$(
      '<div class="macro-param-div"><select class="macro-param-select"></select></div>'
    );
    var select = paramDiv.find("select");
    select.append(AJS.$("<option></option>").attr("value", "").text(""));

    function hasOption(value) {
      var found = false;
      select.find("option").each(function () {
        if (AJS.$(this).attr("value") === value) {
          found = true;
        }
      });
      return found;
    }

    function ensureOption(value) {
      if (value && !hasOption(value)) {
        select.append(AJS.$("<option></option>").attr("value", value).text(value));
      }
    }

    var pageId = AJS.Meta && AJS.Meta.get ? AJS.Meta.get("page-id") : null;
    if (pageId && String(pageId) !== "0") {
      AJS.$.getJSON(
        AJS.contextPath() + "/rest/api/content/" + pageId + "/child/attachment?limit=200"
      ).done(function (data) {
        var results = (data && data.results) || [];
        for (var i = 0; i < results.length; i++) {
          var name = results[i].title || "";
          if (/\.(md|markdown)$/i.test(name)) {
            ensureOption(name);
          }
        }
      });
    }

    return {
      paramDiv: paramDiv,
      getValue: function () {
        return select.val() || "";
      },
      setValue: function (value) {
        ensureOption(value);
        select.val(value);
      }
    };
  }

  AJS.toInit(function () {
    if (!AJS.MacroBrowser || !AJS.MacroBrowser.setMacroJsOverride) {
      return;
    }
    AJS.MacroBrowser.setMacroJsOverride("md-share", {
      fields: { string: { attachment: mdShareAttachmentField } }
    });
  });
})();
