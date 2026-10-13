import AppKit
import WebKit

final class Capturer: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    let window: NSWindow
    let script: String
    let outputPath: String

    init(url: URL, script: String, outputPath: String) {
        webView = WKWebView(frame: NSRect(x: 0, y: 0, width: 1440, height: 900))
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1440, height: 900), styleMask: [.borderless], backing: .buffered, defer: false)
        window.contentView = webView
        self.script = script
        self.outputPath = outputPath
        super.init()
        webView.navigationDelegate = self
        webView.load(URLRequest(url: url))
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            self.webView.evaluateJavaScript(self.script) { _, _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self.snapshot() }
            }
        }
    }

    func snapshot() {
        let configuration = WKSnapshotConfiguration()
        configuration.snapshotWidth = 2160
        webView.takeSnapshot(with: configuration) { image, _ in
            guard let image = image, let tiff = image.tiffRepresentation, let rep = NSBitmapImageRep(data: tiff),
                  let png = rep.representation(using: .png, properties: [:]) else { exit(1) }
            try? png.write(to: URL(fileURLWithPath: self.outputPath))
            print("captura:", self.outputPath, Int(image.size.width), "x", Int(image.size.height))
            NSApplication.shared.terminate(nil)
        }
    }
}

let arguments = CommandLine.arguments
let application = NSApplication.shared
application.setActivationPolicy(.accessory)
let capturer = Capturer(url: URL(string: arguments[1])!, script: arguments[2], outputPath: arguments[3])
application.run()
