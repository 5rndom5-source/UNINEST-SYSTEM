(function () {
  const contextPath = window.location.pathname.indexOf("/uninest/") === 0 ? "/uninest" : "";
  const escapeText = (value) => String(value || "").replace(/[&<>"']/g, (item) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[item]));

  const loadReviews = async () => {
    const rows = document.getElementById("landlordReviewRows");
    const count = document.getElementById("reviewCount");
    const empty = document.getElementById("reviewEmptyState");
    try {
      const response = await fetch(`${contextPath}/LandlordReviewsServlet`, {
        headers: { Accept: "application/json" }, credentials: "same-origin"
      });
      if (response.status === 401) {
        window.location.href = `${contextPath}/createAccount/login.html`;
        return;
      }
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || "Reviews could not be loaded");
      const reviews = Array.isArray(data.reviews) ? data.reviews : [];
      count.textContent = `${reviews.length} review${reviews.length === 1 ? "" : "s"}`;
      empty.hidden = reviews.length !== 0;
      rows.innerHTML = reviews.length ? reviews.map((review) => `
        <tr>
          <td>${escapeText(review.property)}</td>
          <td>${escapeText(review.student)}</td>
          <td><span class="review-score" aria-label="${review.rating} out of 5">${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}</span></td>
          <td>${escapeText(review.feedback)}</td>
          <td>${escapeText(review.createdAt)}</td>
        </tr>`).join("") : '<tr><td class="table-empty" colspan="5">No reviews available.</td></tr>';
    } catch (error) {
      count.textContent = "Unavailable";
      rows.innerHTML = `<tr><td class="table-empty" colspan="5">${escapeText(error.message)}</td></tr>`;
    }
  };

  document.addEventListener("DOMContentLoaded", loadReviews);
})();
