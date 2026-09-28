import Foundation

// Kotlin's Float overloads of kotlin.math (exp, ln, pow, tanh, ...) widen to Double, call the JVM
// routine and narrow the result. The labs train small models for hundreds of epochs and quote the
// numbers they land on, so the Swift ports go through the same widen-and-narrow instead of the
// C float routines, which can differ in the last bit.

func kExp(_ x: Float) -> Float { Float(exp(Double(x))) }
func kLn(_ x: Float) -> Float { Float(log(Double(x))) }
func kPow(_ x: Float, _ y: Float) -> Float { Float(pow(Double(x), Double(y))) }
func kTanh(_ x: Float) -> Float { Float(tanh(Double(x))) }
func kSqrt(_ x: Float) -> Float { Float(Double(x).squareRoot()) }
func kSigmoid(_ x: Float) -> Float { 1 / (1 + kExp(-x)) }

extension Comparable {
    /// Kotlin's `coerceIn`.
    func coerced(_ lo: Self, _ hi: Self) -> Self { min(max(self, lo), hi) }
}
