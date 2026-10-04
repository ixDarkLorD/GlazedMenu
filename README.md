# Glazed Menu site

Source of the Glazed Menu site, https://ixdarklord.github.io/GlazedMenu/, built with
[Material for MkDocs](https://squidfunk.github.io/mkdocs-material/). The mod's code is on the version branches
(`main`, `26.2`, `26.3`, `1.21-1.21.1`, `1.20-1.20.1`).

Every push to this branch rebuilds and publishes the site (`.github/workflows/docs.yml`).

Preview locally:

```bash
pip install -r requirements.txt
mkdocs serve
```

Pages are Markdown files under `docs/`; the navigation is in `mkdocs.yml`.
