const uuid='[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}';
const activity=new RegExp(`^/projects/${uuid}/activity$`,'i');
export function safeNotificationUrl(value,origin) {
  try {
    if(typeof value!=='string'||value.length>300)return '/projects';
    const url=new URL(value,origin);
    if(url.origin!==origin||url.username||url.password||url.hash)return '/projects';
    if(url.pathname==='/projects'&&!url.search)return '/projects';
    if(!activity.test(url.pathname))return '/projects';
    const keys=[...url.searchParams.keys()];
    if(keys.length && (keys.length!==1||keys[0]!=='sessionId'||!new RegExp(`^${uuid}$`,'i').test(url.searchParams.get('sessionId'))))return '/projects';
    return url.pathname+url.search;
  } catch {return '/projects';}
}
export function notificationView(payload,ownerId,origin) {
  if(!payload||typeof payload!=='object'||!ownerId||payload.ownerId!==ownerId||typeof payload.body!=='string')return null;
  const tag=typeof payload.tag==='string'&&new RegExp(`^classforge:${uuid}$`,'i').test(payload.tag)?payload.tag:'classforge:activity';
  return {title:'ClassForge',options:{body:payload.body.slice(0,180),tag,
    icon:'/assets/push/icon-192.png',badge:'/assets/push/badge-96.png',
    data:{url:safeNotificationUrl(payload.url,origin),ownerId}}};
}
