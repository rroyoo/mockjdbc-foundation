# MockJDBC documentation site

This is the [Docusaurus](https://docusaurus.io/) site that powers the MockJDBC user documentation, published at https://rroyoo.github.io/mockjdbc-foundation/.

Engineering documentation (ADRs, module status, open-source launch roadmap) lives in [`../docs/`](../docs) instead and is not part of this site.

## Requirements

Node.js >= 20.

## Local development

```bash
npm install
npm start
```

This starts a local dev server with live reload.

## Build

```bash
npm run build
```

Generates static content into `build/`. CI runs this same command on every pull request touching `website/**` to validate the site builds before merge.

## Deployment

Deployment is automated: `.github/workflows/docs-deploy.yml` builds and publishes `build/` to GitHub Pages on every push to `main`. There is no manual deploy step.
