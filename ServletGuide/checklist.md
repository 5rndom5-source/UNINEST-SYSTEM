# Connection Checklist

Use this checklist before presenting or testing the project.

1. Start MySQL and confirm `uninest_db` exists.
2. Start Tomcat.
3. Open `http://localhost:8080/uninest/HomePage/home.html`.
4. Register a student, landlord, or admin account from `createAccount/index.html`.
5. Log in from `createAccount/login.html`.
6. Confirm each role redirects correctly:
   - student: `UserDashboard/index.html`
   - landlord: `Landlord/index.html`
   - admin: `Admin/web/jsp/Dashboard.html`
7. Landlord submits a listing from `Landlord/listings.html`.
8. Admin reviews the listing from `Admin/web/jsp/AddProperty.html`.
9. Approved listings appear on:
   - `HomePage/search.html`
   - `UserDashboard/listings.html`
10. Student can save a listing and send a viewing request.

