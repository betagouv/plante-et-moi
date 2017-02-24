package services

import anorm.{Column, MetaDataItem, ToStatement, TypeDoesNotMatch}
import models.Coordinates

trait AnormCoordinate {
  implicit object coordinatesToStatement extends ToStatement[Coordinates] {
    override def set(s: java.sql.PreparedStatement, index: Int, v: Coordinates): Unit = {
      val pointObject = new org.postgresql.util.PGobject()
      pointObject.setType("point")
      pointObject.setValue(s"point(${v.longitude},${v.latitude})")
      s.setObject(index, pointObject)
    }
  }

  private def pointStringToCoordinates(pointString: String): Coordinates = {
    val point = pointString.drop(1).dropRight(1).split(",").map(_.toDouble)
    val latitude = point(0)
    val longitude = point(1)
    Coordinates(latitude, longitude)
  }

  implicit val columnToCoordinates: Column[Coordinates] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, _, _) = meta
    value match {
      case coordinates: org.postgresql.util.PGobject =>
        Right(pointStringToCoordinates(coordinates.getValue))
      case coordinates: String =>
        Right(pointStringToCoordinates(coordinates))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Coordinates for column $qualified"))
    }
  }
}
