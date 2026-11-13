(function () {
  const galleryScript = document.createElement('script');
  galleryScript.src = new URL('../property-gallery.js', document.currentScript.src).href;
  document.head.appendChild(galleryScript);
  const serverOrigin = "http://localhost:8080";
  const contextPath = "/uninest";
  const searchPageSize = 6;
  let searchPage = 1;
  let searchRequest = 0;
  let searchController;

  const apiBase = () => {
    if (window.location.protocol === "file:") return serverOrigin + contextPath;
    return window.location.pathname.indexOf(contextPath + "/") === 0 ? contextPath : "";
  };

  const escapeText = (value) => String(value || "").replace(/[&<>"']/g, (item) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[item]));

  const normalizeImage = (image) => {
    if (!image) return "../createAccount/uninest-logo.png";
    if (/^https?:\/\//.test(image)) return image;
    if (image.charAt(0) === "/" && window.location.protocol === "file:") return serverOrigin + image;
    return image;
  };

  const tagsMarkup = (tags) => {
    const values = Array.isArray(tags) && tags.length ? tags.slice(0, 4) : ["Available"];
    return values.map((tag) => `<span>${escapeText(tag)}</span>`).join("");
  };

  const ratingMarkup = (listing) => {
    const average = Number(listing.ratingAverage || 0);
    const count = Number(listing.ratingCount || 0);
    const stars = Array.from({ length: 5 }, (_, index) =>
      `<span class="${index < Math.round(average) ? "filled" : ""}">&#9733;</span>`).join("");
    return `<div class="listing-rating" aria-label="${average.toFixed(1)} out of 5 from ${count} reviews">
      <span class="rating-stars">${stars}</span>
      <span>${count ? `${average.toFixed(1)} (${count})` : "No reviews yet"}</span>
    </div>`;
  };

  const homeCard = (listing) => `
    <article class="card">
      <div class="card-image"><img loading="lazy" decoding="async" src="${escapeText(normalizeImage(listing.image))}" alt="${escapeText(listing.title)}" /></div>
      <div class="card-content">
        <h3>${escapeText(listing.title)}</h3>
        <p class="location"><i class="fa-solid fa-location-dot"></i> ${escapeText(listing.area)}</p>
        <p class="price">${escapeText(listing.price)}</p>
        ${ratingMarkup(listing)}
        <div class="tags">${tagsMarkup(listing.tags)}</div>
        <button type="button" data-gallery="${escapeText(listing.id)}">View listing</button>
      </div>
    </article>`;

  const searchCard = (listing) => `
    <article class="listing-card">
      <div class="listing-image"><img loading="lazy" decoding="async" src="${escapeText(normalizeImage(listing.image))}" alt="${escapeText(listing.title)}" /></div>
      <div class="listing-content">
        <h2>${escapeText(listing.title)}</h2>
        <p class="listing-price">${escapeText(listing.price)}</p>
        <p class="listing-location"><i class="fa-solid fa-location-dot"></i> ${escapeText(listing.area)}</p>
        ${ratingMarkup(listing)}
        <div class="listing-tags">${tagsMarkup(listing.tags)}</div>
        <p class="listing-description">${escapeText(listing.description)}</p>
        <button type="button" data-gallery="${escapeText(listing.id)}">View listing</button>
      </div>
    </article>`;

  const activeFilters = () => {
    const params = new URLSearchParams();
    const search = document.getElementById("publicSearchInput")?.value.trim();
    const location = document.getElementById("locationFilter")?.value;
    const minPrice = document.getElementById("minPriceFilter")?.value;
    const maxPrice = document.getElementById("maxPriceFilter")?.value;
    const types = Array.from(document.querySelectorAll('input[name="type"]:checked'), item => item.value);
    const sort = document.getElementById("resultsSort")?.value || document.getElementById("publicSort")?.value;
    if (search) params.set("search", search);
    if (location) params.set("location", location);
    if (minPrice) params.set("minPrice", minPrice);
    if (maxPrice) params.set("maxPrice", maxPrice);
    types.forEach(type => params.append("type", type));
    if (sort) params.set("sort", sort);
    document.querySelectorAll('input[name="facility"]:checked').forEach((item) => params.append("facility", item.value));
    return params;
  };

  const fetchListings = async (page, size, filters = new URLSearchParams(), signal) => {
    const params = new URLSearchParams(filters);
    params.set("page", String(page));
    params.set("size", String(size));
    const response = await fetch(`${apiBase()}/PublicListingsServlet?${params.toString()}`, {
      signal,
      headers: { Accept: "application/json" }
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "Listings could not be loaded");
    return data;
  };

  const renderHome = async () => {
    const container = document.querySelector(".featured-cards");
    if (!container || document.getElementById("publicListingsState")) return;
    try {
      const data = await fetchListings(1, 3);
      const listings = Array.isArray(data.listings) ? data.listings : [];
      container.innerHTML = listings.length
        ? listings.map(homeCard).join("")
        : '<p class="listings-state">No approved accommodation is available yet.</p>';
    } catch (error) {
      container.innerHTML = `<p class="listings-state error">${escapeText(error.message)}</p>`;
    }
  };

  const ensureSearchPagination = () => {
    let controls = document.getElementById("searchPagination");
    if (controls) return controls;
    controls = document.createElement("div");
    controls.id = "searchPagination";
    controls.className = "pagination";
    controls.setAttribute("aria-label", "Listing pages");
    document.querySelector(".results")?.appendChild(controls);
    return controls;
  };

  const renderPagination = (data) => {
    const controls = ensureSearchPagination();
    const total = Number(data.totalPages || 0);
    const current = Number(data.page || 1);
    if (total <= 1) {
      controls.hidden = true;
      controls.innerHTML = "";
      return;
    }
    controls.hidden = false;
    const pages = [...new Set([1, current - 1, current, current + 1, total])].filter(page => page >= 1 && page <= total).sort((a,b) => a-b);
    controls.innerHTML = [
      `<button type="button" data-page="${current - 1}" ${current === 1 ? "disabled" : ""}>Previous</button>`,
      ...pages.map((page, index) => `${index && page - pages[index-1] > 1 ? '<span aria-hidden="true">...</span>' : ''}<button type="button" data-page="${page}" ${page === current ? 'aria-current="page"' : ''} aria-label="Page ${page}" class="${page === current ? "active" : ""}">${page}</button>`),
      `<button type="button" data-page="${current + 1}" ${current === total ? "disabled" : ""}>Next</button>`
    ].join("");
    controls.parentElement.appendChild(controls);
  };

  const populateLocations = (locations) => {
    const select = document.getElementById("locationFilter");
    if (!select || select.dataset.loaded === "true") return;
    (locations || []).forEach((location) => {
      if (Array.from(select.options).some(option => option.value.toLowerCase() === String(location).toLowerCase())) return;
      const option = document.createElement("option");
      option.value = location;
      option.textContent = location;
      select.appendChild(option);
    });
    select.dataset.loaded = "true";
    const initial = new URLSearchParams(location.search).get('location');
    if (initial && Array.from(select.options).some(option => option.value === initial)) select.value = initial;
  };

  const renderSearch = async (page = 1) => {
    const results = document.querySelector(".results");
    const state = document.getElementById("publicListingsState");
    if (!results || !state) return;
    const requestId = ++searchRequest;
    searchController?.abort();
    searchController = new AbortController();
    results.setAttribute('aria-busy', 'true');
    document.getElementById('searchRetry')?.remove();
    const pagination = document.getElementById('searchPagination');
    if (pagination) pagination.hidden = true;
    const filters = activeFilters();
    const min = filters.get('minPrice'), max = filters.get('maxPrice');
    if ((min && (!Number.isFinite(Number(min)) || Number(min) < 0)) || (max && (!Number.isFinite(Number(max)) || Number(max) < 0)) || (min && max && Number(min) > Number(max))) {
      state.textContent = 'Enter a valid price range: minimum rent must not exceed maximum rent.';
      state.classList.add('error'); results.setAttribute('aria-busy', 'false'); return;
    }
    const url = new URL(location.href); url.search = filters.toString();
    if (page > 1) url.searchParams.set('page', page);
    history.replaceState(null, '', url);
    state.hidden = false;
    state.textContent = "Loading approved accommodation...";
    state.classList.remove("error");
    results.querySelectorAll(":scope > .listing-card").forEach((card) => card.remove());
    try {
      const data = await fetchListings(page, searchPageSize, filters, searchController.signal);
      if (requestId !== searchRequest) return;
      populateLocations(data.locations);
      searchPage = Number(data.page || page);
      const listings = Array.isArray(data.listings) ? data.listings : [];
      state.textContent = listings.length ? `${data.total} accommodation option${data.total === 1 ? "" : "s"} found` : "No accommodation matches these filters.";
      listings.forEach((listing) => results.insertAdjacentHTML("beforeend", searchCard(listing)));
      renderPagination(data);
    } catch (error) {
      if (requestId !== searchRequest || error.name === 'AbortError') return;
      state.textContent = 'Listings could not be loaded. Check your connection and try again.';
      state.classList.add("error");
      const retry = document.createElement('button'); retry.id = 'searchRetry'; retry.className = 'search-retry'; retry.type = 'button'; retry.textContent = 'Try again';
      retry.addEventListener('click', () => renderSearch(page)); state.after(retry);
    } finally {
      if (requestId === searchRequest) results.setAttribute('aria-busy', 'false');
    }
  };

  const applyQueryToControls = () => {
    const query = new URLSearchParams(window.location.search);
    const input = document.getElementById("publicSearchInput");
    if (input) input.value = query.get("search") || "";
    for (const [id, key] of [['minPriceFilter','minPrice'],['maxPriceFilter','maxPrice'],['publicSort','sort'],['resultsSort','sort']]) {
      const field = document.getElementById(id); if (field && query.has(key)) field.value = query.get(key);
    }
    const area = query.get('location'); const select = document.getElementById('locationFilter');
    if (area && select) { const option = new Option(area, area, true, true); select.appendChild(option); }
    document.querySelectorAll('input[name="facility"]').forEach(item => { item.checked = query.getAll('facility').includes(item.value); });
    const selectedTypes = query.getAll("type");
    document.querySelectorAll('input[name="type"]').forEach(input => { input.checked = selectedTypes.includes(input.value); });
  };

  const resetFilters = () => {
    ["publicSearchInput", "minPriceFilter", "maxPriceFilter"].forEach((id) => {
      const item = document.getElementById(id);
      if (item) item.value = "";
    });
    const location = document.getElementById("locationFilter");
    if (location) location.value = "";
    document.querySelectorAll('input[name="type"], input[name="facility"]').forEach((item) => { item.checked = false; });
    const sort = document.getElementById("resultsSort");
    const topSort = document.getElementById("publicSort");
    if (sort) sort.value = "recent";
    if (topSort) topSort.value = "recent";
    history.replaceState(null, "", "search.html");
    renderSearch(1);
  };

  const bindActions = () => {
    document.getElementById("homeSearchForm")?.addEventListener("submit", (event) => {
      event.preventDefault();
      const value = document.getElementById("homeSearchInput")?.value.trim() || "";
      window.location.href = `search.html${value ? `?search=${encodeURIComponent(value)}` : ""}`;
    });
    document.querySelectorAll("[data-home-type]").forEach((button) => button.addEventListener("click", () => {
      const type = button.dataset.homeType;
      window.location.href = `search.html${type ? `?type=${encodeURIComponent(type)}` : ""}`;
    }));
    document.getElementById("publicSearchButton")?.addEventListener("click", () => renderSearch(1));
    document.getElementById("applyFilters")?.addEventListener("click", () => renderSearch(1));
    document.getElementById("resetFilters")?.addEventListener("click", resetFilters);
    document.getElementById("clearFilters")?.addEventListener("click", resetFilters);
    document.getElementById("resultsSort")?.addEventListener("change", (event) => {
      const top = document.getElementById("publicSort");
      if (top) top.value = event.target.value;
      renderSearch(1);
    });
    document.getElementById("publicSort")?.addEventListener("change", (event) => {
      const resultsSort = document.getElementById("resultsSort");
      if (resultsSort) resultsSort.value = event.target.value;
      renderSearch(1);
    });
    document.getElementById("publicSearchInput")?.addEventListener("keydown", (event) => {
      if (event.key === "Enter") renderSearch(1);
    });
    document.addEventListener("click", (event) => {
      const page = event.target.closest("#searchPagination button[data-page]");
      if (page && !page.disabled) renderSearch(Number(page.dataset.page || searchPage));
      const listing = event.target.closest("[data-view-listing]");
      if (listing) window.location.href = `../UserDashboard/listings.html?room=${encodeURIComponent(listing.dataset.viewListing)}`;
    });
  };

  document.addEventListener("DOMContentLoaded", async () => {
    applyQueryToControls();
    bindActions();
    const initialPage = Number(new URLSearchParams(location.search).get('page'));
    await Promise.all([renderHome(), renderSearch(Number.isInteger(initialPage) && initialPage > 0 ? initialPage : 1)]);
  });
})();
