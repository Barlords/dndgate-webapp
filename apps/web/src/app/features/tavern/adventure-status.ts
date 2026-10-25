export type Status = 'NOT_STARTED' | 'ONGOING' | 'FINISH' | 'ABORT';
export type Visibility = 'PUBLIC' | 'PRIVATE';

/** The status machine of the API: nothing leaves FINISH or ABORT, nothing goes back to NOT_STARTED. */
export function allowedTransitions(status: Status): Status[] {
  switch (status) {
    case 'NOT_STARTED':
      return ['ONGOING', 'ABORT'];
    case 'ONGOING':
      return ['FINISH', 'ABORT'];
    default:
      return [];
  }
}

/** A finished or abandoned adventure is closed for good. */
export function isClosed(status: Status): boolean {
  return status === 'FINISH' || status === 'ABORT';
}
