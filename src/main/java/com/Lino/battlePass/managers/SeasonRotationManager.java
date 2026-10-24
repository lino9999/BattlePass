package com.Lino.battlePass.managers;

import com.Lino.battlePass.BattlePass;
import com.Lino.battlePass.utils.AtomicYamlFile;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Publishes a season only after both reward files and its rotation index have been saved. */
public class SeasonRotationManager {
    private final BattlePass plugin;
    private final File rotationFile;
    private final SeasonFileTransaction files;
    private final SeasonArchiveInitializer archives;
    private int currentSeason;
    private int totalSeasons;
    private boolean rotationEnabled;

    public SeasonRotationManager(BattlePass plugin) {
        this.plugin = plugin;
        this.rotationFile = new File(plugin.getDataFolder(), "season_rotation.yml");
        this.files = new SeasonFileTransaction(plugin.getDataFolder().toPath(), plugin.getLogger());
        this.archives = new SeasonArchiveInitializer(plugin);
        reload();
    }

    public synchronized void reload() {
        try {
            files.recover();
            boolean enabled = plugin.getConfig().getBoolean("season.rotation.enabled", false);
            int total = Math.max(1, plugin.getConfig().getInt("season.rotation.total-seasons", 3));
            boolean firstRun = !rotationFile.exists();
            if (firstRun && currentSeason > 0) throw new IOException("season_rotation.yml disappeared; restore it before reloading");
            YamlConfiguration state = AtomicYamlFile.load(rotationFile);
            int season = readCurrentSeason(state);
            if (season < 1 || season > total) {
                plugin.getLogger().warning("Current season " + season + " is outside 1.." + total + "; selecting season 1");
                season = 1;
            }
            if (enabled && (firstRun || !new File(plugin.getDataFolder(), "seasons").exists())) createSeasonFolder(season);
            if (enabled) archives.ensure(season);
            if (enabled) {
                Map<String, byte[]> replacements = rewardFiles(season);
                replacements.put("season_rotation.yml", rotationBytes(state, season));
                files.commit(replacements);
            } else if (firstRun || season != readCurrentSeason(state)) {
                state.set("current-season", season);
                AtomicYamlFile.save(rotationFile, state);
            }
            rotationEnabled = enabled;
            totalSeasons = total;
            currentSeason = season;
            archives.configurationChanged(enabled, total);
        } catch (IOException | InvalidConfigurationException failure) { throw failure("reload season rotation", failure); }
    }

    public synchronized void reapplyCurrentSeason() {
        if (rotationEnabled) applySeasonRewards(currentSeason);
    }

    /** Small collections complete inline; large ones continue in bounded background batches. */
    public synchronized void createDefaultSeasonFolders() {
        CompletableFuture<Void> result = createDefaultSeasonFoldersAsync();
        if (result.isCompletedExceptionally() && !result.isCancelled()) {
            try { result.join(); }
            catch (CompletionException failure) { throw failure("create default season folders", failure); }
        }
    }

    /** Completion of the entire collection. Do not block the server thread waiting for a background job. */
    public synchronized CompletableFuture<Void> createDefaultSeasonFoldersAsync() {
        if (!rotationEnabled) return CompletableFuture.completedFuture(null);
        try { return archives.start(totalSeasons, currentSeason, nextSeason()); }
        catch (IOException | InvalidConfigurationException failure) { return CompletableFuture.failedFuture(failure); }
    }

    public synchronized void shutdown() { archives.shutdown(); }

    /** Prepare a requested default archive while background initialization is still covering its range. */
    public synchronized void prepareSeasonArchive(int season) {
        if (!isValidSeason(season)) throw new IllegalArgumentException("Invalid season: " + season);
        try { archives.ensure(season); }
        catch (IOException failure) { throw failure("prepare season " + season, failure); }
    }

    private void createSeasonFolder(int season) throws IOException, InvalidConfigurationException {
        Path target = getSeasonRewardFile(season, false).toPath().getParent();
        if (Files.isDirectory(target)) return;
        if (Files.exists(target)) throw new IOException("Season path is not a directory: " + target);
        SeasonArchiveInitializer.create(plugin.getDataFolder().toPath(), season, SeasonArchiveInitializer.snapshot(plugin.getDataFolder().toPath()));
    }

    /** Preflight before resetting player data; the actual apply validates the files again. */
    public synchronized void validateNextSeason() {
        if (!rotationEnabled) return;
        try {
            files.recover();
            archives.ensure(nextSeason());
            rewardFiles(nextSeason());
            currentRotationState();
            files.validateDestinations(Set.of("BattlePassFREE.yml", "BattlePassPREMIUM.yml", "season_rotation.yml"));
        } catch (IOException | InvalidConfigurationException failure) { throw failure("validate the next season", failure); }
    }

    public synchronized void rotateToNextSeason() {
        if (!rotationEnabled) return;
        try { archives.ensure(nextSeason()); }
        catch (IOException failure) { throw failure("prepare the next season", failure); }
        selectSeason(nextSeason());
    }

    /** Idempotent recovery of a committed SQL transition, including after rotation was disabled. */
    synchronized void completeSeasonTransition(int season) {
        selectSeason(season);
    }

    private void selectSeason(int next) {
        if (!isValidSeason(next)) throw new IllegalStateException("Pending season " + next + " is outside the configured range; restore its configuration before recovery");
        try {
            files.recover();
            Map<String, byte[]> replacements = rewardFiles(next);
            replacements.put("season_rotation.yml", rotationBytes(currentRotationState(), next));
            files.commit(replacements);
            currentSeason = next;
            plugin.getLogger().info("Season rotated to: " + next);
        } catch (IOException | InvalidConfigurationException failure) { throw failure("rotate season rewards", failure); }
    }

    public synchronized void applySeasonRewards(int seasonNumber) {
        if (!isValidSeason(seasonNumber)) throw new IllegalArgumentException("Invalid season: " + seasonNumber);
        try { archives.ensure(seasonNumber); files.commit(rewardFiles(seasonNumber)); }
        catch (IOException | InvalidConfigurationException failure) { throw failure("apply season " + seasonNumber + " rewards", failure); }
    }

    private int nextSeason() { return currentSeason >= totalSeasons ? 1 : currentSeason + 1; }

    int getNextSeason() { return nextSeason(); }

    private Map<String, byte[]> rewardFiles(int season) throws IOException, InvalidConfigurationException {
        Map<String, byte[]> rewards = new LinkedHashMap<>();
        rewards.put("BattlePassFREE.yml", readRewards(getSeasonRewardFile(season, false)));
        rewards.put("BattlePassPREMIUM.yml", readRewards(getSeasonRewardFile(season, true)));
        return rewards;
    }

    private byte[] readRewards(File file) throws IOException, InvalidConfigurationException {
        return SeasonArchiveInitializer.readRewards(file.toPath());
    }

    private byte[] rotationBytes(YamlConfiguration state, int season) {
        state.set("current-season", season);
        return state.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    private int readCurrentSeason(YamlConfiguration state) throws IOException {
        Object value = state.get("current-season");
        if (value == null) return 1;
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < Integer.MIN_VALUE
                || ((Number) value).longValue() > Integer.MAX_VALUE) throw new IOException("current-season must be an integer in season_rotation.yml");
        return ((Number) value).intValue();
    }

    private YamlConfiguration currentRotationState() throws IOException, InvalidConfigurationException {
        if (!rotationFile.isFile()) throw new IOException("Missing rotation state: " + rotationFile);
        YamlConfiguration state = AtomicYamlFile.load(rotationFile);
        if (readCurrentSeason(state) != currentSeason) throw new IOException("Rotation state changed; reload it before changing seasons");
        return state;
    }

    private IllegalStateException failure(String operation, Exception failure) {
        plugin.getLogger().severe("Could not " + operation + ": " + failure.getMessage());
        return new IllegalStateException("Could not " + operation + ": " + failure.getMessage(), failure);
    }

    public File getSeasonRewardFile(int seasonNumber, boolean isPremium) {
        return new File(plugin.getDataFolder(), "seasons/season-" + seasonNumber + "/BattlePass" + (isPremium ? "PREMIUM" : "FREE") + ".yml");
    }

    public boolean isValidSeason(int seasonNumber) { return seasonNumber >= 1 && seasonNumber <= totalSeasons; }
    public int getCurrentSeason() { return currentSeason; }
    public int getTotalSeasons() { return totalSeasons; }
    public boolean isRotationEnabled() { return rotationEnabled; }
}
