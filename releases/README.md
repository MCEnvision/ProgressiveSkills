# Phase Checkpoint JARs

Each phase directory contains the exact verified JAR committed on that phase branch. The matching checklist and verification evidence live in `docs/verification/PHASE-N.md`.

`main` is the latest user approved and tested checkpoint. Older approved phase branches and tags preserve earlier versions. Active higher phase branches remain beta code until the final mass test is approved.

Every checkpoint records its SHA-256 digest in `checksums.txt`.

The current cumulative beta checkpoint is stored in `phase-19/progressiveskills-phase-19.jar` on the `envy/phase-19` branch. It contains Phases 13 through 19 in one final player test build. Earlier phase directories preserve their matching checkpoint artifacts.
