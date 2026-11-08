'use strict';
const CACHE_NAME='binary30-shell-v1';
const CORE_ASSETS=['./','./index.html','./bundle-1.txt','./bundle-2.txt','./bundle-3.txt','./manifest.webmanifest','./icon.svg'];
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(CORE_ASSETS)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE_NAME).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',event=>{
 const request=event.request;
 if(request.method!=='GET'||new URL(request.url).origin!==self.location.origin)return;
 event.respondWith(caches.match(request).then(cached=>{
  if(cached)return cached;
  return fetch(request).then(response=>{
   if(response&&response.ok&&new URL(request.url).pathname.startsWith(new URL(self.registration.scope).pathname)){
    const copy=response.clone();caches.open(CACHE_NAME).then(cache=>cache.put(request,copy));
   }
   return response;
  }).catch(()=>caches.match('./index.html'));
 }));
});