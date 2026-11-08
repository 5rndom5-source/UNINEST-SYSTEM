package com.uninest;

import com.uninest.utils.AccountStatus;

public class AccountStatusChecks {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        check(AccountStatus.adminMayChange(1, 2, "active", false), "admin deactivates an active account");
        check(AccountStatus.adminMayChange(1, 2, "pending_verification", false), "admin deactivates a pending landlord");
        check(!AccountStatus.adminMayChange(1, 1, "active", false), "admin cannot deactivate themselves");
        check(!AccountStatus.adminMayChange(1, 1, "inactive", true), "admin cannot reactivate themselves");
        check(!AccountStatus.adminMayChange(1, 2, "inactive", false), "already inactive is not deactivated again");
        check(!AccountStatus.adminMayChange(1, 2, "suspended", false), "suspended accounts are left alone");
        check(AccountStatus.adminMayChange(1, 2, "inactive", true), "admin reactivates an inactive account");
        check(!AccountStatus.adminMayChange(1, 2, "active", true), "active account is not reactivated");
        check(!AccountStatus.adminMayChange(1, 2, "suspended", true), "suspended account is not reactivated here");
        check(!AccountStatus.adminMayChange(1, 2, null, true), "unknown status is refused");

        check(AccountStatus.mayCloseOwn("student", "active", 0), "student closes own account");
        check(AccountStatus.mayCloseOwn("landlord", "pending_verification", 0), "pending landlord closes own account");
        check(AccountStatus.mayCloseOwn("admin", "active", 1), "admin closes own account when another admin remains");
        check(!AccountStatus.mayCloseOwn("admin", "active", 0), "last active admin cannot close own account");
        check(AccountStatus.mayCloseOwn("admin", "pending_verification", 0), "pending admin request can be withdrawn");
        check(!AccountStatus.mayCloseOwn("student", "inactive", 0), "inactive account cannot be closed again");
        check(!AccountStatus.mayCloseOwn("student", "suspended", 0), "suspended account cannot close itself");
        System.out.println("PASS: " + checks + " account status checks");
    }
}
