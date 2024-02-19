package tracks.indexer.models

class Vector {
    val speed: Double
    val course: Double

    constructor(speed: Double, course: Double) {
        this.speed = speed
        this.course = course
    }

    operator fun plus(other: Vector): Vector {
        return Vector(this.speed + other.speed, (this.course + other.course) % 360.0)
    }

    fun fraction(fraction: Double): Vector {
        return Vector(this.speed * fraction, this.course * fraction)
    }
}
