import { onMounted, onUnmounted } from "vue";

const FOCUSABLE =
  'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

/** 동적으로 열리는 모든 modal dialog에 focus trap과 focus 복원을 일관되게 적용한다. */
export function useDialogAccessibility(): void {
  let activeDialog: HTMLElement | null = null;
  let returnTarget: HTMLElement | null = null;
  let observer: MutationObserver | undefined;

  /** 현재 DOM에서 가장 위에 렌더링된 modal dialog를 찾는다. */
  function currentDialog(): HTMLElement | null {
    // 기존 화면의 modal도 중앙 계약으로 보완해 누락된 ARIA 선언이 focus trap을 우회하지 않게 한다.
    document
      .querySelectorAll<HTMLElement>(".delivery-modal-backdrop .delivery-modal")
      .forEach((dialog) => {
        dialog.setAttribute("role", "dialog");
        dialog.setAttribute("aria-modal", "true");
      });
    const dialogs = [
      ...document.querySelectorAll<HTMLElement>(
        '[role="dialog"][aria-modal="true"]',
      ),
    ].filter((dialog) => dialog.getClientRects().length > 0);
    return dialogs.length ? dialogs[dialogs.length - 1] : null;
  }

  /** dialog 등장과 제거를 감지해 최초 focus와 복원 대상을 관리한다. */
  function synchronize(): void {
    const next = currentDialog();
    if (next === activeDialog) return;
    if (!next && activeDialog) returnTarget?.focus();
    if (next) {
      returnTarget =
        document.activeElement instanceof HTMLElement
          ? document.activeElement
          : null;
      if (!next.hasAttribute("tabindex")) next.tabIndex = -1;
      queueMicrotask(() =>
        (next.querySelector<HTMLElement>(FOCUSABLE) || next).focus(),
      );
    }
    activeDialog = next;
  }

  /** Tab/Shift+Tab이 활성 modal 바깥으로 이탈하지 않도록 순환시킨다. */
  function trap(event: KeyboardEvent): void {
    if (event.key !== "Tab" || !activeDialog) return;
    const focusable = [
      ...activeDialog.querySelectorAll<HTMLElement>(FOCUSABLE),
    ].filter((element) => element.getClientRects().length > 0);
    if (!focusable.length) {
      event.preventDefault();
      activeDialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  onMounted(() => {
    document.addEventListener("keydown", trap, true);
    observer = new MutationObserver(synchronize);
    observer.observe(document.body, { childList: true, subtree: true });
    synchronize();
  });
  onUnmounted(() => {
    document.removeEventListener("keydown", trap, true);
    observer?.disconnect();
  });
}
