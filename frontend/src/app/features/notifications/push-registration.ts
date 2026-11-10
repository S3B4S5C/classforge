interface WorkerContainer<T> {
  getRegistration(scope:string):Promise<T|undefined>;
  register(script:string,options:{type:'module';scope:string}):Promise<T>;
  ready:Promise<T>;
}
export async function resolvePushRegistration<T>(container:WorkerContainer<T>,create:boolean):Promise<T|undefined> {
  const existing=await container.getRegistration('/');
  if(!create)return existing;
  await container.register('/push-worker.js',{type:'module',scope:'/'});
  return container.ready;
}
