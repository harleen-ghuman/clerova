// Loaded only when a PDF is opened in the file viewer (pdf.js is large).
import { GlobalWorkerOptions, getDocument, type PDFDocumentProxy } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";

GlobalWorkerOptions.workerSrc = workerUrl;

/** Most pages drawn at once; rate cons, BOLs and invoices are a few pages. */
export const MAX_PAGES = 30;

/** The PDF, and how to free it (and its worker) when the viewer is done. */
export async function openPdf(data: ArrayBuffer): Promise<{ pdf: PDFDocumentProxy; close: () => void }> {
  const task = getDocument({
    data,
    // Decoders for scanned pages and the standard fonts, served from /pdfjs/ (see vite.config.ts)
    wasmUrl: "/pdfjs/wasm/",
    standardFontDataUrl: "/pdfjs/standard_fonts/",
    cMapUrl: "/pdfjs/cmaps/",
    cMapPacked: true,
    iccUrl: "/pdfjs/iccs/",
    enableXfa: false,
  });

  return { pdf: await task.promise, close: () => void task.destroy() };
}

/** Draws one page to fit `width` CSS pixels, sharp on high-density screens. */
export async function drawPage(pdf: PDFDocumentProxy, number: number, canvas: HTMLCanvasElement, width: number) {
  const page = await pdf.getPage(number);
  const unscaled = page.getViewport({ scale: 1 });
  const viewport = page.getViewport({ scale: width / unscaled.width });
  const ratio = Math.min(window.devicePixelRatio || 1, 3);

  canvas.width = Math.floor(viewport.width * ratio);
  canvas.height = Math.floor(viewport.height * ratio);
  canvas.style.width = `${Math.floor(viewport.width)}px`;
  canvas.style.height = `${Math.floor(viewport.height)}px`;

  await page.render({
    canvas,
    viewport,
    transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0],
  }).promise;
}
