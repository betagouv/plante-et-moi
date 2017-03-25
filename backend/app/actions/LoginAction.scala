package actions

import javax.inject.{Inject, Singleton}

import controllers.routes
import models._
import play.api.mvc._
import play.api.mvc.Results.{Redirect, _}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

class RequestWithAgent[A](val currentAgent: Agent, val currentCity: String, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class LoginAction @Inject()(agentService: AgentService) extends ActionBuilder[RequestWithAgent] with ActionRefiner[Request, RequestWithAgent] {
  private def getCity(request: RequestHeader) =
    request.session.get("city").getOrElse("arles").toLowerCase()

  private def queryToString(qs: Map[String, Seq[String]]) = {
    val queryString = qs.map { case (key, value) => key + "=" + value.sorted.mkString("|,|") }.mkString("&")
    if (queryString.nonEmpty) "?" + queryString else ""
  }

  override def refine[A](request: Request[A]) =
    Future.successful {
      val city = getCity(request)
      (request.getQueryString("key").flatMap(agentService.byKey(city)), request.session.get("agentId").flatMap(agentService.byId(city))) match {
        case (Some(agent), _) =>
          val url = request.path + queryToString(request.queryString - "key")
          Left(Redirect(Call(request.method, url)).withSession(request.session - "agentId" + ("agentId" -> agent.id)))
        case (None, Some(agent)) =>
          Right(new RequestWithAgent(agent, city, request))
        case _ =>
          Left(Redirect(routes.ApplicationController.login()).withSession(request.session - "agentId").flashing("message" -> "Vous n'avez pas pu être identifié"))
      }
    }

  private def redirectAgent(agent: Agent) = if(agent.instructor) {
      routes.ApplicationController.all()
    } else {
      routes.ApplicationController.my()
    }
}
