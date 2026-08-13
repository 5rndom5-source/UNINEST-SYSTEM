const LISTINGS_KEY = "uninestLandlordListings";
const SERVER_ORIGIN = "http://localhost:8080";
const CONTEXT_PATH = "/uninest";
const LANDLORD_PAGE_SIZE = 4;

let landlordPage = 1;
let usingDatabaseListings = false;
let databaseListings = [];

const servletUrl = (path) => {
  if (window.location.protocol === "file:") return SERVER_ORIGIN + CONTEXT_PATH + path;
  return window.location.pathname.indexOf(CONTEXT_PATH + "/") === 0 ? CONTEXT_PATH + path : path;
};

const showBanner = (id, message) => {
  const banner = document.getElementById(id);
  if (!banner) return;
  banner.textContent = message;
  banner.classList.add("show");
};

const getListings = () => {
  try {
    return JSON.parse(localStorage.getItem(LISTINGS_KEY) || "[]");
  } catch {
    return [];
  }
};

const saveListings = (listings) => {
  localStorage.setItem(LISTINGS_KEY, JSON.stringify(listings));
};

const formatPrice = (value) => {
  const amount = Number(value || 0);
  return amount > 0 ? `R${amount.toLocaleString()}/month` : "Price not set";
};

const statusLabel = (status) => {
  if (status === "approved") return "Approved";
  if (status === "denied") return "Denied";
  return "Pending";
};

const statusNote = (listing) => {
  if (listing.status === "approved") return "Published";
  if (listing.status === "denied") return listing.adminNote ? "Changes requested" : "Denied by admin";
  return "Awaiting review";
};

const readFirstImage = (input) => {
  const file = input?.files?.[0];
  if (!file) return Promise.resolve("");

  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => resolve("");
    reader.readAsDataURL(file);
  });
};

const makeListingCard = (listing) => {
  const card = document.createElement("article");
  card.className = "classic-property-card";
  card.dataset.id = listing.id;
  card.dataset.source = listing.source || "local";

  const title = document.createElement("h3");
  title.textContent = listing.title;

  const body = document.createElement("div");
  body.className = "classic-property-body";

  const imageBox = document.createElement("div");
  imageBox.className = "classic-card-image";
  if (listing.image) {
    const img = document.createElement("img");
    img.src = listing.image;
    img.alt = listing.title;
    imageBox.appendChild(img);
  } else {
    imageBox.textContent = "Image";
  }

  const details = document.createElement("div");
  details.className = "classic-card-details";

  const location = document.createElement("p");
  location.textContent = listing.location;

  const price = document.createElement("p");
  price.textContent = listing.priceText || formatPrice(listing.price);

  const status = document.createElement("span");
  status.className = `classic-status ${listing.status || "pending"}`;
  status.textContent = statusLabel(listing.status || "pending");

  const actions = document.createElement("div");
  actions.className = "classic-card-actions";

  if (card.dataset.source === "database") {
    const note = document.createElement("span");
    note.className = "classic-card-note";
    note.textContent = statusNote(listing);
    actions.appendChild(note);
  }
  const edit = document.createElement("button");
  edit.type = "button";
  edit.className = "btn edit-btn";
  edit.textContent = "Edit";
  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "btn btn-red delete-btn";
  remove.textContent = "Delete";
  actions.append(edit, remove);
  details.append(location, price, status, actions);
  body.append(imageBox, details);
  card.append(title, body);

  return card;
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

const renderLandlordPagination = (current, totalPages) => {
  const controls = document.getElementById("propertyPagination");
  if (!controls) return;

  if (totalPages <= 1) {
    controls.innerHTML = "";
    controls.hidden = true;
    return;
  }

  controls.hidden = false;
  controls.innerHTML = [
    `<button type="button" data-property-page="${Math.max(1, current - 1)}" ${current === 1 ? "disabled" : ""}>Previous</button>`,
    ...pageItems(current, totalPages).map((item) => {
      if (item === "...") return '<span class="page-ellipsis">...</span>';
      return `<button type="button" data-property-page="${item}" class="${item === current ? "active" : ""}">${item}</button>`;
    }),
    `<button type="button" data-property-page="${Math.min(totalPages, current + 1)}" ${current === totalPages ? "disabled" : ""}>Next</button>`
  ].join("");
};

const renderListingCards = (listings, page, totalPages) => {
  const grid = document.getElementById("propertyGrid");
  if (!grid) return;

  grid.innerHTML = "";

  if (!listings.length) {
    const empty = document.createElement("article");
    empty.className = "classic-empty-listing";
    empty.id = "emptyListingState";
    empty.textContent = "No property listings have been added yet.";
    grid.appendChild(empty);
    renderLandlordPagination(1, 0);
    return;
  }

  listings.forEach((listing) => grid.appendChild(makeListingCard(listing)));
  renderLandlordPagination(page, totalPages);
};

const renderLocalListings = (page = landlordPage) => {
  usingDatabaseListings = false;
  const listings = getListings();
  const totalPages = Math.ceil(listings.length / LANDLORD_PAGE_SIZE);
  landlordPage = Math.min(Math.max(page, 1), totalPages || 1);
  const start = (landlordPage - 1) * LANDLORD_PAGE_SIZE;
  renderListingCards(listings.slice(start, start + LANDLORD_PAGE_SIZE), landlordPage, totalPages);
};

const loadLandlordListings = async (page = landlordPage) => {
  try {
    const response = await fetch(`${servletUrl("/LandlordListingsServlet")}?page=${page}&size=${LANDLORD_PAGE_SIZE}`, {
      headers: { Accept: "application/json" },
      credentials: "same-origin"
    });

    if (!response.ok) {
      renderLocalListings(page);
      return;
    }

    const data = await response.json();
    const listings = Array.isArray(data.listings)
      ? data.listings.map((listing) => ({ ...listing, source: "database" }))
      : [];
    databaseListings = listings;
    usingDatabaseListings = true;
    landlordPage = Number(data.page || page);
    renderListingCards(listings, landlordPage, Number(data.totalPages || 0));
  } catch {
    renderLocalListings(page);
  }
};

const renderListings = () => {
  loadLandlordListings(landlordPage);
};

const handleListingForm = () => {
  const form = document.getElementById("propertyForm");
  if (!form) return;

  const fileInput = document.getElementById("propertyPhotos");
  const uploadName = document.getElementById("uploadName");
  const uploadPreview = document.getElementById("uploadPreview");
  const status = document.getElementById("listingStatus");

  renderListings();

  fileInput?.addEventListener("change", async () => {
    const count = fileInput.files?.length || 0;
    if (uploadName) {
      uploadName.textContent =
        count === 1 ? fileInput.files[0].name : `${count} images selected`;
    }

    if (uploadPreview) {
      const preview = await readFirstImage(fileInput);
      uploadPreview.src = preview;
      uploadPreview.classList.toggle("hidden", !preview);
    }
  });

  form.addEventListener("reset", () => {
    form.removeAttribute("data-editing-id");
    document.getElementById("propertyRoomId").value = "";
    document.getElementById("propertySubmitButton").textContent = "Submit Listing";
    if (uploadName) uploadName.textContent = "No image selected";
    if (uploadPreview) uploadPreview.classList.add("hidden");
    if (status) status.textContent = "Pending Approval";
  });

  form.addEventListener("submit", async (event) => {
    if (form.getAttribute("action")) return;

    event.preventDefault();

    const editingId = form.getAttribute("data-editing-id");
    const currentListings = getListings();
    const existing = currentListings.find((item) => item.id === editingId);
    const image = (await readFirstImage(fileInput)) || existing?.image || "";

    const amenities = Array.from(form.querySelectorAll('input[name="amenities"]:checked'))
      .map((item) => item.value);

    const listing = {
      id: editingId || `listing-${Date.now()}`,
      title: document.getElementById("propertyTitle").value.trim(),
      description: document.getElementById("propertyDescription").value.trim(),
      location: document.getElementById("propertyLocation").value,
      price: document.getElementById("propertyPrice").value,
      amenities,
      image,
      status: "Pending",
      updatedAt: new Date().toISOString()
    };

    if (!listing.title || !listing.location || !listing.price) return;

    const nextListings = editingId
      ? currentListings.map((item) => (item.id === editingId ? listing : item))
      : [listing, ...currentListings];

    saveListings(nextListings);
    renderLocalListings(1);

    form.reset();
    form.removeAttribute("data-editing-id");
    if (uploadName) uploadName.textContent = "No image selected";
    if (uploadPreview) uploadPreview.classList.add("hidden");
    if (status) {
      status.textContent = editingId ? "Listing Updated - Pending Approval" : "Listing Submitted - Pending Approval";
    }
  });

  document.getElementById("propertyGrid")?.addEventListener("click", (event) => {
    const card = event.target.closest(".classic-property-card");
    if (!card) return;
    const database = card.dataset.source === "database";
    const listings = database ? databaseListings : getListings();
    const listing = listings.find((item) => String(item.id) === String(card.dataset.id));
    if (!listing) return;

    if (database && event.target.closest(".delete-btn")) {
      if (!window.confirm(`Delete "${listing.title}"? This cannot be undone.`)) return;
      fetch(servletUrl("/LandlordDeletePropertyServlet"), {
        method: "POST",
        credentials: "same-origin",
        headers: { Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ roomId: String(listing.id) })
      }).then(async (response) => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || "Listing could not be deleted");
        loadLandlordListings(landlordPage);
      }).catch((error) => {
        if (status) status.textContent = error.message;
      });
      return;
    }

    if (event.target.closest(".delete-btn")) {
      saveListings(listings.filter((item) => item.id !== listing.id));
      renderLocalListings(landlordPage);
      return;
    }

    if (event.target.closest(".edit-btn")) {
      document.getElementById("propertyTitle").value = listing.title;
      document.getElementById("propertyDescription").value = listing.description || "";
      document.getElementById("propertyLocation").value = listing.location;
      document.getElementById("propertyPrice").value = listing.price;
      form.setAttribute("data-editing-id", listing.id);
      document.getElementById("propertyRoomId").value = database ? listing.id : "";
      document.getElementById("propertySubmitButton").textContent = "Save Changes";
      if (status) status.textContent = "Editing Listing - Pending Approval";
      form.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });

  document.getElementById("propertyPagination")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-property-page]");
    if (!button || button.disabled) return;

    const nextPage = Number(button.dataset.propertyPage || 1);
    if (usingDatabaseListings) {
      loadLandlordListings(nextPage);
    } else {
      renderLocalListings(nextPage);
    }
  });
};

const hydrateContactListing = () => {
  const preview = document.getElementById("contactPropertyPreview");
  if (!preview) return;

  const latest = getListings()[0];
  if (!latest) return;

  document.getElementById("contactPropertyTitle").textContent = latest.title;
  const propertyInput = document.getElementById("contactPropertyInput");
  if (propertyInput) propertyInput.value = latest.title;
  document.getElementById("contactPropertyLocation").textContent = latest.location;
  document.getElementById("contactPropertyPrice").textContent = formatPrice(latest.price);
  const contactStatus = document.getElementById("contactPropertyStatus");
  contactStatus.textContent = "Available";
  contactStatus.classList.remove("neutral");

  const imageBox = document.getElementById("contactPropertyImage");
  imageBox.innerHTML = "";
  if (latest.image) {
    const img = document.createElement("img");
    img.src = latest.image;
    img.alt = latest.title;
    imageBox.appendChild(img);
  } else {
    imageBox.textContent = "Image";
  }
};

document.getElementById("landlordLoginForm")?.addEventListener("submit", (event) => {
  if (event.currentTarget.getAttribute("action")) return;

  event.preventDefault();
  window.location.href = "index.html";
});

document.getElementById("contactLandlordForm")?.addEventListener("submit", (event) => {
  if (event.currentTarget.getAttribute("action")) return;

  event.preventDefault();
  event.target.reset();
  document.getElementById("messageSuccess")?.classList.add("show");
});

document.querySelectorAll("[data-pending-form]").forEach((form) => {
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const bannerId = form.querySelector(".status-banner")?.id || form.getAttribute("data-banner") || "";
    showBanner(bannerId, "This information could not be saved. Please try again.");
  });
});

handleListingForm();
hydrateContactListing();
