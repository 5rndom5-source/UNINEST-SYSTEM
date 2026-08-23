const SERVER_ORIGIN = "http://localhost:8080";
const CONTEXT_PATH = "/uninest";
const API_BASE = window.location.protocol === "file:"
  ? SERVER_ORIGIN + CONTEXT_PATH
  : (window.location.pathname.indexOf(CONTEXT_PATH + "/") === 0 ? CONTEXT_PATH : "");

const LISTING_PAGE_SIZE = 6;
const FALLBACK_IMAGES = [
  "../HomePage/listing-rondebosch.png",
  "../HomePage/listing-cbd-room.png",
  "../HomePage/listing-bellville-residence.png"
];

const state = {
  page: 1,
  totalPages: 0,
  listings: [],
  favoriteIds: new Set()
};
let searchTimer;

const pageName = document.body.dataset.page || "overview";
const byId = (id) => document.getElementById(id);
const endpoint = (path) => API_BASE + path;

const toast = (message) => {
  const item = byId("toast");
  if (!item || !message) return;
  item.textContent = message;
  item.classList.add("show");
  window.clearTimeout(toast.timer);
  toast.timer = window.setTimeout(() => item.classList.remove("show"), 2400);
};

const showQueryMessage = () => {
  const params = new URLSearchParams(window.location.search);
  const message = params.get("success") || params.get("error");
  if (message) toast(message);
};

const initialsFromName = (name) => {
  const parts = String(name || "Student Account").trim().split(/\s+/).slice(0, 2);
  return parts.map((part) => part.charAt(0)).join("").toUpperCase() || "ST";
};

const normalizeImage = (image, index = 0) => {
  if (!image) return FALLBACK_IMAGES[index % FALLBACK_IMAGES.length];
  if (image.indexOf("http://") === 0 || image.indexOf("https://") === 0) return image;
  if (image.charAt(0) === "/" && window.location.protocol === "file:") return SERVER_ORIGIN + image;
  return image;
};

const getJson = async (url) => {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (response.redirected) {
    window.location.href = response.url;
    return null;
  }
  if (response.status === 401) {
    window.location.href = endpoint("/createAccount/login.html");
    return null;
  }
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.message || data.error || "Request failed");
  }
  return data;
};

const postForm = async (url, values) => {
  const body = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => body.append(key, value == null ? "" : value));

  const response = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body
  });

  if (response.redirected) {
    window.location.href = response.url;
    return null;
  }
  if (response.status === 401) {
    window.location.href = endpoint("/createAccount/login.html");
    return null;
  }

  const data = await response.json();
  if (!response.ok || data.status === "error") {
    throw new Error(data.message || "Request failed");
  }
  return data;
};

const setActiveNavigation = () => {
  document.querySelectorAll("[data-page-link]").forEach((link) => {
    link.classList.toggle("active", link.dataset.pageLink === pageName);
  });
};

const setProfileLabels = (profile = {}) => {
  const name = profile.fullName || profile.name || "Student Account";
  const email = profile.email || "No email saved yet";
  const phone = profile.phone || "No phone saved yet";
  const initials = initialsFromName(name);

  if (byId("profileNameLabel")) byId("profileNameLabel").textContent = name;
  [byId("profileInitials"), byId("profileAvatarLarge")].filter(Boolean).forEach((avatar) => {
    avatar.dataset.avatarFallback = initials;
    if (!avatar.querySelector("img[data-profile-image]")) avatar.textContent = initials;
  });
  if (byId("profileCardName")) byId("profileCardName").textContent = name;
  if (byId("profileCardEmail")) byId("profileCardEmail").textContent = email;
  if (byId("profileCardPhone")) byId("profileCardPhone").textContent = phone;
};

const loadProfileSummary = async () => {
  try {
    const data = await getJson(endpoint("/StudentProfileServlet?format=json"));
    if (data && data.profile) setProfileLabels(data.profile);
  } catch {
    setProfileLabels({});
  }
};

const loadCounts = async () => {
  const saved = byId("savedCount");
  const requests = byId("requestCount");
  const messages = byId("messageCount");
  if (!saved && !requests && !messages) return;

  try {
    const data = await getJson(endpoint("/StudentDashboardServlet?format=json"));
    if (!data) return;
    if (saved) saved.textContent = data.savedCount || 0;
    if (requests) requests.textContent = data.requestCount || 0;
    if (messages) messages.textContent = data.messageCount || 0;
  } catch {
    if (saved) saved.textContent = "0";
    if (requests) requests.textContent = "0";
    if (messages) messages.textContent = "0";
  }
};

const createEmpty = (message) => {
  const empty = document.createElement("div");
  empty.className = "empty-state";
  empty.textContent = message;
  return empty;
};

const addTag = (wrapper, text) => {
  if (!text) return;
  const span = document.createElement("span");
  span.textContent = text;
  wrapper.appendChild(span);
};

const addRating = (wrapper, listing) => {
  const average = Number(listing.ratingAverage || 0);
  const count = Number(listing.ratingCount || 0);
  const rating = document.createElement("div");
  rating.className = "listing-rating";
  rating.setAttribute("aria-label", `${average.toFixed(1)} out of 5 from ${count} reviews`);
  const stars = document.createElement("span");
  stars.className = "rating-stars";
  stars.textContent = "★★★★★";
  stars.style.setProperty("--rating-width", `${Math.max(0, Math.min(100, average * 20))}%`);
  const summary = document.createElement("span");
  summary.textContent = count ? `${average.toFixed(1)} (${count})` : "No reviews yet";
  rating.append(stars, summary);
  wrapper.appendChild(rating);
};

const makeReviewForm = (roomId) => {
  const form = document.createElement("form");
  form.className = "review-form";
  form.dataset.reviewForm = roomId;
  const fieldset = document.createElement("fieldset");
  const legend = document.createElement("legend");
  legend.textContent = "Rate this accommodation";
  const stars = document.createElement("div");
  stars.className = "review-stars";
  for (let value = 5; value >= 1; value -= 1) {
    const input = document.createElement("input");
    input.type = "radio";
    input.name = `rating-${roomId}`;
    input.id = `rating-${roomId}-${value}`;
    input.value = String(value);
    input.required = true;
    const label = document.createElement("label");
    label.htmlFor = input.id;
    label.title = `${value} star${value === 1 ? "" : "s"}`;
    label.textContent = "★";
    stars.append(input, label);
  }
  const comment = document.createElement("textarea");
  comment.name = "reviewText";
  comment.maxLength = 1000;
  comment.rows = 2;
  comment.placeholder = "Share a short comment (optional)";
  const submit = document.createElement("button");
  submit.type = "submit";
  submit.className = "listing-action";
  submit.textContent = "Submit Review";
  fieldset.append(legend, stars, comment, submit);
  form.appendChild(fieldset);
  return form;
};

const makeListingCard = (listing, context = "browse", index = 0) => {
  const id = String(listing.id || "");
  const card = document.createElement("article");
  card.className = "listing-card";
  card.dataset.search = [
    listing.title,
    listing.area,
    listing.type,
    (listing.tags || []).join(" ")
  ].join(" ").toLowerCase();

  const image = document.createElement("div");
  image.className = "listing-image";
  const img = document.createElement("img");
  img.src = normalizeImage(listing.image, index);
  img.alt = listing.title || "Accommodation listing";
  img.onerror = () => {
    img.onerror = null;
    img.src = FALLBACK_IMAGES[index % FALLBACK_IMAGES.length];
  };
  image.appendChild(img);

  const body = document.createElement("div");
  body.className = "listing-body";

  const title = document.createElement("h3");
  title.textContent = listing.title || "Student accommodation";

  const price = document.createElement("p");
  price.className = "listing-price";
  price.textContent = listing.price || "Price not set";

  const meta = document.createElement("div");
  meta.className = "listing-meta";
  addTag(meta, listing.area || "Cape Town");
  addTag(meta, listing.type || "Accommodation");

  const tags = document.createElement("div");
  tags.className = "listing-tags";
  (listing.tags || []).forEach((tag) => addTag(tags, tag));

  const rating = document.createElement("div");
  addRating(rating, listing);

  const actions = document.createElement("div");
  actions.className = "card-actions";

  const favoriteButton = document.createElement("button");
  favoriteButton.className = "listing-action secondary";
  favoriteButton.type = "button";
  favoriteButton.dataset.favoriteAction = context === "saved" ? "remove" : "save";
  favoriteButton.dataset.roomId = id;
  favoriteButton.textContent = state.favoriteIds.has(id) || context === "saved" ? "Saved" : "Save";

  const requestButton = document.createElement("button");
  requestButton.className = "listing-action";
  requestButton.type = "button";
  requestButton.dataset.roomRequest = id;
  requestButton.textContent = "Request Viewing";

  actions.append(favoriteButton, requestButton);

  if (context === "saved") {
    const removeButton = document.createElement("button");
    removeButton.className = "listing-action danger";
    removeButton.type = "button";
    removeButton.dataset.favoriteAction = "remove";
    removeButton.dataset.roomId = id;
    removeButton.textContent = "Remove";
    actions.appendChild(removeButton);
  }

  body.append(title, price, meta, rating, tags, actions);
  if (context === "browse") body.appendChild(makeReviewForm(id));
  card.append(image, body);
  return card;
};

const renderListingGrid = (container, listings, emptyMessage, context) => {
  if (!container) return;
  container.innerHTML = "";
  if (!listings.length) {
    container.appendChild(createEmpty(emptyMessage));
    return;
  }
  listings.forEach((listing, index) => container.appendChild(makeListingCard(listing, context, index)));
};

const pageItems = (current, total) => {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1);
  const items = [1];
  if (current > 3) items.push("...");
  for (let page = Math.max(2, current - 1); page <= Math.min(total - 1, current + 1); page += 1) {
    items.push(page);
  }
  if (current < total - 2) items.push("...");
  items.push(total);
  return items;
};

const renderPagination = () => {
  const controls = byId("browsePagination");
  if (!controls || state.totalPages <= 1) {
    if (controls) controls.hidden = true;
    return;
  }

  controls.hidden = false;
  controls.innerHTML = "";

  const addButton = (label, page, options = {}) => {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.dataset.listingPage = page;
    if (options.active) button.classList.add("active");
    if (options.disabled) button.disabled = true;
    controls.appendChild(button);
  };

  addButton("Previous", Math.max(1, state.page - 1), { disabled: state.page === 1 });
  pageItems(state.page, state.totalPages).forEach((item) => {
    if (item === "...") {
      const span = document.createElement("span");
      span.className = "page-ellipsis";
      span.textContent = "...";
      controls.appendChild(span);
    } else {
      addButton(String(item), item, { active: item === state.page });
    }
  });
  addButton("Next", Math.min(state.totalPages, state.page + 1), { disabled: state.page === state.totalPages });
};

const addFilterOption = (select, value) => {
  if (!select || !value) return;
  const exists = Array.from(select.options).some((option) => option.value === value);
  if (exists) return;
  const option = document.createElement("option");
  option.value = value;
  option.textContent = value;
  select.appendChild(option);
};

const syncFilters = (locations = [], types = []) => {
  const area = byId("areaFilter");
  const type = byId("typeFilter");
  locations.forEach((location) => addFilterOption(area, location));
  types.forEach((value) => addFilterOption(type, value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase())));
};

const filteredListings = () => {
  return state.listings;
};

const listingQuery = (page) => {
  const params = new URLSearchParams({ page: String(page), size: String(LISTING_PAGE_SIZE) });
  const search = byId("globalSearch")?.value.trim();
  const area = byId("areaFilter")?.value;
  const type = byId("typeFilter")?.value;
  if (search) params.set("search", search);
  if (area && area !== "all") params.set("location", area);
  if (type && type !== "all") params.set("type", type.toLowerCase().replaceAll(" ", "_"));
  return params;
};

const loadFavorites = async () => {
  try {
    const data = await getJson(endpoint("/StudentFavoriteServlet"));
    if (!data) return [];
    state.favoriteIds = new Set((data.listings || []).map((listing) => String(listing.id)));
    return data.listings || [];
  } catch {
    state.favoriteIds = new Set();
    return [];
  }
};

const loadListings = async (page = 1, target = "browseListings") => {
  const container = byId(target);
  if (container) container.innerHTML = '<div class="empty-state">Loading listings...</div>';

  try {
    const data = await getJson(endpoint(`/PublicListingsServlet?${listingQuery(page).toString()}`));
    if (!data) return;

    state.page = Number(data.page || page);
    state.totalPages = Number(data.totalPages || 0);
    state.listings = (data.listings || []).map((listing, index) => ({
      id: String(listing.id || ""),
      title: listing.title,
      area: listing.area,
      type: listing.type,
      price: listing.price,
      image: normalizeImage(listing.image, index),
      tags: Array.isArray(listing.tags) ? listing.tags : [],
      ratingAverage: listing.ratingAverage,
      ratingCount: listing.ratingCount
    }));

    syncFilters(data.locations || [], data.types || []);
    renderListingGrid(container, filteredListings(), "No approved listings are available yet.", "browse");
    renderPagination();
  } catch (error) {
    if (container) {
      container.innerHTML = "";
      container.appendChild(createEmpty("Listings could not be loaded."));
    }
    toast(error.message);
  }
};

const loadOverviewListings = async () => {
  const container = byId("overviewListings");
  if (!container) return;
  container.innerHTML = '<div class="empty-state">Loading listings...</div>';
  try {
    const data = await getJson(endpoint("/PublicListingsServlet?page=1&size=2"));
    const listings = (data?.listings || []).map((listing, index) => ({
      id: String(listing.id || ""),
      title: listing.title,
      area: listing.area,
      type: listing.type,
      price: listing.price,
      image: normalizeImage(listing.image, index),
      tags: Array.isArray(listing.tags) ? listing.tags : [],
      ratingAverage: listing.ratingAverage,
      ratingCount: listing.ratingCount
    }));
    renderListingGrid(container, listings, "No approved listings are available yet.", "overview");
  } catch {
    container.innerHTML = "";
    container.appendChild(createEmpty("Listings could not be loaded."));
  }
};

const loadSavedListings = async () => {
  const container = byId("savedListings");
  if (!container) return;
  container.innerHTML = '<div class="empty-state">Loading saved places...</div>';
  const listings = await loadFavorites();
  renderListingGrid(container, listings, "No saved places yet.", "saved");
};

const renderRequests = (requests) => {
  const tbody = byId("requestRows");
  if (!tbody) return;
  tbody.innerHTML = "";

  if (!requests.length) {
    const row = document.createElement("tr");
    row.innerHTML = '<td colspan="5" class="empty-state">No listing requests have been sent yet.</td>';
    tbody.appendChild(row);
    return;
  }

  requests.forEach((request) => {
    const row = document.createElement("tr");
    row.dataset.search = [
      request.listingTitle,
      request.area,
      request.requestType,
      request.status
    ].join(" ").toLowerCase();
    row.innerHTML = `
      <td><strong>${request.listingTitle}</strong></td>
      <td>${request.area}</td>
      <td>${request.requestType}</td>
      <td><span class="status-pill pending">${request.status}</span></td>
      <td>${request.createdAt}</td>
    `;
    tbody.appendChild(row);
  });
};

const loadRequests = async () => {
  try {
    const data = await getJson(endpoint("/StudentRequestServlet"));
    renderRequests(data?.requests || []);
  } catch {
    renderRequests([]);
  }
};

const renderMessages = (messages) => {
  const list = byId("messageList");
  if (!list) return;
  list.innerHTML = "";

  if (!messages.length) {
    list.appendChild(createEmpty("No messages have been sent yet."));
    return;
  }

  messages.forEach((message) => {
    const item = document.createElement("article");
    item.className = "message-item";
    item.dataset.search = [message.sender, message.recipient, message.subject, message.body].join(" ").toLowerCase();
    item.innerHTML = `
      <strong>${message.subject}</strong>
      <span>${message.sender} to ${message.recipient} | ${message.createdAt}</span>
      <p>${message.body}</p>
    `;
    list.appendChild(item);
  });
};

const loadMessages = async () => {
  try {
    const data = await getJson(endpoint("/StudentMessageServlet"));
    renderMessages(data?.messages || []);
  } catch {
    renderMessages([]);
  }
};

const fillProfileForm = (profile) => {
  setProfileLabels(profile);
  if (byId("studentName")) byId("studentName").value = profile.fullName || "";
  if (byId("studentEmail")) byId("studentEmail").value = profile.email || "";
  if (byId("studentPhone")) byId("studentPhone").value = profile.phone || "";
  if (byId("studentInstitution")) byId("studentInstitution").value = profile.institution || "";
  if (byId("studentProgram")) byId("studentProgram").value = profile.program || "";
  if (byId("studentYearLevel")) byId("studentYearLevel").value = profile.yearLevel || "";
  if (byId("studentEmergencyContact")) byId("studentEmergencyContact").value = profile.emergencyContact || "";
};

const loadProfile = async () => {
  try {
    const data = await getJson(endpoint("/StudentProfileServlet?format=json"));
    fillProfileForm(data?.profile || {});
  } catch {
    fillProfileForm({});
  }
};

const saveFavorite = async (roomId, action) => {
  if (!roomId) return;
  const data = await postForm(endpoint("/StudentFavoriteServlet"), { roomId, action });
  if (!data) return;
  toast(data.message || "Saved places updated.");
  if (pageName === "saved") {
    loadSavedListings();
  } else {
    await loadFavorites();
    if (pageName === "listings") renderListingGrid(byId("browseListings"), filteredListings(), "No approved listings are available yet.", "browse");
    if (pageName === "overview") loadOverviewListings();
  }
  loadCounts();
};

const sendListingRequest = async (roomId) => {
  if (!roomId) return;
  const data = await postForm(endpoint("/StudentRequestServlet"), {
    roomId,
    requestType: "Viewing"
  });
  if (!data) return;
  window.location.href = `requests.html?success=${encodeURIComponent(data.message || "Request sent")}`;
};

const submitReview = async (form) => {
  const selected = form.querySelector('input[type="radio"]:checked');
  if (!selected) throw new Error("Choose a star rating first");
  const data = await postForm(endpoint("/StudentReviewServlet"), {
    roomId: form.dataset.reviewForm,
    rating: selected.value,
    reviewText: form.querySelector("textarea")?.value.trim() || ""
  });
  if (!data) return;
  toast(data.message || "Review saved");
  window.setTimeout(() => {
    window.location.href = endpoint(`/${data.redirectUrl || "HomePage/home.html?review=success"}`);
  }, 450);
};

const bindGlobalSearch = () => {
  const input = byId("globalSearch");
  if (!input) return;
  input.addEventListener("input", () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(() => loadListings(1), 250);
  });
};

const bindForms = () => {
  const messageForm = byId("messageForm");
  if (messageForm) {
    messageForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        const data = await postForm(endpoint("/StudentMessageServlet"), {
          recipient: byId("messageRecipient").value,
          subject: byId("messageSubject").value.trim(),
          message: byId("messageBody").value.trim()
        });
        if (!data) return;
        messageForm.reset();
        toast(data.message || "Message sent.");
        loadMessages();
        loadCounts();
      } catch (error) {
        toast(error.message);
      }
    });
  }

  const profileForm = byId("profileForm");
  if (profileForm) {
    profileForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        const data = await postForm(endpoint("/StudentProfileServlet"), {
          fullName: byId("studentName").value.trim(),
          email: byId("studentEmail").value.trim(),
          phone: byId("studentPhone").value.trim(),
          institution: byId("studentInstitution").value.trim(),
          program: byId("studentProgram").value.trim(),
          yearLevel: byId("studentYearLevel").value,
          emergencyContact: byId("studentEmergencyContact").value.trim()
        });
        if (!data) return;
        toast(data.message || "Profile saved.");
        loadProfile();
      } catch (error) {
        toast(error.message);
      }
    });
  }
};

const bindPageActions = () => {
  document.addEventListener("click", async (event) => {
    const favoriteButton = event.target.closest("[data-favorite-action]");
    if (favoriteButton) {
      event.preventDefault();
      try {
        await saveFavorite(favoriteButton.dataset.roomId, favoriteButton.dataset.favoriteAction);
      } catch (error) {
        toast(error.message);
      }
      return;
    }

    const requestButton = event.target.closest("[data-room-request]");
    if (requestButton) {
      event.preventDefault();
      try {
        await sendListingRequest(requestButton.dataset.roomRequest);
      } catch (error) {
        toast(error.message);
      }
      return;
    }

    const pageButton = event.target.closest("[data-listing-page]");
    if (pageButton && !pageButton.disabled) {
      loadListings(Number(pageButton.dataset.listingPage || 1));
    }
  });

  document.addEventListener("submit", async (event) => {
    const form = event.target.closest("[data-review-form]");
    if (!form) return;
    event.preventDefault();
    try {
      await submitReview(form);
    } catch (error) {
      toast(error.message);
    }
  });

  byId("areaFilter")?.addEventListener("change", () => {
    loadListings(1);
  });
  byId("typeFilter")?.addEventListener("change", () => {
    loadListings(1);
  });
};

const initPage = async () => {
  setActiveNavigation();
  showQueryMessage();
  bindGlobalSearch();
  bindForms();
  bindPageActions();
  loadProfileSummary();

  if (pageName === "overview") {
    loadCounts();
    await loadFavorites();
    loadOverviewListings();
  }
  if (pageName === "listings") {
    await loadFavorites();
    loadListings(1);
  }
  if (pageName === "saved") loadSavedListings();
  if (pageName === "requests") loadRequests();
  if (pageName === "messages") loadMessages();
  if (pageName === "profile") loadProfile();
};

initPage();
