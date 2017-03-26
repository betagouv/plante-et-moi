package models

import javax.inject.Inject

import play.api.libs.json.{JsPath, JsString, Json, Reads}
import services.SettingService

case class EmailTemplate(from: String, title: String, body: String, replyTo: Option[String])

@javax.inject.Singleton
class EmailTemplateService @Inject()(configuration: play.api.Configuration, settingService: SettingService) {

  private implicit val emailTemplateRead = Json.reads[EmailTemplate]

  def get(city: String, id: String) = {
    settingService.findByKey(city)(id).flatMap(_._2.validate[EmailTemplate].asOpt)
  }
}