package com.uninest.utils;

public final class MessagePermissions {
    private MessagePermissions() { }

    public static boolean canMarkRead(int userId, String role, int recipientId, String recipientRole) {
        return userId == recipientId
                || ("admin".equals(role) && "admin".equalsIgnoreCase(recipientRole));
    }

    /**
     * Opening a messages page marks its messages and notifications read. Live background refreshes
     * send background=1 and must not, so the bell badge stays until the user actually looks.
     */
    public static boolean marksReadOnLoad(String backgroundParameter) {
        return !"1".equals(backgroundParameter);
    }
}
