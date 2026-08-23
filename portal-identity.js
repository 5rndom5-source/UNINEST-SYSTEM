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

  var avatarSelector = ".admin-avatar, .avatar-sm, .avatar, #profileInitials, #profileAvatar, #profileAvatarLarge, [data-user-initials]";

  function setAvatarFallback(value) {
    document.querySelectorAll(avatarSelector).forEach(function (host) {
      host.dataset.avatarFallback = value;
      if (!host.querySelector("img[data-profile-image]")) {
        host.textContent = value;
      }
    });
  }

  function applyProfileImage() {
    var url = contextPath() + "/ProfileImageServlet?v=" + Date.now();
    document.querySelectorAll(avatarSelector).forEach(function (host) {
      var image = document.createElement("img");
      image.alt = "Profile picture";
      image.src = url;
      image.dataset.profileImage = "true";
      image.style.width = "100%";
      image.style.height = "100%";
      image.style.objectFit = "cover";
      image.style.borderRadius = "50%";
      image.style.display = "block";
      image.addEventListener("load", function () {
        host.style.overflow = "hidden";
        host.replaceChildren(image);
        host.classList.add("has-profile-image");
      });
      image.addEventListener("error", function () {
        if (!host.querySelector("img[data-profile-image]")) {
          host.textContent = host.dataset.avatarFallback || "UN";
          host.classList.remove("has-profile-image");
        }
      });
    });
  }

  async function uploadProfileImage(form) {
    if (form.dataset.uploading === "true") return;

    var input = form.querySelector('input[type="file"][name="photo"]');
    var message = form.querySelector("[data-profile-image-message]");
    var submit = form.querySelector('button[type="submit"]');
    var file = input && input.files ? input.files[0] : null;
    if (!file) {
      if (message) message.textContent = "Choose a picture first";
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      if (message) message.textContent = "Profile pictures must be 5 MB or smaller";
      return;
    }

    form.dataset.uploading = "true";
    if (submit) submit.disabled = true;
    if (message) message.textContent = "Uploading picture...";
    try {
      var response = await fetch(contextPath() + "/ProfileImageServlet", {
        method: "POST",
        credentials: "same-origin",
        body: new FormData(form),
        headers: { Accept: "application/json" }
      });
      var result = await response.json().catch(function () {
        return {};
      });
      if (!response.ok) throw new Error(result.message || "Profile picture could not be updated");
      if (message) message.textContent = result.message || "Profile picture updated";
      applyProfileImage();
      form.reset();
      document.dispatchEvent(new CustomEvent("uninest:profile-image-updated"));
    } catch (error) {
      if (message) message.textContent = error.message;
    } finally {
      form.dataset.uploading = "false";
      if (submit) submit.disabled = false;
    }
  }

  function bindProfileImageForms() {
    document.querySelectorAll("[data-profile-image-form]").forEach(function (form) {
      form.addEventListener("submit", function (event) {
        event.preventDefault();
        uploadProfileImage(form);
      });
      var input = form.querySelector('input[type="file"][name="photo"]');
      if (input) {
        input.addEventListener("change", function () {
          if (input.files && input.files.length) uploadProfileImage(form);
        });
      }
    });
  }

  function applyIdentity(user) {
    var displayName = user.fullName || "UniNest User";
    var displayRole = user.role ? user.role.charAt(0).toUpperCase() + user.role.slice(1) : "User";
    setAvatarFallback(initials(displayName));
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

  function bindLogoutLinks() {
    var logoutUrl = contextPath() + "/LogoutServlet?portal=" + encodeURIComponent(portal().role);
    document.querySelectorAll("a").forEach(function (link) {
      if (link.textContent.trim().toLowerCase() === "logout") {
        link.href = logoutUrl;
      }
    });
  }

  async function loadIdentity() {
    ensureLogo();
    bindLogoutLinks();
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
