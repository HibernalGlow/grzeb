import AppKit
import Foundation

let size = 1024.0
let image = NSImage(size: NSSize(width: size, height: size))

image.lockFocus()
guard let ctx = NSGraphicsContext.current?.cgContext else {
    fatalError("No graphics context")
}

// Enable anti-aliasing
ctx.setAllowsAntialiasing(true)
ctx.setShouldAntialias(true)

// 1. Squircle background (macOS standard icon rounded rect)
let rectInset: CGFloat = 80.0
let iconRect = CGRect(x: rectInset, y: rectInset, width: size - rectInset * 2, height: size - rectInset * 2)
let cornerRadius: CGFloat = 190.0
let bgPath = CGPath(roundedRect: iconRect, cornerWidth: cornerRadius, cornerHeight: cornerRadius, transform: nil)

// Drop shadow for the icon body
ctx.saveGState()
ctx.setShadow(offset: CGSize(width: 0, height: -16), blur: 32, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.35))
ctx.addPath(bgPath)
ctx.setFillColor(CGColor(red: 0.15, green: 0.08, blue: 0.28, alpha: 1.0))
ctx.fillPath()
ctx.restoreGState()

// Clip to squircle for gradient
ctx.saveGState()
ctx.addPath(bgPath)
ctx.clip()

// Gradient: Material 3 Deep Violet (#5E35B1) to Midnight Purple (#1A0938)
let colorSpace = CGColorSpaceCreateDeviceRGB()
let colors = [
    CGColor(red: 0.45, green: 0.28, blue: 0.85, alpha: 1.0), // Top: vibrant violet
    CGColor(red: 0.22, green: 0.10, blue: 0.45, alpha: 1.0), // Mid
    CGColor(red: 0.11, green: 0.04, blue: 0.24, alpha: 1.0)  // Bottom: dark midnight
] as CFArray
let locations: [CGFloat] = [0.0, 0.55, 1.0]
if let gradient = CGGradient(colorsSpace: colorSpace, colors: colors, locations: locations) {
    ctx.drawLinearGradient(gradient, start: CGPoint(x: size / 2, y: size - rectInset), end: CGPoint(x: size / 2, y: rectInset), options: [])
}

// Subtle top edge highlight
let borderPath = CGPath(roundedRect: iconRect.insetBy(dx: 1.5, dy: 1.5), cornerWidth: cornerRadius - 1.5, cornerHeight: cornerRadius - 1.5, transform: nil)
ctx.addPath(borderPath)
ctx.setStrokeColor(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.18))
ctx.setLineWidth(3.0)
ctx.strokePath()

ctx.restoreGState() // unclip

// 2. Center Graphic: Stylized Magnifying Glass & Code/Search Lines
// Center of lens
let lensCenterX: CGFloat = 460.0
let lensCenterY: CGFloat = 560.0
let lensRadius: CGFloat = 190.0
let strokeWidth: CGFloat = 44.0

// Outer glow/shadow for the search lens
ctx.saveGState()
ctx.setShadow(offset: CGSize(width: 0, height: -8), blur: 24, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.4))

// Magnifying handle
let handleAngle: CGFloat = -CGFloat.pi / 4.0 // 45 degrees down-right
let handleStart = CGPoint(
    x: lensCenterX + cos(handleAngle) * (lensRadius + strokeWidth * 0.2),
    y: lensCenterY + sin(handleAngle) * (lensRadius + strokeWidth * 0.2)
)
let handleEnd = CGPoint(
    x: lensCenterX + cos(handleAngle) * (lensRadius + 250.0),
    y: lensCenterY + sin(handleAngle) * (lensRadius + 250.0)
)

ctx.setLineCap(.round)
ctx.setLineWidth(strokeWidth + 4.0)
ctx.setStrokeColor(CGColor(red: 0.82, green: 0.72, blue: 1.0, alpha: 1.0)) // Lavender primary
ctx.move(to: handleStart)
ctx.addLine(to: handleEnd)
ctx.strokePath()

// Lens Circle
ctx.setLineWidth(strokeWidth)
ctx.setStrokeColor(CGColor(red: 0.95, green: 0.92, blue: 1.0, alpha: 1.0)) // Bright on-primary container
ctx.addArc(center: CGPoint(x: lensCenterX, y: lensCenterY), radius: lensRadius, startAngle: 0, endAngle: CGFloat.pi * 2, clockwise: false)
ctx.strokePath()
ctx.restoreGState()

// Inner Lens Glow / Subtle Fill
ctx.saveGState()
ctx.addArc(center: CGPoint(x: lensCenterX, y: lensCenterY), radius: lensRadius - strokeWidth / 2, startAngle: 0, endAngle: CGFloat.pi * 2, clockwise: false)
ctx.clip()
let lensFillColors = [
    CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.12),
    CGColor(red: 0.7, green: 0.5, blue: 1.0, alpha: 0.04)
] as CFArray
if let lensGrad = CGGradient(colorsSpace: colorSpace, colors: lensFillColors, locations: [0.0, 1.0]) {
    ctx.drawLinearGradient(lensGrad, start: CGPoint(x: lensCenterX, y: lensCenterY + lensRadius), end: CGPoint(x: lensCenterX, y: lensCenterY - lensRadius), options: [])
}
ctx.restoreGState()

// 3. Search / Code Lines inside lens (Comb/Grep motif)
ctx.saveGState()
ctx.setLineCap(.round)
ctx.setStrokeColor(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.95))

// Line 1 (long)
ctx.setLineWidth(24.0)
ctx.move(to: CGPoint(x: lensCenterX - 95.0, y: lensCenterY + 70.0))
ctx.addLine(to: CGPoint(x: lensCenterX + 95.0, y: lensCenterY + 70.0))
ctx.strokePath()

// Line 2 (medium with accent highlight point)
ctx.move(to: CGPoint(x: lensCenterX - 95.0, y: lensCenterY + 10.0))
ctx.addLine(to: CGPoint(x: lensCenterX + 40.0, y: lensCenterY + 10.0))
ctx.strokePath()

// Highlight search match dot / pill
ctx.setFillColor(CGColor(red: 1.0, green: 0.75, blue: 0.25, alpha: 1.0)) // Amber secondary match
ctx.fillEllipse(in: CGRect(x: lensCenterX + 62.0, y: lensCenterY - 1.0, width: 24.0, height: 24.0))

// Line 3 (short)
ctx.move(to: CGPoint(x: lensCenterX - 95.0, y: lensCenterY - 50.0))
ctx.addLine(to: CGPoint(x: lensCenterX + 10.0, y: lensCenterY - 50.0))
ctx.strokePath()

ctx.restoreGState()

image.unlockFocus()

// Save PNG
guard let tiffData = image.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: tiffData),
      let pngData = bitmap.representation(using: .png, properties: [:]) else {
    fatalError("Failed to convert image to PNG")
}

let outputPath = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "grzeb-icon-1024.png"
try pngData.write(to: URL(fileURLWithPath: outputPath))
print("Successfully generated icon at: \(outputPath)")
