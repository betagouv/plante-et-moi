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
        "review" -> false,
        "finalReview" -> false
      ),
      Json.obj(
        "id" -> "voirie",
        "name" -> "Jean Marc",
        "qualite" -> "service de la voirie",
        "email" -> "jean.marc.voirie@example.com",
        "admin" -> false,
        "instructor" -> false,
        "review" -> true,
        "finalReview" -> false
      ),
      Json.obj(
        "id" -> "elu",
        "name" -> "Natasha Dupond",
        "qualite" -> "adjointe au maire, parc et jardins",
        "email" -> "natasha.dupond.elu@example.com",
        "admin" -> true,
        "instructor" -> false,
        "review" -> true,
        "finalReview" -> true
      )
    )

      /*
      Agent(id: String,
                 name: String,
                 qualite: String,
                 email: String,
                 key: String,
                 admin: Boolean,
                 instructor: Boolean,
                 review: Boolean,
                 finalReview: Boolean)
      Agent("admin", "Jean Paul", "service développement durable", "jean.paul.durable@example.com", Hash.sha256(s"${cryptoSecret}Iz7h_09MqC"), true, true, false, false),
      Agent("verts", "Jeanne D'arc", "direction des espaces verts-propreté", "jeanne.d-arc.verts@example.com", Hash.sha256(s"${cryptoSecret}nYX3aeNs"), false, false, true, false),
      Agent("voirie", "Jeanne D'arc", "service de la voirie", "jeanne.d-arc.voirie@example.com", Hash.sha256(s"${cryptoSecret}o-35qG5T"), false, false, true, false),
      Agent("public", "Jeanne D'arc", "direction occupation du domaine public", "jeanne.d-arc.public@example.com", Hash.sha256(s"{cryptoSecret}_eMMIKc9"), false, false, true, false),
      Agent("elu", "Richard Dupont", "adjoint au maire parcs et jardins", "jdupont.elu@example.com", Hash.sha256(s"${cryptoSecret}Bv5_75R"), true, false, true, true) */
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

  private val settingParser = SqlParser.str("key") ~ SqlParser.get[JsValue]("value") map(SqlParser.flatten)

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
