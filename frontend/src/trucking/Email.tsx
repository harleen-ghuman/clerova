import { Fragment } from "react";
import { emailBreaks } from "./format";

/** An email address that wraps after the "@" or at a dot instead of mid-word. */
export function Email({ value }: { value: string }) {
  return (
    <span className="tk-email">
      {emailBreaks(value).map((part, index) => (
        <Fragment key={index}>
          {index > 0 && <wbr />}
          {part}
        </Fragment>
      ))}
    </span>
  );
}
