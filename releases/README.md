# Phase Checkpoint JARs

Each phase directory contains the exact verified JAR committed on that phase branch. The matching checklist and verification evidence live in `docs/verification/PHASE-N.md`.

`main` is the latest automated green checkpoint. Older phase branches and tags preserve earlier versions. An active higher phase branch is test code until its automated gate passes and `main` advances.

Every checkpoint records its SHA-256 digest in `checksums.txt`.
