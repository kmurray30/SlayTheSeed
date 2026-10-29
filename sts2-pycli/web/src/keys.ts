/** True when the event target is a text-entry control, so global shortcuts
 *  should not hijack the keystroke (e.g. typing a seed). */
export function isTextInput(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName);
}
