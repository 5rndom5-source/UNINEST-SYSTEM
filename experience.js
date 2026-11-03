(function () {
  'use strict';
  function ready() {
    const filters = document.querySelector('.search-filter-panel');
    if (filters) {
      const mobile = matchMedia('(max-width: 700px)');
      const resize = () => { filters.open = !mobile.matches; };
      resize(); mobile.addEventListener('change', resize);
    }
    const main = document.querySelector('main, .main-content, .content, .search-container, .hero');
    if (main && !document.querySelector('.uninest-skip-link')) {
      if (!main.id) main.id = 'uninest-main';
      if (!main.hasAttribute('tabindex')) main.tabIndex = -1;
      const skip = document.createElement('a');
      skip.className = 'uninest-skip-link'; skip.href = '#' + main.id;
      skip.textContent = 'Skip to content';
      skip.addEventListener('click', () => main.focus());
      document.body.prepend(skip);
    }
    document.querySelectorAll('nav a[href], .sidebar a[href]').forEach(link => {
      const target = new URL(link.href, location.href);
      if (target.origin === location.origin && target.pathname === location.pathname) link.setAttribute('aria-current', 'page');
    });
    document.querySelectorAll('.profile-menu, .admin-profile-menu').forEach((host, index) => {
      const trigger = host.querySelector('.profile-trigger, .admin-profile-trigger');
      const menu = host.querySelector('.profile-dropdown, .admin-profile-dropdown');
      if (!trigger || !menu || host.hasAttribute('data-accessible-menu')) return;
      host.dataset.accessibleMenu = 'true';
      if (!menu.id) menu.id = 'uninest-profile-actions-' + index;
      trigger.setAttribute('aria-controls', menu.id);
      function toggle(open) {
        host.classList.toggle('uninest-menu-open', open);
        trigger.setAttribute('aria-expanded', String(open));
      }
      toggle(false);
      trigger.addEventListener('click', () => toggle(!host.classList.contains('uninest-menu-open')));
      host.addEventListener('keydown', event => {
        if (event.key === 'Escape') { toggle(false); trigger.focus(); }
        if (event.key === 'ArrowDown' && event.target === trigger) {
          event.preventDefault(); toggle(true); menu.querySelector('a, button')?.focus();
        }
      });
      host.addEventListener('focusout', event => { if (!host.contains(event.relatedTarget)) toggle(false); });
      document.addEventListener('click', event => { if (!host.contains(event.target)) toggle(false); });
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready);
  else ready();
})();
