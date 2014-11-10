package taxilator

import io.github.karols.units._
import io.github.karols.units.SI._
import io.github.karols.units.defining._

object Units {

  type radian = DefineUnit[_r ~: _a ~: _d]
  type degree = DefineUnit[_d ~: _e ~: _g]

  implicit val rad_to_deg = one[radian].contains(180 / math.Pi)[degree]

  implicit class RichVector2Udegree(vector: Coords) {
    def lon = vector.x
    def lat = vector.y

    def distanceTo(to: Vector2U[degree])(implicit planet: Planet): Vector2U[metre] = {
      val (δlon, δlat) = (vector - to).convert[radian].value
      Vector2U(δlon * planet.r(vector.lat), δlat * planet.radius)
    }

    def offsetBy(by: Vector2U[metre])(implicit planet: Planet): Vector2U[degree] = {
      val δx = by.x / planet.r(vector.lat)
      val δy = by.y / planet.radius
      vector + Vector2U(δx.of[radian].convert[degree], δy.of[radian].convert[degree])
    }

    def deviateBy(offset: DoubleU[degree]) = {
      def randomDeviation = math.random * 2 - 1
      Vector2U(randomDeviation * offset, randomDeviation * offset) + vector
    }
  }

  type Coords = Vector2U[degree]

  object Coords {
    def apply(lon: Double, lat: Double): Vector2U[degree] = Vector2U(lon.of[degree], lat.of[degree])
  }

  case class Planet(radius: DoubleU[metre]) {
    def r(lat: DoubleU[degree]) = radius * math.cos(lat.convert[radian].value)
  }

  val Earth = Planet(radius = 6371.kilo[metre])
}
