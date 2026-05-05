# Plugin Performance Optimization Design

## Background

The IntelliJ plugin currently exhibits general UI stutter rather than a single isolated slow path. Based on the current implementation, the most likely causes are synchronous file IO, PSI lookup, full-list filtering, and event-driven refresh work being performed on or too close to the UI thread.

The user wants broad reduction of plugin stutter, accepts moderate internal refactoring, and accepts asynchronous data hydration as long as functionality and visual behavior remain broadly unchanged.

## Goals

- Reduce visible UI stutter across common plugin interactions.
- Keep existing plugin features, persisted data format, and overall UI structure unchanged.
- Prefer moving expensive work off the EDT rather than redesigning features.
- Avoid large architectural rewrites that would substantially increase regression risk.

## Non-Goals

- No redesign of plugin workflows or user-facing interaction model.
- No changes to persisted JSON schema.
- No full replacement of the event system or data layer.
- No speculative optimization of code paths not tied to observed stutter risks.

## Current Bottlenecks

### 1. Synchronous context loading

`DataContext.getClassDataContext()` may synchronously:

- read parameter data from disk
- resolve PSI classes
- build `ClassDataContext`

This work is used by UI entry points and file-switch handling, so it can block the EDT.

### 2. Immediate refresh on file selection changes

`ParamListUI` reacts to editor selection changes by immediately reading and rebuilding table state. Rapid file switching can queue repeated heavy refreshes for transient selections.

### 3. Search performs full filtering on every keystroke

Search currently filters the full in-memory index on each document change and performs repeated lowercase conversion during matching. This increases input latency as cached history grows.

### 4. Event fan-out is synchronous

`DefaultMulticaster.fireEvent()` dispatches listeners synchronously. If listeners perform data sync or UI refresh work inline, the source thread inherits that latency.

### 5. Mixed preparation and apply phases

Several flows combine data loading, transformation, and UI application in one call path. Even where final UI updates must run on EDT, the preparation phase does not.

## Recommended Approach

Use moderate refactoring to separate background preparation from EDT application, add cancellation-aware refresh scheduling, and avoid repeated work during rapid interactions.

## Design

### A. Asynchronous class context loading

Introduce an async loading path for class data used by the parameter list and method dialog flows.

Behavior:

- UI requests a class context load without blocking.
- Data file reads and PSI/class resolution run in a background task.
- The result is applied on EDT only if it is still current.
- If a newer request supersedes an older one, the older result is dropped.

Expected outcome:

- opening plugin UI no longer stalls while waiting for data and PSI work
- switching files avoids immediate blocking refreshes

### B. Refresh coalescing for file-switch driven updates

Add a lightweight refresh coordinator in `ParamListUI` or a nearby helper.

Behavior:

- Track the last requested class/file identity.
- Skip refresh when the selected class has not materially changed.
- Use a short debounce/coalescing window for rapid file selection changes.
- Cancel or ignore stale background refresh results.

Expected outcome:

- less repeated table rebuild work during quick editor navigation
- reduced jitter from bursty selection changes

### C. Search debounce and normalized index fields

Optimize the search popup without changing user-visible semantics.

Behavior:

- debounce search input by a short interval
- normalize searchable text once per index item instead of per comparison
- perform filtering off the EDT when result sets are non-trivial
- cap displayed results to a practical upper bound

Expected outcome:

- smoother typing in search
- lower CPU churn when history grows

### D. Async follow-up for event-triggered refresh work

Keep the existing multicaster contract but change expensive listeners to schedule refresh work rather than executing it inline.

Behavior:

- data change events remain logically synchronous from the caller perspective
- listeners that need re-read/rebuild operations enqueue background refreshes
- final UI mutation remains on EDT

Expected outcome:

- less blocking propagation from save/delete/update event chains
- lower probability that one slow listener stalls unrelated UI actions

### E. Explicit prepare/apply split in UI flows

For major UI refresh paths, separate:

- prepare: file IO, data cloning, filtering, PSI lookup, transformation
- apply: table model replacement, component state sync, repaint-sensitive actions

Only the apply step runs on EDT.

Expected outcome:

- better responsiveness even when full refresh still occurs
- clearer boundaries for future profiling and optimization

## Proposed Implementation Scope

Primary code areas expected to change:

- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/ParamListUI.java`
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/DataContext.java`
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/MainUI.java`
- `any-door-plugin/src/main/java/io/github/lgp547/anydoorplugin/dialog/event/DefaultMulticaster.java`
- supporting helpers near dialog/data packages as needed

Possible small supporting changes:

- add a reusable background task helper for stale-result suppression
- add normalized search fields or cached derived values for `ParamIndexData` handling

## Concurrency and Correctness Rules

- Never mutate Swing components off EDT.
- Background results must verify they still correspond to the latest requested file/class/query before applying.
- Existing persisted data must remain source-of-truth compatible.
- Async loading may temporarily show empty or old UI state, but must converge quickly to current data.
- Save behavior remains async as today; this work focuses on removing read/refresh stalls.

## Risks

### Risk 1. Stale async result overwrites current state

Mitigation:

- use monotonically increasing request tokens or current-key checks before applying results

### Risk 2. UI appears inconsistent during async hydration

Mitigation:

- use predictable transient states
- only replace visible data once a complete current result is ready

### Risk 3. Event ordering regressions

Mitigation:

- keep logical event emission order unchanged
- only defer expensive downstream refresh work, not the state mutation itself

### Risk 4. Hidden EDT work remains in helper methods

Mitigation:

- audit helper calls used in refresh paths
- explicitly document which methods are safe for background use

## Testing Strategy

### Manual verification

- open the plugin on a class with cached data and verify faster initial responsiveness
- switch rapidly across Java files/classes and confirm the list updates without obvious UI stalls
- use search with a larger cache set and verify typing remains smooth
- save, update, and delete cached parameter entries and confirm UI state eventually reflects the latest data
- reopen dialogs and verify no visible regression in selected-item synchronization

### Regression focus

- no incorrect table contents after rapid file switching
- no duplicate or missing search results caused by debounce or stale request suppression
- no outdated data overriding newer state after save/delete/update

## Rollout Sequence

1. Introduce background refresh primitives and stale-result suppression.
2. Convert file-switch driven list refresh to async/coalesced flow.
3. Convert search to debounced filtering with normalized text.
4. Move event-triggered heavy follow-up work off the immediate dispatch path.
5. Verify EDT boundaries and clean up remaining synchronous hotspots discovered during testing.

## Recommendation

Proceed with the moderate refactor above. This gives the best expected reduction in general stutter while preserving plugin behavior and avoiding a high-risk rewrite.
