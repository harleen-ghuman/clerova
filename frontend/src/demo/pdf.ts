/**
 * A very small PDF maker for the demo: pages of text lines in Helvetica.
 * Enough for sample rate cons, PODs and invoices without a server.
 */

export interface PdfLine {
  text: string;
  x: number;
  y: number;
  size?: number;
  bold?: boolean;
  grey?: boolean;
}

/** Letter size in points. */
export const PAGE_WIDTH = 612;
export const PAGE_HEIGHT = 792;

function clean(text: string): string {
  return text
    .replace(/→/g, "to")
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[–—]/g, "-")
    .replace(/[^\x20-\x7E]/g, "")
    .replace(/\\/g, "\\\\")
    .replace(/\(/g, "\\(")
    .replace(/\)/g, "\\)");
}

function content(lines: PdfLine[]): string {
  return lines
    .map((line) => {
      const colour = line.grey ? "0.36 0.39 0.44 rg" : "0.09 0.09 0.11 rg";
      return `BT ${colour} /${line.bold ? "F2" : "F1"} ${line.size ?? 10} Tf ${line.x} ${line.y} Td (${clean(line.text)}) Tj ET`;
    })
    .join("\n");
}

/** Builds a PDF from pages of lines. */
export function makePdf(pages: PdfLine[][]): Blob {
  const objects: string[] = [];
  const add = (body: string) => {
    objects.push(body);
    return objects.length;
  };

  const catalog = add("");
  const pagesId = add("");
  const regular = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
  const bold = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
  const pageIds: number[] = [];

  for (const lines of pages) {
    const stream = content(lines);
    const streamId = add(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
    pageIds.push(
      add(
        `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 ${PAGE_WIDTH} ${PAGE_HEIGHT}] ` +
          `/Resources << /Font << /F1 ${regular} 0 R /F2 ${bold} 0 R >> >> /Contents ${streamId} 0 R >>`
      )
    );
  }

  objects[catalog - 1] = `<< /Type /Catalog /Pages ${pagesId} 0 R >>`;
  objects[pagesId - 1] = `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pageIds.length} >>`;

  let pdf = "%PDF-1.4\n";
  const offsets: number[] = [];

  objects.forEach((body, index) => {
    offsets.push(pdf.length);
    pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
  });

  const xref = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`).join("");
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${catalog} 0 R >>\nstartxref\n${xref}\n%%EOF`;

  return new Blob([pdf], { type: "application/pdf" });
}

/** Lays out text top to bottom, starting a new page when one fills up. */
export class PageWriter {
  pages: PdfLine[][] = [[]];
  y = PAGE_HEIGHT - 60;

  line(text: string, options: { size?: number; bold?: boolean; grey?: boolean; x?: number; gap?: number } = {}) {
    const size = options.size ?? 10;

    if (this.y < 60) {
      this.pages.push([]);
      this.y = PAGE_HEIGHT - 60;
    }

    this.pages[this.pages.length - 1].push({ text, x: options.x ?? 54, y: this.y, size, bold: options.bold, grey: options.grey });
    this.y -= options.gap ?? size * 1.5;
  }

  /** A label on the left and an amount on the right, on one line. */
  row(label: string, amount: string, options: { bold?: boolean } = {}) {
    const page = this.pages[this.pages.length - 1];
    page.push({ text: amount, x: 470, y: this.y, size: 10, bold: options.bold });
    this.line(label, { bold: options.bold });
  }

  space(points = 10) {
    this.y -= points;
  }

  newPage() {
    this.pages.push([]);
    this.y = PAGE_HEIGHT - 60;
  }
}
