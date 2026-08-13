# Page And Servlet Route Map

## Public Pages

| Page | Servlet | Method | Notes |
| --- | --- | --- | --- |
| all HTML pages | `/DatabaseStatusServlet` | `GET` | Returns live data-service status for the shared `system-status.js` script. |
| `HomePage/search.html` | `/PublicListingsServlet` | `GET` | Loads approved public listings with pagination. |
| `HomePage/home.html` | `/PublicListingsServlet` | `GET` | Loads featured approved listings. |
| `HomePage/contact.html` | `/PublicContactServlet` | `POST` | `name`, `email`, `subject`, `message` |
| public contact shortcut | `/ContactPageRedirectServlet` | `GET` | Redirects to `HomePage/contact.html`. |

## Account Pages

| Page | Servlet | Method | Form Names |
| --- | --- | --- | --- |
| `createAccount/index.html` | `/AccountRegisterServlet` | `POST` | `fullname`, `email`, `password`, `confirmPassword`, `role` |
| `createAccount/login.html` | `/AccountLoginServlet` | `POST` | `email`, `password`, `role` |

## Student Dashboard

| Page | Servlet | Method | Form Names |
| --- | --- | --- | --- |
| `UserDashboard/index.html` | `/StudentDashboardServlet?format=json` | `GET` | dashboard counts |
| `UserDashboard/listings.html` | `/PublicListingsServlet` | `GET` | approved listings |
| `UserDashboard/saved.html` | `/StudentFavoriteServlet` | `GET`, `POST` | `roomId`, `action` |
| `UserDashboard/requests.html` | `/StudentRequestServlet` | `GET`, `POST` | `roomId`, `requestType` |
| `UserDashboard/messages.html` | `/StudentMessageServlet` | `GET`, `POST` | `recipient`, `subject`, `message` |
| `UserDashboard/profile.html` | `/StudentProfileServlet` | `GET`, `POST` | `fullName`, `email`, `phone`, `institution`, `program`, `yearLevel`, `emergencyContact` |

## Landlord Portal

| Page | Servlet | Method | Form Names |
| --- | --- | --- | --- |
| `Landlord/listings.html` | `/LandlordListingsServlet` | `GET` | loads landlord listings |
| `Landlord/listings.html` | `/LandlordAddPropertyServlet` | `POST` | `propertyTitle`, `description`, `location`, `price`, `photos`, `amenities` |
| `Landlord/booking-requests.html` | `/BookingDecisionServlet` | `POST` | `bookingId`, `decision` |
| `Landlord/contact-landlord.html` | `/ContactLandlordServlet` | `POST` | `propertyTitle`, `studentName`, `studentEmail`, `message` |
| `Landlord/profile.html` | `/LandlordProfileServlet` | `POST` | `fullName`, `email`, `phone`, `mainArea`, `bio` |
| `Landlord/reviews.html` | `/ReviewResponseServlet` | `POST` | `reviewId`, `response` |
| `Landlord/logout.html` | `/LandlordLogoutServlet` | `GET` | ends landlord session |

## Admin Portal

| Page | Servlet | Method | Form Names |
| --- | --- | --- | --- |
| `Admin/web/jsp/Login.html` | `/LoginServlet` | `POST` | `email`, `password`, `role`, `remember` |
| `Admin/web/jsp/Register.html` | `/AdminRegisterRequestServlet` | `POST` | `fullName`, `email`, `reason` |
| `Admin/web/jsp/ForgotPassword.html` | `/ForgotPasswordServlet` | `POST` | `email` |
| `Admin/web/jsp/AddProperty.html` | `/AdminListingQueueServlet` | `GET` | listing verification queue |
| `Admin/web/jsp/EditProperty.html` | `/VerifyPropertyServlet` | `POST` | `propertyId`, `decision`, `adminNote` |
| `Admin/web/jsp/Profile.html` | `/ProfileServlet` | `POST` | `fullName`, `email`, `phone`, `role`, `adminNotes` |
| `Admin/web/jsp/Messages.html` | `/SendMessageServlet` | `POST` | `recipientId`, `message` |

## Admin Page Router

`AdminPageServlet` keeps old Admin page routes working.

For example, `/Dashboard.html` redirects to:

```text
/Admin/web/jsp/Dashboard.html
```

This allows older servlet redirects to keep working while the source project keeps only one real Admin page set.
