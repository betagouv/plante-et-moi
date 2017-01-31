package models

import javax.inject.Inject

import anorm.{Macro, RowParser, SQL}
import play.api.db.DBApi

case class ApplicationExtra(applicationId: String, status: String)

@javax.inject.Singleton
class ApplicationExtraService @Inject()(dbapi: DBApi) {
  private val db = dbapi.database("default")
  val simple: RowParser[ApplicationExtra] = Macro.parser[ApplicationExtra]("application_id", "status")

  private def create(applicationId: String) = {
    val newApplicationExtra = ApplicationExtra(applicationId, "Nouvelle")
    insertOrUpdate(newApplicationExtra)
    newApplicationExtra
  }

  def findByApplicationId(applicationId: String) = db.withConnection { implicit connection =>
    lazy val newApplicationExtra = create(applicationId)
    SQL("SELECT * FROM application_extra WHERE application_id = {application_id}").on('application_id -> applicationId).as(simple.singleOpt).getOrElse(newApplicationExtra)
  }

  def insertOrUpdate(applicationExtra: ApplicationExtra) = db.withConnection { implicit connection =>
    SQL(   // Update on and Merge not support on H2 and postgresql
      """
          DELETE FROM application_extra WHERE application_id =
            {application_id}
        """
    ).on(
      'application_id -> applicationExtra.applicationId
    ).executeUpdate()
    SQL(
      """
          INSERT INTO application_extra VALUES (
            {application_id}, {status}
          )
        """
    ).on(
      'application_id -> applicationExtra.applicationId,
      'status -> applicationExtra.status
    ).executeUpdate()
  }
  def all() = db.withConnection { implicit connection =>
    SQL("SELECT * FROM applicationExtra").as(simple.*)
  }
}