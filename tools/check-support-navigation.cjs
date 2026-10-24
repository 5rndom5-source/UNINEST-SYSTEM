const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../portal-identity.js'), 'utf8');
const instrumented = source.replace(/\}\)\(\);\s*$/, 'globalThis.bindSupportTest = bindSupportLinks; })();');
let checks = 0;
function page(root, path, support, storage) {
  const links = [{href: 'https://uninest.test'+root+support, dataset: {},
    hasAttribute: () => true, addEventListener: function (event, callback) { this.click = callback; }}];
  const children = [];
  const document = {
    currentScript: {src: 'https://uninest.test'+root+'/portal-identity.js'},
    head: {appendChild() {}}, readyState: 'loading', addEventListener() {},
    createElement: () => ({style: {}}),
    querySelectorAll: () => links,
    getElementById: id => children.find(child => child.id === id),
    querySelector: () => ({prepend: child => children.unshift(child)})
  };
  const location = {origin: 'https://uninest.test', protocol: 'https:', pathname: root+path,
    search: '?filter=pending', hash: '#details', href: 'https://uninest.test'+root+path+'?filter=pending#details'};
  const context = {URL, document, window: {location, addEventListener() {}}, sessionStorage: storage};
  vm.createContext(context);
  vm.runInContext(instrumented, context);
  return {context, links, children};
}
for (const root of ['', '/uninest']) {
  for (const [role, folder, support, home] of [
    ['student','/UserDashboard','/UserDashboard/messages.html','/UserDashboard/index.html'],
    ['landlord','/Landlord','/Landlord/contact-landlord.html','/Landlord/index.html'],
    ['admin','/Admin/web/jsp','/Admin/web/jsp/Messages.html','/Admin/web/jsp/Dashboard.html']
  ]) {
    const data = new Map();
    const storage = {getItem: key => data.get(key), setItem: (key, value) => data.set(key, value)};
    const user = {role, userId: 42};
    const previous = folder+'/profile.html';
    const start = page(root, previous, support, storage);
    start.context.bindSupportTest(user);
    assert.equal(start.links[0].href, root+support); checks++;
    start.links[0].click();
    const inbox = page(root, support, support, storage);
    inbox.context.bindSupportTest(user);
    assert.equal(inbox.children[0].href, root+previous+'?filter=pending#details'); checks++;
    inbox.links[0].click();
    assert.equal(storage.getItem('uninest.supportReturn.42.'+role), root+previous+'?filter=pending#details'); checks++;
    storage.setItem('uninest.supportReturn.42.'+role, 'https://untrusted.test/');
    const unsafe = page(root, support, support, storage);
    unsafe.context.bindSupportTest(user);
    assert.equal(unsafe.children[0].href, root+home); checks++;
    const unavailable = page(root, support, support, {getItem() {throw Error('disabled');}});
    unavailable.context.bindSupportTest(user);
    assert.equal(unavailable.children[0].href, root+home); checks++;
  }
}
console.log(`PASS: ${checks} support-navigation checks`);
