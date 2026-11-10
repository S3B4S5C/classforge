interface DisposableSubscription { unsubscribe(): Promise<boolean>; }
export function ownerBindingMessage(ownerId:string|null,expectedSessionOwner:string|null,currentSessionOwner:string|null) {
  return expectedSessionOwner===currentSessionOwner?{type:'CLASSFORGE_PUSH_OWNER',ownerId}:null;
}
interface EnrollmentPorts<T extends DisposableSubscription> {
  permission(): Promise<string>;
  subscribe(): Promise<T>;
  register(subscription:T): Promise<void>;
  bind(ownerId:string): Promise<void>;
  currentOwner(): string | null;
}
/** Recheck identity across every browser/network boundary, including other tabs. */
export class PushEnrollment {
  private user:string|null=null;
  private epoch=0;
  owner():string|null { return this.user; }
  setOwner(owner:string|null):void { if(owner!==this.user){this.user=owner;this.epoch++;} }
  async enroll<T extends DisposableSubscription>(ports:EnrollmentPorts<T>):Promise<T> {
    const owner=this.user,epoch=this.epoch;
    const current=()=>!!owner&&this.user===owner&&this.epoch===epoch&&ports.currentOwner()===owner;
    const check=()=>{if(!current())throw Error('Cambió la sesión; vuelve a activar las notificaciones.');};
    check();const permission=await ports.permission();check();
    if(permission!=='granted')throw Error('No se concedió el permiso de notificaciones. Puedes cambiarlo en los ajustes del navegador.');
    const subscription=await ports.subscribe();
    try {check();await ports.register(subscription);check();await ports.bind(owner!);check();return subscription;}
    catch(error){if(!current())await subscription.unsubscribe().catch(()=>false);throw error;}
  }
}
