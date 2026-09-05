package com.slayerspeed.ui;

import com.slayerspeed.SlayerSpeedConfig;
import com.slayerspeed.persistence.TaskHistoryRepository;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.client.callback.ClientThread;

/** File access is asynchronous; repository transitions stay on the client thread. */
public final class HistoryFileActions implements Consumer<String>
{
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private final Component owner;
    private final ClientThread clientThread;
    private final TaskHistoryRepository repository;
    private final SlayerSpeedConfig config;
    private final Runnable refresh;
    private final Runnable reload;

    public HistoryFileActions(Component owner, ClientThread clientThread, TaskHistoryRepository repository,
        SlayerSpeedConfig config, Runnable refresh, Runnable reload)
    {
        this.owner = owner; this.clientThread = clientThread; this.repository = repository;
        this.config = config; this.refresh = refresh; this.reload = reload;
    }

    public void accept(String action)
    {
        String expectedContext = owner instanceof SlayerSpeedPanel ? ((SlayerSpeedPanel) owner).getActionContext() : null;
        clientThread.invoke(() ->
        {
            if (expectedContext != null && (!expectedContext.equals(((SlayerSpeedPanel) owner).getActionContext())
                || !expectedContext.startsWith(repository.getProfileIdentity() + ":"))) { return; }
            String profile = repository.getProfileIdentity();
            if (profile == null) { error(new IllegalStateException("No current account profile")); return; }
            try
            {
                if (action.equals("undo")) { repository.undoDeletion(); refresh.run(); return; }
                if (action.equals("backup"))
                {
                    preview(repository.getBackup(), TaskHistoryRepository.ImportMode.REPLACE, profile);
                    return;
                }
                if (action.equals("fresh"))
                {
                    String original = repository.exportHistory(true);
                    saveFile(original, () -> clientThread.invoke(() ->
                    {
                        if (!Objects.equals(profile, repository.getProfileIdentity()))
                        {
                            error(new IllegalStateException("Account changed. Start recovery again.")); return;
                        }
                        preview("{\"schemaVersion\":5,\"statisticsByTaskKey\":{}}",
                            TaskHistoryRepository.ImportMode.REPLACE, profile);
                    }));
                    return;
                }
                if (action.startsWith("export"))
                {
                    saveFile(repository.exportHistory(action.equals("exportBackup")), null);
                }
                else
                {
                    SwingUtilities.invokeLater(() -> chooseImport(action, profile));
                }
            }
            catch (RuntimeException ex) { error(ex); }
        });
    }

    private void saveFile(String text, Runnable afterSave)
    {
        SwingUtilities.invokeLater(() ->
        {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("slayer-task-speed-history.json"));
            if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) { return; }
            Path path = chooser.getSelectedFile().toPath();
            if (Files.exists(path) && JOptionPane.showConfirmDialog(owner,
                "Replace the selected file?", "Export history", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
            CompletableFuture.runAsync(() ->
            {
                try
                {
                    // Permit saving an oversized original for recovery; import still enforces its limit.
                    Files.write(path, text.getBytes(StandardCharsets.UTF_8));
                }
                catch (java.io.IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
            }).whenComplete((ignored, failure) ->
            {
                if (failure != null) { error(failure); }
                else if (afterSave != null) { afterSave.run(); }
                else { info("History exported."); }
            });
        });
    }

    private void chooseImport(String action, String profile)
    {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) { return; }
        Path path = chooser.getSelectedFile().toPath();
        CompletableFuture.supplyAsync(() ->
        {
            try (java.io.InputStream stream = Files.newInputStream(path))
            {
                byte[] bytes = stream.readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) { throw new IllegalArgumentException("File exceeds the 8 MiB limit"); }
                return new String(bytes, StandardCharsets.UTF_8);
            }
            catch (java.io.IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
        }).whenComplete((text, failure) ->
        {
            if (failure != null) { error(failure); return; }
            clientThread.invoke(() -> preview(text,
                action.equals("merge") ? TaskHistoryRepository.ImportMode.MERGE
                    : action.equals("recoverCheckpoint") ? TaskHistoryRepository.ImportMode.RECOVER_CHECKPOINT
                    : TaskHistoryRepository.ImportMode.REPLACE, profile));
        });
    }

    private void preview(String text, TaskHistoryRepository.ImportMode mode, String profile)
    {
        try
        {
            if (!Objects.equals(profile, repository.getProfileIdentity()))
            {
                throw new IllegalStateException("Account changed. Choose the import again.");
            }
            TaskHistoryRepository.ImportPreview preview = repository.previewImport(text, mode, config.maximumRecentRuns());
            SwingUtilities.invokeLater(() ->
            {
                JTextArea summary = new JTextArea(preview.getSummary(), 5, 45);
                summary.setEditable(false); summary.setLineWrap(true); summary.setWrapStyleWord(true);
                if (JOptionPane.showConfirmDialog(owner, summary, "Review history import",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) { return; }
                clientThread.invoke(() ->
                {
                    try
                    {
                        repository.applyImport(preview);
                        if (mode == TaskHistoryRepository.ImportMode.RECOVER_CHECKPOINT) { reload.run(); }
                        refresh.run();
                    }
                    catch (RuntimeException ex) { error(ex); }
                });
            });
        }
        catch (RuntimeException ex) { error(ex); }
    }

    private void info(String text)
    {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(owner, text));
    }

    private void error(Throwable failure)
    {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(owner, cause.getMessage(),
            "History action was not completed", JOptionPane.WARNING_MESSAGE));
    }
}
