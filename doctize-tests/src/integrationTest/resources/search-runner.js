// Runs the search of a GENERATED site in Node: loads search-index.js and assets/js/search-engine.js exactly as
// a browser page would, then prints the URLs of the best results for every query as JSON.
//   node search-runner.js <site directory> <query>...
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const [siteDirectory, ...queries] = process.argv.slice(2);
const sandbox = { window: {} };
sandbox.self = sandbox;
vm.createContext(sandbox);
for (const file of ["search-index.js", "assets/js/search-engine.js"]) {
  vm.runInContext(fs.readFileSync(path.join(siteDirectory, file), "utf8"), sandbox, { filename: file });
}
const index = sandbox.DoctizeSearch.createIndex(sandbox.window.DOCTIZE_SEARCH_INDEX.records);
const answers = {};
for (const query of queries) {
  answers[query] = sandbox.DoctizeSearch.search(index, query).slice(0, 4).map((r) => r.url);
}
process.stdout.write(JSON.stringify(answers));
