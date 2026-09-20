// Run with: node --test doctize-core/src/test/js
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const Diagrams = require(path.join(__dirname, "../../main/resources/dev/doctize/theme/default/assets/js/diagrams.js"));

/** A page with the given diagrams, just enough of a browser for the loader. */
function fakeWindow({ sources = [], theme = null, prefersDark = false, mermaid = null, basePath = "" } = {}) {
  const nodes = sources.map((text) => ({
    textContent: text,
    attributes: { "data-processed": "true" },
    classes: new Set(),
    removeAttribute(name) { delete this.attributes[name]; },
    classList: { add(name) { nodes.find((n) => n.classList === this).classes.add(name); } },
  }));
  nodes.forEach((node) => { const classes = node.classes; node.classList = { add: (name) => classes.add(name) }; });
  const scripts = [];
  const observers = [];
  const root = { getAttribute: (name) => (name === "data-theme" ? theme : null) };
  const win = {
    document: {
      documentElement: root,
      querySelectorAll: (selector) => (selector === "pre.mermaid" ? nodes : []),
      createElement: () => ({}),
      head: { appendChild: (script) => scripts.push(script) },
    },
    DOCTIZE: { basePath },
    matchMedia: () => ({ matches: prefersDark }),
    MutationObserver: class { constructor(callback) { this.callback = callback; observers.push(this); } observe(target, options) { this.target = target; this.options = options; } },
    mermaid,
  };
  return { win, nodes, scripts, observers, root };
}

function fakeMermaid() {
  const calls = { initialize: [], run: [] };
  return { calls, initialize: (options) => calls.initialize.push(options), run: (options) => { calls.run.push(options); return Promise.resolve(); } };
}

test("a page without a diagram does nothing, and in particular loads no library", () => {
  const { win, scripts } = fakeWindow({ sources: [] });

  assert.equal(Diagrams.start(win), null);
  assert.equal(scripts.length, 0);
});

test("a page with a diagram loads the library from the vendor directory, relative to the site root", () => {
  const { win, scripts } = fakeWindow({ sources: ["flowchart TD\n A-->B"], basePath: "../../" });

  Diagrams.start(win);

  assert.equal(scripts.length, 1);
  assert.equal(scripts[0].src, "../../assets/vendor/mermaid.min.js");
});

test("once the library is loaded the diagrams are drawn in the theme of the site", () => {
  const mermaid = fakeMermaid();
  const { win, nodes, scripts } = fakeWindow({ sources: ["flowchart TD\n A-->B", "classDiagram"], theme: "dark", mermaid: null });
  Diagrams.start(win);

  win.mermaid = mermaid;
  scripts[0].onload();

  assert.deepEqual(mermaid.calls.initialize[0], { startOnLoad: false, securityLevel: "strict", theme: "dark" });
  assert.equal(mermaid.calls.run.length, 1);
  assert.equal(mermaid.calls.run[0].nodes.length, 2);
  assert.equal(nodes[0].textContent, "flowchart TD\n A-->B");
  assert.equal("data-processed" in nodes[0].attributes, false, "so that Mermaid draws it again");
});

test("without a chosen theme the mode of the system decides", () => {
  assert.equal(Diagrams.isDark(fakeWindow({ prefersDark: true }).win), true);
  assert.equal(Diagrams.isDark(fakeWindow({ prefersDark: false }).win), false);
  assert.equal(Diagrams.isDark(fakeWindow({ theme: "light", prefersDark: true }).win), false, "the choice on the page wins");
  assert.equal(Diagrams.isDark(fakeWindow({ theme: "dark", prefersDark: false }).win), true);
});

test("switching the mode of the site draws the diagrams again from their source", () => {
  const mermaid = fakeMermaid();
  const { win, nodes, scripts, observers, root } = fakeWindow({ sources: ["flowchart TD\n A-->B"], theme: "light" });
  Diagrams.start(win);
  win.mermaid = mermaid;
  scripts[0].onload();
  nodes[0].textContent = "<svg>drawn</svg>";

  assert.equal(observers.length, 1);
  assert.deepEqual(observers[0].options, { attributes: true, attributeFilter: ["data-theme"] });
  assert.equal(observers[0].target, root);
  root.getAttribute = () => "dark";
  observers[0].callback();

  assert.equal(mermaid.calls.initialize.at(-1).theme, "dark");
  assert.equal(nodes[0].textContent, "flowchart TD\n A-->B");
  assert.equal(mermaid.calls.run.length, 2);
});

test("a library that is already there is used without loading it again", () => {
  const mermaid = fakeMermaid();
  const { win, scripts } = fakeWindow({ sources: ["flowchart TD"], mermaid });

  Diagrams.start(win);

  assert.equal(scripts.length, 0);
  assert.equal(mermaid.calls.run.length, 1);
});

test("if the library cannot be loaded, the source stays and the diagrams say so", () => {
  const { win, nodes, scripts } = fakeWindow({ sources: ["flowchart TD\n A-->B"] });
  const state = Diagrams.start(win);

  scripts[0].onerror();

  assert.equal(state.failed, true);
  assert.equal(nodes[0].classes.has("mermaid-failed"), true);
  assert.equal(nodes[0].textContent, "flowchart TD\n A-->B");
});
