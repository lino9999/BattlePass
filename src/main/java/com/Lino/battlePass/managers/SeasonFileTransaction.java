package com.Lino.battlePass.managers;

import com.Lino.battlePass.utils.AtomicFile;
import com.Lino.battlePass.utils.AtomicYamlFile;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Recoverable replacement of the two live reward files and the rotation index on the server thread. */
final class SeasonFileTransaction {
    static final String JOURNAL = "season_rotation.pending.yml";
    private static final Set<String> ALLOWED = Set.of("BattlePassFREE.yml", "BattlePassPREMIUM.yml", "season_rotation.yml");
    private final Path folder;
    private final Path journal;
    private final Logger logger;

    SeasonFileTransaction(Path folder, Logger logger) {
        this.folder = folder.toAbsolutePath().normalize();
        this.journal = this.folder.resolve(JOURNAL);
        this.logger = logger;
    }

    void commit(Map<String, byte[]> replacements) throws IOException, InvalidConfigurationException {
        recover();
        if (replacements.isEmpty() || !ALLOWED.containsAll(replacements.keySet())) throw new IOException("Invalid season file set");
        validateDestinations(replacements.keySet());
        YamlConfiguration record = new YamlConfiguration();
        record.set("version", 1);
        record.set("status", "prepared");
        Set<Path> destinations = new HashSet<>();
        int index = 0;
        for (String name : replacements.keySet()) {
            Path target = target(name);
            Path resolved = resolved(target);
            if (!destinations.add(resolved) || resolved.equals(resolved(journal))) throw new IOException("Season files share a destination");
            String path = "files.entry" + index++;
            record.set(path + ".name", name);
            record.set(path + ".target", resolved.toString());
            boolean existed = Files.exists(target);
            record.set(path + ".existed", existed);
            if (existed) record.set(path + ".previous", Base64.getEncoder().encodeToString(Files.readAllBytes(target)));
        }
        AtomicYamlFile.save(journal.toFile(), record);
        try {
            for (var replacement : replacements.entrySet()) AtomicFile.write(target(replacement.getKey()), replacement.getValue());
            record.set("status", "committed");
            AtomicYamlFile.save(journal.toFile(), record);
        } catch (IOException | RuntimeException failure) {
            try { recover(); }
            catch (IOException | InvalidConfigurationException | RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                logger.severe("Season recovery is incomplete. Keep " + journal + " for the next recovery attempt: " + rollbackFailure.getMessage());
            }
            throw failure;
        }
        discardJournal();
    }

    void recover() throws IOException, InvalidConfigurationException {
        if (!Files.exists(journal)) return;
        YamlConfiguration record = AtomicYamlFile.load(journal.toFile());
        if (record.getInt("version") != 1) throw new IOException("Unsupported season recovery journal: " + journal);
        String status = record.getString("status");
        if (!"prepared".equals(status) && !"committed".equals(status)) throw new IOException("Invalid season recovery status");
        var entries = record.getConfigurationSection("files");
        if (entries == null || entries.getKeys(false).isEmpty() || entries.getKeys(false).size() > ALLOWED.size()) throw new IOException("Invalid season recovery entries");
        Map<Path, byte[]> backups = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        Set<Path> destinations = new HashSet<>();
        for (String key : entries.getKeys(false)) {
            var entry = entries.getConfigurationSection(key);
            if (entry == null || entry.getString("name") == null || !ALLOWED.contains(entry.getString("name"))
                    || !(entry.get("existed") instanceof Boolean)) throw new IOException("Invalid season recovery entry");
            String name = entry.getString("name");
            Path file = target(name);
            Path resolved = resolved(file);
            if (!names.add(name) || !destinations.add(resolved) || !resolved.toString().equals(entry.getString("target"))
                    || resolved.equals(resolved(journal))) throw new IOException("Season recovery destinations changed; retain " + journal);
            byte[] previous = null;
            if (entry.getBoolean("existed")) {
                try { previous = Base64.getDecoder().decode(Objects.requireNonNull(entry.getString("previous"))); }
                catch (IllegalArgumentException | NullPointerException invalid) { throw new IOException("Invalid season recovery backup", invalid); }
            }
            backups.put(file, previous);
        }
        if ("prepared".equals(status)) {
            IOException failure = null;
            for (var backup : backups.entrySet()) {
                try {
                    if (backup.getValue() != null) AtomicFile.write(backup.getKey(), backup.getValue());
                    else Files.deleteIfExists(backup.getKey());
                } catch (IOException restoreFailure) {
                    if (failure == null) failure = restoreFailure;
                    else failure.addSuppressed(restoreFailure);
                }
            }
            if (failure != null) throw failure;
            logger.warning("Recovered an interrupted season file update");
        }
        discardJournal();
    }

    void validateDestinations(Set<String> names) throws IOException {
        Set<Path> destinations = new HashSet<>();
        Path record = resolved(journal);
        for (String name : names) {
            Path destination = resolved(target(name));
            if (!destinations.add(destination) || destination.equals(record)) throw new IOException("Season files share a destination");
        }
    }

    private Path target(String name) throws IOException {
        if (name == null || !ALLOWED.contains(name)) throw new IOException("Unsupported season file: " + name);
        Path file = folder.resolve(name);
        Path destination = resolved(file); // Also validate recovery deletions before mutation.
        Path archives = folder.resolve("seasons").toFile().getCanonicalFile().toPath();
        if (destination.startsWith(archives)) throw new IOException("A live season file points into an archive: " + file);
        return file;
    }

    private Path resolved(Path file) throws IOException {
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) throw new IOException("Season destination is not a file: " + file);
            return file.toRealPath();
        }
        if (Files.isSymbolicLink(file)) throw new IOException("Season destination is a dangling link: " + file);
        return file.toFile().getCanonicalFile().toPath();
    }

    private void discardJournal() {
        try { Files.deleteIfExists(journal); }
        catch (IOException failure) { logger.warning("Could not remove resolved season journal " + journal + ": " + failure.getMessage()); }
    }
}
