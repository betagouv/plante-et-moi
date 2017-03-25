package models

import javax.inject.Inject

import services.SettingService
import utils.Hash
import play.api.libs.json._
import play.api.libs.json.Reads._

case class Agent(id: String,
                 name: String,
                 qualite: String,
                 email: String,
                 key: String,
                 admin: Boolean,
                 instructor: Boolean,
                 review: Boolean,
                 finalReview: Boolean)

@javax.inject.Singleton
class AgentService @Inject()(configuration: play.api.Configuration, settingService: SettingService) {
  private lazy val cryptoSecret = configuration.underlying.getString("play.crypto.secret")

  private implicit def resultReads(city: String): Reads[Agent] = {
    JsPath.json.update((JsPath \ 'key).json.copyFrom((JsPath \ 'email).json.pick[JsString].map{ jsString => JsString(Hash.sha256(s"${jsString.value}$city$cryptoSecret")) })) andThen Json.reads[Agent]
  }

  private lazy val agents = List(
    Agent("admin", "Jean Paul", "service développement durable", "jean.paul.durable@example.com", Hash.sha256(s"${cryptoSecret}Iz7h_09MqC"), true, true, false, false),
    Agent("verts", "Jeanne D'arc", "direction des espaces verts-propreté", "jeanne.d-arc.verts@example.com", Hash.sha256(s"${cryptoSecret}nYX3aeNs"), false, false, true, false),
    Agent("voirie", "Jeanne D'arc", "service de la voirie", "jeanne.d-arc.voirie@example.com", Hash.sha256(s"${cryptoSecret}o-35qG5T"), false, false, true, false),
    Agent("public", "Jeanne D'arc", "direction occupation du domaine public", "jeanne.d-arc.public@example.com", Hash.sha256(s"{cryptoSecret}_eMMIKc9"), false, false, true, false),
    Agent("elu", "Richard Dupont", "adjoint au maire parcs et jardins", "jdupont.elu@example.com", Hash.sha256(s"${cryptoSecret}Bv5_75R"), true, false, true, true)
  )

  def all(city: String) = {
    implicit val agentReads = resultReads(city)
    settingService.findByKey(city)("AGENTS").flatMap(_._2.validate[List[Agent]].asOpt).getOrElse(List())
  }

  def byId(city: String)(id: String) = all(city).find(_.id == id)

  def byKey(city: String)(key: String) = all(city).find(_.key == key)
}