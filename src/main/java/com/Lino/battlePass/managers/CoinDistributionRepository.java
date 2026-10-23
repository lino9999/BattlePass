package com.Lino.battlePass.managers;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Credits a delivery and advances its deadline in the same transaction. */
final class CoinDistributionRepository {
    record Result(boolean applied, LocalDateTime nextDeadline, Map<UUID, Integer> credits) {}

    private final DatabaseConnections connections;
    private final SeasonDataRepository seasons;

    CoinDistributionRepository(DatabaseConnections connections, SeasonDataRepository seasons) {
        this.connections = connections;
        this.seasons = seasons;
    }

    CompletableFuture<Result> distribute(LocalDateTime expectedDeadline, LocalDateTime nextDeadline, Map<UUID, Integer> amounts) {
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = null;
            try {
                conn = connections.getConnection();
                try (DatabaseTransaction transaction = new DatabaseTransaction(conn)) {
                    String prefix = connections.getPrefix();
                    String lock = connections.isMySQL() ? " FOR UPDATE" : "";
                    try (Statement statement = conn.createStatement()) {
                        statement.executeUpdate((connections.isMySQL() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                                + prefix + "season_data (id) VALUES (1)");
                    }
                    LocalDateTime stored = null;
                    try (PreparedStatement statement = conn.prepareStatement(
                            "SELECT next_coins_distribution FROM " + prefix + "season_data WHERE id = 1" + lock);
                         ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            String value = result.getString(1);
                            if (value != null && !value.isBlank()) stored = LocalDateTime.parse(value);
                        }
                    }
                    if (stored != null && !stored.equals(expectedDeadline)) {
                        return new Result(false, stored, Map.of());
                    }

                    Map<UUID, Integer> credits = new LinkedHashMap<>();
                    if (!amounts.isEmpty()) {
                        String placeholders = String.join(",", Collections.nCopies(amounts.size(), "?"));
                        Map<UUID, Integer> balances = new HashMap<>();
                        try (PreparedStatement statement = conn.prepareStatement("SELECT uuid, battle_coins FROM " + prefix
                                + "players WHERE uuid IN (" + placeholders + ")" + lock)) {
                            int index = 1;
                            for (UUID uuid : amounts.keySet()) statement.setString(index++, uuid.toString());
                            try (ResultSet result = statement.executeQuery()) {
                                while (result.next()) balances.put(UUID.fromString(result.getString("uuid")),
                                        Math.max(0, result.getInt("battle_coins")));
                            }
                        }
                        try (PreparedStatement statement = conn.prepareStatement(
                                "UPDATE " + prefix + "players SET battle_coins = ? WHERE uuid = ?")) {
                            for (var entry : amounts.entrySet()) {
                                Integer balance = balances.get(entry.getKey());
                                int credited = balance == null ? 0 : Math.min(Math.max(0, entry.getValue()), Integer.MAX_VALUE - balance);
                                credits.put(entry.getKey(), credited);
                                if (balance != null) {
                                    statement.setInt(1, balance + credited);
                                    statement.setString(2, entry.getKey().toString());
                                    statement.addBatch();
                                }
                            }
                            statement.executeBatch();
                        }
                    }
                    seasons.saveCoinsOnConnection(conn, nextDeadline);
                    transaction.commit();
                    return new Result(true, nextDeadline, credits);
                }
            } catch (SQLException failure) {
                throw new CompletionException("Failed to commit coin distribution", failure);
            } finally {
                if (connections.isMySQL() && conn != null) {
                    try { conn.close(); } catch (SQLException ignored) {}
                }
            }
        }, connections.getExecutor());
    }
}
