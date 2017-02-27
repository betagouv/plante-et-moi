package services

import javax.inject._

import akka.actor._
import controllers.routes
import models.{Application, Coordinates}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.libs.ws.WSClient

import scala.collection.mutable.ListBuffer
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class TypeformService @Inject()(system: ActorSystem, configuration: play.api.Configuration, ws: WSClient, applicationService: ApplicationService, mailerClient: MailerClient){
  private lazy val typeformIds = configuration.underlying.getString("typeform.ids").split(",")
  private lazy val typeformKey = configuration.underlying.getString("typeform.key")

  private val refresh = configuration.getMilliseconds("typeform.refresh") match {
    case Some(t) => t millis
    case None => 2 minutes
  }
  private val delay = 0 minutes

  private val scheduledTask = system.scheduler.schedule(delay, refresh)(refreshTask)

  def refreshTask = {
    val responses = Future.reduce(typeformIds.map(getResponsesByFormId))(_ ++ _)
    responses.foreach { applications =>
      applications.foreach { application =>
        val app = applicationService.findByApplicationId(application.id)
        if(app.isEmpty) {
          sendNewApplicationEmail(application)
          applicationService.insert(application)
        }
      }
    }
  }

  private def sendNewApplicationEmail(application: models.Application) = {
    val email = Email(
      s"Nouvelle demande de permis de végétalisation: ${application.address}",
      "Plante et Moi <administration@plante-et-moi.fr>",
      Seq(s"${application.name} <${application.email}>"),
      bodyText = Some(s"""Bonjour ${application.name},
                         |
                         |Nous avons bien reçu votre demande de végétalisation au ${application.address} (c'est un projet de ${application._type}).
                         |
                         |L’autorisation suivra dans quelques semaines, accompagnée de la charte rappelant vos engagements ainsi que la liste des plantes proscrites et des plantes recommandées.
                         |
                         |Merci de votre demande,
                         |Si vous avez des questions, n'hésitez pas à nous contacter""".stripMargin),
      bodyHtml = Some(
        s"""<html>
           |<body>
           | Bonjour ${application.name}, <br>
           | <br>
           | Nous avons bien reçu votre demande de végétalisation au ${application.address} (c'est un projet de ${application._type}).<br>
           | <br>
           | L’autorisation suivra dans quelques semaines, accompagnée de la charte rappelant vos engagements ainsi que la liste des plantes proscrites et des plantes recommandées. <br>
           | <br>
           | Merci de votre demande, <br>
           | Si vous avez des questions, n'hésitez pas à nous contacter
           |</body>
           |</html>""".stripMargin)
    )
    Logger.info(s"Send mail to ${application.email}")
    mailerClient.send(email)
  }

  def mapArlesTypeformJsonToApplication(answer: JsValue): models.Application = {
    val selectedAddress = (answer \ "hidden" \ "address").asOpt[String].getOrElse("12 rue de la demo")
    val address = (answer \ "answers" \ "textfield_38117960").asOpt[String].getOrElse(selectedAddress)
    val `type` = (answer \ "hidden" \ "type").asOpt[String].map(_.stripPrefix("projet de ").stripSuffix(" fleuris").capitalize).getOrElse("Inconnu")
    val email = (answer \ "answers" \ "email_38072800").asOpt[String].getOrElse("inconnue@example.com")
    implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd HH:mm:ss")
    val date = (answer \ "metadata" \ "date_submit").as[DateTime]
    val firstname = (answer \ "answers" \ "textfield_38072796").asOpt[String].getOrElse("John")
    val lastname = (answer \ "answers" \ "textfield_38072795").asOpt[String].getOrElse("Doe")
    val name = s"$firstname $lastname"
    val id = (answer \ "token").as[String]
    val phone = (answer \ "answers" \ "textfield_38072797").asOpt[String]
    val lat = (answer \ "hidden" \ "lat").as[String].toDouble
    val lon = (answer \ "hidden" \ "lon").as[String].toDouble
    val city = (answer \ "hidden" \ "city").as[String]
    val coordinates = Coordinates(lat, lon)
    var fields = Map[String,String]()
    (answer \ "answers" \ "textfield_41115782").asOpt[String].map { answer =>
      fields += "Espéces de plante grimpante" -> answer
    }
    (answer \ "answers" \ "textfield_41934708").asOpt[String].map { answer =>
      fields += "Forme" -> answer
    }
    (answer \ "answers" \ "list_42010898_choice").asOpt[String].map { answer =>
      fields += "Couleur" -> answer
    }
    (answer \ "answers" \ "list_42010898_other").asOpt[String].map { answer =>
      fields += "Couleur" -> answer
    }
    (answer \ "answers" \ "textfield_41934830").asOpt[String].map { answer =>
      fields += "Matériaux" -> answer
    }
    (answer \ "answers" \ "list_41934920_choice").asOpt[String].map { answer =>
      fields += "Position" -> answer
    }
    (answer \ "answers" \ "list_40487664_choice").asOpt[String].map { answer =>
      fields += "Collectif" -> "Oui"
    }
    (answer \ "answers" \ "textfield_40930276").asOpt[String].map { answer =>
      fields += "Nom du collectif" -> answer
    }
    var files = ListBuffer[String]()
    (answer \ "answers" \ "fileupload_40488503").asOpt[String].map { croquis =>
      files.append(croquis.split('?')(0))
    }
    (answer \ "answers" \ "fileupload_40489342").asOpt[String].map { image =>
      files.append(image.split('?')(0))
    }
    models.Application(id, city, "Nouvelle", name, email, `type`, address, date, coordinates, phone, fields, files.toList)
  }

  def getResponsesByFormId(id: String) = {
    Logger.info(s"Refresh form $id")
    ws.url(s"https://api.typeform.com/v1/form/$id")
      .withQueryString("key" -> typeformKey,
        "completed" -> "true",
        "order_by" -> "date_submit,desc",
        "limit" -> "100").get().map { response =>
      val json = response.json
      val totalShowing = (json \ "stats" \ "responses" \ "completed").as[Int]
      val totalCompleted = (json \ "stats" \ "responses" \ "showing").as[Int]

      (json \ "responses").as[List[JsValue]].filter { answer =>
        (answer \ "hidden" \ "city").get != JsNull &&
          (answer \ "hidden" \ "lat").get != JsNull &&
          (answer \ "hidden" \ "lon").get != JsNull
      }.map(mapArlesTypeformJsonToApplication)
    }
  }
}