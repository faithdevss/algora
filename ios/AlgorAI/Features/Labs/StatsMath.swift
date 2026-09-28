import Foundation

// Shared least-squares helpers from PreprocessMath.kt, used by several labs' Lab objects.

/// Least squares by Gaussian elimination on the normal equations, with an intercept column.
func leastSquares(_ x: [[Double]], _ y: [Double]) -> [Double] {
    let p = x[0].count + 1
    let design = x.map { row in (0..<p).map { $0 == 0 ? 1.0 : row[$0 - 1] } }
    var a = [[Double]](repeating: [Double](repeating: 0, count: p + 1), count: p)
    for i in 0..<p {
        for j in 0..<p { a[i][j] = design.indices.reduce(0.0) { $0 + design[$1][i] * design[$1][j] } }
        a[i][p] = design.indices.reduce(0.0) { $0 + design[$1][i] * y[$1] }
    }
    for i in 0..<p { a[i][i] += 1e-8 }
    for col in 0..<p {
        let pivot = (col..<p).max { abs(a[$0][col]) < abs(a[$1][col]) }!
        a.swapAt(col, pivot)
        for row in 0..<p where row != col && a[col][col] != 0 {
            let factor = a[row][col] / a[col][col]
            for k in col...p { a[row][k] -= factor * a[col][k] }
        }
    }
    return (0..<p).map { a[$0][p] / a[$0][$0] }
}

func predictLinear(_ coefficients: [Double], _ row: [Double]) -> Double {
    coefficients[0] + row.indices.reduce(0.0) { $0 + coefficients[$1 + 1] * row[$1] }
}
