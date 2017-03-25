package controllers

import javax.inject.{Inject, Singleton}

import actions.LoginAction
import models.Review
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.libs.json.Json
import services.SettingService
import play.api.mvc._
import play.api.data.Forms._

import scala.concurrent.Future

@Singleton
class SettingController @Inject()(settingService: SettingService,
                                loginAction: LoginAction) extends Controller {
  def all = loginAction { implicit request =>
    val settings = settingService.all(request.currentCity).mapValues(Json.prettyPrint)
    Ok(views.html.allSettings(settings, request.currentAgent))
  }

  case class SettingData(key: String, value: String)
  val reviewForm = Form(
    mapping(
      "key" -> text,
      "value" -> text
    )(SettingData.apply)(SettingData.unapply)
  )
  def update = loginAction { implicit request =>
    reviewForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest("Key or value attribute not found")
      },
      settingData => {
        settingService.update(request.currentCity)(settingData.key, Json.parse(settingData.value))
        Redirect(routes.SettingController.all())
      }
    )
  }
}
