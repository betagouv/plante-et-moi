package models

import org.joda.time.DateTime
import javax.inject.Inject

import anorm.SqlParser._
import anorm._
import play.api.db.DBApi
import anorm.{Macro, RowParser}
import anorm.JodaParameterMetaData._

import play.api.libs.concurrent.Execution.Implicits.defaultContext


case class Review(applicationId: String, agentId: String, creationDate: DateTime, favorable: Boolean, comment: String)

@javax.inject.Singleton
class ReviewService @Inject()(dbapi: DBApi) {
  private val db = dbapi.database("default")
  val simple: RowParser[Review] = Macro.parser[Review]("application_id", "agent_id", "creation_date", "favorable", "comment")

  def findByApplicationId(applicationId: String) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM review WHERE application_id = {application_id}").on('application_id -> applicationId).as(simple.*)
  }

  def insertOrUpdate(review: Review) = db.withConnection { implicit connection =>
    SQL(   // Update on and Merge not support on H2 and postgresql
      """
          DELETE FROM review WHERE application_id =
            {application_id} AND agent_id = {agent_id}
        """
    ).on(
      'application_id -> review.applicationId,
      'agent_id -> review.agentId
    ).executeUpdate()
    SQL(
        """
          INSERT INTO review VALUES (
            {application_id}, {agent_id}, {creation_date}, {favorable}, {comment}
          )
        """
      ).on(
        'application_id -> review.applicationId,
        'agent_id -> review.agentId,
        'creation_date -> review.creationDate,
        'favorable -> review.favorable,
        'comment -> review.comment
    ).executeUpdate()
  }
}