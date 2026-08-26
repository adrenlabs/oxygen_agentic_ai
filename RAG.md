# RAG.md

RAG is mandatory and real.

## Pipeline

```
Document → Parser → Cleaner → Chunker → Metadata → Embedding → Vector store → Retriever → Filter → Context Engine
```

## Formats

PDF, TXT, Markdown, source, JSON, CSV, DOCX (OOXML), other text.

## Embeddings

`EmbeddingProvider` is replaceable.

Shipped default: `NgramHashEmbeddingProvider` (384-d signed feature hashing over words, bigrams, char 3-grams). This is a legitimate sparse-hash embedding, **not** the Qwen chat model, and works fully offline.

A GGUF embedding model can be plugged in later through the same interface.

## Vectors

Chunks + embeddings live in Room (`document_chunks`). Retrieval is hybrid cosine + BM25. Agent Core is not coupled to a particular ANN library.

## Citations

Hits keep `title`, `page`, `documentId`. The UI renders `OxygenSourceCard`. Citations are never fabricated by the retrieval layer.
