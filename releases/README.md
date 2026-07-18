# Phase Checkpoint JARs

Each phase directory contains the exact verified JAR committed on that phase branch. The matching checklist and verification evidence live in `docs/verification/PHASE-N.md`.

`main` is the latest user approved and tested checkpoint. Older approved phase branches and tags preserve earlier versions. Active higher phase branches remain beta code until they are approved.

Every checkpoint records its SHA-256 digest in `checksums.txt`.

The current approved release is stored in `phase-19/progressiveskills-phase-19.jar` on `main`, tagged as `phase-19`, with its historical branch preserved at `envy/phase-19`. It contains Phases 13 through 19 in one cumulative build. Earlier phase directories preserve their matching checkpoint artifacts.
