(function () {
  "use strict";
  if (window.uninestCsrf) return;
  var base = new URL(".", document.currentScript.src);
  var experienceStyle = document.createElement('link');
  experienceStyle.rel = 'stylesheet'; experienceStyle.href = new URL('experience.css', base);
  document.head.appendChild(experienceStyle);
  var experienceScript = document.createElement('script');
  experienceScript.src = new URL('experience.js', base);
  document.head.appendChild(experienceScript);
  var rawFetch = window.fetch.bind(window);
  var pending = null;
  var allowedForms = new WeakSet();
  var busyForms = new WeakSet();
  function unsafe(method) { return !["GET", "HEAD", "OPTIONS"].includes(String(method).toUpperCase()); }
  function internal(url) { return url.origin === base.origin && url.pathname.startsWith(base.pathname); }
  async function token() {
    if (!pending) pending = rawFetch(new URL("CsrfTokenServlet", base), {
      credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json" }
    }).then(async function (response) {
      if (!response.ok) throw new Error("Unable to verify your session. Reload the page or sign in again; your request was not sent.");
      var data = await response.json();
      if (!/^[A-Za-z0-9_-]{43}$/.test(data.token || "")) throw new Error("Security verification failed. Your request was not sent.");
      return data.token;
    });
    try { return await pending; } finally { pending = null; }
  }
  window.uninestCsrf = { token: token };
  window.fetch = async function (input, init) {
    var request = new Request(input, init);
    if (!internal(new URL(request.url)) || !unsafe(request.method)) return rawFetch(request);
    var headers = new Headers(request.headers);
    headers.set("X-CSRF-Token", await token());
    // Never retry a write automatically: a failed response may still have committed.
    return rawFetch(new Request(request, { headers: headers }));
  };
  function showError(form, error) {
    var message = form.querySelector("[data-csrf-error]");
    if (!message) {
      message = document.createElement("p");
      message.dataset.csrfError = "true";
      message.setAttribute("role", "alert");
      form.prepend(message);
    }
    message.textContent = error.message;
  }
  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    var submitter = event.submitter;
    var action = new URL(submitter && submitter.hasAttribute("formaction") ? submitter.formAction : form.action, window.location.href);
    var method = submitter && submitter.hasAttribute("formmethod") ? submitter.formMethod : form.method;
    if (!internal(action) || !unsafe(method) || method === "dialog") return;
    if (allowedForms.has(form)) { allowedForms.delete(form); return; }
    event.preventDefault();
    event.stopImmediatePropagation();
    if (busyForms.has(form)) return;
    busyForms.add(form);
    token().then(function (value) {
      var field = form.querySelector('input[name="_csrf"]');
      if (!field) {
        field = document.createElement("input");field.type = "hidden";field.name = "_csrf";form.appendChild(field);
      }
      field.value = value;
      allowedForms.add(form);
      try { form.requestSubmit(submitter || undefined); } finally { allowedForms.delete(form); }
    }).catch(function (error) { showError(form, error); }).finally(function () { busyForms.delete(form); });
  }, true);
  document.addEventListener("click", function (event) {
    var link = event.target.closest && event.target.closest("a[href]");
    if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    var url = new URL(link.href, window.location.href);
    if (!internal(url) || !/\/(LogoutServlet|LandlordLogoutServlet)$/.test(url.pathname)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (link.dataset.signingOut) return;
    link.dataset.signingOut = "true";
    window.fetch(url, { method: "POST", credentials: "same-origin" }).then(function (response) {
      if (!response.ok) throw new Error("Sign-out could not be completed. Please try again.");
      window.location.assign(new URL("createAccount/login.html?signedOut=1", base).href);
    }).catch(function (error) {
      link.removeAttribute("data-signing-out");
      window.alert(error.message);
    });
  }, true);
})();
