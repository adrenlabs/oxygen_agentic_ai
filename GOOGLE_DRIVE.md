# GOOGLE_DRIVE.md

Drive is **external persistence**.

```
Local SQLite  ↔  Oxygen Sync Layer  ↔  Google Drive
```

## Folder layout

```
OXYGEN/
├── memory/
├── conversations/
├── documents/
├── embeddings/
├── backups/
├── settings/
└── metadata/
```

Each record: `schemaVersion deviceId recordId createdAt updatedAt revision checksum syncState`.

## Modes

`Local Only` · `Drive Backup` · `Drive Sync`

Nothing uploads until the user leaves Local Only and signs in. Offline mutations queue in `offline_queue`. Conflicts use revision then timestamp; ties keep both.

OAuth client id and tokens are user-provided and Keystore-stored. The app works fully if Drive is unavailable.
