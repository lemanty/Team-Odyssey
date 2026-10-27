// Dependency-free behavior tests. This is a DOM mock, not browser/visual QA.
// Run from the repository root: node --test scripts/tests/hero-motion.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const scriptPath = 'src/main/resources/static/js/hero-motion.js';

class Events {
    constructor() { this.listeners = new Map(); }
    addEventListener(type, listener) {
        if (!this.listeners.has(type)) this.listeners.set(type, []);
        this.listeners.get(type).push(listener);
    }
    removeEventListener(type, listener) {
        this.listeners.set(type, (this.listeners.get(type) || []).filter(item => item !== listener));
    }
    dispatch(type, detail = {}) {
        for (const listener of this.listeners.get(type) || []) listener({type, target: this, ...detail});
    }
}
class Element extends Events {
    constructor() { super(); this.dataset = {}; this.attributes = {}; this.hidden = false; }
    setAttribute(name, value) { this.attributes[name] = String(value); }
    getAttribute(name) { return this.attributes[name] ?? null; }
    removeAttribute(name) { delete this.attributes[name]; }
}
function setup({reduced = false, hidden = false, observer = true, media = true, heroPresent = true, storageThrows = false} = {}) {
    const label = {textContent: ''};
    const button = new Element();
    button.hidden = true;
    button.dataset.labelPause = 'Pause background animation';
    button.dataset.labelPlay = 'Play background animation';
    button.querySelector = selector => {
        assert.equal(selector, '[data-hero-motion-label]');
        return label;
    };
    const art = new Element();
    const img = new Element();
    const hero = new Element();
    hero.querySelector = selector => {
        if (selector === '.hero-art') return art;
        if (selector === '.hero-motion-toggle' || selector === '[data-hero-motion-toggle]') return button;
        if (selector === '[data-hero-motion-label]') return label;
        if (selector === '.hero-voyage' || selector === '#hero-voyage') return img;
        throw new Error('Unexpected selector: ' + selector);
    };
    const document = new Events();
    document.hidden = hidden;
    document.querySelector = selector => {
        assert.equal(selector, '[data-hero-motion]');
        return heroPresent ? hero : null;
    };
    document.querySelectorAll = selector => {
        assert.equal(selector, '[data-hero-motion]');
        return heroPresent ? [hero] : [];
    };
    const preference = new Events();
    preference.matches = reduced;
    preference.addListener = listener => preference.addEventListener('change', listener);
    const window = new Events();
    const stored = new Map();
    const sessionStorage = {
        getItem(key) { if (storageThrows) throw new Error('Storage blocked'); return stored.get(key) ?? null; },
        setItem(key, value) { if (storageThrows) throw new Error('Storage blocked'); stored.set(key, String(value)); }
    };
    const observations = [];
    class IntersectionObserver {
        constructor(callback, options) { this.callback = callback; this.options = options; observations.push(this); }
        observe(target) { this.target = target; }
        unobserve() {}
        disconnect() { this.disconnected = true; }
    }
    if (media) window.matchMedia = query => {
        assert.equal(query, '(prefers-reduced-motion: reduce)');
        return preference;
    };
    if (observer) window.IntersectionObserver = IntersectionObserver;
    window.sessionStorage = sessionStorage;
    const context = {window, document, sessionStorage, console};
    if (media) context.matchMedia = window.matchMedia;
    if (observer) context.IntersectionObserver = IntersectionObserver;
    vm.runInNewContext(read(scriptPath), context, {filename: scriptPath});
    return {
        hero, art, button, label, document, window, preference, observations,
        visible(value) {
            assert.ok(observations.length, 'Visibility observer must exist');
            for (const io of observations) io.callback([{target: io.target, isIntersecting: value, intersectionRatio: value ? 1 : 0}]);
        },
        click() { button.dispatch('click'); },
        reduce(value) { preference.matches = value; preference.dispatch('change', {matches: value}); },
        hide(value) { document.hidden = value; document.dispatch('visibilitychange'); }
    };
}
const state = fixture => fixture.hero.dataset.motionState;

test('script parses independently without build tools', () => {
    assert.doesNotThrow(() => new vm.Script(read(scriptPath)));
});
test('unsupported observation leaves the control hidden and image static', () => {
    const f = setup({observer: false});
    assert.equal(f.button.hidden, true);
    assert.notEqual(f.hero.dataset.motionReady, 'true');
    assert.notEqual(state(f), 'running');
});
test('unsupported motion preference leaves static fallback', () => {
    const f = setup({media: false});
    assert.equal(f.button.hidden, true);
    assert.notEqual(state(f), 'running');
});
test('pages with no hero are safe', () => {
    assert.doesNotThrow(() => setup({heroPresent: false}));
});
test('starts paused until artwork visibility is known, then runs', () => {
    const f = setup();
    assert.equal(f.hero.dataset.motionReady, 'true');
    assert.equal(state(f), 'paused');
    assert.equal(f.button.hidden, false);
    assert.equal(f.observations[0].target, f.art, 'Observe artwork, not the text-containing hero');
    f.visible(true);
    assert.equal(state(f), 'running');
    assert.equal(f.label.textContent, f.button.dataset.labelPause);
});
test('repeated pause and play clicks remain consistent', () => {
    const f = setup(); f.visible(true);
    for (let repeat = 0; repeat < 4; repeat++) {
        f.click();
        assert.equal(state(f), 'paused');
        assert.equal(f.label.textContent, f.button.dataset.labelPlay);
        f.click();
        assert.equal(state(f), 'running');
        assert.equal(f.label.textContent, f.button.dataset.labelPause);
    }
});
test('offscreen and visibility changes pause and resume', () => {
    const f = setup(); f.visible(true);
    f.visible(false); assert.equal(state(f), 'paused');
    f.visible(true); assert.equal(state(f), 'running');
    f.hide(true); assert.equal(state(f), 'paused');
    f.hide(false); assert.equal(state(f), 'running');
});
test('a hidden page cannot start even when observer reports visibility', () => {
    const f = setup({hidden: true}); f.visible(true);
    assert.equal(state(f), 'paused');
    f.hide(false); assert.equal(state(f), 'running');
});
test('manual pause survives offscreen and hidden transitions', () => {
    const f = setup(); f.visible(true); f.click();
    f.visible(false); f.hide(true); f.visible(true); f.hide(false);
    assert.equal(state(f), 'paused');
    assert.equal(f.label.textContent, f.button.dataset.labelPlay);
    f.click(); assert.equal(state(f), 'running');
});
test('reduced motion begins static and cannot be overridden by visibility', () => {
    const f = setup({reduced: true}); f.visible(true);
    assert.equal(state(f), 'paused');
    assert.equal(f.button.hidden, true);
    f.reduce(false);
    assert.equal(f.button.hidden, false);
    assert.equal(state(f), 'running');
});
test('live reduced-motion changes pause and preserve an explicit manual pause', () => {
    const f = setup(); f.visible(true);
    f.reduce(true); assert.equal(state(f), 'paused');
    assert.equal(f.button.hidden, true);
    f.reduce(false); assert.equal(state(f), 'running');
    f.click(); f.reduce(true); f.reduce(false);
    assert.equal(state(f), 'paused');
});
test('pagehide and pageshow handle BFCache without losing pause choice', () => {
    const f = setup(); f.visible(true);
    f.window.dispatch('pagehide', {persisted: true}); assert.equal(state(f), 'paused');
    f.window.dispatch('pageshow', {persisted: true}); assert.equal(state(f), 'running');
    f.click();
    f.window.dispatch('pagehide', {persisted: true});
    f.window.dispatch('pageshow', {persisted: true});
    assert.equal(state(f), 'paused');
});
test('print pauses animation and afterprint respects other pause reasons', () => {
    const f = setup(); f.visible(true);
    f.window.dispatch('beforeprint'); assert.equal(state(f), 'paused');
    f.window.dispatch('afterprint'); assert.equal(state(f), 'running');
    f.click(); f.window.dispatch('beforeprint'); f.window.dispatch('afterprint');
    assert.equal(state(f), 'paused');
});
test('blocked browser storage does not prevent initialization or controls', () => {
    const f = setup({storageThrows: true}); f.visible(true); f.click();
    assert.equal(state(f), 'paused');
});
test('original scene and feathered mask bytes are unchanged', () => {
    const expected = {
        'odyssey-voyage.png': '5c9746339c2bfcf29cea596d158a54498fa2726264cd84bd2147183ff78d15c1',
        'voyage-reveal.svg': '9643755c2777a8a6ecf4618629e6669628ec4e8beccdd11d7c75fe40d4fcb3a2'
    };
    for (const [file, digest] of Object.entries(expected)) {
        const bytes = fs.readFileSync(path.join(root, 'src/main/resources/static/images', file));
        assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'), digest, file);
    }
});

test('CSS keeps the original mask on a stationary wrapper, not on the moving image', () => {
    const css = read('src/main/resources/static/css/style.css');
    const artRule = css.match(/\.hero-art\s*\{([^}]+)\}/)[1];
    const imageRule = css.match(/\.hero-voyage\s*\{([^}]+)\}/)[1];
    assert.match(artRule, /mask-image:\s*url\('\/images\/voyage-reveal\.svg\?v=soft-2'\)/);
    assert.match(artRule, /overflow:\s*hidden/);
    assert.doesNotMatch(artRule, /animation:|transform:/);
    assert.doesNotMatch(imageRule, /mask-image/);
});
test('motion CSS has closed cycles, lower mobile amplitude, static preference and no-JS guards', () => {
    const css = read('src/main/resources/static/css/hero-motion.css');
    assert.match(css, /\[data-motion-ready="true"\]/);
    assert.match(css, /\.hero-motion-toggle\[hidden\]\s*\{\s*display:\s*none/);
    assert.match(css, /prefers-reduced-motion:\s*no-preference/);
    assert.match(css, /prefers-reduced-motion:\s*reduce/);
    assert.match(css, /animation:\s*none\s*!important/);
    assert.match(css, /animation-play-state:\s*paused/);
    assert.match(css, /voyage-drift\s+46s/);
    assert.match(css, /animation-duration:\s*56s/);
    assert.equal((css.match(/0%,\s*100%\s*\{/g) || []).length, 3, 'All three loops share exact endpoints');
    assert.match(css, /scale\(1\.008\)/);
    assert.match(css, /scale\(1\.004\)/);
    assert.doesNotMatch(css, /rotate\(|skew\(|scale[XY]\(|filter:/, 'No architectural deformation or expensive image filters');
    assert.doesNotMatch(css, /\.hero-copy\s*[,{]/, 'Headline and CTA are not motion targets');
});
test('shared entrance animation excludes all enhanced hero descendants', () => {
    const shared = read('src/main/resources/static/js/site-motion.js');
    assert.match(shared, /!element\.closest\('\[data-hero-motion\]'\)/);
});
