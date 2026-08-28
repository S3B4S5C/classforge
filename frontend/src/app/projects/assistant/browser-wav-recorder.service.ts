import {
  Injectable,
} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class BrowserWavRecorderService {
  private stream:
    MediaStream | null = null;

  private context:
    AudioContext | null = null;

  private source:
    MediaStreamAudioSourceNode | null = null;

  private processor:
    ScriptProcessorNode | null = null;

  private chunks:
    Float32Array[] = [];

  private sampleRate =
    48_000;

  async start(): Promise<void> {
    if (this.stream) {
      throw new Error(
        'Ya existe una grabacion activa.',
      );
    }

    if (
      !navigator.mediaDevices
      || !navigator.mediaDevices.getUserMedia
    ) {
      throw new Error(
        'Este navegador no permite capturar el microfono.',
      );
    }

    const stream =
      await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          channelCount: 1,
        },
      });

    const context =
      new AudioContext();

    await context.resume();

    const source =
      context.createMediaStreamSource(
        stream,
      );

    const processor =
      context.createScriptProcessor(
        4096,
        1,
        1,
      );

    this.stream =
      stream;

    this.context =
      context;

    this.source =
      source;

    this.processor =
      processor;

    this.sampleRate =
      context.sampleRate;

    this.chunks = [];

    processor.onaudioprocess =
      (event) => {
        const input =
          event.inputBuffer.getChannelData(0);

        this.chunks.push(
          new Float32Array(input),
        );
      };

    source.connect(
      processor,
    );

    processor.connect(
      context.destination,
    );
  }

  async stop(): Promise<Blob> {
    if (
      !this.stream
      || !this.context
      || !this.processor
    ) {
      throw new Error(
        'No existe una grabacion activa.',
      );
    }

    const sampleRate =
      this.sampleRate;

    const samples =
      this.mergeChunks(
        this.chunks,
      );

    await this.cleanup();

    const durationSeconds =
      samples.length / sampleRate;

    if (durationSeconds < 0.25) {
      throw new Error(
        'La grabacion fue demasiado corta.',
      );
    }

    const resampled =
      this.resample(
        samples,
        sampleRate,
        16_000,
      );

    return new Blob(
      [
        this.encodeWav(
          resampled,
          16_000,
        ),
      ],
      {
        type: 'audio/wav',
      },
    );
  }

  async cancel(): Promise<void> {
    await this.cleanup();
    this.chunks = [];
  }

  private async cleanup(): Promise<void> {
    if (this.processor) {
      this.processor.onaudioprocess =
        null;

      this.processor.disconnect();
    }

    if (this.source) {
      this.source.disconnect();
    }

    this.stream
      ?.getTracks()
      .forEach(
        (track) =>
          track.stop(),
      );

    const context =
      this.context;

    this.processor = null;
    this.source = null;
    this.stream = null;
    this.context = null;

    if (
      context
      && context.state !== 'closed'
    ) {
      await context.close();
    }
  }

  private mergeChunks(
    chunks: Float32Array[],
  ): Float32Array {
    const length =
      chunks.reduce(
        (total, chunk) =>
          total + chunk.length,
        0,
      );

    const merged =
      new Float32Array(length);

    let offset = 0;

    for (const chunk of chunks) {
      merged.set(
        chunk,
        offset,
      );

      offset += chunk.length;
    }

    return merged;
  }

  private resample(
    input: Float32Array,
    sourceRate: number,
    targetRate: number,
  ): Float32Array {
    if (
      input.length === 0
      || sourceRate === targetRate
    ) {
      return input;
    }

    const outputLength =
      Math.max(
        1,
        Math.round(
          input.length
            * targetRate
            / sourceRate,
        ),
      );

    const output =
      new Float32Array(
        outputLength,
      );

    if (outputLength === 1) {
      output[0] =
        input[0] ?? 0;

      return output;
    }

    const scale =
      (input.length - 1)
      / (outputLength - 1);

    for (
      let index = 0;
      index < outputLength;
      index += 1
    ) {
      const position =
        index * scale;

      const lower =
        Math.floor(position);

      const upper =
        Math.min(
          input.length - 1,
          lower + 1,
        );

      const fraction =
        position - lower;

      output[index] =
        input[lower]
        + (
          input[upper]
          - input[lower]
        )
        * fraction;
    }

    return output;
  }

  private encodeWav(
    samples: Float32Array,
    sampleRate: number,
  ): ArrayBuffer {
    const dataBytes =
      samples.length * 2;

    const buffer =
      new ArrayBuffer(
        44 + dataBytes,
      );

    const view =
      new DataView(buffer);

    this.writeAscii(
      view,
      0,
      'RIFF',
    );

    view.setUint32(
      4,
      36 + dataBytes,
      true,
    );

    this.writeAscii(
      view,
      8,
      'WAVE',
    );

    this.writeAscii(
      view,
      12,
      'fmt ',
    );

    view.setUint32(
      16,
      16,
      true,
    );

    view.setUint16(
      20,
      1,
      true,
    );

    view.setUint16(
      22,
      1,
      true,
    );

    view.setUint32(
      24,
      sampleRate,
      true,
    );

    view.setUint32(
      28,
      sampleRate * 2,
      true,
    );

    view.setUint16(
      32,
      2,
      true,
    );

    view.setUint16(
      34,
      16,
      true,
    );

    this.writeAscii(
      view,
      36,
      'data',
    );

    view.setUint32(
      40,
      dataBytes,
      true,
    );

    let offset = 44;

    for (const sample of samples) {
      const clamped =
        Math.max(
          -1,
          Math.min(
            1,
            sample,
          ),
        );

      const pcm =
        clamped < 0
          ? clamped * 0x8000
          : clamped * 0x7fff;

      view.setInt16(
        offset,
        Math.round(pcm),
        true,
      );

      offset += 2;
    }

    return buffer;
  }

  private writeAscii(
    view: DataView,
    offset: number,
    value: string,
  ): void {
    for (
      let index = 0;
      index < value.length;
      index += 1
    ) {
      view.setUint8(
        offset + index,
        value.charCodeAt(index),
      );
    }
  }
}