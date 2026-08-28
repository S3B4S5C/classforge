export class UmlCommandError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly elementId: string | null = null,
  ) {
    super(message);
    this.name = 'UmlCommandError';
  }
}