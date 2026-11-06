import { createRoot, type Root } from "react-dom/client";

import { FileViewer, type ViewerFile } from "../components/FileViewer";

let host: { element: HTMLDivElement; root: Root } | null = null;

/**
 * Opens a photo or PDF over the app, so people stay in Clerova (a new tab
 * strands them, above all in the home-screen app). Back or Close returns.
 */
export function viewFile(file: ViewerFile) {
  if (!host) {
    const element = document.createElement("div");
    document.body.appendChild(element);
    host = { element, root: createRoot(element) };
  }

  const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;

  host.root.render(
    <FileViewer
      key={Date.now()}
      file={file}
      onClosed={() => {
        host?.root.render(null);
        opener?.focus();
      }}
    />
  );
}

