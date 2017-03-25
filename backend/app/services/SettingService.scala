package services

import javax.inject.Inject

import anorm.Column.nonNull
import anorm.{MetaDataItem, SQL, SqlParser, TypeDoesNotMatch}
import play.api.Configuration
import play.api.db.DBApi
import play.api.libs.json.{JsObject, JsValue, Json}
import utils.Hash

@javax.inject.Singleton
class SettingService @Inject()(dbapi: DBApi, configuration: Configuration) extends AnormJson with AnormCoordinate {
  private val db = dbapi.database("default")

  private lazy val cryptoSecret = configuration.underlying.getString("play.crypto.secret")

  private val defaultValues = Map(
    "AGENTS" -> Json.arr(
      Json.obj(
        "id" -> "admin",
        "name" -> "Jean Paul",
        "qualite" -> "service développement durable",
        "email" -> "jean.paul.durable@example.com",
        "admin" -> true,
        "instructor" -> true,
        "canReview" -> false,
        "finalReview" -> false
      ),
      Json.obj(
        "id" -> "voirie",
        "name" -> "Jean Marc",
        "qualite" -> "service de la voirie",
        "email" -> "jean.marc.voirie@example.com",
        "admin" -> false,
        "instructor" -> false,
        "canReview" -> true,
        "finalReview" -> false
      ),
      Json.obj(
        "id" -> "elu",
        "name" -> "Natasha Dupond",
        "qualite" -> "adjointe au maire, parc et jardins",
        "email" -> "natasha.dupond.elu@example.com",
        "admin" -> true,
        "instructor" -> false,
        "canReview" -> true,
        "finalReview" -> true
      )
    )
  )

  @inline private def className(that: Any): String =
    if (that == null) "<null>" else that.getClass.getName

  private implicit val jsValueParser: anorm.Column[JsValue] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue))
        case json: String =>
          Right(Json.parse(json))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to JsValue for column $qualified"))
      }
    }

  private val settingParser = SqlParser.str("key") ~ SqlParser.get[JsValue]("value") map SqlParser.flatten

  def findByKey(city: String)(key: String) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM setting WHERE key = {key} AND city = {city}").on('key -> key, 'city -> city).as(settingParser.singleOpt).orElse(defaultValues.get(key).map(key -> _))
  }

  def update(city: String)(key: String, value: JsValue) = db.withTransaction { implicit connection =>
    SQL(
      """
          INSERT INTO setting VALUES (
            {key}, {city}, {value}
          ) ON CONFLICT (key, city) DO UPDATE SET value = {value}
      """
    ).on(
      'key -> key,
      'city -> city,
      'value -> value
    ).executeUpdate()
  }

  def all(city: String) = db.withConnection { implicit connection =>
    val dbResult = SQL("SELECT * FROM setting WHERE city = {city}").on('city -> city).as(settingParser.*).toMap
    defaultValues.map { case (key, value) =>
      key -> dbResult.getOrElse(key, value)
    }
  }
}
