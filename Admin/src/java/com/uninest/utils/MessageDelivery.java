package com.uninest.utils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.http.Part;

/** Keeps a message, its attachment reference and notifications in one transaction. */
public final class MessageDelivery implements AutoCloseable {
    private final Connection connection;
    private String attachment;
    private boolean committed;

    public MessageDelivery(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Message transaction already active");
        this.connection = connection;
        connection.setAutoCommit(false);
    }

    public String saveAttachment(Part part, int messageId) throws IOException {
        attachment = MessageAttachmentSupport.save(part, messageId);
        return attachment;
    }

    public void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    @Override public void close() throws SQLException {
        if (!committed) {
            // Never enable autocommit after a failed rollback: that could commit partial work.
            connection.rollback();
            MessageAttachmentSupport.delete(attachment);
        }
        connection.setAutoCommit(true);
    }
}
