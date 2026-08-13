(function () {
  const serverOrigin = "http://localhost:8080";
  const contextPath = "/uninest";
  const searchPageSize = 6;
  const fallbackImages = [
    "listing-rondebosch.png",
    "listing-cbd-room.png",
    "listing-bellville-residence.png"
  ];

  let searchPage = 1;

  const apiBase = () => {
    if (window.location.protocol === "file:") {
      return serverOrigin + contextPath;
    }
    return window.location.pathname.indexOf(contextPath + "/") === 0 ? contextPath : "";
  };

  const normalizeImage = (image, index) => {
    const fallback = fallbackImages[index % fallbackImages.length];
    if (!image) return fallback;
    if (image.indexOf("http://") === 0 || image.indexOf("https://") === 0) return image;
    if (image.charAt(0) === "/" && window.location.protocol === "file:") {
      return serverOrigin + image;
    }
    return image;
  };

  const escapeText = (value) =>
    String(value || "").replace(/[&<>"']/g, (item) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    }[item]));

  const tagsMarkup = (tags) => {
    const safeTags = Array.isArray(tags) && tags.length ? tags.slice(0, 4) : ["Available"];
    return safeTags.map((tag) => `<span>${escapeText(tag)}</span>`).join("");
  };

  const homeCard = (listing, index) => `
    <div class="card">
      <div class="card-image">
        <img src="${escapeText(normalizeImage(listing.image, index))}" alt="${escapeText(listing.title)}" />
      </div>
      <div class="card-content">
        <h3>${escapeText(listing.title)}</h3>
        <p class="location"><i class="fa-solid fa-location-dot"></i> ${escapeText(listing.area)}</p>
        <p class="price">${escapeText(listing.price)}</p>
        <div class="tags">${tagsMarkup(listing.tags)}</div>
        <button type="button" onclick="window.location.href='search.html'">View Details</button>
      </div>
    </div>
  `;

  const searchCard = (listing, index) => `
    <div class="listing-card">
      <div class="listing-image">
        <img src="${escapeText(normalizeImage(listing.image, index))}" alt="${escapeText(listing.title)}" />
        <i class="fa-regular fa-heart"></i>
      </div>
      <div class="listing-content">
        <h2>${escapeText(listing.title)}</h2>
        <p class="listing-price">${escapeText(listing.price)}</p>
        <p class="listing-location"><i class="fa-solid fa-location-dot"></i> ${escapeText(listing.area)}</p>
        <div class="listing-tags">${tagsMarkup(listing.tags)}</div>
        <button type="button" onclick="window.location.href='../UserDashboard/listings.html'">View Details</button>
      </div>
    </div>
  `;

  const fetchListings = async (page, size) => {
    const response = await fetch(`${apiBase()}/PublicListingsServlet?page=${page}&size=${size}`, {
      headers: { Accept: "application/json" }
    });
    if (!response.ok) return null;
    return response.json();
  };

  const renderHome = async () => {
    const container = document.querySelector(".featured-cards");
    if (!container) return;

    const data = await fetchListings(1, 3);
    const listings = data && Array.isArray(data.listings) ? data.listings : [];
    if (!listings.length) return;
    container.innerHTML = listings.map(homeCard).join("");
  };

  const ensureSearchPagination = () => {
    let controls = document.getElementById("searchPagination");
    if (controls) return controls;

    const results = document.querySelector(".results");
    controls = document.createElement("div");
    controls.id = "searchPagination";
    controls.className = "pagination";
    controls.setAttribute("aria-label", "Listing pages");
    results.appendChild(controls);
    return controls;
  };

  const pageItems = (current, total) => {
    if (total <= 7) {
      return Array.from({ length: total }, (_, index) => index + 1);
    }

    const items = [1];
    if (current > 3) items.push("...");
    for (let page = Math.max(2, current - 1); page <= Math.min(total - 1, current + 1); page++) {
      items.push(page);
    }
    if (current < total - 2) items.push("...");
    items.push(total);
    return items;
  };

  const renderPagination = (metadata) => {
    const controls = ensureSearchPagination();
    const totalPages = Number(metadata.totalPages || 0);
    if (totalPages <= 1) {
      controls.innerHTML = "";
      controls.hidden = true;
      return;
    }

    controls.hidden = false;
    const current = Number(metadata.page || 1);
    const buttons = [
      `<button type="button" data-page="${Math.max(1, current - 1)}" ${current === 1 ? "disabled" : ""}>Previous</button>`,
      ...pageItems(current, totalPages).map((item) => {
        if (item === "...") return '<span class="page-ellipsis">...</span>';
        return `<button type="button" data-page="${item}" class="${item === current ? "active" : ""}">${item}</button>`;
      }),
      `<button type="button" data-page="${Math.min(totalPages, current + 1)}" ${current === totalPages ? "disabled" : ""}>Next</button>`
    ];
    controls.innerHTML = buttons.join("");
  };

  const renderSearch = async (page) => {
    const container = document.querySelector(".results");
    if (!container) return;

    const data = await fetchListings(page, searchPageSize);
    const listings = data && Array.isArray(data.listings) ? data.listings : [];
    if (!listings.length && Number(data?.total || 0) === 0) {
      return;
    }

    searchPage = Number(data.page || page);
    container.querySelectorAll(":scope > .listing-card").forEach((card) => card.remove());
    listings.forEach((listing, index) => {
      container.insertAdjacentHTML("beforeend", searchCard(listing, index));
    });
    renderPagination(data);
  };

  const bindSearchPagination = () => {
    const results = document.querySelector(".results");
    if (!results) return;

    results.addEventListener("click", (event) => {
      const button = event.target.closest("#searchPagination button[data-page]");
      if (!button || button.disabled) return;
      const nextPage = Number(button.dataset.page || searchPage);
      renderSearch(nextPage);
      document.querySelector(".results-top")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  };

  const loadListings = async () => {
    try {
      bindSearchPagination();
      await Promise.all([renderHome(), renderSearch(searchPage)]);
    } catch (error) {
    console.warn("Public listings could not be loaded.", error);
    }
  };

  document.addEventListener("DOMContentLoaded", loadListings);
})();
