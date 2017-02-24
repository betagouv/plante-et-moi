package services

import anorm.{Column, MetaDataItem, RowParser, ToStatement, TypeDoesNotMatch}
import play.api.libs.json.{JsValue, Json}
import anorm.SqlParser.get
import anorm._

trait AnormJson {
  implicit object jsonToStatement extends ToStatement[JsValue] {
    override def set(s: java.sql.PreparedStatement, index: Int, v: JsValue): Unit = {
      val jsonObject = new org.postgresql.util.PGobject()
      jsonObject.setType("json")
      jsonObject.setValue(Json.stringify(v))
      s.setObject(index, jsonObject)
    }
  }

  implicit val columnToJson: Column[JsValue] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, _, _) = meta
    value match {
      case json: org.postgresql.util.PGobject => Right(Json.parse(json.getValue))
      case json: String => Right(Json.parse(json))
      case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Json for column $qualified"))
    }
  }

  protected[this] def json(name: String): RowParser[JsValue] = get[JsValue](name)
}
