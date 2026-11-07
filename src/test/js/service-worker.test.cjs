const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');

function worker(fetchResult = new Response('network')) {
  const handlers = {};
  const stored = new Map([['/offline.html', new Response('offline screen')]]);
  const writes = [];
  const deleted = [];
  const installCacheModes = [];
  let currentCache;
  const cache = {
    addAll: async assets => {
      writes.push(...assets.map(asset => new URL(asset.url).pathname));
      installCacheModes.push(...assets.map(asset => asset.cache));
    },
    match: async request => stored.get(typeof request === 'string' ? request : new URL(request.url).pathname),
    put: async (request, response) => writes.push(new URL(request.url).pathname),
  };
  const context = {
    URL, Promise, Request,
    self: {
      location: {origin: 'https://taskflow.test'},
      addEventListener: (name, handler) => handlers[name] = handler,
      skipWaiting: async () => {},
      clients: {claim: async () => {}},
    },
    caches: {
      open: async name => { currentCache = name; return cache; },
      match: async path => stored.get(path),
      keys: async () => ['taskflow-static-old', currentCache, 'another-app'],
      delete: async key => deleted.push(key),
    },
    fetch: async () => {
      if (fetchResult instanceof Error) throw fetchResult;
      return fetchResult;
    },
  };
  vm.runInNewContext(readFileSync(resolve(__dirname, '../../main/resources/static/sw.js'), 'utf8'), context);
  return {handlers, writes, deleted, installCacheModes};
}

function request(path, {method = 'GET', mode = 'cors', origin = 'https://taskflow.test'} = {}) {
  return {url: origin + path, method, mode};
}

test('installation caches only public assets that exist in the project', async () => {
  const {handlers, writes, installCacheModes} = worker();
  let done;
  handlers.install({waitUntil: value => done = value});
  await done;
  assert.ok(writes.includes('/offline.html'));
  assert.ok(writes.includes('/icons/icon-512.png'));
  assert.ok(installCacheModes.every(mode => mode === 'reload'));
  for (const path of writes) {
    assert.ok(!/login|register|dashboard|tasks|profile|categories/.test(path));
    assert.ok(readFileSync(resolve(__dirname, '../../main/resources/static', path.slice(1))).length > 0);
  }
});

test('private navigation stays on the network and is never written to Cache Storage', async () => {
  const {handlers, writes} = worker(new Response('private tasks, token=secret'));
  let result;
  handlers.fetch({request: request('/tasks', {mode:'navigate'}), respondWith: value => result = value});
  assert.equal(await (await result).text(), 'private tasks, token=secret');
  assert.deepEqual(writes, []);
});

test('offline navigation returns the generic connection screen', async () => {
  const {handlers, writes} = worker(new Error('offline'));
  let result;
  handlers.fetch({request: request('/profile', {mode:'navigate'}), respondWith: value => result = value});
  assert.equal(await (await result).text(), 'offline screen');
  assert.deepEqual(writes, []);
});

test('POST, unknown resources, query variants and external requests are not intercepted', () => {
  const {handlers} = worker();
  const untouched = [request('/tasks/12/toggle', {method:'POST'}), request('/private.json'),
    request('/css/app.css?private=1'), request('/css/app.css', {origin:'https://other.test'})];
  for (const item of untouched) {
    handlers.fetch({request:item, respondWith: () => assert.fail('Unexpected interception')});
  }
});

test('activation removes old TaskFlow versions and preserves unrelated caches', async () => {
  const {handlers, deleted} = worker();
  let done;
  handlers.install({waitUntil: value => done = value});
  await done;
  handlers.activate({waitUntil: value => done = value});
  await done;
  assert.deepEqual(deleted, ['taskflow-static-old']);
});
