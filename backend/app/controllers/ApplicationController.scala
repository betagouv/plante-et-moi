package controllers

import javax.inject._

import play.api._
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import models._
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._

import scala.concurrent.Future

@Singleton
class ApplicationController @Inject() (ws: WSClient, configuration: play.api.Configuration, reviewService: ReviewService) extends Controller {
  private def getCity(request: RequestHeader) =
    request.session.get("city").getOrElse("Arles")

  private def currentAgent(request: RequestHeader): Agent = {
    val id = request.session.get("agentId").getOrElse("admin")
    agents.find(_.id == id).get
  }

  private val agents = List(
    Agent("admin", "Jean Paul", "service développement durable", "jean.paul@durable.example.com", true),
    Agent("voirie", "Jeanne D'arc", "service de la voirie", "jeanne.d-arc@voirie.example.com", false),
    Agent("elu", "Richard Dupont", "adjoint au maire, Transition écologique et énergétique, Parcs et jardins", "jdupont@elu.example.com", false)
  )

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
        (answer \ "hidden" \ "city").get == Json.toJson(city) &&
          (answer \ "hidden" \ "lat").get != JsNull &&
          (answer \ "hidden" \ "lon").get != JsNull
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
        val lat = (answer \ "hidden" \ "lat").as[String].toDouble
        val lon = (answer \ "hidden" \ "lon").as[String].toDouble
        val coordinates = Coordinates(lat, lon)

        //date.format(DateTimeFormatter.)
        models.Application(id, name, email, "En cours", "0/6", typ, address, date, coordinates, phone)
      }
      val defaults = List(
        models.Application("23", "Yves Laurent", "yves.laurent@example.com", "En cours", "1/5", "Pied d'arbre", s"9 Avenue de Provence, $city", new DateTime("2017-01-04"), Coordinates(0,0)),
        models.Application("02", "Jean-Paul Dupont", "jean-paul.dupont@example.com", "Accepté", "5/5", "Jardinière", s"3 Rue Vauban, $city", new DateTime("2017-01-02"), Coordinates(0,0))
      )
      responses ++ defaults
    }

  def all = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      Ok(views.html.allApplications(responses, currentAgent(request)))
    }
  }

  def map = Action.async { implicit request =>
    val city = getCity(request)
    projects(city).map { responses =>
      Ok(views.html.mapApplications(city, responses, currentAgent(request)))
    }
  }

  def my = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      val afterFilter = responses.filter { _.status == "En cours" }
      Ok(views.html.myApplications(afterFilter, currentAgent(request)))
    }
  }

  def show(id: String) = Action.async { implicit request =>
    projects(getCity(request)).map { responses =>
      responses.filter {_.id == id } match {
        case x :: _ =>
          Ok(views.html.application(x, currentAgent(request)))
        case _ =>
          NotFound("")
      }
    }
  }

  def changeCity(newCity: String) = Action {
    Redirect(routes.ApplicationController.all()).withSession("city" -> newCity)
  }

  def changeAgent(newAgentId: String) = Action {
    Redirect(routes.ApplicationController.all()).withSession("agentId" -> newAgentId)
  }

  case class ReviewData(favorable: Boolean, comment: String)
  val reviewForm = Form(
    mapping(
      "favorable" -> boolean,
      "comment" -> text
    )(ReviewData.apply)(ReviewData.unapply)
  )

  def addReview(applicationId: String) = Action.async { implicit request =>
    reviewForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(""))
      },
      reviewData => {
        val city = getCity(request)
        val agent = currentAgent(request)
        val review = Review(applicationId, agent.id, DateTime.now(), reviewData.favorable, reviewData.comment)
        Future(reviewService.insert(review)).map { _ =>
          Redirect(routes.ApplicationController.show(applicationId))
        }
      }
    )
  }
}