import SwiftUI

// Port of feature/topics/CodeBlockSection.kt. Code chrome is fixed-dark regardless of app theme.
private let codeBg = Color(hex: 0x0F1523)
private let codeDefault = Color(hex: 0xE6E9F0)
private let codeComment = Color(hex: 0x9AA1B1)

// Cross-language on purpose: Kotlin plus Python/Java/JS keywords and both comment styles.
private let tokenPattern = try! NSRegularExpression(pattern:
    "(//.*|#.*)" +
    "|(\"[^\"]*\"|'[^']*')" +
    "|(\\b(?:fun|val|var|if|else|elif|for|while|class|private|public|static|void|override|return|require|in|until|downTo|object|internal|const|def|function|let|new|import|self|this|true|false|True|False|None|null)\\b)" +
    "|(\\b[A-Z][A-Za-z0-9_]*\\b)" +
    "|(\\b\\d+\\b)"
)

func highlightCode(_ code: String, keyword: Color, type: Color) -> AttributedString {
    var result = AttributedString()
    let ns = code as NSString
    var last = 0
    func append(_ range: NSRange, _ color: Color) {
        var piece = AttributedString(ns.substring(with: range))
        piece.foregroundColor = color
        result += piece
    }
    for match in tokenPattern.matches(in: code, range: NSRange(location: 0, length: ns.length)) {
        if match.range.location > last { append(NSRange(location: last, length: match.range.location - last), codeDefault) }
        let color: Color =
            match.range(at: 1).location != NSNotFound ? codeComment :
            match.range(at: 2).location != NSNotFound ? CategoryAccents.darkGreen :
            match.range(at: 3).location != NSNotFound ? keyword :
            match.range(at: 4).location != NSNotFound ? type :
            match.range(at: 5).location != NSNotFound ? CategoryAccents.amber : codeDefault
        append(match.range, color)
        last = match.range.location + match.range.length
    }
    if last < ns.length { append(NSRange(location: last, length: ns.length - last), codeDefault) }
    return result
}

/// Dark, horizontally scrolling code panel with syntax colouring.
struct CodePanel: View {
    let code: String
    @Environment(\.palette) private var palette

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            Text(highlightCode(code, keyword: palette.primary, type: palette.tertiary))
                .font(.code)
                .lineSpacing(4)
                .fixedSize()
                .textSelection(.enabled)
                .padding(.horizontal, 16)
                .padding(.vertical, 15)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(codeBg)
    }
}

struct CodeBlockCard: View {
    let block: CodeBlock
    @State var expanded: Bool
    @State private var selectedLang = 0
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: block.accentColor)
        let shown = block.variants.isEmpty ? block.code : block.variants[min(selectedLang, block.variants.count - 1)].code
        VStack(spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
            } label: {
                HStack(spacing: 0) {
                    Rectangle().fill(accent).frame(width: 4)
                    HStack {
                        Text(block.title).font(.titleMedium).multilineTextAlignment(.leading)
                        Spacer()
                        Image(systemName: expanded ? "chevron.up" : "chevron.down").foregroundStyle(palette.muted)
                    }
                    .padding(.horizontal, 15)
                    .padding(.vertical, 11)
                }
                .fixedSize(horizontal: false, vertical: true)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if expanded {
                if !block.variants.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(block.variants.enumerated()), id: \.offset) { index, variant in
                                Button { selectedLang = index } label: {
                                    Text(variant.language)
                                        .font(.code)
                                        .foregroundStyle(index == selectedLang ? .white : codeComment)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(index == selectedLang ? accent : .white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                    .background(codeBg)
                }
                CodePanel(code: shown)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .card(radius: 16)
    }
}
