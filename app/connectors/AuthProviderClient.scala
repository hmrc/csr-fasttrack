/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import config.WSHttp
import connectors.AuthProviderClient._
import connectors.ExchangeObjects.Implicits._
import connectors.ExchangeObjects._
import model.Exceptions.{ ConnectorException, EmailTakenException }
import play.api.http.Status._
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException, Upstream4xxResponse }

object AuthProviderClient extends AuthProviderClient {
  sealed class ErrorRetrievingReportException(message: String) extends Exception(message)
  sealed class TokenExpiredException() extends Exception
  sealed class TokenEmailPairInvalidException() extends Exception
  sealed class UserRoleDoesNotExist(message: String) extends Exception(message)
  sealed class TooManyResultsException(message: String) extends Exception(message)
  sealed class CannotUpdateDocumentException(message: String) extends Exception(message)
}

trait AuthProviderClient {

  sealed abstract class UserRole(val name: String)

  case object CandidateRole extends UserRole("candidate")

  case object FasttrackTeamRole extends UserRole("fasttrack-team")
  case object ServiceSupportRole extends UserRole("service-support")
  case object ServiceAdminRole extends UserRole("service-admin")
  case object SuperAdminRole extends UserRole("super-admin")
  case object TechnicalAdminRole extends UserRole("tech-admin")
  case object Assessor extends UserRole("assessor")

  val allRoles = List(FasttrackTeamRole, ServiceSupportRole, ServiceAdminRole, SuperAdminRole, TechnicalAdminRole, Assessor)

  def getRole(roleName: String) = allRoles.find(_.name == roleName).getOrElse(throw new UserRoleDoesNotExist(s"No such role: $roleName"))

  val ServiceName = "fasttrack17"

  import config.MicroserviceAppConfig.userManagementConfig._

  def candidatesReport(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    WSHttp.GET(s"$url/service/$ServiceName/findByRole/${CandidateRole.name}").map { response =>
      if (response.status == OK) {
        response.json.as[List[Candidate]]
      } else {
        throw new ErrorRetrievingReportException("There was a general problem retrieving the list of candidates")
      }
    }

  def addUser(email: String, password: String, firstName: String,
    lastName: String, role: UserRole)(implicit hc: HeaderCarrier): Future[UserResponse] =
    WSHttp.POST(s"$url/add", AddUserRequest(email.toLowerCase, password, firstName, lastName, role.name, ServiceName)).map { response =>
      response.json.as[UserResponse]
    }.recover {
      case Upstream4xxResponse(_, 409, _, _) => throw EmailTakenException()
    }

  def removeAllUsers()(implicit hc: HeaderCarrier): Future[Unit] =
    WSHttp.GET(s"$url/test-commands/remove-all?service=$ServiceName").map { response =>
      if (response.status == OK) {
        ()
      } else {
        throw new ConnectorException(s"Bad response received when removing users: $response")
      }
    }

  def getToken(email: String)(implicit hc: HeaderCarrier): Future[String] =
    WSHttp.GET(s"$url/auth-code/$ServiceName/$email").map { response =>
      if (response.status == OK) {
        response.body
      } else {
        throw new ConnectorException(s"Bad response received when getting token for user: $response")
      }
    }

  def activate(email: String, token: String)(implicit hc: HeaderCarrier): Future[Unit] =
    WSHttp.POST(s"$url/activate", ActivateEmailRequest(ServiceName, email.toLowerCase, token)).map(_ => (): Unit)
      .recover {
        case Upstream4xxResponse(_, 410, _, _) => throw new TokenExpiredException()
        case _: NotFoundException => throw new TokenEmailPairInvalidException()
      }

  def findByFirstName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(s"$url/service/$ServiceName/findByFirstName", FindByFirstNameRequest(roles, name)).map { response =>
      response.json.as[List[Candidate]]
    }.recover {
      case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }

  def findByLastName(name: String, roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(s"$url/service/$ServiceName/findByLastName", FindByLastNameRequest(roles, name)).map { response =>
      response.json.as[List[Candidate]]
    }.recover {
      case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }

  def findByFirstNameAndLastName(firstName: String, lastName: String,
    roles: List[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(
      s"$url/service/$ServiceName/findByFirstNameLastName",
      FindByFirstNameLastNameRequest(roles, firstName, lastName)
    ).map { response =>
        response.json.as[List[Candidate]]
      }.recover {
        case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
          throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
        case errorResponse =>
          throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
      }
  }

  def findByUserId(userId: String)(implicit hc: HeaderCarrier): Future[Option[AuthProviderUserDetails]] = {
    WSHttp.POST(s"$url/service/$ServiceName/findUserById", FindByUserIdRequest(userId.toString())).map { response =>
      response.json.asOpt[AuthProviderUserDetails]
    }.recover {
      case Upstream4xxResponse(_, REQUEST_ENTITY_TOO_LARGE, _, _) =>
        throw new TooManyResultsException(s"Too many results were returned, narrow your search parameters")
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }

  def update(userId: String, request: UpdateDetailsRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    WSHttp.PUT(s"$url/service/$ServiceName/details/$userId", request).map(_ => (): Unit)
      .recover {
        case errorResponse =>
          throw new CannotUpdateDocumentException(s"Cannot update user details: $errorResponse")
      }
  }

  def findByUserIds(userIds: Set[String])(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    WSHttp.POST(s"$url/service/$ServiceName/findUsersByIds", FindByUserIdsRequest(userIds.toList)).map { response =>
      response.json.as[List[Candidate]]
    }.recover {
      case errorResponse =>
        throw new ConnectorException(s"Bad response received when getting token for user: $errorResponse")
    }
  }
}
