package com.Lino.battlePass.managers;

import java.sql.Connection;
import java.sql.SQLException;

/** Owns one transaction on an otherwise auto-commit connection. */
final class DatabaseTransaction implements AutoCloseable {
    private final Connection connection;
    private boolean committed;

    DatabaseTransaction(Connection connection) throws SQLException {
        this.connection = connection;
        if (!connection.getAutoCommit()) throw new SQLException("Database connection already has an active transaction");
        connection.setAutoCommit(false);
    }

    void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    @Override
    public void close() throws SQLException {
        if (!committed) {
            try {
                connection.rollback();
            } catch (SQLException failure) {
                // Never enable auto-commit after a failed rollback: that could commit a partial operation.
                try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }
        connection.setAutoCommit(true);
    }
}
