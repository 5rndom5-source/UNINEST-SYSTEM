(function () {
  "use strict";

  function contextPath() {
    if (window.location.protocol === "file:") return "http://localhost:8080/uninest";
    var match = window.location.pathname.match(/^\/[^/]+/);
    return match ? match[0] : "";
  }

  function initials(name) {
    var parts = String(name || "").trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return "UN";
    return (parts[0].charAt(0) + (parts.length > 1 ? parts[parts.length - 1].charAt(0) : ""))
      .toUpperCase();
  }

  function setText(selector, value) {
    document.querySelectorAll(selector).forEach(function (element) {
      element.textContent = value;
    });
  }

  function setValue(selector, value) {
    document.querySelectorAll(selector).forEach(function (element) {
      element.value = value || "";
    });
  }

  function applyProfileImage() {
    var url = contextPath() + "/ProfileImageServlet?v=" + Date.now();
    document.querySelectorAll(".admin-avatar, .avatar-sm, .avatar, #profileInitials, #profileAvatar, #profileAvatarLarge, [data-user-initials]").forEach(function (host) {
      var image = document.createElement("img");
      image.alt = "Profile picture";
      image.src = url;
      image.style.width = "100%";
      image.style.height = "100%";
      image.style.objectFit = "cover";
      image.style.borderRadius = "50%";
      image.addEventListener("load", function () {
        host.textContent = "";
        host.appendChild(image);
      });
    });
  }

  function bindProfileImageForms() {
    document.querySelectorAll("[data-profile-image-form]").forEach(function (form) {
      form.addEventListener("submit", async function (event) {
        event.preventDefault();
        var message = form.querySelector("[data-profile-image-message]");
        try {
          var response = await fetch(contextPath() + "/ProfileImageServlet", {
            method: "POST",
            credentials: "same-origin",
            body: new FormData(form),
            headers: { Accept: "application/json" }
          });
          var result = await response.json();
          if (!response.ok) throw new Error(result.message || "Profile picture could not be updated");
          if (message) message.textContent = result.message;
          applyProfileImage();
          form.reset();
        } catch (error) {
          if (message) message.textContent = error.message;
        }
      });
    });
  }

  function applyIdentity(user) {
    var displayName = user.fullName || "UniNest User";
    var displayRole = user.role ? user.role.charAt(0).toUpperCase() + user.role.slice(1) : "User";
    setText(".admin-avatar, .avatar-sm, #profileInitials, #profileAvatar, #profileAvatarLarge, [data-user-initials]", initials(displayName));
    setText(".admin-profile-trigger > span:not(.admin-avatar), .admin-profile-trigger > strong, .profile-trigger > span:not(.avatar-sm), .profile-trigger > strong, .topbar-title > span, #profileName, #profileNameLabel, #profileCardName, [data-user-name]", displayName);
    setText("#profileCardEmail, [data-user-email]", user.email || "");
    setText("#profileCardPhone, [data-user-phone]", user.phone || "");
    setText("[data-user-role]", displayRole);
    setValue('input[name="fullName"]', displayName);
    setValue('input[name="email"]', user.email);
    setValue('input[name="phone"]', user.phone);
    window.uninestCurrentUser = user;
    applyProfileImage();
    document.dispatchEvent(new CustomEvent("uninest:identity-ready", { detail: user }));
  }

  function ensureLogo() {
    document.querySelectorAll(".brand, .sidebar-brand, .logo, .login-brand, .auth-brand").forEach(function (host) {
      if (host.querySelector("img")) return;
      var image = document.createElement("img");
      image.src = contextPath() + "/Admin/web/uninest-logo.png";
      image.alt = "UniNest";
      image.className = "uninest-shared-logo";
      image.style.width = "48px";
      image.style.height = "48px";
      image.style.objectFit = "contain";
      image.style.flex = "0 0 auto";
      host.insertBefore(image, host.firstChild);
    });
  }

  function portal() {
    var path = window.location.pathname.toLowerCase();
    if (path.indexOf("/landlord/") !== -1) return { role: "landlord", login: "/Landlord/login.html" };
    if (path.indexOf("/userdashboard/") !== -1) return { role: "student", login: "/createAccount/login.html" };
    return { role: "admin", login: "/Admin/web/jsp/Login.html" };
  }

  function redirectToLogin() {
    window.location.replace(contextPath() + portal().login);
  }

  async function loadIdentity() {
    ensureLogo();
    bindProfileImageForms();
    try {
      var response = await fetch(contextPath() + "/CurrentUserServlet", {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        cache: "no-store"
      });
      if (response.status === 401) {
        redirectToLogin();
        return;
      }
      if (!response.ok) throw new Error("Identity request failed");
      var user = await response.json();
      if (String(user.role || "").toLowerCase() !== portal().role) {
        redirectToLogin();
        return;
      }
      applyIdentity(user);
    } catch (error) {
      document.documentElement.dataset.identityStatus = "unavailable";
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", loadIdentity);
  } else {
    loadIdentity();
  }
})();
