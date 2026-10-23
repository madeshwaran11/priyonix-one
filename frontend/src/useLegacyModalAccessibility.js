import { useEffect } from "react";

// Existing feature dialogs predate the native dialog primitive. Keep keyboard
// focus inside those dialogs and return it to the control that opened them.
export function useLegacyModalAccessibility() {
  useEffect(() => {
    let current = null;
    let trigger = null;
    let previousOverflow = "";
    const focusable = (node) => [...node.querySelectorAll('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex="0"]')].filter((element) => element.getClientRects().length);
    function update() {
      const next = [...document.querySelectorAll('.dash-modal')].at(-1) || null;
      if (next === current) return;
      if (next && !current) {
        trigger = document.activeElement;
        previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
      }
      current = next;
      if (current) {
        current.setAttribute("role", "dialog");
        current.setAttribute("aria-modal", "true");
        if (!current.hasAttribute("aria-label") && !current.hasAttribute("aria-labelledby")) current.setAttribute("aria-label", current.querySelector("h3")?.textContent || "Record dialog");
        current.tabIndex = -1;
        (focusable(current)[0] || current).focus();
      } else {
        document.body.style.overflow = previousOverflow;
        if (trigger?.isConnected) trigger.focus();
        trigger = null;
      }
    }
    function trap(event) {
      if (!current) return;
      if (event.key === "Escape") {
        const close = [...current.querySelectorAll("button:not([disabled])")].find((button) => /^(cancel|close)$/i.test(button.textContent.trim()) || /close/i.test(button.getAttribute("aria-label") || ""));
        close?.click();
        return;
      }
      if (event.key !== "Tab") return;
      const elements = focusable(current);
      if (!elements.length) { event.preventDefault(); current.focus(); return; }
      const first = elements[0]; const last = elements.at(-1);
      if (event.shiftKey && (document.activeElement === first || !current.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
      if (!event.shiftKey && (document.activeElement === last || !current.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    }
    const observer = new MutationObserver(update);
    observer.observe(document.body, { childList: true, subtree: true });
    document.addEventListener("keydown", trap);
    return () => { observer.disconnect(); document.removeEventListener("keydown", trap); if (current) document.body.style.overflow = previousOverflow; };
  }, []);
}
