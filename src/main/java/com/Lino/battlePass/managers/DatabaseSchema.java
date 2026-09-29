package com.Lino.battlePass.managers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Additive migrations shared by SQLite and MySQL. */
final class DatabaseSchema {
    private DatabaseSchema() {
    }

    static void ensureColumns(Connection connection, String table, Map<String, String> requiredColumns) throws SQLException {
        Set<String> columns = readColumns(connection, table);
        for (var column : requiredColumns.entrySet()) {
            if (columns.contains(column.getKey().toLowerCase(Locale.ROOT))) continue;
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column.getKey() + " " + column.getValue());
            } catch (SQLException failure) {
                // Another server may have migrated the shared database concurrently. Ignore only
                // that case; permission errors and invalid SQL must stop startup, not be hidden.
                if (!readColumns(connection, table).contains(column.getKey().toLowerCase(Locale.ROOT))) {
                    throw new SQLException("Could not add " + table + "." + column.getKey() +
                            ". Check that the database user has ALTER permission. " + failure.getMessage(), failure);
                }
            }
        }
    }

    private static Set<String> readColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            var metadata = result.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add(metadata.getColumnName(i).toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }
}
