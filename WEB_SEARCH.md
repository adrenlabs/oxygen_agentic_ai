# WEB_SEARCH.md

Web search is a `SearchProvider`.

Primary adapter: **SearXNG-compatible** `GET {base}/search?q=&format=json`.

Optional commercial APIs can be additional adapters. None are hardcoded into Agent Core.

## Result fields

`title url domain snippet source retrievedAt`

Results are deduped by URL, stripped of instruction-like roles, ranked into the Context Engine, and shown as citations. Entire pages are not injected.

Search is disabled in Local Only mode and when offline.
