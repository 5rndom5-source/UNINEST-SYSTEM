package com.uninest.servlets.pages;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet({
        "/DashboardServlet",
        "/AddPropertyPageServlet",
        "/BookingDetailsServlet",
        "/BookingRequestsPageServlet",
        "/DownloadReceiptServlet",
        "/EditPropertyPageServlet",
        "/EnquiriesPageServlet",
        "/ForgotPasswordPageServlet",
        "/LoginPageServlet",
        "/LogoutPageServlet",
        "/MaintenancePageServlet",
        "/MessagesPageServlet",
        "/MyPropertiesPageServlet",
        "/PaymentsPageServlet",
        "/ProfilePageServlet",
        "/RegisterPageServlet",
        "/ViewBookingServlet",
        "/ViewMaintenanceServlet",
        "/ViewPaymentServlet",
        "/ViewPropertyServlet",
        "/Dashboard.html",
        "/AddProperty.html",
        "/BookingDetails.html",
        "/BookingRequests.html",
        "/DownloadReceipt.html",
        "/EditProperty.html",
        "/Enquiries.html",
        "/ForgotPassword.html",
        "/Login.html",
        "/Logout.html",
        "/Maintenance.html",
        "/Messages.html",
        "/MyProperties.html",
        "/Payments.html",
        "/Profile.html",
        "/Register.html",
        "/ViewBooking.html",
        "/ViewMaintenance.html",
        "/ViewPayment.html",
        "/ViewProperty.html"
})
public class AdminPageServlet extends HttpServlet {

    private static final Map<String, String> PAGES = new HashMap<String, String>();

    static {
        PAGES.put("/DashboardServlet", "Dashboard.html");
        PAGES.put("/AddPropertyPageServlet", "AddProperty.html");
        PAGES.put("/BookingDetailsServlet", "BookingDetails.html");
        PAGES.put("/BookingRequestsPageServlet", "BookingRequests.html");
        PAGES.put("/DownloadReceiptServlet", "DownloadReceipt.html");
        PAGES.put("/EditPropertyPageServlet", "EditProperty.html");
        PAGES.put("/EnquiriesPageServlet", "Enquiries.html");
        PAGES.put("/ForgotPasswordPageServlet", "ForgotPassword.html");
        PAGES.put("/LoginPageServlet", "Login.html");
        PAGES.put("/LogoutPageServlet", "Logout.html");
        PAGES.put("/MaintenancePageServlet", "Maintenance.html");
        PAGES.put("/MessagesPageServlet", "Messages.html");
        PAGES.put("/MyPropertiesPageServlet", "MyProperties.html");
        PAGES.put("/PaymentsPageServlet", "Payments.html");
        PAGES.put("/ProfilePageServlet", "Profile.html");
        PAGES.put("/RegisterPageServlet", "Register.html");
        PAGES.put("/ViewBookingServlet", "ViewBooking.html");
        PAGES.put("/ViewMaintenanceServlet", "ViewMaintenance.html");
        PAGES.put("/ViewPaymentServlet", "ViewPayment.html");
        PAGES.put("/ViewPropertyServlet", "ViewProperty.html");
        PAGES.put("/Dashboard.html", "Dashboard.html");
        PAGES.put("/AddProperty.html", "AddProperty.html");
        PAGES.put("/BookingDetails.html", "BookingDetails.html");
        PAGES.put("/BookingRequests.html", "BookingRequests.html");
        PAGES.put("/DownloadReceipt.html", "DownloadReceipt.html");
        PAGES.put("/EditProperty.html", "EditProperty.html");
        PAGES.put("/Enquiries.html", "Enquiries.html");
        PAGES.put("/ForgotPassword.html", "ForgotPassword.html");
        PAGES.put("/Login.html", "Login.html");
        PAGES.put("/Logout.html", "Logout.html");
        PAGES.put("/Maintenance.html", "Maintenance.html");
        PAGES.put("/Messages.html", "Messages.html");
        PAGES.put("/MyProperties.html", "MyProperties.html");
        PAGES.put("/Payments.html", "Payments.html");
        PAGES.put("/Profile.html", "Profile.html");
        PAGES.put("/Register.html", "Register.html");
        PAGES.put("/ViewBooking.html", "ViewBooking.html");
        PAGES.put("/ViewMaintenance.html", "ViewMaintenance.html");
        PAGES.put("/ViewPayment.html", "ViewPayment.html");
        PAGES.put("/ViewProperty.html", "ViewProperty.html");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String page = PAGES.get(request.getServletPath());
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/Admin/web/jsp/" + page);
    }
}
