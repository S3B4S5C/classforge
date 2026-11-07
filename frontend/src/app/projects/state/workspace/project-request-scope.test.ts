import assert from 'node:assert/strict';
import test from 'node:test';
import { ProjectRequestScope } from './project-request-scope.ts';
import { Subject, finalize } from 'rxjs';

test('project loads and reloading the same project invalidate all earlier responses', () => {
  const scope = new ProjectRequestScope();
  const first = scope.begin('p1');
  const second = scope.begin('p2');
  assert.equal(scope.accepts(first), false);
  assert.equal(scope.accepts(second), true);
  const third = scope.begin('p2');
  assert.equal(scope.accepts(second), false);
  assert.equal(scope.accepts(third), true);
  scope.clear();
  assert.equal(scope.accepts(third), false);
});

test('late workspace success, failure and finalization cannot overwrite a new project operation', () => {
  const scope = new ProjectRequestScope();
  const state = { name: 'A', error: null as string | null, busy: false };
  function operation() {
    const token = scope.capture();
    const response = new Subject<string>();
    state.busy = true;
    response.pipe(finalize(scope.guard(token, () => { state.busy = false; }))).subscribe({
      next: scope.guard(token, name => { state.name = name; }),
      error: scope.guard(token, error => { state.error = error.message; }),
    });
    return response;
  }
  scope.begin('A');
  const oldSave = operation();
  const oldValidation = operation();
  scope.begin('B');
  state.name = 'B';
  const currentRename = operation();
  oldSave.next('A guardado');
  oldSave.complete();
  oldValidation.error(new Error('Error de validación A'));
  assert.deepEqual(state, { name: 'B', error: null, busy: true });
  currentRename.next('B renombrado');
  currentRename.complete();
  assert.deepEqual(state, { name: 'B renombrado', error: null, busy: false });
  const currentError = operation();
  currentError.error(new Error('Error de B'));
  assert.deepEqual(state, { name: 'B renombrado', error: 'Error de B', busy: false });
});
