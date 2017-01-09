package controllers

import javax.inject._

import play.api._
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import models._

@Singleton
class ApplicationController @Inject() (ws: WSClient, configuration: play.api.Configuration) extends Controller {
  private def getCity(request: RequestHeader) =
    request.session.get("city").getOrElse("Arles")

  def projects(city: String) =
    ws.url("https://api.typeform.com/v1/form/WNwIJx")
      .withQueryString("key" -> configuration.underlying.getString("typeform.key"),
        "completed" -> "true",
        "order_by" -> "date_submit,desc",
        "limit" -> "20").get().map { response =>
      val json = response.json
      val totalShowing = (json \ "stats" \ "responses" \ "completed").as[Int]
      val totalCompleted = (json \ "stats" \ "responses" \ "showing").as[Int]

      val responses = (json \ "responses").as[List[JsValue]].filter { answer =>
        (answer \ "hidden" \ "city").get == Json.toJson(city)
      }.map { answer =>
        val address = (answer \ "hidden" \ "address").asOpt[String].getOrElse("12 rue non renseigné")
        val typ = (answer \ "hidden" \ "type").asOpt[String].map(_.stripPrefix("projet de ").capitalize).getOrElse("Inconnu")
        val email = (answer \ "answers" \ "email_38072800").asOpt[String].getOrElse("non_renseigné@example.com")
        val date = (answer \ "metadata" \ "date_submit").as[String]
        val firstname = (answer \ "answers" \ "textfield_38072796").asOpt[String].getOrElse("John")
        val lastname = (answer \ "answers" \ "textfield_38072795").asOpt[String].getOrElse("Doe")
        val name = s"$firstname $lastname"
        val id = (answer \ "token").as[String]
        val phone = (answer \ "answers" \ "textfield_38072797").asOpt[String]
        models.Application(id, name, email, "Nouvelle", "0/6", typ, address, date, phone)
      }
      val defaults = List(
        models.Application("23", "Yves Laurent", "yves.laurent@example.com", "Demande d'avis", "1/5", "Pied d'arbre", s"9 Avenue de Provence, $city", "2017-01-04 11:30:14"),
        models.Application("02", "Jean-Paul Dupont", "jean-paul.dupont@example.com", "Accepté", "5/5", "Jardiniére", s"3 Rue Vauban, $city", "2017-01-02 16:30:14")
      )
      responses ++ defaults
    }

  def all = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      Ok(views.html.allApplications(responses))
    }
  }

  def my = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      val afterFilter = responses.filter { _.status == "Nouvelle" }
      Ok(views.html.myApplications(afterFilter))
    }
  }

  def show(id: String) = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      responses.filter {_.id == id } match {
        case x :: _ =>
          Ok(views.html.application(x))
        case _ =>
          NotFound("")
      }
    }
  }

  def change(newCity: String) = Action {
      Ok("City changed").withSession("city" -> newCity)
  }
}