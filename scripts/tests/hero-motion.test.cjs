// Node DOM/canvas mock: behavior checks complement actual browser visual review.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root,'src/main/resources/static/js/hero-motion.js'),'utf8');

async function setup({reduced=false, fail=false, supported=true}={}) {
    const events = () => ({listeners:{},addEventListener(type,fn){this.listeners[type]=fn;},
        emit(type){this.listeners[type]?.();}});
    let transforms=[], paints=0;
    const ctx = {setTransform(){paints++;transforms=[];},clearRect(){},translate(...args){transforms.push(args);},
        scale(){},drawImage(){},save(){},restore(){},clip(){},rotate(){},fillRect(){}};
    const art={getBoundingClientRect:()=>({width:1000,height:600})};
    const original={decode:()=>Promise.resolve()};
    const canvas={dataset:{seaSrc:'/images/odyssey-voyage-sea.png'},hidden:true,getContext:()=>ctx};
    const hero={dataset:{},querySelector:s=>({'.hero-art':art,'.hero-voyage':original,'.hero-scene':canvas}[s])};
    const document={...events(),hidden:false,querySelector:()=>hero};
    const preference={...events(),matches:reduced};
    let observer, nextId=0;
    const queue=new Map();
    class Observer {constructor(fn){observer=fn;}observe(){}}
    class SizeObserver {constructor(fn){this.fn=fn;}observe(){this.fn();}}
    class Image {decode(){return fail?Promise.reject(Error('missing image')):Promise.resolve();}}
    class Path2D {}
    const window={...events(),matchMedia:()=>preference,IntersectionObserver:supported?Observer:null,
        ResizeObserver:SizeObserver,Path2D,devicePixelRatio:2};
    vm.runInNewContext(source,{window,document,Image,Path2D,IntersectionObserver:Observer,ResizeObserver:SizeObserver,
        requestAnimationFrame:fn=>{const id=++nextId;queue.set(id,fn);return id;},
        cancelAnimationFrame:id=>queue.delete(id)});
    await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    return {hero,canvas,queue,window,document,preference,
        visible(value){observer([{target:art,isIntersecting:value}]);},
        step(now){const callbacks=[...queue.values()];queue.clear();callbacks.forEach(fn=>fn(now));},
        get transforms(){return transforms;},get paints(){return paints;}};
}

test('autoplays only after loaded and visible, without a button',async()=>{
    const f=await setup(); assert.equal(f.queue.size,0); f.visible(true);
    assert.equal(f.hero.dataset.motionState,'running'); assert.equal(f.canvas.hidden,false);
    assert.equal(f.queue.size,1);
});
test('offscreen and background stop rendering and resume only once',async()=>{
    const f=await setup(); f.visible(true); f.visible(false); assert.equal(f.queue.size,0);
    f.visible(true); f.document.hidden=true; f.document.emit('visibilitychange'); assert.equal(f.queue.size,0);
    f.document.hidden=false; f.document.emit('visibilitychange'); f.document.emit('visibilitychange');
    assert.equal(f.queue.size,1);
});
test('reduced motion restores original image and cancels animation',async()=>{
    const f=await setup({reduced:true});f.visible(true);assert.equal(f.canvas.hidden,true);assert.equal(f.queue.size,0);
    f.preference.matches=false;f.preference.emit('change');assert.equal(f.queue.size,1);
    f.preference.matches=true;f.preference.emit('change');assert.equal(f.queue.size,0);assert.equal(f.canvas.hidden,true);
});
test('failed image decode retains static fallback',async()=>{
    const f=await setup({fail:true});f.visible(true);assert.equal(f.canvas.hidden,true);assert.equal(f.queue.size,0);
});
test('unsupported browser keeps static fallback',async()=>{
    const f=await setup({supported:false});assert.equal(f.canvas.hidden,true);assert.equal(f.queue.size,0);
});
test('BFCache and print suspend animation without hiding the fallback',async()=>{
    const f=await setup();f.visible(true);f.window.emit('pagehide');assert.equal(f.queue.size,0);
    f.window.emit('pageshow');assert.equal(f.queue.size,1);
    f.window.emit('beforeprint');assert.equal(f.canvas.hidden,true);assert.equal(f.queue.size,0);
    f.window.emit('afterprint');assert.equal(f.queue.size,1);assert.equal(f.canvas.hidden,false);
});
test('boat moves independently and returns to its starting position after 24 seconds',async()=>{
    const f=await setup();f.visible(true);f.step(0);const start=f.transforms[1].slice();
    for(let i=1;i<=120;i++)f.step(i*50);
    assert.ok(Math.abs(f.transforms[1][0]-start[0])>30);
    for(let i=121;i<=480;i++)f.step(i*50);
    assert.ok(Math.abs(f.transforms[1][0]-start[0])<.001);
    assert.ok(Math.abs(f.transforms[1][1]-start[1])<.001);
});
test('source contains neither cursor initialization nor playback buttons',()=>{
    const layout=fs.readFileSync(path.join(root,'src/main/resources/templates/fragments/layout.html'),'utf8');
    const home=fs.readFileSync(path.join(root,'src/main/resources/templates/home.html'),'utf8');
    assert.equal(layout.includes('cursor-effects'),false);
    assert.equal(home.includes('hero-motion-toggle'),false);
});
