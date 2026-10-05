import Foundation

// Port of ClusteringMath.kt: the float point type and distance the dimensionality-reduction labs
// share. The clustering labs themselves live in ClusterStoryLabs.swift.

struct Pt: Hashable {
    let x: Float
    let y: Float
    init(_ x: Float, _ y: Float) { self.x = x; self.y = y }
}

func dist(_ a: Pt, _ b: Pt) -> Float {
    let dx = a.x - b.x, dy = a.y - b.y
    return (dx * dx + dy * dy).squareRoot()
}
