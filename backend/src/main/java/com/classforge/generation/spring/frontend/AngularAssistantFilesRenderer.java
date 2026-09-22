package com.classforge.generation.spring.frontend;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class AngularAssistantFilesRenderer {

    String assistantService() {
        return """
                import { HttpClient } from '@angular/common/http';
                import { inject, Injectable } from '@angular/core';
                import { Observable } from 'rxjs';

                export interface AssistantPlanResponse {
                  source: string;
                  transcript: string;
                  intent: string;
                  summary: string;
                  requiresConfirmation: boolean;
                  previewToken: string | null;
                  result: unknown | null;
                }

                @Injectable({ providedIn: 'root' })
                export class AssistantService {
                  private readonly http = inject(HttpClient);

                  plan(text: string): Observable<AssistantPlanResponse> {
                    return this.http.post<AssistantPlanResponse>('/api/assistant/plan', { text });
                  }

                  voice(audio: Blob): Observable<AssistantPlanResponse> {
                    const body = new FormData();
                    body.append('audio', audio, 'generated-assistant.wav');
                    return this.http.post<AssistantPlanResponse>('/api/assistant/voice', body);
                  }

                  apply(previewToken: string): Observable<AssistantPlanResponse> {
                    return this.http.post<AssistantPlanResponse>('/api/assistant/apply', { previewToken });
                  }
                }
                """;
    }

    String browserWavRecorder() {
        return """
                import { Injectable } from '@angular/core';

                @Injectable({ providedIn: 'root' })
                export class BrowserWavRecorderService {
                  private stream: MediaStream | null = null;
                  private context: AudioContext | null = null;
                  private source: MediaStreamAudioSourceNode | null = null;
                  private processor: ScriptProcessorNode | null = null;
                  private chunks: Float32Array[] = [];
                  private sampleRate = 48_000;

                  async start(): Promise<void> {
                    if (this.stream) throw new Error('Ya existe una grabacion activa.');
                    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Este navegador no permite capturar el microfono.');
                    const stream = await navigator.mediaDevices.getUserMedia({
                      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true, channelCount: 1 },
                    });
                    const context = new AudioContext();
                    await context.resume();
                    const source = context.createMediaStreamSource(stream);
                    const processor = context.createScriptProcessor(4096, 1, 1);
                    this.stream = stream; this.context = context; this.source = source; this.processor = processor;
                    this.sampleRate = context.sampleRate; this.chunks = [];
                    processor.onaudioprocess = (event) => this.chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
                    source.connect(processor); processor.connect(context.destination);
                  }

                  async stop(): Promise<Blob> {
                    if (!this.stream || !this.context || !this.processor) throw new Error('No existe una grabacion activa.');
                    const rate = this.sampleRate;
                    const samples = this.merge(this.chunks);
                    await this.cleanup();
                    if (samples.length / rate < 0.25) throw new Error('La grabacion fue demasiado corta.');
                    const resampled = this.resample(samples, rate, 16_000);
                    return new Blob([this.wav(resampled, 16_000)], { type: 'audio/wav' });
                  }

                  async cancel(): Promise<void> { await this.cleanup(); this.chunks = []; }

                  private async cleanup(): Promise<void> {
                    if (this.processor) { this.processor.onaudioprocess = null; this.processor.disconnect(); }
                    this.source?.disconnect(); this.stream?.getTracks().forEach((track) => track.stop());
                    const context = this.context;
                    this.processor = null; this.source = null; this.stream = null; this.context = null;
                    if (context && context.state !== 'closed') await context.close();
                  }
                  private merge(chunks: Float32Array[]): Float32Array {
                    const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
                    const out = new Float32Array(length); let offset = 0;
                    for (const chunk of chunks) { out.set(chunk, offset); offset += chunk.length; }
                    return out;
                  }
                  private resample(input: Float32Array, sourceRate: number, targetRate: number): Float32Array {
                    if (!input.length || sourceRate === targetRate) return input;
                    const length = Math.max(1, Math.round(input.length * targetRate / sourceRate));
                    const out = new Float32Array(length);
                    const scale = length === 1 ? 0 : (input.length - 1) / (length - 1);
                    for (let i = 0; i < length; i += 1) {
                      const position = i * scale; const lower = Math.floor(position); const upper = Math.min(input.length - 1, lower + 1);
                      const fraction = position - lower; out[i] = input[lower] + (input[upper] - input[lower]) * fraction;
                    }
                    return out;
                  }
                  private wav(samples: Float32Array, sampleRate: number): ArrayBuffer {
                    const bytes = samples.length * 2; const buffer = new ArrayBuffer(44 + bytes); const view = new DataView(buffer);
                    this.ascii(view, 0, 'RIFF'); view.setUint32(4, 36 + bytes, true); this.ascii(view, 8, 'WAVE'); this.ascii(view, 12, 'fmt ');
                    view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true);
                    view.setUint32(28, sampleRate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true); this.ascii(view, 36, 'data'); view.setUint32(40, bytes, true);
                    let offset = 44;
                    for (const sample of samples) { const value = Math.max(-1, Math.min(1, sample)); view.setInt16(offset, Math.round(value < 0 ? value * 0x8000 : value * 0x7fff), true); offset += 2; }
                    return buffer;
                  }
                  private ascii(view: DataView, offset: number, value: string): void {
                    for (let i = 0; i < value.length; i += 1) view.setUint8(offset + i, value.charCodeAt(i));
                  }
                }
                """;
    }

    String assistantComponent() {
        return """
                import { ChangeDetectorRef, Component, DestroyRef, inject, OnDestroy } from '@angular/core';
                import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
                import { FormsModule } from '@angular/forms';
                import { AssistantPlanResponse, AssistantService } from './assistant.service';
                import { BrowserWavRecorderService } from './browser-wav-recorder.service';

                interface AssistantMessage { role: 'user' | 'assistant'; text: string; detail?: string; }

                @Component({
                  standalone: true,
                  imports: [FormsModule],
                  template: `
                    <main class="page">
                      <div class="page-header"><div><h1>Asistente</h1><p class="muted">Lenguaje natural y voz usando Whisper + Qwen locales a traves del backend generado.</p></div></div>
                      <section class="card">
                        <div class="assistant-history">
                          @for (message of messages; track $index) {
                            <article class="detail-item"><strong>{{ message.role === 'user' ? 'Tu' : 'Asistente' }}</strong><div>{{ message.text }}</div>@if (message.detail) { <pre>{{ message.detail }}</pre> }</article>
                          }
                        </div>
                        @if (pending) {
                          <div class="detail-item"><strong>Confirmacion requerida</strong><p>{{ pending.summary }}</p><div class="actions"><button type="button" class="btn btn-primary" (click)="apply()" [disabled]="busy">Confirmar</button><button type="button" class="btn" (click)="pending = null" [disabled]="busy">Cancelar</button></div></div>
                        }
                        @if (error) { <p class="error">{{ error }}</p> }
                        <label>Instruccion<textarea rows="3" [(ngModel)]="text" [disabled]="busy || recording"></textarea></label>
                        <div class="actions">
                          <button type="button" class="btn btn-primary" (click)="send()" [disabled]="busy || recording || !text.trim()">Enviar</button>
                          @if (!recording) { <button type="button" class="btn" (click)="startVoice()" [disabled]="busy">🎤 Hablar</button> }
                          @else { <button type="button" class="btn btn-primary" (click)="stopVoice()" [disabled]="busy">Detener y enviar</button> }
                        </div>
                      </section>
                    </main>
                  `,
                })
                export class AssistantComponent implements OnDestroy {
                  private readonly changes = inject(ChangeDetectorRef);
                  private readonly destroyRef = inject(DestroyRef);
                  private readonly assistant = inject(AssistantService);
                  private readonly recorder = inject(BrowserWavRecorderService);
                  text = '';
                  busy = false;
                  recording = false;
                  error = '';
                  pending: AssistantPlanResponse | null = null;
                  readonly messages: AssistantMessage[] = [];

                  ngOnDestroy(): void { void this.recorder.cancel(); }

                  send(): void {
                    if (this.busy || this.recording) return;
                    this.pending = null;
                    const value = this.text.trim(); if (!value) return;
                    this.messages.push({ role: 'user', text: value }); this.text = ''; this.busy = true; this.error = '';
                    this.assistant.plan(value).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: (result) => this.accept(result), error: (error) => this.fail(error) });
                  }
                  async startVoice(): Promise<void> {
                    if (this.busy || this.recording) return;
                    this.error = ''; this.busy = true; this.pending = null;
                    try {
                      await this.recorder.start();
                      if (this.destroyRef.destroyed) { await this.recorder.cancel(); return; }
                      this.recording = true;
                    } catch (error) { this.error = this.message(error); }
                    finally { this.busy = false; if (!this.destroyRef.destroyed) this.changes.markForCheck(); }
                  }
                  async stopVoice(): Promise<void> {
                    if (this.busy || !this.recording) return;
                    this.busy = true; this.error = '';
                    try {
                      const blob = await this.recorder.stop(); this.recording = false;
                      if (this.destroyRef.destroyed) return;
                      this.changes.markForCheck();
                      this.assistant.voice(blob).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: (result) => { this.messages.push({ role: 'user', text: result.transcript }); this.accept(result); }, error: (error) => this.fail(error) });
                    } catch (error) { this.recording = false; this.fail(error); }
                  }
                  apply(): void {
                    if (this.busy || this.recording) return;
                    const token = this.pending?.previewToken; if (!token) return;
                    this.busy = true; this.error = '';
                    this.assistant.apply(token).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: (result) => { this.pending = null; this.accept(result); }, error: (error) => this.fail(error) });
                  }
                  private accept(result: AssistantPlanResponse): void {
                    this.busy = false;
                    this.changes.markForCheck();
                    if (result.requiresConfirmation) { this.pending = result; this.messages.push({ role: 'assistant', text: result.summary }); return; }
                    this.pending = null;
                    this.messages.push({ role: 'assistant', text: result.summary, detail: result.result == null ? undefined : JSON.stringify(result.result, null, 2) });
                  }
                  private fail(error: unknown): void { this.busy = false; this.error = this.message(error); if (!this.destroyRef.destroyed) this.changes.markForCheck(); }
                  private message(error: unknown): string {
                    if (typeof error === 'object' && error !== null && 'error' in error) {
                      const body = (error as { error?: { message?: string } }).error; if (body?.message) return body.message;
                    }
                    return error instanceof Error ? error.message : 'No se pudo ejecutar el Assistant.';
                  }
                }
                """;
    }
}
