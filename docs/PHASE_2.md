# Phase 2 backlog

Phase 1 deliberately supports only an uncompressed, non-QuDedup HBS backup
object whose content starts with the `Salted__` envelope. Each item below
requires representative fixtures and compatibility tests before it can be
declared supported.

- Compressed backup payloads, including the required decompression behavior.
- QuDedup `.qdff` containers.
- HBS Sync envelopes and QENC v1 and v2 formats.
- Broader fixtures across HBS and QTS versions, storage targets, and edge cases.
- Long-running background execution if real recovery workflows demonstrate that
  it is necessary.

Until then, the app rejects formats outside the verified phase-1 boundary rather
than attempting a best-effort recovery.
