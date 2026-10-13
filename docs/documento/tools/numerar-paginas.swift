import AppKit
import PDFKit

let arguments = CommandLine.arguments
guard let source = PDFDocument(url: URL(fileURLWithPath: arguments[1])) else { exit(1) }
var mediaBox = CGRect(x: 0, y: 0, width: 612, height: 792)
guard let context = CGContext(URL(fileURLWithPath: arguments[2]) as CFURL, mediaBox: &mediaBox, nil) else { exit(1) }

let attributes: [NSAttributedString.Key: Any] = [
    .font: NSFont(name: "Arial", size: 12) ?? NSFont.systemFont(ofSize: 12),
    .foregroundColor: NSColor.black
]

for index in 0..<source.pageCount {
    guard let page = source.page(at: index), let reference = page.pageRef else { continue }
    context.beginPDFPage(nil)
    context.drawPDFPage(reference)
    if index > 0 {
        let label = NSAttributedString(string: "\(index + 1)", attributes: attributes)
        let width = label.size().width
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: false)
        label.draw(at: NSPoint(x: 540 - width, y: 36))
        NSGraphicsContext.restoreGraphicsState()
    }
    context.endPDFPage()
}
context.closePDF()
print("paginas numeradas:", source.pageCount)
