import AppKit
import WebKit
import PDFKit

final class Printer: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    let window: NSWindow
    let outputURL: URL
    init(htmlURL: URL, outputURL: URL) {
        let configuration = WKWebViewConfiguration()
        webView = WKWebView(frame: NSRect(x: 0, y: 0, width: 612, height: 792), configuration: configuration)
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 612, height: 792), styleMask: [.borderless], backing: .buffered, defer: false)
        window.contentView = webView
        self.outputURL = outputURL
        super.init()
        webView.navigationDelegate = self
        webView.loadFileURL(htmlURL, allowingReadAccessTo: htmlURL.deletingLastPathComponent().deletingLastPathComponent())
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { self.printPages() }
    }
    func printPages() {
        let info = NSPrintInfo()
        info.paperSize = NSSize(width: 612, height: 792)
        info.topMargin = 72; info.bottomMargin = 72; info.leftMargin = 72; info.rightMargin = 72
        info.isHorizontallyCentered = false; info.isVerticallyCentered = false
        info.jobDisposition = .save
        info.dictionary()[NSPrintInfo.AttributeKey.jobSavingURL] = outputURL
        let operation = webView.printOperation(with: info)
        operation.showsPrintPanel = false
        operation.showsProgressPanel = false
        operation.view?.frame = NSRect(x: 0, y: 0, width: 612, height: 792)
        operation.runModal(for: window, delegate: self, didRun: #selector(printFinished(_:success:contextInfo:)), contextInfo: nil)
    }
    @objc func printFinished(_ operation: NSPrintOperation, success: Bool, contextInfo: UnsafeMutableRawPointer?) {
        print("impresion:", success)
        NSApplication.shared.terminate(nil)
    }
}

let arguments = CommandLine.arguments
let application = NSApplication.shared
application.setActivationPolicy(.accessory)
let printer = Printer(htmlURL: URL(fileURLWithPath: arguments[1]), outputURL: URL(fileURLWithPath: arguments[2]))
application.run()
