package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;

/** Complete large default collections in bounded filesystem-only batches, retaining a frozen template pair. */
final class SeasonArchiveInitializer {
    static final int BATCH_SIZE = 16;
    private final BattlePass plugin;
    private final Path root;
    private Job job;
    private boolean closed;

    SeasonArchiveInitializer(BattlePass plugin) { this.plugin = plugin; root = plugin.getDataFolder().toPath(); }

    synchronized CompletableFuture<Void> start(int total, int current, int next) throws IOException, InvalidConfigurationException {
        if (closed) return cancelledFuture();
        if (job != null && !job.result.isDone() && job.total == total) {
            job.ensure(current); job.ensure(next); return job.result;
        }
        if (total <= BATCH_SIZE && allFoldersExist(total)) { cancel(); return CompletableFuture.completedFuture(null); }
        Job previous = job;
        job = null;
        if (previous != null) previous.stopScheduling();
        try {
            Templates template = snapshot(root);
            Job started = new Job(total, template);
            started.ensure(current);
            started.ensure(next);
            job = started;
            if (total <= BATCH_SIZE) started.run();
            else {
                plugin.getLogger().info("Initializing " + total + " default season archives in background batches");
                started.schedule();
            }
            return started.result;
        } finally {
            if (previous != null) previous.cancel();
        }
    }

    synchronized void ensure(int season) throws IOException {
        if (job != null) job.ensure(season);
    }

    synchronized void configurationChanged(boolean enabled, int total) {
        if (!enabled || (job != null && job.total != total)) cancel();
    }

    synchronized void cancel() {
        Job previous = job;
        job = null;
        if (previous != null) previous.cancel();
    }

    synchronized void shutdown() { closed = true; cancel(); }

    private static CompletableFuture<Void> cancelledFuture() {
        CompletableFuture<Void> result = new CompletableFuture<>(); result.cancel(false); return result;
    }

    private boolean allFoldersExist(int total) {
        for (int season = 1; season <= total; season++) if (!Files.isDirectory(root.resolve("seasons/season-" + season))) return false;
        return true;
    }

    private final class Job implements Runnable {
        final int total;
        final Templates template;
        final CompletableFuture<Void> result = new CompletableFuture<>();
        final Set<Integer> prioritized = ConcurrentHashMap.newKeySet();
        volatile long cursor = 1;
        private boolean cancelled;
        private BukkitTask scheduled;

        Job(int total, Templates template) {
            this.total = total; this.template = template;
            result.whenComplete((ignored, failure) -> { if (result.isCancelled()) cancel(); });
        }

        private boolean inactive() { return cancelled || plugin.isStopping() || (total > BATCH_SIZE && !plugin.isEnabled()); }

        void stopScheduling() {
            BukkitTask pending;
            synchronized (this) {
                if (cancelled) return;
                cancelled = true;
                pending = scheduled;
            }
            if (pending != null) pending.cancel();
        }

        void cancel() { stopScheduling(); if (!result.isDone()) result.cancel(false); }

        void schedule() {
            RuntimeException rejected = null;
            boolean stopped;
            synchronized (this) {
                stopped = inactive();
                if (!stopped) {
                    try { scheduled = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this, 1L); }
                    catch (RuntimeException failure) { rejected = failure; }
                }
            }
            if (stopped) cancel();
            else if (rejected != null) fail(rejected);
        }

        void ensure(int season) throws IOException {
            Path target = root.resolve("seasons/season-" + season);
            if (Files.exists(target)) {
                if (!Files.isDirectory(target)) throw new IOException("Season path is not a directory: " + target);
                return;
            }
            synchronized (this) {
                if (season < 1 || season > total || cursor > season || prioritized.contains(season))
                    throw new IOException("Previously prepared season archive is missing: " + target);
                if (inactive() || result.isCompletedExceptionally()) throw new IOException("Default archive initialization is stopped; retry it before using season " + season);
            }
            create(root, season, template, (temporary, destination) -> {
                synchronized (Job.this) {
                    if (inactive()) throw new CancellationException("Archive initialization stopped");
                    move(temporary, destination);
                    prioritized.add(season);
                }
            });
        }

        @Override public void run() {
            try {
                for (int work = 0; work < BATCH_SIZE && cursor <= total; work++) {
                    boolean stopped;
                    synchronized (this) { stopped = inactive(); }
                    if (stopped) { cancel(); return; }
                    int season = (int) cursor;
                    ensure(season);
                    cursor++;
                    prioritized.remove(season);
                }
                if (cursor > total) result.complete(null);
                else schedule();
            } catch (CancellationException stopped) { cancel(); }
            catch (IOException | RuntimeException failure) { fail(failure); }
        }

        private void fail(Throwable failure) {
            if (result.isDone()) return;
            plugin.getLogger().severe("Default season archive initialization failed at season " + cursor + ": " + failure.getMessage());
            result.completeExceptionally(failure);
        }
    }

    record Templates(byte[] free, byte[] premium) {}
    private interface Publisher { void publish(Path temporary, Path target) throws IOException; }

    static Templates snapshot(Path root) throws IOException, InvalidConfigurationException {
        return new Templates(readRewards(root.resolve("BattlePassFREE.yml")), readRewards(root.resolve("BattlePassPREMIUM.yml")));
    }

    static byte[] readRewards(Path file) throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing reward file: " + file);
        byte[] bytes = Files.readAllBytes(file);
        new YamlConfiguration().loadFromString(new String(bytes, StandardCharsets.UTF_8));
        return bytes;
    }

    static void create(Path root, int season, Templates template) throws IOException { create(root, season, template, SeasonArchiveInitializer::move); }

    private static void create(Path root, int season, Templates template, Publisher publisher) throws IOException {
        Path target = root.resolve("seasons/season-" + season);
        if (Files.exists(target)) {
            if (!Files.isDirectory(target)) throw new IOException("Season path is not a directory: " + target);
            return;
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempDirectory(target.getParent(), ".season-" + season + "-pending-");
        try {
            Files.write(temporary.resolve("BattlePassFREE.yml"), template.free());
            Files.write(temporary.resolve("BattlePassPREMIUM.yml"), template.premium());
            publisher.publish(temporary, target);
        } finally {
            Files.deleteIfExists(temporary.resolve("BattlePassFREE.yml"));
            Files.deleteIfExists(temporary.resolve("BattlePassPREMIUM.yml"));
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path temporary, Path target) throws IOException {
        if (Files.exists(target)) {
            if (Files.isDirectory(target)) return;
            throw new FileAlreadyExistsException(target.toString());
        }
        try {
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, target); }
        } catch (FileAlreadyExistsException | DirectoryNotEmptyException competing) {
            if (!Files.isDirectory(target)) throw competing;
        }
    }
}
