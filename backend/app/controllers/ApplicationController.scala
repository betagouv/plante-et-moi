package controllers

import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject._

import play.api._
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import models._
import org.joda.time.DateTime
import play.api.data.format.Formats


@Singleton
class ApplicationController @Inject() (ws: WSClient, configuration: play.api.Configuration) extends Controller {
  private def getCity(request: RequestHeader) =
    request.session.get("city").getOrElse("Arles")

  private def getService(request: RequestHeader) =
    request.session.get("service").getOrElse("Arles")

  private lazy val typeformId = configuration.underlying.getString("typeform.id")
  private lazy val typeformKey = configuration.underlying.getString("typeform.key")

  def projects(city: String) =
    ws.url(s"https://api.typeform.com/v1/form/$typeformId")
      .withQueryString("key" -> typeformKey,
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
        val typ = (answer \ "hidden" \ "type").asOpt[String].map(_.stripPrefix("projet de ").stripSuffix(" fleuris").capitalize).getOrElse("Inconnu")
        val email = (answer \ "answers" \ "email_38072800").asOpt[String].getOrElse("non_renseigné@example.com")
        implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd HH:mm:ss")
        val date = (answer \ "metadata" \ "date_submit").as[DateTime]
        val firstname = (answer \ "answers" \ "textfield_38072796").asOpt[String].getOrElse("John")
        val lastname = (answer \ "answers" \ "textfield_38072795").asOpt[String].getOrElse("Doe")
        val name = s"$firstname $lastname"
        val id = (answer \ "token").as[String]
        val phone = (answer \ "answers" \ "textfield_38072797").asOpt[String]
        //date.format(DateTimeFormatter.)
        models.Application(id, name, email, "En cours", "0/6", typ, address, date, phone)
      }
      val defaults = List(
        models.Application("23", "Yves Laurent", "yves.laurent@example.com", "En cours", "1/5", "Pied d'arbre", s"9 Avenue de Provence, $city", new DateTime("2017-01-04")),
        models.Application("02", "Jean-Paul Dupont", "jean-paul.dupont@example.com", "Accepté", "5/5", "Jardinière", s"3 Rue Vauban, $city", new DateTime("2017-01-02"))
      )
      responses ++ defaults
    }

  def all = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      Ok(views.html.allApplications(responses))
    }
  }

  def map = Action.async { implicit request =>
    val city = getCity(request)
    projects(city).map { responses =>
      Ok(views.html.mapApplications(city, responses))
    }
  }

  def my = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      val afterFilter = responses.filter { _.status == "En cours" }
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
    Redirect(routes.ApplicationController.all()).withSession("city" -> newCity)
  }
}