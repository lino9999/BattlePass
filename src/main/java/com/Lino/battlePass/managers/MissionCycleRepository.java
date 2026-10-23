package com.Lino.battlePass.managers;

import com.Lino.battlePass.models.Mission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Replaces the active mission cycle, calendar and all player progress in one transaction. */
final class MissionCycleRepository {
    private final DatabaseConnections connections;
    private final DailyMissionRepository missions;
    private final SeasonDataRepository seasons;

    MissionCycleRepository(DatabaseConnections connections, DailyMissionRepository missions, SeasonDataRepository seasons) {
        this.connections = connections;
        this.missions = missions;
        this.seasons = seasons;
    }

    CompletableFuture<Void> replace(List<Mission> replacements, String missionDate, LocalDateTime startDate,
                                    LocalDateTime endDate, LocalDateTime nextReset) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            try {
                conn = connections.getConnection();
                try (DatabaseTransaction transaction = new DatabaseTransaction(conn)) {
                    missions.replaceOnConnection(conn, replacements, missionDate);
                    seasons.saveOnConnection(conn, startDate, endDate, nextReset, missionDate);
                    try (PreparedStatement statement = conn.prepareStatement("DELETE FROM " + connections.getPrefix() + "missions")) {
                        statement.executeUpdate();
                    }
                    transaction.commit();
                }
            } catch (SQLException failure) {
                throw new CompletionException("Failed to replace the daily mission cycle", failure);
            } finally {
                if (connections.isMySQL() && conn != null) {
                    try { conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, connections.getExecutor());
    }
}
