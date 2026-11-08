package com.uninest;

import com.uninest.utils.MessageDelivery;
import com.uninest.utils.MessagePermissions;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class MessageDeliveryChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(!MessagePermissions.canMarkRead(1, "student", 2, "admin"), "sender cannot forge support read receipt");
        check(MessagePermissions.canMarkRead(2, "admin", 2, "admin"), "recipient reads message");
        check(MessagePermissions.canMarkRead(3, "admin", 2, "admin"), "another admin reads shared support inbox");
        check(!MessagePermissions.canMarkRead(2, "admin", 1, "student"), "admin cannot mark outgoing reply read");
        check(!MessagePermissions.canMarkRead(3, "landlord", 2, "admin"), "unrelated landlord denied");
        check(MessagePermissions.canMarkRead(1, "student", 1, "student"), "student reads received reply");
        check(MessagePermissions.marksReadOnLoad(null), "opening the messages page marks read");
        check(MessagePermissions.marksReadOnLoad(""), "empty background flag still marks read");
        check(!MessagePermissions.marksReadOnLoad("1"), "live background refresh leaves notifications unread");
        check(MessagePermissions.marksReadOnLoad("true"), "only background=1 skips marking read");
        State success = new State();
        try (MessageDelivery delivery = new MessageDelivery(success.connection())) { delivery.commit(); }
        check(success.calls.equals(List.of("auto:false", "commit", "auto:true")), "successful delivery commits once");
        State failure = new State();
        try (MessageDelivery delivery = new MessageDelivery(failure.connection())) { }
        check(failure.calls.equals(List.of("auto:false", "rollback", "auto:true")), "early return rolls back");
        State notificationFailure = new State();
        try (MessageDelivery delivery = new MessageDelivery(notificationFailure.connection())) {
            throw new SQLException("notification insert failed");
        } catch (SQLException expected) { }
        check(notificationFailure.calls.equals(List.of("auto:false", "rollback", "auto:true")), "notification failure rolls back message");
        State failedCommit = new State(); failedCommit.failCommit = true;
        try (MessageDelivery delivery = new MessageDelivery(failedCommit.connection())) { delivery.commit(); }
        catch (SQLException expected) { }
        check(failedCommit.calls.equals(List.of("auto:false", "commit", "rollback", "auto:true")), "commit failure attempts rollback");
        State failedRollback = new State(); failedRollback.failRollback = true;
        boolean rejected = false;
        try (MessageDelivery delivery = new MessageDelivery(failedRollback.connection())) { }
        catch (SQLException expected) { rejected = true; }
        check(rejected && failedRollback.calls.equals(List.of("auto:false", "rollback")), "rollback failure never enables autocommit");
        State nested = new State(); nested.auto = false; rejected = false;
        try { new MessageDelivery(nested.connection()); } catch (SQLException expected) { rejected = true; }
        check(rejected && nested.calls.isEmpty(), "existing transaction is not hijacked");
        System.out.println("PASS: " + checks + " messaging checks");
    }
    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks++;
    }
    private static final class State {
        boolean auto = true, failCommit, failRollback;
        final List<String> calls = new ArrayList<>();
        Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class}, (p,m,a) -> {
                switch (m.getName()) {
                    case "getAutoCommit": return auto;
                    case "setAutoCommit": auto = (Boolean)a[0]; calls.add("auto:" + auto); return null;
                    case "commit": calls.add("commit"); if (failCommit) throw new SQLException("commit failed"); return null;
                    case "rollback": calls.add("rollback"); if (failRollback) throw new SQLException("rollback failed"); return null;
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });
        }
    }
}
