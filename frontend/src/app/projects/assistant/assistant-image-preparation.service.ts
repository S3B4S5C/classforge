import { Injectable } from '@angular/core';

export interface AssistantPreparedImage {
  file: File;
  previewUrl: string;
  rotation: number;
  cropInsetPercent: number;
}

@Injectable({ providedIn: 'root' })
export class AssistantImagePreparationService {
  async prepare(
    source: File,
    rotation: number,
    cropInsetPercent: number,
  ): Promise<AssistantPreparedImage> {
    const bitmap = await createImageBitmap(source);
    try {
      const crop = Math.max(0, Math.min(35, cropInsetPercent)) / 100;
      const sx = Math.round(bitmap.width * crop);
      const sy = Math.round(bitmap.height * crop);
      const sw = Math.max(1, bitmap.width - sx * 2);
      const sh = Math.max(1, bitmap.height - sy * 2);
      const normalizedRotation = ((rotation % 360) + 360) % 360;
      const swapped = normalizedRotation === 90 || normalizedRotation === 270;
      const canvas = document.createElement('canvas');
      canvas.width = swapped ? sh : sw;
      canvas.height = swapped ? sw : sh;
      const context = canvas.getContext('2d');
      if (!context) {
        throw new Error('No se pudo preparar el canvas de imagen.');
      }

      context.translate(canvas.width / 2, canvas.height / 2);
      context.rotate((normalizedRotation * Math.PI) / 180);
      context.drawImage(
        bitmap,
        sx,
        sy,
        sw,
        sh,
        -sw / 2,
        -sh / 2,
        sw,
        sh,
      );

      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (value) => value ? resolve(value) : reject(new Error('No se pudo codificar la imagen preparada.')),
          'image/png',
        );
      });

      const baseName = source.name.replace(/\.[^.]+$/, '') || 'diagram';
      const file = new File([blob], `${baseName}-prepared.png`, { type: 'image/png' });
      return {
        file,
        previewUrl: URL.createObjectURL(file),
        rotation: normalizedRotation,
        cropInsetPercent: Math.round(crop * 100),
      };
    } finally {
      bitmap.close();
    }
  }
}
