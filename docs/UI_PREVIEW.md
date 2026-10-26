# Standalone UI preview

This uses the actual SlayerSpeedPanel and SlayerSpeedOverlay with RuneLite's look and feel.
It does not start the game, construct a Client/ConfigManager, or load account history or credentials.
All runs, rates, completion results and mutations in the preview are synthetic.

The infobox rendering test writes `build/ux-preview/infoboxes.png` using RuneLite's native infobox component. Run `./gradlew test --tests '*SlayerSpeedInfoBoxTest'` to generate this additional preview. The standalone preview window continues to show the sidebar and task overlay.

## Open the interactive preview
From the project folder:

~~~powershell
.\gradlew.bat previewUi --offline
~~~

Choose a scenario, sidebar and overlay detail independently, width and font size. The real sidebar
buttons, search, history expansion, encounter selection and Compare dialog work. Delete/exclude/reset
operate only on synthetic history; Undo restores that synthetic snapshot for up to 30 seconds.
Import/export/recovery menu items are explanatory stubs. Export PNGs in the preview toolbar writes
images to build/ux-preview; it does not export account data.
Close the preview window to stop it. No Jagex login setup is needed.

## Automatically rebuild after source edits (Windows)

~~~powershell
.\scripts\preview-ui.ps1 -Watch
~~~

The watcher uses a separate build/preview-harness directory so tests and normal client builds can run alongside it. It compiles changed source and restarts only its own preview process. Scenario state resets
on restart. A compile failure keeps the previous preview open; save a correction to retry.
Close the preview window or press Ctrl+C in the watcher terminal to stop. This is rebuild/restart,
not Java hot reload. It does not stop any RuneLite game clients.
For a one-shot compile and launch, omit -Watch. The Gradle previewUi task works without the script.

## Render without opening a window

~~~powershell
.\gradlew.bat renderUi --offline
~~~

Outputs are under build/ux-preview: active, first_task, waiting, completed, empty, long_names, cannon,
deleted, detailed and narrow-large-font. PNGs are produced from real Java components, not generated
artwork. The PNG width is the panel's width; the interactive window adds a scrollbar beside it.

## Iteration workflow
1. Edit the production component.
2. Let the watcher recompile/restart, or rerun previewUi/renderUi.
3. Inspect Simple/Detailed, waiting/empty and long-name states at narrow width and larger fonts.
4. Run relevant tests after behavioral changes.
5. Recheck in RuneLite before release: docking, display scaling, live events, persistence and account
   transitions are integration checks that the synthetic preview cannot validate.

The sidebar's Simple/Detailed buttons update the existing setting in the real plugin. The new
Overlay detail setting defaults to Follow sidebar to preserve previous configurations; Compact is
available independently. The preview defaults its overlay to Compact to demonstrate the mockup.

Simple mode now opens on the current-task summary. Task options reveals estimate provenance, recording progress, encounter selection and Compare. View task history opens the search and saved runs. Detailed mode expands both sections. History remains visible without an active task.
