# md-share for Confluence

Confluence Server macro plugin that renders Markdown right on your pages — from the macro
body, a page attachment, or an [md-share](https://github.com/junsik/md-share) link.
Built for messengers-can't-render-markdown workflows: agents publish reports to md-share,
people paste the link into Confluence, and the page shows the rendered document.

Targets **Confluence Server 6.1.x** (Java 8 / P2 plugin). Newer 6.x/7.x will likely work
but are untested.

## The `{md-share}` macro

One macro, three sources (priority order):

| Source | How |
| --- | --- |
| Attachment | Pick a `.md` attachment on the page via the macro browser |
| URL | Paste an md-share link — `/d/{id}` share links are auto-normalised to the raw endpoint (`/api/documents/{id}/raw`); plain raw URLs work too |
| Body | Write Markdown directly in the macro body |

Rendering is server-side with [flexmark-java](https://github.com/vladsch/flexmark-java)
(GFM tables, task lists, strikethrough). Tables carry the native
`confluenceTable`/`confluenceTh`/`confluenceTd` classes, so they match Confluence styling
on pages **and in PDF/Word export**.

### Mermaid diagrams

Two modes:

- **Kroki (recommended)** — set a [Kroki](https://kroki.io) service URL and mermaid fences
  render server-side to PNG images served from the Confluence origin
  (`/plugins/servlet/md-share/mermaid/{encoded}.png`), so diagrams appear on pages **and in
  PDF/Word export**. Configure in `setenv.sh`/`setenv.bat`:

  ```
  -Dmdshare.confluence.kroki-url=https://kroki.example.com
  ```

  The image URL embeds the diagram source (Kroki's deflate+base64url encoding), so the
  servlet is stateless and responses are immutable/cacheable.
- **Client-side fallback** — without Kroki, a bundled mermaid 9.4.3 renders diagrams in the
  browser on page view (loaded only when a diagram is present). PDF/Word export keeps the
  code block in this mode.

### Security model

- **Raw HTML in Markdown is escaped** — a shared document can never inject markup into
  your Confluence page.
- **URL fetching is fail-closed** and honours the built-in Confluence whitelist:
  - **Primary**: Administration → Security → **Whitelist** — add your md-share domain
    (e.g. `https://md-share.example.com/*`). No restart, managed in the admin UI,
    shared with the other whitelist-aware macros (RSS, widgets).
  - **Fallback**: if an admin has *turned the whitelist off*, Confluence treats every
    outbound URL as allowed — this macro deliberately does **not** inherit that
    (it would become an SSRF proxy). Instead it falls back to an explicit prefix list
    via a system property in `setenv.sh`/`setenv.bat`:

    ```
    -Dmdshare.confluence.allowed-url-prefixes=https://md-share.example.com/
    ```

    With neither configured, the `url` parameter is disabled.
  - Redirects are not followed, so an allowed host cannot bounce the fetch elsewhere.
- Fetches have a 5s timeout and a 2 MB size cap; responses are cached for 5 minutes.

### Expired documents

md-share documents can carry a TTL. When a linked document has expired the macro renders
a friendly notice instead of the report. If the content must outlive the md-share
retention, attach the `.md` file to the page and use the attachment source instead.

## Build

Requires **JDK 8** (Confluence 6.1 era). With the
[Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/):

```bash
atlas-package                                   # target/md-share-confluence-<ver>.jar
atlas-run --product confluence --version 6.1.2  # local dev instance with the plugin
```

Or with plain Maven + Docker (no SDK install):

```bash
docker run --rm -v "$PWD":/src -w /src maven:3.8-openjdk-8 mvn package
```

## Install

Confluence Administration → **Manage add-ons** → **Upload add-on** → select the jar.
No restart required. Then add the `mdshare.confluence.allowed-url-prefixes` system
property (restart needed for that once) if you want URL rendering.

## Roadmap

- Syntax highlighting for code blocks
- Configurable cache TTL

## License

[MIT](LICENSE)
