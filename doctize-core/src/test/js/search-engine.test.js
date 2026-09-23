// Run with: node --test doctize-core/src/test/js
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const Search = require(path.join(__dirname, "../../main/resources/dev/doctize/theme/default/assets/js/search-engine.js"));

const records = [
  { url: "quick-start/start.html", title: "Quick start", section: "Quick start", heading: "", text: "Create a cache with CacheBuilder and put your first value.", keywords: "tutorial", kind: "page", area: "guides" },
  { url: "quick-start/start.html#install", title: "Quick start", section: "Quick start", heading: "Install", text: "Add the dependency to your Gradle build.", keywords: "", kind: "section", area: "guides" },
  { url: "api/Cache.html", title: "Cache", section: "API", heading: "", text: "A bounded in-memory cache with eviction.", keywords: "lru memoize", kind: "interface", area: "api" },
  { url: "api/Cache.html#method-evict", title: "Cache", section: "API", heading: "evict()", text: "Removes the entry for the key.", keywords: "", kind: "method", area: "api" },
  { url: "api/CacheBuilder.html", title: "CacheBuilder", section: "API", heading: "", text: "Fluent builder for caches.", keywords: "", kind: "class", area: "api" },
];
const index = Search.createIndex(records);
const urls = (query) => Search.search(index, query).map((r) => r.url);

test("tokenize splits identifiers at case changes and keeps the whole word", () => {
  assert.deepEqual(Search.tokenize("CacheBuilder"), ["cachebuilder", "cache", "builder"]);
  assert.deepEqual(Search.tokenize("max_size HTTPServer"), ["max_size", "max", "size", "httpserver", "http", "server"]);
});

test("finds camelCase parts, prefixes and keywords", () => {
  assert.equal(urls("builder")[0], "api/CacheBuilder.html");
  assert.equal(urls("instal")[0], "quick-start/start.html#install");
  assert.equal(urls("memoize")[0], "api/Cache.html");
});

test("tolerates typos", () => {
  assert.equal(urls("cache bulder")[0], "api/CacheBuilder.html");
  assert.equal(urls("grade")[0], "quick-start/start.html#install");
});

test("ranks exact headings first and returns nothing for gibberish", () => {
  assert.equal(urls("evict")[0], "api/Cache.html#method-evict");
  assert.deepEqual(urls("zzzzzz"), []);
  assert.deepEqual(urls("   "), []);
});

test("groups hits of one page and marks follow-ups as children", () => {
  const results = Search.search(index, "cache");
  assert.equal(results.filter((r) => !r.child).length, new Set(results.map((r) => r.url.split("#")[0])).size);
});

test("snippets escape HTML and highlight matches", () => {
  const custom = Search.createIndex([{ url: "a.html", title: "A", section: "S", heading: "", text: "Use <b>eviction</b> wisely", keywords: "" }]);
  const [result] = Search.search(custom, "eviction");
  assert.ok(result.snippetHtml.includes("&lt;b&gt;<mark>eviction</mark>&lt;/b&gt;"));
});

test("results carry the kind of what they found", () => {
  const kinds = Object.fromEntries(Search.search(index, "cache").map((r) => [r.url, r.kind]));
  assert.equal(kinds["api/Cache.html"], "interface");
  assert.equal(Search.search(index, "evict")[0].kind, "method");
  assert.equal(Search.search(index, "install")[0].kind, "section");
});

test("a scope limits the results to the API reference or the guides", () => {
  const api = Search.search(index, "cache", { scope: "api" });
  const guides = Search.search(index, "cache", { scope: "guides" });
  assert.ok(api.length > 0 && api.every((r) => r.area === "api"));
  assert.ok(guides.length > 0 && guides.every((r) => r.area === "guides"));
  assert.equal(Search.search(index, "cache", { scope: "anything" }).length, Search.search(index, "cache").length);
  assert.deepEqual(Search.areas(index), ["api", "guides"]);
});

test("records without kind and area, from older indexes, count as guides", () => {
  const old = Search.createIndex([{ url: "a.html", title: "A", section: "S", heading: "", text: "alpha", keywords: "" }]);
  const [result] = Search.search(old, "alpha", { scope: "guides" });
  assert.equal(result.kind, "page");
  assert.deepEqual(Search.areas(old), ["guides"]);
});
