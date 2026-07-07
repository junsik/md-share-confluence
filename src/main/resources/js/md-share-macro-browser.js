/* md-share macro browser field: the built-in "attachment" parameter type never
 * populates its dropdown (long-standing Confluence bug), so we render our own
 * select filled with the current page's markdown attachments via REST. */
(function () {
  "use strict";

  function buildParamDiv() {
    try {
      var markup = Confluence.Templates.MacroBrowser.macroParameterSelect().toString();
      var div = AJS.$(markup);
      if (div.find("select").length) {
        return div;
      }
    } catch (e) {
      // template unavailable — fall through to manual markup
    }
    return AJS.$('<div class="macro-param-div"><select class="macro-param-select"></select></div>');
  }

  function mdShareAttachmentField(param, options) {
    var paramDiv = buildParamDiv();
    var select = paramDiv.find("select");
    select.empty();
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

    // Macro browser expects the AJS.MacroBrowser.Field contract (paramDiv + input,
    // it calls field.input.addClass(...)); build it the official way when possible.
    var field;
    if (AJS.MacroBrowser && typeof AJS.MacroBrowser.Field === "function") {
      field = AJS.MacroBrowser.Field(paramDiv, select, options);
    } else {
      field = {
        paramDiv: paramDiv,
        input: select,
        getValue: function () {
          return select.val() || "";
        },
        setValue: function (value) {
          select.val(value);
        }
      };
    }
    var baseSetValue = field.setValue;
    field.setValue = function (value) {
      ensureOption(value);
      if (baseSetValue) {
        baseSetValue.call(field, value);
      } else {
        select.val(value);
      }
    };
    return field;
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
