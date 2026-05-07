UNINEST ADMIN SERVLETS - COPY AND PASTE GUIDE

These servlet templates match the attributes used in the Admin HTML pages.

How to use:
1. Copy each .txt file into your Java source folder.
2. Rename the copied file from .txt to .java.
3. Keep the class name exactly the same as the file name.
4. If your project uses Jakarta EE instead of Java EE, replace:
   javax.servlet
   with:
   jakarta.servlet

Important:
- The HTML pages are inside the Admin folder.
- Each servlet has two mappings, for example:
  @WebServlet({"/BookingServlet", "/Admin/BookingServlet"})
- This prevents 404 errors when a form submits from Admin/Booking.html.

Main page servlets:
- LoginServlet.txt
- AdminSearchServlet.txt
- AdminProfileServlet.txt
- GenerateReportServlet.txt
- BookingServlet.txt
- StudentServlet.txt
- RoomServlet.txt
- PaymentServlet.txt
- MaintenanceServlet.txt
- ReportServlet.txt
- SettingsServlet.txt
- LogoutServlet.txt

Bulk action servlets:
- BulkBookingActionServlet.txt
- BulkStudentActionServlet.txt
- BulkInvoiceActionServlet.txt
- BulkMaintenanceActionServlet.txt
- BulkReportActionServlet.txt

Button/action servlets:
- AddBookingServlet.txt
- AddStudentServlet.txt
- AddRoomServlet.txt
- GenerateInvoiceServlet.txt
- CreateTicketServlet.txt

View/edit link servlets:
- BookingDetailsServlet.txt
- EditBookingServlet.txt
- MaintenanceDetailsServlet.txt
- AssignTicketServlet.txt
- UpdateTicketStatusServlet.txt
- EditStudentServlet.txt
- StudentProfileServlet.txt
- ApproveStudentServlet.txt
- AlumniServlet.txt
- StudentDetailsServlet.txt
- ReconnectStudentServlet.txt
- InvoiceDetailsServlet.txt
- EmailInvoiceServlet.txt
- VoidInvoiceServlet.txt
- RoomDetailsServlet.txt
- ReportDetailsServlet.txt
- DownloadReportServlet.txt
- ForgotPasswordServlet.txt
