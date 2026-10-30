import { useLayoutEffect, useRef, type TextareaHTMLAttributes } from "react";

// Taller than this, the box scrolls instead of pushing the buttons away
const MAX_HEIGHT = 480;

/**
 * A text box that grows with its text, so a whole draft can be read
 * before approving it, without scrolling inside the box.
 */
export function AutoTextarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const ref = useRef<HTMLTextAreaElement>(null);

  useLayoutEffect(() => {
    const box = ref.current;

    if (!box) {
      return;
    }

    box.style.height = "auto";
    box.style.height = `${Math.min(box.scrollHeight + 2, MAX_HEIGHT)}px`;
  }, [props.value]);

  return <textarea ref={ref} {...props} />;
}
