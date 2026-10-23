import { HttpErrorResponse } from '@angular/common/http';

export interface ApiFieldError {
  field: string;
  message: string;
}

/** An API failure (RFC 9457 problem+json) reduced to what the screens need. */
export interface ApiProblem {
  status: number;
  title?: string;
  detail?: string;
  correlationId?: string;
  errors: ApiFieldError[];
  retryAfterSeconds?: number;
}

function text(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function fieldErrors(value: unknown): ApiFieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.flatMap((item) => {
    const entry = (item ?? {}) as Record<string, unknown>;
    return typeof entry['field'] === 'string' && typeof entry['message'] === 'string'
      ? [{ field: entry['field'], message: entry['message'] }]
      : [];
  });
}

function retryAfter(error: HttpErrorResponse): number | undefined {
  const raw = error.headers?.get('Retry-After');
  const seconds = raw === null || raw === undefined ? NaN : Number(raw);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined;
}

/** Reads a failed HTTP call. A Blob body (binary requests) needs `toApiProblemAsync`. */
export function toApiProblem(error: unknown): ApiProblem {
  if (!(error instanceof HttpErrorResponse) || error.status === 0) {
    return { status: 0, detail: 'network', errors: [] };
  }
  const body = (
    typeof error.error === 'object' && error.error !== null ? error.error : {}
  ) as Record<string, unknown>;
  return {
    status: error.status,
    title: text(body['title']),
    detail: text(body['detail']),
    correlationId: text(body['correlationId']),
    errors: fieldErrors(body['errors']),
    retryAfterSeconds: retryAfter(error),
  };
}

/** Like `toApiProblem`, but also reads a problem that arrived as a Blob body. */
export async function toApiProblemAsync(error: unknown): Promise<ApiProblem> {
  if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
    let parsed: unknown = null;
    try {
      parsed = JSON.parse(await error.error.text());
    } catch {
      // Not a problem document: keep the status only.
    }
    return toApiProblem(
      new HttpErrorResponse({ status: error.status, headers: error.headers, error: parsed }),
    );
  }
  return toApiProblem(error);
}
