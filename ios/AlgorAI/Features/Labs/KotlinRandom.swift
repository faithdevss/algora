import Foundation

/// Bit-for-bit port of Kotlin's `kotlin.random.Random(seed)` (XorWowRandom). The labs build their
/// datasets from fixed seeds and several quote measured numbers in their copy, so iOS must draw the
/// same sequence Android does — a different generator would produce different data and numbers.
struct KotlinRandom {
    private var x: Int32, y: Int32, z: Int32, w: Int32, v: Int32, addend: Int32

    /// `Random(seed: Int)`.
    init(_ seed: Int32) { self.init(seed1: seed, seed2: seed >> 31) }

    /// `Random(seed: Long)`.
    init(long seed: Int64) { self.init(seed1: Int32(truncatingIfNeeded: seed), seed2: Int32(truncatingIfNeeded: seed >> 32)) }

    /// Convenience for Swift `Int` seeds that fit in 32 bits, matching Kotlin `Random(Int)`.
    init(seed: Int) { self.init(Int32(truncatingIfNeeded: seed)) }

    private init(seed1: Int32, seed2: Int32) {
        x = seed1
        y = seed2
        z = 0
        w = 0
        v = ~seed1
        addend = (seed1 << 10) ^ Int32(bitPattern: UInt32(bitPattern: seed2) >> 4)
        for _ in 0..<64 { _ = nextInt() }
    }

    mutating func nextInt() -> Int32 {
        var t = x
        t = t ^ Int32(bitPattern: UInt32(bitPattern: t) >> 2)
        x = y
        y = z
        z = w
        let v0 = v
        w = v0
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4)
        v = t
        addend = addend &+ 362_437
        return t &+ addend
    }

    mutating func nextBits(_ bitCount: Int) -> Int32 {
        let r = UInt32(bitPattern: nextInt()) >> UInt32(32 - bitCount)
        // takeUpperBits: masked to zero when bitCount is 0.
        return bitCount == 0 ? 0 : Int32(bitPattern: r)
    }

    /// `nextInt(until)`.
    mutating func nextInt(_ until: Int) -> Int { nextInt(from: 0, until: until) }

    mutating func nextInt(from: Int, until: Int) -> Int {
        let n = Int32(truncatingIfNeeded: until - from)
        precondition(until > from, "Random range is empty")
        let rnd: Int32
        if n > 0 || n == Int32.min {
            if n & -n == n {
                rnd = nextBits(31 - n.leadingZeroBitCount)
            } else {
                var bits: Int32
                var value: Int32
                repeat {
                    bits = Int32(bitPattern: UInt32(bitPattern: nextInt()) >> 1)
                    value = bits % n
                } while bits &- value &+ (n &- 1) < 0
                rnd = value
            }
            return from + Int(rnd)
        }
        while true {
            let r = Int(nextInt())
            if r >= from && r < until { return r }
        }
    }

    mutating func nextFloat() -> Float { Float(nextBits(24)) / Float(1 << 24) }

    mutating func nextDouble() -> Double {
        let hi = Int64(nextBits(26))
        let lo = Int64(nextBits(27))
        return Double((hi << 27) + lo) / Double(Int64(1) << 53)
    }

    mutating func nextBoolean() -> Bool { nextBits(1) != 0 }

    /// Kotlin's `nextDouble(from, until)`.
    mutating func nextDouble(_ from: Double, _ until: Double) -> Double {
        let r = nextDouble()
        let size = until - from
        return from + r * size
    }

    /// Kotlin's `nextDouble(until)`.
    mutating func nextDouble(_ until: Double) -> Double { nextDouble(0, until) }

    /// Kotlin's `nextFloat()`-based gaussian is not in the stdlib; labs that need one build it
    /// from nextDouble themselves, so it lives with them.
}

extension Array {
    /// Kotlin's `shuffled(random)`: Fisher-Yates from the end.
    func kotlinShuffled(_ random: inout KotlinRandom) -> [Element] {
        var copy = self
        copy.kotlinShuffle(&random)
        return copy
    }

    mutating func kotlinShuffle(_ random: inout KotlinRandom) {
        guard count > 1 else { return }
        for i in stride(from: count - 1, through: 1, by: -1) {
            let j = random.nextInt(i + 1)
            swapAt(i, j)
        }
    }
}
