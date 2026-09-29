package com.Lino.battlePass.managers;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseSchemaTest {
    @Test
    void addsMissingTextColumnWithoutUnsupportedMySqlDefault() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        stubColumns(connection, statement, "id", "name");

        DatabaseSchema.ensureColumns(connection, "bp_daily_missions", Map.of("additional_targets", "TEXT"));

        verify(statement).executeUpdate("ALTER TABLE bp_daily_missions ADD COLUMN additional_targets TEXT");
    }

    @Test
    void doesNotAlterAnAlreadyUpgradedTable() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        stubColumns(connection, statement, "id", "additional_targets");

        DatabaseSchema.ensureColumns(connection, "bp_daily_missions", Map.of("additional_targets", "TEXT"));

        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void migrationPermissionFailureIsPropagatedInsteadOfPretendingSuccess() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        stubColumns(connection, statement, "id");
        SQLException denied = new SQLException("ALTER command denied", "42000", 1142);
        when(statement.executeUpdate(anyString())).thenThrow(denied);

        SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.ensureColumns(
                connection, "bp_daily_missions", Map.of("additional_targets", "TEXT")));

        assertSame(denied, failure.getCause());
        assertTrue(failure.getMessage().contains("bp_daily_missions.additional_targets"));
    }

    @Test
    void acceptsColumnCreatedConcurrentlyByAnotherServer() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSetMetaData metadata = stubColumns(connection, statement, "id", "additional_targets");
        AtomicBoolean migrated = new AtomicBoolean();
        when(metadata.getColumnCount()).thenAnswer(call -> migrated.get() ? 2 : 1);
        when(statement.executeUpdate(anyString())).thenAnswer(call -> {
            migrated.set(true);
            throw new SQLException("Duplicate column name", "42S21", 1060);
        });

        assertDoesNotThrow(() -> DatabaseSchema.ensureColumns(connection, "bp_daily_missions",
                Map.of("additional_targets", "TEXT")));
    }

    private ResultSetMetaData stubColumns(Connection connection, Statement statement, String... columns) throws Exception {
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(result);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(columns.length);
        for (int i = 0; i < columns.length; i++) when(metadata.getColumnName(i + 1)).thenReturn(columns[i]);
        return metadata;
    }
}
