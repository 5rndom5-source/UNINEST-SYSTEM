# UniNest Servlet Guide

This folder explains how the UniNest pages connect to Java servlets.

Use this guide when adding a form, changing a button action, or checking whether a page is connected to the backend.

## Folder Map

| Folder | Purpose | Main Pages |
| --- | --- | --- |
| `HomePage` | Public website pages | `home.html`, `search.html`, `about.html`, `contact.html` |
| `createAccount` | Shared registration and login | `index.html`, `login.html` |
| `UserDashboard` | Student dashboard | `index.html`, `listings.html`, `saved.html`, `requests.html`, `messages.html`, `profile.html` |
| `Landlord` | Landlord portal | `index.html`, `listings.html`, `booking-requests.html`, `reviews.html`, `contact-landlord.html`, `profile.html`, `logout.html` |
| `Admin/web/jsp` | Admin portal | `Dashboard.html`, `AddProperty.html`, `MyProperties.html`, `BookingRequests.html`, `Enquiries.html`, `Messages.html`, `Payments.html`, `Maintenance.html`, `Profile.html` |
| `Admin/src/java` | Shared backend utilities and Admin servlets | database, validation, authentication, verification |

## Servlet Source Folders

| Source Folder | Package | What It Handles |
| --- | --- | --- |
| `HomePage/java-servlets` | `com.uninest.servlets.publicpages` | public listings, public contact, public page redirects |
| `createAccount/java-servlets` | `com.uninest.servlets.account` | shared login and account registration |
| `UserDashboard/java-servlets` | `com.uninest.servlets.student` | student dashboard data, saved listings, requests, messages, profile |
| `Landlord/java-servlets` | `com.uninest.servlets.landlord` | landlord listings, profile, contact, bookings, reviews, logout |
| `Admin/src/java/com/uninest/servlets` | `com.uninest.servlets.*` | admin login, listing verification, records, messages, notifications |

## Important Rule

Servlets use `@WebServlet("/RouteName")`.

That means a form inside a folder must point back to the application root before the servlet name.

Example from `UserDashboard/profile.html`:

```html
<form action="../StudentProfileServlet" method="post">
```

Example from `Admin/web/jsp/Profile.html`:

```html
<form action="../../../ProfileServlet" method="post">
```

The number of `../` parts depends on how deep the HTML file is inside the project.

## Data Service Status

Every HTML page loads `system-status.js`. The script calls:

```text
/DatabaseStatusServlet
```

The servlet returns JSON with `connected`, `status`, `label`, and `checkedAt`. This helps each team member confirm whether their local MySQL and servlet setup is reachable while testing in NetBeans or Tomcat.
