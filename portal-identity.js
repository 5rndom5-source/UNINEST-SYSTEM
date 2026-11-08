(function () {
  "use strict";
  var identityScript = document.currentScript;
  var applicationRoot = identityScript ? new URL(".", identityScript.src).pathname.replace(/\/$/, "") : "";
  var galleryScript = document.createElement("script");
  galleryScript.src = applicationRoot + "/property-gallery.js";
  document.head.appendChild(galleryScript);

  function contextPath() {
    if (window.location.protocol === "file:") return "http://localhost:8080/uninest";
    return applicationRoot;
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
      image.src = url;
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
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      if (message) message.textContent = 'Choose a JPG or PNG picture.';
      return;
    }

    form.dataset.uploading = "true";
    if (submit) submit.disabled = true;
    if (message) message.textContent = "Uploading picture...";
    try {
      // Resize phone photos before upload; the server still validates the decoded image.
      var bitmap = await createImageBitmap(file);
      var body = new FormData();
      try {
        var scale = Math.min(1, 1024 / Math.max(bitmap.width, bitmap.height));
        var canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(bitmap.width * scale));
        canvas.height = Math.max(1, Math.round(bitmap.height * scale));
        canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        var photo = await new Promise(function (resolve, reject) {
          canvas.toBlob(function (blob) { if (blob) resolve(blob); else reject(new Error('Could not process this picture. Choose another JPG or PNG.')); }, 'image/png');
        });
        body.append('photo', photo, 'profile.png');
      } finally { bitmap.close(); }
      if (!window.uninestCsrf) throw new Error('Security verification is unavailable. Reload the page before uploading.');
      var token = await window.uninestCsrf.token();
      var response = await fetch(contextPath() + "/ProfileImageServlet", {
        method: "POST",
        credentials: "same-origin",
        body: body,
        headers: { Accept: "application/json", 'X-CSRF-Token': token }
      });
      var result = await response.json().catch(function () {
        return {};
      });
      if (response.status === 401) throw new Error('Your session expired. Sign in again before uploading.');
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
    });
  }

  function applyIdentity(user, refreshFields) {
    bindSupportLinks(user);
    if (user.role === 'admin' && !document.querySelector('[data-admin-access]')) {
      var adminNav = document.querySelector('.sidebar-nav, .side-nav, .sidebar nav, .sidebar');
      if (adminNav) {
        var accessLink = document.createElement('a');
        accessLink.className = 'nav-item';
        accessLink.dataset.adminAccess = 'true';
        accessLink.href = contextPath() + '/AdminAccessServlet';
        accessLink.textContent = 'Admin access requests';
        adminNav.appendChild(accessLink);
        var usersLink = document.createElement('a');
        usersLink.className = 'nav-item' + (/\/Users\.html$/i.test(window.location.pathname) ? ' active' : '');
        usersLink.href = contextPath() + '/Admin/web/jsp/Users.html';
        usersLink.textContent = 'Users';
        adminNav.appendChild(usersLink);
      }
    }
    setupCloseAccount();
    if ((user.role === "student" || user.role === "landlord") && user.demoPayments && !document.querySelector('[data-demo-payments]')) {
      var nav = document.querySelector('.side-nav');
      if (nav) { var link = document.createElement('a'); link.className='nav-item'; link.dataset.demoPayments='true'; link.href=contextPath()+'/PaymentSimulationServlet'; link.textContent='Demo payments'; nav.appendChild(link); }
    }
    var displayName = user.fullName || "UniNest User";
    var displayRole = user.role ? user.role.charAt(0).toUpperCase() + user.role.slice(1) : "User";
    setAvatarFallback(initials(displayName));
    setText(".admin-profile-trigger > span:not(.admin-avatar), .admin-profile-trigger > strong, .profile-trigger > span:not(.avatar-sm), .profile-trigger > strong, .topbar-title > span, #profileName, #profileNameLabel, #profileCardName, [data-user-name]", displayName);
    setText("#profileCardEmail, [data-user-email]", user.email || "");
    setText("#profileCardPhone, [data-user-phone]", user.phone || "");
    setText("[data-user-role]", displayRole);
    if (refreshFields !== false) {
      setValue('input[name="fullName"]', displayName);
      setValue('input[name="email"]', user.email);
      setValue('input[name="phone"]', user.phone);
    }
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
    window.location.replace(contextPath() + "/createAccount/login.html?error=Please+sign+in+to+continue");
  }

  function bindLogoutLinks() {
    var logoutUrl = contextPath() + "/LogoutServlet";
    document.querySelectorAll("a").forEach(function (link) {
      if (link.textContent.trim().toLowerCase() === "logout") {
        link.href = logoutUrl;
      }
    });
  }

  function bindSupportLinks(user) {
    var paths = {
      student: "/UserDashboard/messages.html",
      landlord: "/Landlord/contact-landlord.html",
      admin: "/Admin/web/jsp/Messages.html"
    };
    var supportPath = paths[user.role];
    if (!supportPath) return;
    var root = contextPath();
    var destination = root + supportPath;
    var folder = destination.slice(0, destination.lastIndexOf("/") + 1);
    var storageKey = "uninest.supportReturn." + user.userId + "." + user.role;
    var onSupportPage = window.location.pathname === destination;
    document.querySelectorAll("a[href]").forEach(function (link) {
      var url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      if (url.pathname !== destination && !link.hasAttribute("data-support-link")) return;
      link.href = destination;
      if (link.dataset.supportBound) return;
      link.dataset.supportBound = "true";
      link.addEventListener("click", function () {
        if (onSupportPage) return;
        try {
          sessionStorage.setItem(storageKey, window.location.pathname + window.location.search + window.location.hash);
        } catch (error) { /* Navigation remains usable with browser storage disabled. */ }
      });
    });
    if (!onSupportPage || document.getElementById("supportReturnLink")) return;
    var fallback = root + (user.role === "admin" ? "/Admin/web/jsp/Dashboard.html" : "/" + (user.role === "student" ? "UserDashboard" : "Landlord") + "/index.html");
    var target = fallback;
    try {
      var stored = new URL(sessionStorage.getItem(storageKey) || fallback, window.location.origin);
      // Return only to an HTML page in this user's portal, never logout or an external URL.
      if (stored.origin === window.location.origin && stored.pathname.startsWith(folder)
          && stored.pathname.endsWith(".html") && stored.pathname !== destination
          && !/\/(login|logout|register|forgotpassword)\.html$/i.test(stored.pathname)) {
        target = stored.pathname + stored.search + stored.hash;
      }
    } catch (error) { }
    var main = document.querySelector("main, .main-content");
    if (!main) return;
    var back = document.createElement("a");
    back.id = "supportReturnLink";
    back.href = target;
    back.textContent = "Back to previous page";
    back.style.cssText = "display:inline-block;margin:0 0 16px;text-decoration:underline;";
    main.prepend(back);
  }

  // Adds "Close account" under the profile form on the student, landlord and admin profile pages.
  // Closing deactivates the account (it is never deleted) after the current password is confirmed.
  function setupCloseAccount() {
    if (document.querySelector("[data-close-account]")) return;
    var profileForm = document.querySelector('form[action$="ProfileServlet"]');
    if (!profileForm) return;
    var host = profileForm.closest("section, .panel") || profileForm;
    var card = document.createElement("section");
    card.dataset.closeAccount = "true";
    card.className = host.className;
    card.style.cssText = "margin-top:22px;border:1px solid #f1b5ae;";
    var heading = document.createElement("h2");
    heading.textContent = "Close account";
    heading.style.cssText = "margin:0 0 8px;font-size:18px;color:#b42318;";
    var text = document.createElement("p");
    text.textContent = "Closing your account signs you out and stops you signing in again. Your bookings, payments and messages are kept on record. Contact support if you want it reopened later.";
    text.style.cssText = "margin:0 0 14px;color:#475569;font-size:14px;line-height:1.5;";
    var form = document.createElement("form");
    form.style.cssText = "display:flex;flex-wrap:wrap;gap:10px;align-items:center;";
    var password = document.createElement("input");
    password.type = "password";
    password.name = "password";
    password.required = true;
    password.autocomplete = "current-password";
    password.placeholder = "Current password";
    password.setAttribute("aria-label", "Current password");
    password.style.cssText = "flex:1 1 220px;padding:10px 12px;border:1px solid #cbd5e1;border-radius:6px;";
    var submit = document.createElement("button");
    submit.type = "submit";
    submit.textContent = "Close my account";
    submit.style.cssText = "padding:10px 16px;border:0;border-radius:6px;background:#b42318;color:#fff;font-weight:700;cursor:pointer;";
    var status = document.createElement("p");
    status.setAttribute("role", "status");
    status.style.cssText = "flex-basis:100%;margin:4px 0 0;color:#b42318;font-size:13px;";
    form.append(password, submit, status);
    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      if (!window.confirm("Close your UniNest account? You will be signed out and cannot sign in again.")) return;
      submit.disabled = true;
      status.textContent = "";
      try {
        var response = await fetch(contextPath() + "/CloseAccountServlet", {
          method: "POST", credentials: "same-origin",
          headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({ password: password.value })
        });
        var result = await response.json().catch(function () { return {}; });
        if (response.ok && result.success) {
          window.location.replace(contextPath() + "/createAccount/login.html?success="
            + encodeURIComponent("Your account has been closed"));
          return;
        }
        status.textContent = result.message || "Your account could not be closed. Try again.";
      } catch (error) {
        status.textContent = error.message || "Your account could not be closed. Try again.";
      } finally {
        password.value = "";
        submit.disabled = false;
      }
    });
    card.append(heading, text, form);
    host.after(card);
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
      .uninest-notification-toast { position: fixed; z-index: 1200; right: 22px; bottom: 22px; width: min(340px, calc(100vw - 28px));
        display: grid; gap: 4px; padding: 14px 16px; border-left: 4px solid #075f59; border-radius: 8px;
        background: #fff; color: #1f2937; box-shadow: 0 18px 50px rgba(15,23,42,.25); font: 13px/1.45 Arial, sans-serif; }
      .uninest-notification-toast[hidden] { display: none; }
      .uninest-notification-toast span { color: #475569; }
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

    // The first load only records the newest id, so existing notifications do not pop up again.
    var newestNotificationId = null;
    var refreshing = false;
    window.uninestRefreshNotifications = async function () {
      if (refreshing) return;
      refreshing = true;
      try {
        var response = await fetch(contextPath() + "/NotificationServlet", {
          credentials: "same-origin", headers: { Accept: "application/json" }, cache: "no-store"
        });
        if (!response.ok) return;
        var data = await response.json();
        renderNotificationPanel(panel, data);
        announceNewNotifications(data.notifications || []);
      } catch (error) {
        // Keep portal navigation usable if notifications are temporarily unavailable.
      } finally {
        refreshing = false;
      }
    };

    function announceNewNotifications(items) {
      var newest = items.reduce(function (max, item) { return Math.max(max, Number(item.id) || 0); }, 0);
      var previous = newestNotificationId;
      newestNotificationId = Math.max(previous || 0, newest);
      if (previous === null) return;
      var fresh = items.filter(function (item) { return Number(item.id) > previous && !item.read; });
      if (!fresh.length) return;
      showNotificationToast(fresh);
      // Pages such as Messages listen for this to reload their own lists.
      window.dispatchEvent(new CustomEvent("uninest:notification", { detail: { notifications: fresh } }));
    }
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
    // Near-live updates while the tab is visible; hidden tabs pause to spare the server.
    window.setInterval(function () {
      if (!document.hidden) window.uninestRefreshNotifications();
    }, NOTIFICATION_POLL_MS);
    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) window.uninestRefreshNotifications();
    });
  }

  var NOTIFICATION_POLL_MS = 5000;

  function showNotificationToast(items) {
    var toast = document.getElementById("uninest-notification-toast");
    if (!toast) {
      toast = document.createElement("div");
      toast.id = "uninest-notification-toast";
      toast.className = "uninest-notification-toast";
      toast.setAttribute("role", "status");
      toast.setAttribute("aria-live", "polite");
      document.body.appendChild(toast);
    }
    toast.replaceChildren();
    var title = document.createElement("strong");
    var message = document.createElement("span");
    if (items.length === 1) {
      title.textContent = items[0].title || "New notification";
      message.textContent = items[0].message || "";
    } else {
      title.textContent = items.length + " new notifications";
      message.textContent = items[0].title || "";
    }
    toast.append(title, message);
    toast.hidden = false;
    window.clearTimeout(showNotificationToast.timer);
    showNotificationToast.timer = window.setTimeout(function () { toast.hidden = true; }, 6000);
    playNotificationSound();
  }

  // Browsers only allow sound after the user has interacted with the page; failures stay silent.
  function playNotificationSound() {
    try {
      var AudioContext = window.AudioContext || window.webkitAudioContext;
      if (!AudioContext) return;
      var audio = playNotificationSound.context || (playNotificationSound.context = new AudioContext());
      if (audio.state === "suspended") audio.resume();
      var tone = audio.createOscillator();
      var volume = audio.createGain();
      tone.frequency.value = 880;
      volume.gain.setValueAtTime(0.08, audio.currentTime);
      volume.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + 0.35);
      tone.connect(volume).connect(audio.destination);
      tone.start();
      tone.stop(audio.currentTime + 0.35);
    } catch (error) { /* Sound is optional. */ }
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
  window.addEventListener("pageshow", function (event) {
    if (event.persisted) window.location.reload();
  });
  document.addEventListener("visibilitychange", async function () {
    if (document.visibilityState !== "visible") return;
    try {
      var response = await fetch(contextPath() + "/CurrentUserServlet", { credentials: "same-origin", cache: "no-store" });
      if (response.status === 401) { redirectToLogin(); return; }
      if (response.ok) {
        var user = await response.json();
        if (user.role !== portal().role) { window.location.reload(); return; }
        // Refresh renamed accounts by ID without replacing another tab's unsaved form edits.
        applyIdentity(user, false);
      }
    } catch (error) { /* A network outage does not mean the session has ended. */ }
  });
})();
