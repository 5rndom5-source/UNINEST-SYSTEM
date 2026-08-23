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

  function notificationButton() {
    return document.querySelector(
      '.admin-notification, .icon-btn[aria-label*="Notification"], .icon-button[aria-label*="notification"]'
    );
  }

  function installNotificationStyles() {
    if (document.getElementById("uninest-notification-styles")) return;
    var style = document.createElement("style");
    style.id = "uninest-notification-styles";
    style.textContent = `
      .uninest-notification-button { position: relative !important; }
      .uninest-notification-badge {
        position: absolute; top: -5px; right: -5px; min-width: 19px; height: 19px;
        display: grid; place-items: center; padding: 0 5px; border: 2px solid #fff;
        border-radius: 999px; background: #b42318; color: #fff; font: 700 11px/1 Arial, sans-serif;
      }
      .uninest-notification-panel {
        position: fixed; z-index: 1100; top: 72px; right: 22px; width: min(390px, calc(100vw - 28px));
        max-height: min(620px, calc(100vh - 92px)); overflow: auto; border: 1px solid #d7e0e7;
        border-radius: 8px; background: #fff; color: #1f2937; box-shadow: 0 18px 50px rgba(15,23,42,.2);
      }
      .uninest-notification-panel[hidden] { display: none; }
      .uninest-notification-head { position: sticky; top: 0; display: flex; justify-content: space-between;
        align-items: center; gap: 12px; padding: 14px 16px; border-bottom: 1px solid #e5e7eb; background: #fff; }
      .uninest-notification-head strong { font-size: 15px; }
      .uninest-notification-head button, .uninest-notification-delete {
        border: 0; background: transparent; color: #075f59; font: inherit; font-size: 12px; font-weight: 700; cursor: pointer;
      }
      .uninest-notification-list { display: grid; }
      .uninest-notification-item { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 8px;
        padding: 13px 16px; border-bottom: 1px solid #edf0f2; background: #fff; }
      .uninest-notification-item.unread { background: #eef8f7; }
      .uninest-notification-open { border: 0; background: transparent; color: inherit; text-align: left; cursor: pointer; padding: 0; }
      .uninest-notification-open strong, .uninest-notification-open span, .uninest-notification-open small { display: block; }
      .uninest-notification-open strong { font-size: 13px; margin-bottom: 4px; }
      .uninest-notification-open span { color: #475569; font-size: 12px; line-height: 1.45; }
      .uninest-notification-open small { color: #768396; font-size: 11px; margin-top: 5px; }
      .uninest-notification-empty { padding: 28px 18px; color: #64748b; text-align: center; font-size: 13px; }
      @media (max-width: 640px) { .uninest-notification-panel { top: 62px; right: 14px; } }
    `;
    document.head.appendChild(style);
  }

  function notificationUrl(actionUrl) {
    var target = String(actionUrl || "").trim();
    if (!target) return "";
    if (/^https?:/i.test(target)) return "";
    if (target.charAt(0) === "/") return contextPath() + target;
    if (target.indexOf("/") >= 0) return contextPath() + "/" + target.replace(/^\/+/, "");
    return new URL(target, window.location.href).href;
  }

  async function notificationAction(action, notificationId) {
    var body = new URLSearchParams({ action: action });
    if (notificationId) body.set("notificationId", notificationId);
    var response = await fetch(contextPath() + "/NotificationServlet", {
      method: "POST",
      credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
      body: body.toString()
    });
    if (!response.ok) {
      var result = await response.json().catch(function () { return {}; });
      throw new Error(result.message || "Notification could not be updated");
    }
  }

  function renderNotificationPanel(panel, data) {
    var list = panel.querySelector(".uninest-notification-list");
    var badge = notificationButton() && notificationButton().querySelector(".uninest-notification-badge");
    var unread = Number(data.unreadCount || 0);
    if (badge) {
      badge.textContent = unread > 99 ? "99+" : String(unread);
      badge.hidden = unread === 0;
    }
    list.replaceChildren();
    var items = data.notifications || [];
    if (!items.length) {
      var empty = document.createElement("div");
      empty.className = "uninest-notification-empty";
      empty.textContent = "No notifications";
      list.appendChild(empty);
      return;
    }
    items.forEach(function (item) {
      var row = document.createElement("article");
      row.className = "uninest-notification-item" + (item.read ? "" : " unread");
      var open = document.createElement("button");
      open.type = "button";
      open.className = "uninest-notification-open";
      var title = document.createElement("strong");
      title.textContent = item.title || "Notification";
      var message = document.createElement("span");
      message.textContent = item.message || "";
      var date = document.createElement("small");
      date.textContent = item.createdAt || "";
      open.append(title, message, date);
      open.addEventListener("click", async function () {
        try { await notificationAction("read", item.id); } catch (error) { return; }
        var target = notificationUrl(item.actionUrl);
        if (target) window.location.href = target;
        else window.uninestRefreshNotifications();
      });
      var remove = document.createElement("button");
      remove.type = "button";
      remove.className = "uninest-notification-delete";
      remove.textContent = "Delete";
      remove.setAttribute("aria-label", "Delete notification");
      remove.addEventListener("click", async function () {
        try {
          await notificationAction("delete", item.id);
          window.uninestRefreshNotifications();
        } catch (error) {
          remove.textContent = "Failed";
        }
      });
      row.append(open, remove);
      list.appendChild(row);
    });
  }

  function setupNotifications() {
    var button = notificationButton();
    if (!button || button.dataset.notificationReady === "true") return;
    installNotificationStyles();
    button.dataset.notificationReady = "true";
    button.classList.add("uninest-notification-button");
    var badge = document.createElement("span");
    badge.className = "uninest-notification-badge";
    badge.hidden = true;
    button.appendChild(badge);

    var panel = document.createElement("section");
    panel.className = "uninest-notification-panel";
    panel.hidden = true;
    panel.setAttribute("aria-label", "Notifications");
    var head = document.createElement("div");
    head.className = "uninest-notification-head";
    var heading = document.createElement("strong");
    heading.textContent = "Notifications";
    var markAll = document.createElement("button");
    markAll.type = "button";
    markAll.textContent = "Mark all read";
    head.append(heading, markAll);
    var list = document.createElement("div");
    list.className = "uninest-notification-list";
    panel.append(head, list);
    document.body.appendChild(panel);

    window.uninestRefreshNotifications = async function () {
      try {
        var response = await fetch(contextPath() + "/NotificationServlet", {
          credentials: "same-origin", headers: { Accept: "application/json" }, cache: "no-store"
        });
        if (!response.ok) return;
        renderNotificationPanel(panel, await response.json());
      } catch (error) {
        // Keep portal navigation usable if notifications are temporarily unavailable.
      }
    };
    button.addEventListener("click", function (event) {
      event.preventDefault();
      panel.hidden = !panel.hidden;
      if (!panel.hidden) window.uninestRefreshNotifications();
    });
    markAll.addEventListener("click", async function () {
      try {
        await notificationAction("readAll");
        window.uninestRefreshNotifications();
      } catch (error) {
        markAll.textContent = "Try again";
      }
    });
    document.addEventListener("click", function (event) {
      if (!panel.hidden && !panel.contains(event.target) && !button.contains(event.target)) panel.hidden = true;
    });
    window.uninestRefreshNotifications();
    window.setInterval(window.uninestRefreshNotifications, 30000);
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
      setupNotifications();
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
