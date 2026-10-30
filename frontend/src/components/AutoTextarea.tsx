import { useLayoutEffect, useRef, type TextareaHTMLAttributes } from "react";

// Taller than this, the box scrolls instead of pushing the buttons away
const MAX_HEIGHT = 480;

/**
 * A text box that grows with its text, so a whole draft can be read
 * before approving it, without scrolling inside the box. When empty, it
 * fits its placeholder instead, so an example is never cut off.
 */
export function AutoTextarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const { value, placeholder } = props;

  useLayoutEffect(() => {
    const box = ref.current;

    if (!box) {
      return;
    }

    function fit(target: HTMLTextAreaElement) {
      // The browser doesn't measure a placeholder, so measure it as text
      const empty = target.value === "" && Boolean(target.placeholder);

      if (empty) {
        target.value = target.placeholder;
      }

      target.style.height = "auto";
      target.style.height = `${Math.min(target.scrollHeight + 2, MAX_HEIGHT)}px`;

      if (empty) {
        target.value = "";
      }
    }

    fit(box);

    // A narrower window wraps the text onto more lines
    const onResize = () => fit(box);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [value, placeholder]);

  return <textarea ref={ref} {...props} />;
}
