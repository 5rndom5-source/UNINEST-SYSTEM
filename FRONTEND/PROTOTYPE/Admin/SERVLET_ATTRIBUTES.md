# UniNest Admin Servlet Attribute Map

Use these `name` attributes with `request.getParameter("name")` or `request.getParameterValues("name")`.

## Shared Header
- `globalSearch` -> global search text
- `searchSubmit` -> submitted global search button value
- `profileAction` -> profile menu action

## Login
- `adminEmail` -> admin email address
- `adminPassword` -> admin password
- `rememberMe` -> checkbox value true when selected
- `loginBtn` -> login submit button

## Dashboard
- `reportType` -> dashboard report type
- `generateReportBtn` -> generate report button
- `viewAllBookingsBtn` -> view all bookings button

## Bookings
- `bookingSearch` -> booking search text
- `bookingSearchBtn` -> booking search button
- `leaseStatus` -> all, confirmed, pending, cancelled
- `roomType` -> all, studio, oneBed, shared
- `dateFrom` -> start date
- `dateTo` -> end date
- `selectAllBookings` -> select all checkbox
- `bookingIds` -> selected booking IDs, use `request.getParameterValues("bookingIds")`
- `addBookingBtn` -> add booking button

## Students
- `studentSearch` -> student search text
- `studentSearchBtn` -> student search button
- `studentStatus` -> all, active, graduated, onHold, alumni
- `program` -> all, itAppDev, business, engineering
- `selectAllStudents` -> select all checkbox
- `studentIds` -> selected student IDs, use `request.getParameterValues("studentIds")`
- `addStudentBtn` -> add student button

## Rooms
- `globalSearch` -> room keyword search in top bar
- `roomSearch` -> room search text
- `roomSearchBtn` -> room search button
- `roomType` -> all, studio, oneBed, shared, suite
- `roomStatus` -> all, occupied, available, partial, maintenance
- `filterOpen` -> filter button
- `addRoomBtn` -> add room button

## Payments
- `invoiceType` -> all, deposit, monthly, penalty
- `paymentStatus` -> all, paid, pending, overdue
- `invoiceSearch` -> invoice search text
- `invoiceSearchBtn` -> invoice search button
- `generateInvoiceBtn` -> generate invoice button
- `selectAllInvoices` -> select all checkbox
- `invoiceIds` -> selected invoice IDs, use `request.getParameterValues("invoiceIds")`

## Maintenance
- `priority` -> all, high, medium, low
- `ticketType` -> all, repair, inspection, regularService
- `ticketStatus` -> all, pending, inProgress, complete
- `ticketSearch` -> ticket search text
- `ticketSearchBtn` -> ticket search button
- `createTicketBtn` -> create ticket button
- `selectAllTickets` -> select all checkbox
- `ticketIds` -> selected ticket IDs, use `request.getParameterValues("ticketIds")`

## Reporting
- `reportType` -> all, revenue, occupancy, maintenance, demographics
- `reportStatus` -> all, complete, processing, expired
- `reportSearch` -> report search text
- `reportSearchBtn` -> report search button
- `generateReportBtn` -> generate new report button
- `selectAllReports` -> select all checkbox
- `reportIds` -> selected report IDs, use `request.getParameterValues("reportIds")`

## Settings
- `systemName` -> system display name
- `academicYear` -> selected academic year
- `currency` -> selected currency code
- `notifyBookings` -> checkbox value true when enabled
- `paymentReminders` -> checkbox value true when enabled
- `maintenanceAlerts` -> checkbox value true when enabled
- `fromEmail` -> sender email address
- `replyToEmail` -> reply-to email address
- `twoFactorEnabled` -> checkbox value true when enabled
- `sessionTimeout` -> timeout in minutes
- `saveSettingsBtn` -> save settings button

## Logout
- `logoutSource` -> page or component that requested logout
- `confirmLogoutBtn` -> confirms logout and invalidates session
- `cancelLogoutBtn` -> cancels logout and returns to dashboard
