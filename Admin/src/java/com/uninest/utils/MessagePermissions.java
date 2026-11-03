package com.uninest.utils;

public final class MessagePermissions {
    private MessagePermissions() { }

    public static boolean canMarkRead(int userId, String role, int recipientId, String recipientRole) {
        return userId == recipientId
                || ("admin".equals(role) && "admin".equalsIgnoreCase(recipientRole));
    }
}
