const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun, Footer, PageNumber,
  AlignmentType, HeadingLevel, WidthType, BorderStyle, ShadingType, LevelFormat, VerticalAlign,
} = require("docx");

const [blocksPath, imagesDirectory, outputPath, professor] = process.argv.slice(2);
const blocks = JSON.parse(fs.readFileSync(blocksPath, "utf8"));

const FONT = "Arial";
const BODY_SIZE = 24;
const TABLE_SIZE = 24;
const CAPTION_SIZE = 24;
const CONTENT_WIDTH = 9360;
const PIXELS_PER_POINT = 96 / 72;

const gray = "999999";
const thinBorder = { style: BorderStyle.SINGLE, size: 4, color: gray };
const tableBorders = { top: thinBorder, bottom: thinBorder, left: thinBorder, right: thinBorder };
const boxBorder = { style: BorderStyle.SINGLE, size: 6, color: "555555" };
const codeBorders = { top: boxBorder, bottom: boxBorder, left: boxBorder, right: boxBorder };

let orderedListCount = 0;
const orderedReferences = [];

function runsOf(runs, size, extra = {}) {
  return runs.map((run) =>
    run.br
      ? new TextRun({ break: 1, font: FONT, size })
      : new TextRun({ text: run.text, bold: Boolean(run.bold) || extra.bold, font: FONT, size, color: extra.color })
  );
}

function paragraph(block) {
  return new Paragraph({
    alignment: block.left ? AlignmentType.LEFT : AlignmentType.JUSTIFIED,
    spacing: { after: 140, line: 288 },
    children: runsOf(block.runs, BODY_SIZE),
  });
}

function caption(block) {
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { after: 200, line: 264 },
    children: runsOf(block.runs, CAPTION_SIZE, { color: "333333" }),
  });
}

let breakBeforeNext = false;

function heading(block, level) {
  const startsPage = Boolean(block.pagebreak) || breakBeforeNext;
  breakBeforeNext = false;
  return new Paragraph({
    heading: level,
    pageBreakBefore: startsPage,
    children: [new TextRun({ text: block.runs.map((run) => run.text).join(""), font: FONT, bold: true })],
  });
}

function list(block) {
  if (block.ordered) {
    orderedListCount += 1;
    orderedReferences.push(`ordered-${orderedListCount}`);
  }
  const reference = block.ordered ? `ordered-${orderedListCount}` : "bullets";
  return block.items.map(
    (runs) =>
      new Paragraph({
        numbering: { reference, level: 0 },
        alignment: AlignmentType.LEFT,
        spacing: { after: 70, line: 276 },
        children: runsOf(runs, BODY_SIZE),
      })
  );
}

function codeBox(block) {
  const lines = block.lines.map((line) => {
    const leading = line.length - line.trimStart().length;
    return new Paragraph({
      alignment: AlignmentType.LEFT,
      spacing: { after: 0, line: 264 },
      children: [new TextRun({ text: " ".repeat(leading * 2) + line.trimStart(), font: FONT, size: BODY_SIZE })],
    });
  });
  const cell = new TableCell({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    borders: codeBorders,
    margins: { top: 100, bottom: 100, left: 160, right: 160 },
    children: lines,
  });
  return [
    new Table({
      width: { size: CONTENT_WIDTH, type: WidthType.DXA },
      columnWidths: [CONTENT_WIDTH],
      rows: [new TableRow({ cantSplit: block.lines.length <= 22, children: [cell] })],
    }),
    new Paragraph({ spacing: { after: 120 }, children: [] }),
  ];
}

function columnWidths(firstRow) {
  const declared = firstRow.map((cell) => cell.width);
  const used = declared.filter(Boolean).reduce((total, value) => total + value, 0);
  const missing = declared.filter((value) => !value).length;
  const share = missing ? (100 - used) / missing : 0;
  const percents = declared.map((value) => value || share);
  const widths = percents.map((percent) => Math.round((CONTENT_WIDTH * percent) / 100));
  widths[widths.length - 1] += CONTENT_WIDTH - widths.reduce((total, value) => total + value, 0);
  return widths;
}

function table(block) {
  const widths = columnWidths(block.rows[0]);
  const spanning = new Array(widths.length).fill(0);
  const rows = block.rows.map((row, rowIndex) => {
    let column = 0;
    const cells = row.map((cell) => {
      while (spanning[column] > 0) {
        spanning[column] -= 1;
        column += 1;
      }
      const width = widths[column];
      if (cell.rowspan > 1) spanning[column] = cell.rowspan - 1;
      column += 1;
      return new TableCell({
        width: { size: width, type: WidthType.DXA },
        borders: tableBorders,
        rowSpan: cell.rowspan > 1 ? cell.rowspan : undefined,
        shading: cell.header ? { type: ShadingType.CLEAR, fill: "E8E8E8" } : undefined,
        margins: { top: 50, bottom: 50, left: 100, right: 100 },
        verticalAlign: VerticalAlign.TOP,
        children: [
          new Paragraph({
            alignment: AlignmentType.LEFT,
            spacing: { after: 0, line: 252 },
            children: runsOf(cell.runs, TABLE_SIZE, { bold: cell.header }),
          }),
        ],
      });
    });
    return new TableRow({ cantSplit: true, tableHeader: rowIndex === 0 && row[0].header, children: cells });
  });
  return [
    new Table({ width: { size: CONTENT_WIDTH, type: WidthType.DXA }, columnWidths: widths, rows }),
    new Paragraph({ spacing: { after: 140 }, children: [] }),
  ];
}

function image(file, widthPoints) {
  const data = fs.readFileSync(path.join(imagesDirectory, file));
  const width = data.readUInt32BE(16);
  const height = data.readUInt32BE(20);
  const pixelWidth = Math.round(widthPoints * PIXELS_PER_POINT);
  const pixelHeight = Math.round((pixelWidth * height) / width);
  return new ImageRun({ type: "png", data, transformation: { width: pixelWidth, height: pixelHeight } });
}

function figure(block) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    keepNext: true,
    spacing: { before: 120, after: 140 },
    children: [image(block.src, 410)],
  });
}

function centeredLines(lines, after) {
  const children = [];
  lines.forEach((line, position) => {
    if (position > 0) children.push(new TextRun({ break: 1, font: FONT, size: 23 }));
    children.push(new TextRun({ text: line, bold: true, font: FONT, size: 23 }));
  });
  return new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after, line: 336 }, children });
}

function cover() {
  return [
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 1300, after: 1000 }, children: [image("tec-logo.png", 270)] }),
    centeredLines(["Instituto Tecnológico de Costa Rica"], 700),
    centeredLines(["Escuela de Ingeniería en Computación", "Lenguajes de Programación"], 1200),
    centeredLines([
      "Proyecto: dos paradigmas de programación en un sistema integrado",
      "GameStats, plataforma de procesamiento y análisis de eventos de videojuegos",
    ], 1000),
    centeredLines(["Estudiante:", "Kevin Miranda Méndez", "Carné 2024120764"], 1000),
    centeredLines(["Profesor:", professor], 800),
    centeredLines(["II Semestre 2026"], 0),
  ];
}

const children = [];
blocks.forEach((block) => {
  switch (block.type) {
    case "cover": children.push(...cover()); break;
    case "pagebreak": breakBeforeNext = true; break;
    case "h1": children.push(heading(block, HeadingLevel.HEADING_1)); break;
    case "h2": children.push(heading(block, HeadingLevel.HEADING_2)); break;
    case "h3": children.push(heading(block, HeadingLevel.HEADING_3)); break;
    case "paragraph": children.push(paragraph(block)); break;
    case "caption": children.push(caption(block)); break;
    case "list": children.push(...list(block)); break;
    case "table": children.push(...table(block)); break;
    case "code": children.push(...codeBox(block)); break;
    case "figure": children.push(figure(block)); break;
    default: throw new Error(`Bloque desconocido: ${block.type}`);
  }
});

const headingStyle = (id, name, size, before, outlineLevel) => ({
  id, name, basedOn: "Normal", next: "Normal", quickFormat: true,
  run: { font: FONT, size, bold: true, color: "000000" },
  paragraph: { spacing: { before, after: 120 }, keepNext: true, keepLines: true, outlineLevel },
});

const bulletLevel = {
  level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
  style: { paragraph: { indent: { left: 540, hanging: 270 } } },
};

const document = new Document({
  creator: "Kevin Miranda Méndez",
  title: "GameStats, plataforma de procesamiento y análisis de eventos de videojuegos",
  styles: {
    default: { document: { run: { font: FONT, size: BODY_SIZE } } },
    paragraphStyles: [
      headingStyle("Heading1", "Heading 1", 28, 400, 0),
      headingStyle("Heading2", "Heading 2", 24, 280, 1),
      headingStyle("Heading3", "Heading 3", 24, 200, 2),
    ],
  },
  numbering: {
    config: [
      { reference: "bullets", levels: [bulletLevel] },
      ...orderedReferences.map((reference) => ({
        reference,
        levels: [{
          level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 540, hanging: 360 } } },
        }],
      })),
    ],
  },
  sections: [{
    properties: {
      titlePage: true,
      page: { size: { width: 12240, height: 15840 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } },
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.RIGHT,
          children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 24 })],
        })],
      }),
      first: new Footer({ children: [new Paragraph({ children: [] })] }),
    },
    children,
  }],
});

Packer.toBuffer(document).then((buffer) => {
  fs.writeFileSync(outputPath, buffer);
  console.log("DOCX generado:", outputPath, buffer.length, "bytes");
});
