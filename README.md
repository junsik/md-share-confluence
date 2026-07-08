# md-share for Confluence

Confluence Server macro plugin that renders Markdown right on your pages — from the macro
body, a page attachment, or an [md-share](https://github.com/junsik/md-share) link.
Built for messengers-can't-render-markdown workflows: agents publish reports to md-share,
people paste the link into Confluence, and the page shows the rendered document.

Targets **Confluence Server 6.1.x** (Java 8 / P2 plugin). Newer 6.x/7.x will likely work
but are untested.

한국어 사용 가이드: **[docs/USAGE.ko.md](docs/USAGE.ko.md)**

![macro dialog with attachment picker and live preview](docs/images/macro-dialog.png)

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
  PDF/Word export**. Configure it at **`/plugins/servlet/md-share/admin`** (Confluence
  administrators only) — saved to Confluence settings and applied immediately, **no restart**.
  The `-Dmdshare.confluence.kroki-url=...` system property still works as a fallback.

  The image URL embeds the diagram source (Kroki's deflate+base64url encoding), so the
  servlet is stateless and responses are immutable/cacheable.
- **Client-side fallback** — without Kroki, a bundled mermaid 9.4.3 renders diagrams in the
  browser on page view (loaded only when a diagram is present). PDF/Word export keeps the
  code block in this mode.

With Kroki, the web view uses SVG (vector — crisp at any width) and PDF/Word export uses PNG
(old export renderers cannot draw SVG). Images are capped at the content width.

#### Authoring tips for well-sized diagrams

Diagram size is mostly decided when the markdown is written, not by the renderer:

- **Pick the direction for the shape of the flow** — `flowchart LR` fits linear pipelines to
  the page width; `TD` grows vertically with every step. Linear step chains read best as `LR`.
- **Short labels, data in tables** — node size follows label length. Put structure in the
  diagram and numbers in a table next to it, instead of `C["collect: 409 items"]`.
- **Two small diagrams beat one dense one** — split at a natural boundary (e.g.
  collect→verdict and verdict→follow-up) when branches pile up.
- **Fine-tune spacing per diagram** with an init directive:

  ```
  %%{init: {'flowchart': {'nodeSpacing': 30, 'rankSpacing': 30}}}%%
  ```

#### Copy-paste prompt for AI markdown generators

Most markdown that lands here is written by an AI (report agents, coding assistants).
Paste this block into your AI tool's instructions so the output renders well everywhere
(md-share web view, this Confluence macro, and PDF/Word export):

```text
You are writing a Markdown document that will be rendered as a web page and
exported to PDF. Follow these rules:

- Start with a single `#` heading; it becomes the document title. Include
  the subject and time range in it.
- Use GitHub-flavored Markdown only: tables, task lists, and fenced code
  blocks with a language tag. Never use raw HTML — it is stripped.
- Diagrams are mermaid fenced code blocks. Diagrams carry structure; numbers
  belong in tables next to them. Keep node labels short (no metrics inside
  node labels).
- Linear step flows must be `flowchart LR` (horizontal). Use `TD` only for
  shallow trees. If a diagram would exceed ~10 nodes or two branch levels,
  split it into two diagrams at a natural boundary instead.
- Always state timezones next to timestamps.
```

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

### TLS trust without touching the JVM

Old Confluence releases bundle a JRE whose `cacerts` predates the Let's Encrypt roots, so
fetching a Let's Encrypt-protected md-share or Kroki fails with `PKIX path building failed`
— and fixing the JVM truststore needs a restart. This plugin sidesteps that: its own HTTPS
fetches use the JVM's default trust **plus bundled ISRG Root X1/X2**, scoped to the plugin
only. No `keytool`, no restart.

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
