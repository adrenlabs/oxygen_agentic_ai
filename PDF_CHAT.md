# PDF_CHAT.md

PDF chat is RAG, never “stuff the whole PDF into the prompt”.

```
Attach PDF → extract (PdfBox-Android) → chunk → embed → index → ask → retrieve → Context Engine → Qwen
```

## Features

- SAF picker from the composer
- page-level extraction and citations (`p.N`)
- encrypted PDF detection
- corrupt / empty extraction errors
- re-index by deleting and re-attaching
- single- and multi-document via `conversation.ragSources`

Images can be attached at the storage/UI layer. Local Qwen3-4B text inference is **not** claimed to understand pixels. Attachment types stay extensible for a future vision module.
