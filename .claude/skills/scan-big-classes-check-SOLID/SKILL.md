---
name: scan-big-classes-check-SOLID
description: Scans (src/main/java) for classes over 400 lines of code, lists them by size, then runs the check-SOLID-class review against each to flag Single-Responsibility and other SOLID violations. Use when asked to find oversized classes, audit the codebase for SOLID compliance, or find refactor candidates.
---

## Scope

Backend Java sources only: `src/main/java/**/*.java`. The generated Angular REST client (`src/main/webapp/src/app/rest/**`) and other TypeScript are out of scope for this skill.

## Step 1: measure every class

"Lines of code, not counting import" means: every line in the file except a line that is an `import ...;` statement. Blank lines, comments, the `package` line, javadoc and actual code all still count - only `import` lines are excluded, per the instruction. Run:

```
find src/main/java -name '*.java' -print0 | while IFS= read -r -d '' f; do
  loc=$(grep -vc '^import ' "$f")
  echo "$loc $f"
done | sort -rn
```

## Step 2: filter and present the list first

- Keep only files with `loc > 400`.
- Before doing any per-class analysis, show the user a table (LOC, file path, class name), largest first, so they can see the scope and redirect (e.g. "skip DTO files", "only look at services") before you spend effort auditing every one.
- Note which of the big files are plain data holders (`@Data`/DTO classes with fields and no real behavior - check the file, not just the name) versus classes with actual logic. Still list them (the scan is by LOC only), but flag that a pure data holder is unlikely to have a real SRP violation regardless of its line count, so effort is better spent on the classes with logic.

## Step 3: run check-SOLID-class per class

For each class over the threshold (skipping anything the user asked to exclude in Step 2), invoke the `check-SOLID-class` skill for that file, using the same rubric it defines: Single Responsibility is the dominant check, O/L/I/D are light-touch, and it must not recommend manufactured splits or flag size alone as a violation (see that skill for the full detail - don't re-derive the rubric here, delegate to it).

If there are many big classes (more than ~8), consider forking a subagent per class (or small batch of classes) to run the check-SOLID-class review in parallel and keep this conversation's context from filling up with every class's full source - each fork should report back just the verdict, the responsibilities found, and the top suggested extraction.

## Step 4: aggregate report

Produce one summary table, most actionable first:

| Class | LOC | Verdict | Responsibilities found | Top suggested extraction |
|---|---|---|---|---|

- Sort `NEEDS-REFACTOR` classes first, ordered by number of extra responsibilities (most first), then `PASS` classes.
- Keep each row terse (this is a summary of what `check-SOLID-class` already reported in detail per class, not a re-explanation).

This skill only reports; it does not apply any refactor unless the user asks, and even then, work through the classes one at a time (via `check-SOLID-class`'s own apply behavior) rather than mass-editing everything at once.
