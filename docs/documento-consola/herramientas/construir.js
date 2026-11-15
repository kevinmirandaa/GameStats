const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun, Footer, PageNumber,
  AlignmentType, WidthType, BorderStyle, ShadingType, LevelFormat, HeadingLevel, PageBreak
} = require("docx");
const { portada, bloques } = require("./contenido");

const FONT = "Arial";
const BODY_SIZE = 24;
const CONTENT_WIDTH = 9360;
const rootDirectory = path.resolve(__dirname, "..");
const outputHtml = process.argv[2];
const outputDocx = process.argv[3];

function escapeHtml(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function pngSize(file) {
  const buffer = fs.readFileSync(file);
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
}

function htmlOfBlock(block) {
  switch (block.t) {
    case "h1": return `<h1>${escapeHtml(block.text)}</h1>`;
    case "h2": return `<h2>${escapeHtml(block.text)}</h2>`;
    case "p": return `<p>${escapeHtml(block.text)}</p>`;
    case "ul": return `<ul>${block.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`;
    case "ol": return `<ol>${block.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ol>`;
    case "code": return `<div class="code">${escapeHtml(block.lines.join("\n"))}</div>`;
    case "img": return `<div class="fig"><img src="${block.file}" style="width:${block.widthPt}pt" alt=""></div><p class="cap">${escapeHtml(block.caption)}</p>`;
    case "table": {
      const head = block.head.map((cell, index) => `<th style="width:${block.widths[index]}%">${escapeHtml(cell)}</th>`).join("");
      const rows = block.rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join("")}</tr>`).join("");
      return `<table><tr>${head}</tr>${rows}</table>`;
    }
    default: throw new Error("bloque desconocido " + block.t);
  }
}

function buildHtml() {
  const cover = `<div class="cover">
<img src="tec-logo.png" alt="">
<p>${portada.institucion}</p>
<p>${portada.escuela.join("<br>")}</p>
<p style="margin-top:22pt">${portada.proyecto.join("<br>")}</p>
<p style="margin-top:22pt">Estudiantes:<br>${portada.estudiantes.join("<br>")}</p>
<p style="margin-top:22pt">Profesor:<br>${portada.profesor}</p>
<p style="margin-top:22pt">${portada.semestre}</p>
</div><div class="pb"></div>`;
  const parts = [];
  for (let index = 0; index < bloques.length; index++) {
    const block = bloques[index];
    const next = bloques[index + 1];
    const keepWithNext = (block.t === "h1" || block.t === "h2") && next && (next.t === "p" || next.t === "img" || next.t === "code" || (next.t === "table" && next.rows.length <= 8));
    if (keepWithNext) {
      parts.push(`<div class="keep">${htmlOfBlock(block)}${htmlOfBlock(next)}</div>`);
      index++;
    } else if (block.t === "img" || (block.t === "table" && block.rows.length <= 8) || block.t === "code") {
      parts.push(`<div class="keep">${htmlOfBlock(block)}</div>`);
    } else {
      parts.push(htmlOfBlock(block));
    }
  }
  return `<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>GameConsole</title><style>
* { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { font-family: Arial, Helvetica, sans-serif; font-size: 12pt; line-height: 1.35; color: #000; margin: 0; }
p { margin: 0 0 7pt; text-align: justify; }
h1 { font-size: 14pt; margin: 20pt 0 7pt; page-break-after: avoid; }
h2 { font-size: 12pt; margin: 14pt 0 5pt; page-break-after: avoid; }
ul, ol { margin: 0 0 7pt; padding-left: 19pt; }
li { margin-bottom: 3pt; text-align: justify; }
table { border-collapse: collapse; width: 100%; font-size: 12pt; line-height: 1.25; margin: 3pt 0 9pt; }
th { background: #e8e8e8; text-align: left; font-weight: bold; }
th, td { border: 1px solid #999; padding: 3pt 5pt; vertical-align: top; }
tr { page-break-inside: avoid; }
.keep { page-break-inside: avoid; break-inside: avoid; }
.code { font-family: Arial, Helvetica, sans-serif; font-size: 12pt; line-height: 1.3; border: 1px solid #555; padding: 5pt 8pt; white-space: pre-wrap; margin: 3pt 0 9pt; text-align: left; }
.cap { font-size: 12pt; margin: -4pt 0 9pt; text-align: left; }
.pb { page-break-before: always; }
.fig { text-align: center; margin: 6pt 0 8pt; }
.cover { text-align: center; }
.cover img { width: 250pt; margin: 10pt 0 14pt; }
.cover p { text-align: center; font-weight: bold; font-size: 12pt; line-height: 1.4; margin: 0 0 14pt; }
</style></head><body>${cover}${parts.join("\n")}</body></html>`;
}

const thinBorder = { style: BorderStyle.SINGLE, size: 4, color: "999999" };
const cellBorders = { top: thinBorder, bottom: thinBorder, left: thinBorder, right: thinBorder };

function run(text, extra = {}) {
  return new TextRun({ text, font: FONT, size: BODY_SIZE, ...extra });
}

function paragraph(text, options = {}) {
  return new Paragraph({ alignment: AlignmentType.JUSTIFIED, spacing: { after: 140, line: 276 }, children: [run(text)], ...options });
}

function docxOfBlock(block) {
  switch (block.t) {
    case "h1": return [new Paragraph({ heading: HeadingLevel.HEADING_1, keepNext: true, children: [run(block.text, { bold: true })] })];
    case "h2": return [new Paragraph({ heading: HeadingLevel.HEADING_2, keepNext: true, children: [run(block.text, { bold: true })] })];
    case "p": return [paragraph(block.text)];
    case "ul": return block.items.map((item) => new Paragraph({ numbering: { reference: "bullets", level: 0 }, alignment: AlignmentType.JUSTIFIED, spacing: { after: 60, line: 276 }, children: [run(item)] }));
    case "ol": return block.items.map((item) => new Paragraph({ numbering: { reference: "steps", level: 0 }, alignment: AlignmentType.JUSTIFIED, spacing: { after: 60, line: 276 }, children: [run(item)] }));
    case "code": return [new Paragraph({
      alignment: AlignmentType.LEFT, keepLines: true, spacing: { before: 60, after: 160, line: 260 },
      border: { top: { style: BorderStyle.SINGLE, size: 6, color: "555555", space: 4 }, bottom: { style: BorderStyle.SINGLE, size: 6, color: "555555", space: 4 }, left: { style: BorderStyle.SINGLE, size: 6, color: "555555", space: 4 }, right: { style: BorderStyle.SINGLE, size: 6, color: "555555", space: 4 } },
      children: block.lines.flatMap((line, index) => [new TextRun({ text: line, font: FONT, size: BODY_SIZE, break: index === 0 ? 0 : 1 })])
    })];
    case "img": {
      const file = path.join(rootDirectory, block.file);
      const size = pngSize(file);
      const width = Math.round(block.widthPt * 96 / 72);
      const height = Math.round(width * size.height / size.width);
      return [
        new Paragraph({ alignment: AlignmentType.CENTER, keepNext: true, spacing: { before: 100, after: 80 }, children: [new ImageRun({ type: "png", data: fs.readFileSync(file), transformation: { width, height }, altText: { title: block.caption, description: block.caption, name: block.file } })] }),
        new Paragraph({ alignment: AlignmentType.LEFT, spacing: { after: 160 }, children: [run(block.caption)] })
      ];
    }
    case "table": {
      const widths = block.widths.map((percent) => Math.round(CONTENT_WIDTH * percent / 100));
      widths[widths.length - 1] += CONTENT_WIDTH - widths.reduce((sum, width) => sum + width, 0);
      const makeCell = (text, index, header) => new TableCell({
        width: { size: widths[index], type: WidthType.DXA }, borders: cellBorders,
        shading: header ? { type: ShadingType.CLEAR, fill: "E8E8E8", color: "auto" } : undefined,
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        children: [new Paragraph({ alignment: AlignmentType.LEFT, children: [run(text, { bold: header })] })]
      });
      const rows = [new TableRow({ tableHeader: true, cantSplit: true, children: block.head.map((cell, index) => makeCell(cell, index, true)) })]
        .concat(block.rows.map((row) => new TableRow({ cantSplit: true, children: row.map((cell, index) => makeCell(cell, index, false)) })));
      return [new Table({ width: { size: CONTENT_WIDTH, type: WidthType.DXA }, columnWidths: widths, rows }), new Paragraph({ spacing: { after: 120 }, children: [] })];
    }
    default: throw new Error("bloque desconocido " + block.t);
  }
}

function coverParagraphs() {
  const centered = (lines, before = 0, after = 360) => new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { before, after, line: 330 },
    children: lines.flatMap((line, index) => [new TextRun({ text: line, font: FONT, size: BODY_SIZE, bold: true, break: index === 0 ? 0 : 1 })])
  });
  const logo = path.join(rootDirectory, "tec-logo.png");
  const size = pngSize(logo);
  const width = 360;
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 800, after: 700 }, children: [new ImageRun({ type: "png", data: fs.readFileSync(logo), transformation: { width, height: Math.round(width * size.height / size.width) }, altText: { title: "TEC", description: "Logo del TEC", name: "tec-logo" } })] }),
    centered([portada.institucion]),
    centered(portada.escuela),
    centered(portada.proyecto, 500),
    centered(["Estudiantes:"].concat(portada.estudiantes), 500),
    centered(["Profesor:", portada.profesor], 500),
    centered([portada.semestre], 400),
    new Paragraph({ children: [new PageBreak()] })
  ];
}

async function buildDocx() {
  const children = coverParagraphs().concat(bloques.flatMap(docxOfBlock));
  const headingStyle = (id, name, size, before) => ({ id, name, basedOn: "Normal", next: "Normal", quickFormat: true, run: { font: FONT, size, bold: true, color: "000000" }, paragraph: { spacing: { before, after: 120 }, keepNext: true, keepLines: true } });
  const document = new Document({
    creator: "Kevin Miranda Méndez y Victor Ubilla",
    title: "GameConsole",
    styles: {
      default: { document: { run: { font: FONT, size: BODY_SIZE } } },
      paragraphStyles: [headingStyle("Heading1", "Heading 1", 28, 400), headingStyle("Heading2", "Heading 2", 24, 280)]
    },
    numbering: { config: [
      { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] },
      { reference: "steps", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } } } }] }
    ] },
    sections: [{
      properties: { titlePage: true, page: { size: { width: 12240, height: 15840 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
      footers: {
        default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: BODY_SIZE })] })] }),
        first: new Footer({ children: [new Paragraph({ children: [] })] })
      },
      children
    }]
  });
  fs.writeFileSync(outputDocx, await Packer.toBuffer(document));
}

fs.writeFileSync(outputHtml, buildHtml());
buildDocx().then(() => console.log("generados:", outputHtml, outputDocx));
