(function () {
  "use strict";

  var context = window.location.pathname.match(/^\/[^/]+/)?.[0] || "";
  var view = document.body.dataset.adminView || "";
  var records = [];

  function textCell(row, value) {
    var cell = document.createElement("td");
    cell.textContent = value == null || value === "" ? "-" : value;
    row.appendChild(cell);
    return cell;
  }

  function statusCell(row, status) {
    var cell = document.createElement("td");
    var badge = document.createElement("span");
    badge.className = "badge badge-" + String(status || "pending").toLowerCase().replace(/_/g, "-");
    badge.textContent = display(status || "pending");
    cell.appendChild(badge);
    row.appendChild(cell);
  }

  function display(value) {
    return String(value || "").replace(/_/g, " ").replace(/\b\w/g, function (letter) {
      return letter.toUpperCase();
    });
  }

  function emptyRow(body, columns, message) {
    body.replaceChildren();
    var row = document.createElement("tr");
    var cell = textCell(row, message);
    cell.colSpan = columns;
    cell.className = "empty-record";
    body.appendChild(row);
  }

  async function getJson(url) {
    var response = await fetch(url, { headers: { Accept: "application/json" }, cache: "no-store" });
    var data = await response.json().catch(function () { return {}; });
    if (response.status === 401) {
      window.location.replace(context + "/Admin/web/jsp/Login.html");
      return null;
    }
    if (!response.ok) throw new Error(data.error || "Records could not be loaded");
    return data;
  }

  function actionForm(servlet, idName, id, options, extraName, extraPlaceholder, selectedValue) {
    var form = document.createElement("form");
    form.className = "admin-inline-action";
    form.method = "post";
    form.action = context + "/" + servlet;
    var hidden = document.createElement("input");
    hidden.type = "hidden";
    hidden.name = idName;
    hidden.value = id;
    var select = document.createElement("select");
    select.name = "status";
    options.forEach(function (option) {
      var item = document.createElement("option");
      item.value = option;
      item.textContent = display(option);
      item.selected = option === selectedValue;
      select.appendChild(item);
    });
    form.append(hidden, select);
    if (extraName) {
      var extra = document.createElement("input");
      extra.name = extraName;
      extra.placeholder = extraPlaceholder || "Optional note";
      form.appendChild(extra);
    }
    var submit = document.createElement("button");
    submit.type = "submit";
    submit.className = "btn btn-primary";
    submit.textContent = "Update";
    form.appendChild(submit);
    return form;
  }

  function renderDashboard(data) {
    Object.entries(data.counts || {}).forEach(function (entry) {
      document.querySelectorAll('[data-admin-count="' + entry[0] + '"]').forEach(function (host) {
        host.textContent = entry[1];
      });
    });
    var body = document.querySelector("#submissionTable tbody");
    records = data.listings || [];
    if (!records.length) return emptyRow(body, 7, "No pending property submissions.");
    body.replaceChildren();
    records.forEach(function (listing) {
      var row = document.createElement("tr");
      row.dataset.search = Object.values(listing).join(" ").toLowerCase();
      textCell(row, listing.property);
      textCell(row, listing.landlord);
      textCell(row, listing.location);
      textCell(row, listing.price);
      textCell(row, "Details, images, ownership");
      statusCell(row, "pending");
      var action = document.createElement("td");
      var link = document.createElement("a");
      link.className = "table-link";
      link.href = "EditProperty.html?id=" + encodeURIComponent(listing.id);
      link.textContent = "Review";
      action.appendChild(link);
      row.appendChild(action);
      body.appendChild(row);
    });
  }

  // Mirrors BookingTransitions on the server, which still enforces these rules.
  var BOOKING_NEXT_STATES = {
    pending: ["confirmed", "cancelled"],
    confirmed: ["completed", "cancelled"]
  };

  function renderBookings(data) {
    records = data.bookings || [];
    var body = document.querySelector("#bookingTable tbody");
    var empty = document.getElementById("bookingEmpty");
    if (empty) empty.hidden = records.length > 0;
    if (!records.length) return emptyRow(body, 7, "No booking requests found.");
    body.replaceChildren();
    records.forEach(function (booking) {
      var row = document.createElement("tr");
      row.dataset.search = Object.values(booking).join(" ").toLowerCase();
      row.dataset.status = booking.status;
      textCell(row, booking.student + "\n" + booking.studentEmail);
      textCell(row, booking.property + "\n" + booking.location);
      textCell(row, booking.checkIn + (booking.checkOut ? " to " + booking.checkOut : ""));
      textCell(row, booking.landlord);
      statusCell(row, booking.status);
      textCell(row, booking.reason);
      var action = document.createElement("td");
      var next = BOOKING_NEXT_STATES[booking.status] || [];
      if (next.length) {
        action.appendChild(actionForm("BookingActionServlet", "bookingId", booking.id,
          next, "reason", "Cancellation reason", next[0]));
      } else {
        action.textContent = "No further action";
      }
      row.appendChild(action);
      body.appendChild(row);
    });
    filterTable("#bookingTable", "bookingSearch", "bookingStatusFilter");
  }

  function renderPayments(data) {
    records = data.payments || [];
    document.querySelectorAll("[data-payment-total]").forEach(function (host) { host.textContent = records.length; });
    document.querySelectorAll("[data-payment-completed]").forEach(function (host) {
      host.textContent = records.filter(function (item) { return item.status === "completed"; }).length;
    });
    document.querySelectorAll("[data-payment-attention]").forEach(function (host) {
      host.textContent = records.filter(function (item) { return item.status === "failed" || item.status === "overdue"; }).length;
    });
    var body = document.querySelector("#recordTable tbody");
    if (!records.length) return emptyRow(body, 8, "No payment records found.");
    body.replaceChildren();
    records.forEach(function (payment) {
      var row = document.createElement("tr");
      row.dataset.search = Object.values(payment).join(" ").toLowerCase();
      textCell(row, payment.student);
      textCell(row, payment.property);
      textCell(row, payment.landlord);
      textCell(row, payment.amount);
      textCell(row, payment.dueDate);
      textCell(row, payment.receipt);
      statusCell(row, payment.status);
      var action = document.createElement("td");
      action.appendChild(actionForm("PaymentsServlet", "paymentId", payment.id,
        ["pending", "completed", "failed", "overdue"], "receiptNumber", "Receipt number", payment.status));
      row.appendChild(action);
      body.appendChild(row);
    });
    filterTable("#recordTable", "recordSearch");
  }

  function renderMaintenance(data) {
    records = data.requests || [];
    var body = document.querySelector("#maintenanceTable tbody");
    var empty = document.getElementById("maintenanceEmpty");
    if (empty) empty.hidden = records.length > 0;
    if (!records.length) return emptyRow(body, 7, "No maintenance requests found.");
    body.replaceChildren();
    records.forEach(function (request) {
      var row = document.createElement("tr");
      row.dataset.search = Object.values(request).join(" ").toLowerCase();
      textCell(row, request.student);
      textCell(row, request.property + "\n" + request.location);
      textCell(row, request.issue);
      statusCell(row, request.priority);
      statusCell(row, request.status);
      textCell(row, request.notes);
      var action = document.createElement("td");
      action.appendChild(actionForm("MaintenanceServlet", "requestId", request.id,
        ["pending", "in_progress", "resolved", "cancelled"], "resolutionNotes", "Resolution notes", request.status));
      row.appendChild(action);
      body.appendChild(row);
    });
    filterTable("#maintenanceTable", "maintenanceSearch");
  }

  function renderListings(data) {
    records = data.listings || [];
    var body = document.querySelector("#queueTable tbody");
    if (!records.length) return emptyRow(body, 7, "No property submissions found.");
    body.replaceChildren();
    records.forEach(function (listing) {
      var row = document.createElement("tr");
      row.dataset.search = Object.values(listing).join(" ").toLowerCase();
      row.dataset.status = listing.status;
      row.dataset.area = String(listing.location || "").toLowerCase();
      textCell(row, listing.title);
      textCell(row, listing.landlordName + "\n" + listing.landlordEmail);
      textCell(row, listing.location);
      textCell(row, listing.price);
      textCell(row, listing.createdAt);
      statusCell(row, listing.status);
      var action = document.createElement("td");
      var link = document.createElement("a");
      link.className = "action-link";
      link.href = "EditProperty.html?id=" + encodeURIComponent(listing.id);
      link.textContent = "Review";
      action.appendChild(link);
      row.appendChild(action);
      body.appendChild(row);
    });
    filterListings();
  }

  function filterTable(tableSelector, searchId, statusId) {
    var table = document.querySelector(tableSelector);
    if (!table) return;
    var term = (document.getElementById(searchId)?.value || "").trim().toLowerCase();
    var status = statusId ? document.getElementById(statusId)?.value || "all" : "all";
    table.querySelectorAll("tbody tr").forEach(function (row) {
      row.hidden = (term && !(row.dataset.search || row.textContent.toLowerCase()).includes(term))
        || (status !== "all" && row.dataset.status !== status);
    });
  }

  function filterListings() {
    var term = (document.getElementById("queueSearch")?.value || "").trim().toLowerCase();
    var status = document.getElementById("statusFilter")?.value || "all";
    var area = (document.getElementById("areaFilter")?.value || "all").toLowerCase();
    document.querySelectorAll("#queueTable tbody tr").forEach(function (row) {
      var matchesArea = area === "all" || (row.dataset.area || "").includes(area);
      row.hidden = (term && !(row.dataset.search || row.textContent.toLowerCase()).includes(term))
        || (status !== "all" && row.dataset.status !== status) || !matchesArea;
    });
  }

  function bindFilters() {
    document.getElementById("dashboardSearch")?.addEventListener("input", function () {
      filterTable("#submissionTable", "dashboardSearch");
    });
    document.getElementById("bookingSearch")?.addEventListener("input", function () {
      filterTable("#bookingTable", "bookingSearch", "bookingStatusFilter");
    });
    document.getElementById("bookingStatusFilter")?.addEventListener("change", function () {
      filterTable("#bookingTable", "bookingSearch", "bookingStatusFilter");
    });
    document.getElementById("recordSearch")?.addEventListener("input", function () {
      filterTable("#recordTable", "recordSearch");
    });
    document.getElementById("maintenanceSearch")?.addEventListener("input", function () {
      filterTable("#maintenanceTable", "maintenanceSearch");
    });
    ["queueSearch", "statusFilter", "areaFilter"].forEach(function (id) {
      var element = document.getElementById(id);
      if (element) element.addEventListener(element.tagName === "INPUT" ? "input" : "change", filterListings);
    });
  }

  async function load() {
    try {
      if (view === "listings") renderListings(await getJson(context + "/AdminListingQueueServlet?size=25&status=all"));
      else {
        var data = await getJson(context + "/AdminOperationsServlet?view=" + encodeURIComponent(view || "dashboard"));
        if (!data) return;
        if (view === "bookings") renderBookings(data);
        else if (view === "payments") renderPayments(data);
        else if (view === "maintenance") renderMaintenance(data);
        else renderDashboard(data);
      }
    } catch (error) {
      var body = document.querySelector("table tbody");
      if (body) emptyRow(body, document.querySelectorAll("table thead th").length || 1, error.message);
    }
  }

  bindFilters();
  load();
})();
