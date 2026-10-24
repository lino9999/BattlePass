package com.Lino.battlePass.managers;

import java.sql.*;
import java.util.concurrent.*;

/** Commits the new SQL season and a durable, explicitly targeted filesystem transition. */
final class SeasonTransitionRepository {
    private final DatabaseConnections connections;
    private final PlayerDataRepository players;
    private final DailyMissionRepository missions;
    private final SeasonDataRepository seasons;

    SeasonTransitionRepository(DatabaseConnections connections, PlayerDataRepository players,
                               DailyMissionRepository missions, SeasonDataRepository seasons) {
        this.connections = connections; this.players = players; this.missions = missions; this.seasons = seasons;
    }

    CompletableFuture<Void> reset(SeasonResetPlan plan) {
        return CompletableFuture.runAsync(() -> {
            Connection connection = null;
            try {
                connection = connections.getConnection();
                try (DatabaseTransaction transaction = new DatabaseTransaction(connection)) {
                    String table = connections.getPrefix() + "season_data";
                    requireNoPendingTransition(connection, connections);
                    players.resetOnConnection(connection, plan.resetCoins());
                    missions.replaceOnConnection(connection, plan.missions(), plan.missionDate());
                    seasons.saveOnConnection(connection, plan.start(), plan.end(), plan.nextReset(), plan.missionDate(), plan.cycleStart());
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table
                            + " SET season_transition_id = ?, pending_rotation_season = ? WHERE id = 1")) {
                        statement.setString(1, plan.id()); statement.setInt(2, plan.rotationTarget());
                        if (statement.executeUpdate() != 1) throw new SQLException("Missing season transition row");
                    }
                    transaction.commit();
                }
            } catch (SQLException failure) { throw new CompletionException("Failed to commit the season transition", failure); }
            finally { close(connection); }
        }, connections.getExecutor());
    }

    CompletableFuture<Boolean> acknowledge(String id) {
        return CompletableFuture.supplyAsync(() -> {
            Connection connection = null;
            try {
                connection = connections.getConnection();
                try (PreparedStatement statement = connection.prepareStatement("UPDATE " + connections.getPrefix()
                        + "season_data SET season_transition_id = NULL, pending_rotation_season = NULL WHERE id = 1 AND season_transition_id = ?")) {
                    statement.setString(1, id); return statement.executeUpdate() == 1;
                }
            } catch (SQLException failure) { throw new CompletionException("Failed to acknowledge the season transition", failure); }
            finally { close(connection); }
        }, connections.getExecutor());
    }

    private void close(Connection connection) {
        if (connections.isMySQL() && connection != null) try { connection.close(); } catch (SQLException ignored) {}
    }

    static void requireNoPendingTransition(Connection connection, DatabaseConnections connections) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT season_transition_id FROM " + connections.getPrefix()
                + "season_data WHERE id = 1" + (connections.isMySQL() ? " FOR UPDATE" : "")); ResultSet result = statement.executeQuery()) {
            if (result.next() && result.getString(1) != null) throw new SQLException("A previous season transition still requires recovery");
        }
    }
}
