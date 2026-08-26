# MEMORY.md

Memory is local-first Room.

## Entity

`id content category importance confidence source createdAt updatedAt conversationId` plus optional embedding and tags.

Categories: `PERSONAL PREFERENCE PROJECT TASK FACT WORKFLOW REFERENCE`

Policies: `SAVE UPDATE MERGE IGNORE DELETE`

Every chat line is **not** stored. `MemoryRepository.decideAndApply` looks for durable signals (“remember that…”, preferences, identity) and either inserts or merges.

## Retrieval

Hybrid:

- cosine similarity on stored embeddings
- lexical BM25-ish
- recency and importance

Only the top-N above `retrievalMinScore` enter the Context Engine.

## UI

Memory screen lists, shows category, and deletes. Settings can disable memory entirely.
