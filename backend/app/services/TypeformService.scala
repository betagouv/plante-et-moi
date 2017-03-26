package services

import java.util.Locale
import javax.inject._

import akka.actor._
import models.{Application, Coordinates, EmailTemplate, EmailTemplateService}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.libs.ws.WSClient

import scala.collection.mutable.ListBuffer
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.mailer.{Email, MailerClient}
import utils.Hash

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@Singleton
class TypeformService @Inject()(system: ActorSystem, configuration: play.api.Configuration, ws: WSClient, applicationService: ApplicationService, mailerClient: MailerClient, emailTemplateService: EmailTemplateService) {

  private case class TypeformQuestion(id: String, question: String, field_id: Int)
  private case class TypeformStats(responses: Map[String,Int])
  private case class TypeformMetadata(browser: String,
                              platform: String,
                              date_land: DateTime,
                              date_submit: DateTime,
                              user_agent: String,
                              referer: String,
                              network_id: String)
  private case class TypeformResponse(completed: String, token: String, metadata: TypeformMetadata, hidden: Map[String, Option[String]], answers: Map[String, String])
  private case class TypeformResult(http_status: Int, stats: TypeformStats, questions: List[TypeformQuestion], responses: List[TypeformResponse])


  private implicit val optionMap = Reads[Option[String]]{
    case JsString(s) => JsSuccess(Some(s))
    case JsNull => JsSuccess(None)
    case _ => JsError("Not a optionnal String")
  }
  //private implicit val hiddenMap = Reads.mapReads[Option[String]]
  private implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd HH:mm:ss")
  private implicit val metadataReads = Json.reads[TypeformMetadata]
  private implicit val questionReads = Json.reads[TypeformQuestion]
  private implicit val statsReads = Json.reads[TypeformStats]
  private implicit val responseReads = Json.reads[TypeformResponse]
  private implicit val resultReads = Json.reads[TypeformResult]


  def getForm(id: String, key: String, completed: Boolean, limit: Int = 20, orderBy: String = "date_submit,desc") = {
    val request = ws.url(s"https://api.typeform.com/v1/form/$id")
      .withQueryString("key" -> key,
        "completed" -> s"$completed",
        "order_by" -> orderBy,
        "limit" -> s"$limit")
    Logger.info(s"Get typeform data ${request.url}")
    request.get()
  }

  private lazy val typeformIds = configuration.underlying.getString("typeform.ids").split(",")
  private lazy val typeformKey = configuration.underlying.getString("typeform.key")
  private lazy val typeformDomains = configuration.underlying.getString("typeform.domains").split(",")

  private val refresh = configuration.getMilliseconds("typeform.refresh") match {
    case Some(t) => t millis
    case None => 2 minutes
  }
  private val delay = 0 minutes

  private val scheduledTask = system.scheduler.schedule(delay, refresh)(refreshTask)

  private def refreshTask = {
    val responses = Future.reduce(typeformIds.map{ id =>
      getForm(id, typeformKey, true, 100).map { response =>
        val result = response.json.validate[TypeformResult]
        var applications = List[Application]()
        result match {
          case success: JsSuccess[TypeformResult] =>
            Logger.info(s"TypeformService: convert data for $id")
            val result = success.get
            applications = result.responses
                .filter(filterPerDomains)
                .map(mapResponseToApplication(result.questions))
          case error: JsError =>
            val errorString = JsError.toJson(error).toString()
            Logger.error(s"TypeformService: json errors for $id $errorString")
        }
        applications
      }
    })(_ ++ _)
    responses.onComplete {
      case Failure(ex) =>
          Logger.error("Error occured when retrieve data from typeform", ex)
      case Success(applications) =>
        applications.groupBy(_.city).foreach { cityApplications =>
          manageApplicationForCity(cityApplications._1, cityApplications._2)
        }
    }
  }

  private def manageApplicationForCity(city: String, applications: List[Application]): Unit = {
    emailTemplateService.get(city, "RECEPTION_EMAIL").fold {
      Logger.error(s"No RECEPTION_EMAIL email template for city $city")
    } { emailTemplate =>
      applications foreach { application =>
        val app = applicationService.findByApplicationId(application.id)
        if (app.isEmpty) {
          Logger.info(s"Import application for ${application.address}")
          sendNewApplicationEmail(emailTemplate)(application)
          applicationService.insert(application)
        }
      }
    }
  }

  private def filterPerDomains(response: TypeformResponse): Boolean = {
    val domain = response.hidden("domain").getOrElse("nodomain")
    typeformDomains.contains(domain)
  }

  private def sendNewApplicationEmail(emailTemplate: EmailTemplate)(application: models.Application) = {
    val applicationString =
      s"""- Date de la demande:
         |${application.creationDate.toString("dd MMM YYYY", new Locale("fr"))}
         |
         |- Nom:
         |${application.lastname}
         |
         |- Prénom:
         |${application.firstname}
         |
         |- Email:
         |${application.email}
         |
         |- Type:
         |${application._type}
         |
         |- Address du projet:
         |${application.address}
         |
         |- Numéro de téléphone:
         |${application.phone.getOrElse("pas de numéro de téléphone")}
         |
         ${application.fields.map{ case (key, value) => s"|- $key:\n $value\n" }.mkString}
         |- Nombre de fichiers joint à la demande: ${application.files.length}
       """.stripMargin
    val body = emailTemplate.body
      .replaceAll("<application.id>", application.id)
      .replaceAll("<application>", applicationString)

    val email = Email(
      emailTemplate.title,
      emailTemplate.from,
      Seq(s"${application.name} <${application.email}>"),
      bodyText = Some(body),
      replyTo = emailTemplate.replyTo
    )
    Logger.info(s"Send mail to ${application.email}")
    mailerClient.send(email)
  }

  def mapResponseToApplication(questions: List[TypeformQuestion])(response: TypeformResponse) = {
    val _type = response.hidden("type").get.stripPrefix("projet de ").stripSuffix(" fleuris").capitalize
    val lat = response.hidden("lat").get.toDouble
    val lon = response.hidden("lon").get.toDouble
    val coordinates = Coordinates(lat, lon)
    val city = response.hidden("city").get.toLowerCase()
    val typeformId = response.token
    val date = response.metadata.date_submit

    var address = response.hidden("address").get
    var email = "inconnue@example.com"
    var firstname = "John"
    var lastname = "Doe"
    var phone: Option[String] = None

    var fields = mutable.Map[String,String]()
    var files = ListBuffer[String]()
    response.answers.foreach { answer =>
      val question = questions.find(_.id == answer._1).map(_.question.replaceAll("<[^>]*>", ""))
      (answer._1, question) match {
        case (id, _) if id.startsWith("fileupload_") =>
          files += answer._2.split('?')(0)
        case (id, _) if id.startsWith("email_") =>
          email = answer._2
        case (id, Some(question)) if id.startsWith("textfield_") && question.toLowerCase.contains("adresse de votre") =>
          address = answer._2
        case (id, Some(question)) if id.startsWith("textfield_") && question.toLowerCase.contains("prénom") =>
          firstname = answer._2
        case (id, Some(question)) if id.startsWith("textfield_") && question.toLowerCase.endsWith("nom") =>
          lastname = answer._2
        case (id, Some(question)) if id.startsWith("textfield_") && question.toLowerCase.contains("téléphone") =>
          phone = Some(answer._2)
        case (id, Some(question)) if id.startsWith("yesno_") =>
          val answerString = answer._2 match {
            case "1" => "Oui"
            case "0" => "Non"
            case _ => "???"
          }
          fields += question -> answerString
        case (_, Some(question)) =>
          val previous = fields.get(question).map(old => s"$old\n${answer._2}").getOrElse(answer._2)
          fields += question -> previous
        case _ =>
      }
    }
    var source = "typeform"
    var applicationId = Hash.md5(s"$source$typeformId")
    models.Application(applicationId, city, "Nouvelle", firstname, lastname, email, _type, address, date, coordinates, source, typeformId, phone, fields.toMap, files.toList)
  }
}