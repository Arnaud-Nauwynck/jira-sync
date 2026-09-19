/** Extracts the most meaningful message from a failed HttpClient call, for display to the end-user. */
export function httpErrorMessage(err: any): string {
  return err?.error?.message ?? err?.message ?? String(err);
}
