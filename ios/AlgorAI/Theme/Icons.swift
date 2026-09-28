import SwiftUI

/// Icon names from the content and the design mock → SF Symbols. Counterpart of IconResolver.kt;
/// every name the content uses is covered so nothing silently falls back to the circle.
func iconSymbol(_ name: String) -> String {
    switch name {
    case "DataArray": "square.grid.3x1.below.line.grid.1x2"
    case "Image", "image": "photo"
    case "TableChart": "tablecells"
    case "Memory", "chip": "cpu"
    case "Layers", "stack": "square.stack.3d.up"
    case "share", "network": "point.3.connected.trianglepath.dotted"
    case "globe": "globe"
    case "browser": "square.grid.2x2"
    case "search": "magnifyingglass"
    case "trend": "chart.line.uptrend.xyaxis"
    case "map": "map"
    case "help": "questionmark.circle"
    case "history": "clock.arrow.circlepath"
    case "robot": "cpu.fill"
    case "game": "gamecontroller"
    case "users": "person.3"
    case "link": "link"
    case "finance": "dollarsign"
    case "music": "music.note"
    case "undo": "arrow.uturn.backward"
    case "book": "book"
    case "flask": "flask"
    case "target": "scope"
    case "chart": "chart.bar"
    case "moon": "moon"
    case "sun": "sun.max"
    case "flame": "flame.fill"
    case "lock": "lock.fill"
    case "chev": "chevron.right"
    case "back": "chevron.backward"
    case "check": "checkmark.circle.fill"
    case "crown": "crown"
    case "bulb": "lightbulb"
    case "info": "info.circle"
    case "menu": "line.3.horizontal"
    case "code": "chevron.left.forwardslash.chevron.right"
    case "quiz": "questionmark.bubble"
    case "cards": "rectangle.on.rectangle"
    case "mic": "person.wave.2"
    case "tree": "arrow.triangle.branch"
    case "functions": "function"
    case "translate": "character.book.closed"
    default: "circle.fill"
    }
}

struct AppIcon: View {
    let name: String
    var size: CGFloat = 18

    var body: some View {
        Image(systemName: iconSymbol(name))
            .font(.system(size: size * 0.9, weight: .semibold))
            .frame(width: size, height: size)
    }
}
