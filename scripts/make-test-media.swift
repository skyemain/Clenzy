import AppKit
import AVFoundation

let directory = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
for (index, size) in [NSSize(width: 800, height: 1200), NSSize(width: 1200, height: 800), NSSize(width: 800, height: 800)].enumerated() {
    let image = NSImage(size: size)
    image.lockFocus()
    [NSColor.systemTeal, .systemOrange, .systemPurple][index].setFill()
    NSBezierPath(rect: NSRect(origin: .zero, size: size)).fill()
    NSColor.white.setStroke()
    let border = NSBezierPath(rect: NSRect(x: 12, y: 12, width: size.width - 24, height: size.height - 24))
    border.lineWidth = 16; border.stroke()
    ("PHOTO \(index + 1)\nUncropped preview" as NSString).draw(at: NSPoint(x: 50, y: size.height / 2), withAttributes: [.font: NSFont.systemFont(ofSize: 45), .foregroundColor: NSColor.white])
    image.unlockFocus()
    let bitmap = NSBitmapImageRep(data: image.tiffRepresentation!)!
    try bitmap.representation(using: .jpeg, properties: [:])!.write(to: directory.appendingPathComponent("photo-\(index).jpg"))
}
let url = directory.appendingPathComponent("video.mov")
try? FileManager.default.removeItem(at: url)
let writer = try AVAssetWriter(outputURL: url, fileType: .mov)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 640, AVVideoHeightKey: 360])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 640, kCVPixelBufferHeightKey as String: 360])
writer.add(input); writer.startWriting(); writer.startSession(atSourceTime: .zero)
for frame in 0..<120 {
    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.01) }
    var buffer: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &buffer)
    let pixels = buffer!
    CVPixelBufferLockBaseAddress(pixels, [])
    let context = CGContext(data: CVPixelBufferGetBaseAddress(pixels), width: 640, height: 360, bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(pixels), space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)!
    context.setFillColor(CGColor(red: CGFloat(frame) / 120, green: 0.35, blue: 0.7, alpha: 1)); context.fill(CGRect(x: 0, y: 0, width: 640, height: 360))
    context.setStrokeColor(CGColor(gray: 1, alpha: 1)); context.setLineWidth(12); context.stroke(CGRect(x: 10, y: 10, width: 620, height: 340))
    CVPixelBufferUnlockBaseAddress(pixels, [])
    precondition(adaptor.append(pixels, withPresentationTime: CMTime(value: Int64(frame), timescale: 12)))
}
input.markAsFinished()
let semaphore = DispatchSemaphore(value: 0)
writer.finishWriting { semaphore.signal() }
semaphore.wait()
if writer.status != .completed { fatalError(writer.error?.localizedDescription ?? "Fixture video failed") }
