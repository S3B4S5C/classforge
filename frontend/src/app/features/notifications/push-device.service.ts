import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { PushEnrollment, ownerBindingMessage } from './push-enrollment';
import { resolvePushRegistration } from './push-registration';

const OWNER='classforge.push.owner';
@Injectable({providedIn:'root'})
export class PushDeviceService {
  private readonly flow=new PushEnrollment();
  private cleanup:Promise<unknown>=Promise.resolve();
  readonly supported=typeof window!=='undefined'&&window.isSecureContext&&'serviceWorker' in navigator&&'PushManager' in window&&'Notification' in window;
  readonly enabled=signal(false);
  readonly active=signal(false);
  readonly busy=signal(false);
  readonly permission=signal<NotificationPermission|'unsupported'>(this.supported?Notification.permission:'unsupported');
  readonly feedback=signal<string|null>(null);
  private publicKey='';
  constructor(private readonly http:HttpClient) {
    window.addEventListener('storage',event=>{
      if(event.key===OWNER&&event.newValue!==this.flow.owner()){
        this.flow.setOwner(null);this.active.set(false);this.enabled.set(false);
        this.feedback.set('La cuenta cambió en otra pestaña. Recarga esta página para continuar.');
      }
    });
  }
  private owner():string|null {return localStorage.getItem(OWNER);}
  setUserId(id:string|null):void {
    const changed=this.flow.owner()!==id;
    this.flow.setOwner(id);this.active.set(false);this.feedback.set(null);
    if(!id){localStorage.removeItem(OWNER);void this.bind(null).catch(()=>{});return;}
    localStorage.setItem(OWNER,id);
    // Quarantine the previous account before any network-dependent status check.
    if(changed)this.cleanup=this.bind(null,id).catch(()=>{});
    void this.refresh();
  }
  private current(id:string|null):boolean {return !!id&&this.flow.owner()===id&&this.owner()===id;}
  private request<T>(value:import('rxjs').Observable<T>):Promise<T>{return firstValueFrom(value.pipe(timeout(8000)));}
  private async registration(create=false):Promise<ServiceWorkerRegistration|undefined> {
    if(!this.supported)return undefined;
    return resolvePushRegistration(navigator.serviceWorker,create);
  }
  private async bind(ownerId:string|null,expectedSessionOwner:string|null=ownerId):Promise<void> {
    const registration=await this.registration(false);if(!registration?.active)return;
    // Do not let an old tab overwrite the binding selected in another tab.
    const message=ownerBindingMessage(ownerId,expectedSessionOwner,this.owner());
    if(!message)return;
    await new Promise<void>((resolve,reject)=>{
      const channel=new MessageChannel();
      const timer=setTimeout(()=>{channel.port1.close();reject(Error('No pudimos preparar las notificaciones. Reintenta.'));},5000);
      channel.port1.onmessage=event=>{clearTimeout(timer);channel.port1.close();event.data?.ok?resolve():reject(Error('No pudimos guardar el estado push.'));};
      registration.active!.postMessage(message,[channel.port2]);
    });
  }
  private async subscriptionLost(user:string):Promise<void> {
    if(!this.current(user))return;
    this.active.set(false);
    this.feedback.set('La suscripción de este navegador ya no está disponible. Revisa los permisos de notificaciones de ClassForge y Chrome en Android y vuelve a activar push.');
    await this.bind(null,user);
  }
  async refresh():Promise<void> {
    const user=this.flow.owner();if(!user||!this.supported||this.busy())return;
    try {
      await this.cleanup;if(!this.current(user))return;
      const config=await this.request(this.http.get<{enabled:boolean;publicKey:string}>('/api/push/config'));
      if(!this.current(user))return;
      this.enabled.set(config.enabled);this.publicKey=config.publicKey;this.permission.set(Notification.permission);
      if(!config.enabled)return;
      const registration=await this.registration(false),subscription=await registration?.pushManager.getSubscription();
      if(!this.current(user))return;
      if(!subscription){if(this.active())await this.subscriptionLost(user);return;}
      const status=await this.request(this.http.post<{registered:boolean}>('/api/push/subscriptions/status',{endpoint:subscription.endpoint}));
      if(!this.current(user))return;
      if(status.registered){await this.bind(user);if(this.current(user))this.active.set(true);}
      else {await subscription.unsubscribe();if(this.current(user))await this.bind(null,user);}
    } catch {if(this.current(user))this.feedback.set('No pudimos comprobar las notificaciones push. Vuelve a abrir este panel para reintentar.');}
  }
  async enable():Promise<void> {
    if(!this.supported||!this.enabled()||this.busy()||!this.current(this.flow.owner()))return;
    // Request permission in the click task, before any network/worker await.
    const permission=Notification.permission==='granted'?Promise.resolve('granted'):Notification.requestPermission();
    this.busy.set(true);this.feedback.set(null);
    try {
      await this.flow.enroll({
        permission:()=>permission,
        currentOwner:()=>this.owner(),
        subscribe:async()=>{
          await this.cleanup;
          const registration=(await this.registration(true))!;
          const existing=await registration.pushManager.getSubscription();
          if(existing)return existing;
          const normalized=this.publicKey.replace(/-/g,'+').replace(/_/g,'/');
          const key=Uint8Array.from(atob(normalized.padEnd(Math.ceil(normalized.length/4)*4,'=')),c=>c.charCodeAt(0));
          return registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:key});
        },
        register:async subscription=>{await this.request(this.http.post<void>('/api/push/subscriptions',subscription.toJSON()));},
        bind:ownerId=>this.bind(ownerId),
      });
      this.active.set(true);this.feedback.set('Notificaciones activadas en este dispositivo. Puedes enviar un aviso de prueba.');
    } catch(error) {this.feedback.set(this.message(error,'No pudimos activar push. Comprueba la conexión y vuelve a intentar.'));}
    finally {this.permission.set(Notification.permission);this.busy.set(false);}
  }
  async disable():Promise<void> {
    const user=this.flow.owner();if(!this.supported||!this.current(user))return;
    this.busy.set(true);
    this.cleanup=(async()=>{
      const registration=await this.registration(false),subscription=await registration?.pushManager.getSubscription();
      if(!this.current(user))return;
      if(subscription){
        try {await this.request(this.http.delete<void>('/api/push/subscriptions',{body:{endpoint:subscription.endpoint}}));}
        finally {if(this.current(user))await subscription.unsubscribe();}
      }
      if(this.current(user)){await this.bind(null,user);this.active.set(false);}
    })();
    try {await this.cleanup;this.feedback.set('Notificaciones desactivadas en este dispositivo.');}
    catch {this.feedback.set('No pudimos confirmar la desactivación. Reintenta con conexión.');}
    finally {this.cleanup=Promise.resolve();this.busy.set(false);}
  }
  async test():Promise<void> {
    if(!this.active()||this.busy())return;
    const user=this.flow.owner();
    this.busy.set(true);this.feedback.set(null);
    try {
      const subscription=await (await this.registration(false))?.pushManager.getSubscription();
      if(!this.current(user))return;
      if(!subscription){
        await this.subscriptionLost(user!);return;
      }
      await this.request(this.http.post<void>('/api/push/subscriptions/test',{endpoint:subscription.endpoint}));
      this.feedback.set('Aviso de prueba en cola. Debería aparecer en las notificaciones del dispositivo.');
    } catch(error){this.feedback.set(this.message(error,'No pudimos enviar la prueba. Reintenta más tarde.'));}
    finally {this.busy.set(false);}
  }
  private message(error:unknown,fallback:string):string {
    const response=error as {error?:{message?:unknown};message?:unknown};
    if(typeof response.error?.message==='string'&&response.error.message.length<250)return response.error.message;
    return error instanceof Error&&!('status' in error)?error.message:fallback;
  }
}
