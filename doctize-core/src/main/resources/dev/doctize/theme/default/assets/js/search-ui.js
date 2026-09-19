/* Search dialog: opens with Ctrl/Cmd+K or "/", searches as you type, navigable by keyboard. */
(function () {
  "use strict";

  const modal = document.getElementById("search-modal");
  const input = document.getElementById("search-input");
  const list = document.getElementById("search-results");
  if (!modal || !input || !list || !window.DoctizeSearch) return;

  const basePath = (window.DOCTIZE && window.DOCTIZE.basePath) || "";
  const isMac = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
  let index = null;
  let results = [];
  let active = -1;
  let lastFocus = null;

  document.querySelectorAll("[data-shortcut]").forEach((el) => (el.textContent = isMac ? "⌘ K" : "Ctrl K"));

  function ensureIndex() {
    if (index || !window.DOCTIZE_SEARCH_INDEX) return index;
    index = window.DoctizeSearch.createIndex(window.DOCTIZE_SEARCH_INDEX.records);
    return index;
  }

  function open() {
    lastFocus = document.activeElement;
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    input.focus();
    input.select();
    ensureIndex();
    render();
  }

  function close() {
    modal.hidden = true;
    document.body.style.overflow = "";
    if (lastFocus && lastFocus.focus) lastFocus.focus();
  }

  function setActive(position) {
    if (!results.length) return;
    active = (position + results.length) % results.length;
    list.querySelectorAll(".search-result").forEach((item, i) => {
      const isActive = i === active;
      item.classList.toggle("is-active", isActive);
      item.setAttribute("aria-selected", String(isActive));
      if (isActive) {
        input.setAttribute("aria-activedescendant", item.id);
        item.scrollIntoView({ block: "nearest" });
      }
    });
  }

  function render() {
    const query = input.value.trim();
    list.innerHTML = "";
    results = [];
    active = -1;
    if (!query) {
      list.innerHTML = '<li class="search-empty">Type to search the documentation</li>';
      return;
    }
    if (!ensureIndex()) {
      list.innerHTML = '<li class="search-empty">The search index could not be loaded.</li>';
      return;
    }
    results = window.DoctizeSearch.search(index, query);
    if (!results.length) {
      list.innerHTML = '<li class="search-empty">No results for “' + escapeHtml(query) + "”</li>";
      return;
    }
    list.innerHTML = results.map(resultHtml).join("");
    list.querySelectorAll(".search-result").forEach((item, i) => {
      item.addEventListener("mousemove", () => i !== active && setActive(i));
    });
    setActive(0);
  }

  function resultHtml(result, i) {
    const path =
      '<span class="search-result-path"><span>' + escapeHtml(result.section) + '</span><span class="sep">›</span><span>' +
      escapeHtml(result.title) + "</span></span>";
    const title = result.heading ? result.headingHtml : result.titleHtml;
    return (
      '<li class="search-result' + (result.child ? " is-child" : "") + '" id="search-result-' + i + '" role="option">' +
      '<a href="' + basePath + escapeAttribute(result.url) + '">' +
      (result.child ? "" : path) +
      '<div class="search-result-title">' + title + "</div>" +
      (result.snippetHtml ? '<div class="search-result-snippet">' + result.snippetHtml + "</div>" : "") +
      "</a></li>"
    );
  }

  function go(result) {
    window.location.href = basePath + result.url;
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }
  const escapeAttribute = escapeHtml;

  input.addEventListener("input", render);
  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown") { event.preventDefault(); setActive(active + 1); }
    else if (event.key === "ArrowUp") { event.preventDefault(); setActive(active - 1); }
    else if (event.key === "Enter" && results[active]) { event.preventDefault(); go(results[active]); }
  });

  list.addEventListener("click", (event) => {
    if (event.target.closest("a")) close();
  });

  document.querySelectorAll("[data-search-open]").forEach((el) => el.addEventListener("click", open));
  document.querySelectorAll("[data-search-close]").forEach((el) => el.addEventListener("click", close));

  document.addEventListener("keydown", (event) => {
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName) || document.activeElement.isContentEditable;
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      modal.hidden ? open() : close();
    } else if (event.key === "/" && !typing && modal.hidden) {
      event.preventDefault();
      open();
    } else if (event.key === "Escape" && !modal.hidden) {
      close();
    }
  });

  // Build the index while the browser is idle, so the first search is instant.
  (window.requestIdleCallback || window.setTimeout)(ensureIndex);
})();
