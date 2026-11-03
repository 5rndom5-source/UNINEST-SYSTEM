(function () {
  const CHECK_INTERVAL_MS = 30000;
  const PORTAL_PATH_PATTERN = /\/(Admin|Landlord|UserDashboard|createAccount)\//;
  const STYLE_ID = "uninest-system-status-style";
  const root = new URL('.', document.currentScript.src).pathname.replace(/\/$/, '');
  let checking = false;

  const contextPath = () => {
    if (window.location.protocol === "file:") {
      return "http://localhost:8080/uninest";
    }

    return root;
  };

  const shouldShowStatus = () => {
    if (document.body && document.body.hasAttribute("data-show-system-status")) {
      return true;
    }
    return PORTAL_PATH_PATTERN.test(window.location.pathname);
  };

  const addStyles = () => {
    if (document.getElementById(STYLE_ID)) return;

    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .system-status-chip {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        min-height: 36px;
        padding: 0 12px;
        border: 1px solid rgba(15, 23, 42, 0.12);
        border-radius: 999px;
        background: #ffffff;
        color: #334155;
        font-size: 12px;
        font-weight: 700;
        box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
        white-space: nowrap;
      }

      .system-status-chip::before {
        content: "";
        width: 8px;
        height: 8px;
        border-radius: 999px;
        background: #94a3b8;
      }

      .system-status-chip[data-status="online"]::before {
        background: #0f766e;
      }

      .system-status-chip[data-status="offline"]::before {
        background: #b45309;
      }

      .system-status-chip[data-status="checking"]::before {
        background: #2563eb;
      }

      .system-status-floating {
        position: fixed;
        right: 18px;
        bottom: 18px;
        z-index: 80;
      }

      @media (max-width: 720px) {
        .system-status-chip {
          min-height: 32px;
          padding: 0 10px;
          font-size: 11px;
        }

        .system-status-floating {
          right: 12px;
          bottom: 12px;
        }
      }
    `;
    document.head.appendChild(style);
  };

  const findHost = () => {
    return document.querySelector("[data-system-status]")
      || document.querySelector(".admin-header-actions")
      || document.querySelector(".topbar-actions")
      || document.querySelector(".top-actions")
      || document.querySelector(".header-right")
      || document.querySelector(".header-bar");
  };

  const ensureChip = () => {
    let chip = document.querySelector("[data-system-status-chip]");
    if (chip) return chip;

    if (!shouldShowStatus()) return null;

    addStyles();
    chip = document.createElement("span");
    chip.className = "system-status-chip";
    chip.dataset.systemStatusChip = "true";
    chip.dataset.status = "checking";
    chip.textContent = "Checking data services";
    chip.setAttribute("role", "status");
    chip.setAttribute("aria-live", "polite");

    const host = findHost();
    if (host) {
      host.insertBefore(chip, host.firstChild);
    } else {
      chip.classList.add("system-status-floating");
      document.body.appendChild(chip);
    }

    return chip;
  };

  const updateDocumentState = (status) => {
    document.documentElement.dataset.databaseStatus = status.status;
    document.documentElement.dataset.databaseConnected = String(status.connected);
    window.dispatchEvent(new CustomEvent("uninest:database-status", { detail: status }));
  };

  const setChipState = (chip, status) => {
    if (!chip) return;
    chip.dataset.status = status.status;
    chip.textContent = status.label;
    chip.title = status.checkedAt ? `Last checked ${status.checkedAt}` : status.label;
  };

  const checkStatus = async () => {
    if (document.hidden || checking) return;
    checking = true;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10000);
    const chip = ensureChip();
    try {
      const response = await fetch(`${contextPath()}/DatabaseStatusServlet`, {
        signal: controller.signal,
        cache: "no-store",
        headers: { Accept: "application/json" }
      });
      const data = await response.json();
      if (!response.ok) throw new Error('Data services unavailable');
      const status = {
        connected: Boolean(data.connected),
        status: data.connected ? "online" : "offline",
        label: data.label || (data.connected ? "Data services online" : "Data services unavailable"),
        checkedAt: data.checkedAt || ""
      };
      updateDocumentState(status);
      setChipState(chip, status);
    } catch (error) {
      const status = {
        connected: false,
        status: "offline",
        label: "Data services unavailable",
        checkedAt: ""
      };
      updateDocumentState(status);
      setChipState(chip, status);
    } finally {
      clearTimeout(timeout);
      checking = false;
    }
  };

  document.addEventListener("DOMContentLoaded", () => {
    checkStatus();
    window.setInterval(checkStatus, CHECK_INTERVAL_MS);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) checkStatus(); });
    window.addEventListener('online', checkStatus);
  });
})();
