import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { toApiProblem, toApiProblemAsync } from './api-problem';

function failure(status: number, error: unknown, headers?: Record<string, string>) {
  return new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) });
}

describe('toApiProblem', () => {
  it('reads detail, correlationId and field errors', () => {
    const problem = toApiProblem(
      failure(400, {
        title: 'Bad Request',
        detail: 'Invalid input',
        correlationId: 'abc-123',
        errors: [{ field: 'email', message: 'must be valid' }],
      }),
    );
    expect(problem).toEqual({
      status: 400,
      title: 'Bad Request',
      detail: 'Invalid input',
      correlationId: 'abc-123',
      errors: [{ field: 'email', message: 'must be valid' }],
      retryAfterSeconds: undefined,
    });
  });

  it('reads a problem from a Blob body', async () => {
    const blob = new Blob([JSON.stringify({ detail: 'No picture', correlationId: 'c-9' })], {
      type: 'application/problem+json',
    });
    const problem = await toApiProblemAsync(failure(404, blob));
    expect(problem.status).toBe(404);
    expect(problem.detail).toBe('No picture');
    expect(problem.correlationId).toBe('c-9');
  });

  it('keeps the status when a Blob body is not JSON', async () => {
    const problem = await toApiProblemAsync(failure(500, new Blob(['oops'])));
    expect(problem).toMatchObject({ status: 500, errors: [] });
  });

  it('reads Retry-After as seconds', () => {
    const problem = toApiProblem(failure(429, null, { 'Retry-After': '12' }));
    expect(problem.retryAfterSeconds).toBe(12);
  });

  it('ignores a Retry-After that is not a number', () => {
    const problem = toApiProblem(failure(429, null, { 'Retry-After': 'soon' }));
    expect(problem.retryAfterSeconds).toBeUndefined();
  });

  it('maps a network error to status 0', () => {
    const problem = toApiProblem(failure(0, new ProgressEvent('error')));
    expect(problem).toMatchObject({ status: 0, detail: 'network', errors: [] });
  });

  it('maps anything else to status 0 too', async () => {
    expect(toApiProblem(new Error('boom'))).toMatchObject({ status: 0, detail: 'network' });
    expect(await toApiProblemAsync(new Error('boom'))).toMatchObject({ status: 0 });
  });

  it('ignores malformed field errors', () => {
    const problem = toApiProblem(
      failure(400, { errors: [{ field: 1 }, 'x', { field: 'a', message: 'm' }] }),
    );
    expect(problem.errors).toEqual([{ field: 'a', message: 'm' }]);
  });
});
