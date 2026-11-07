import { filter, interval, type SchedulerLike } from 'rxjs';
/** Covers messages published before a broker has registered SUBSCRIBE. */
export function assistantReconciliationTicks(connected: () => boolean, scheduler?: SchedulerLike) {
  return interval(5000, scheduler).pipe(filter(() => connected()));
}
