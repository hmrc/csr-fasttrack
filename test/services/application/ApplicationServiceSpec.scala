/*
 * Copyright 2016 HM Revenue & Customs
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

package services.application

import model.Commands._
import model.Exceptions.NotFoundException
import org.mockito.Matchers.{ eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.application.GeneralApplicationRepository
import services.AuditService
import services.applicationassessment.ApplicationAssessmentService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class ApplicationServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val ApplicationId = "1111-1111"
  val withdrawApplicationRequest = WithdrawApplicationRequest("reason", Some("other reason"), "Candidate")
  val auditDetails = Map("applicationId" -> ApplicationId, "withdrawRequest" -> withdrawApplicationRequest.toString)

  "withdraw an application" should {
    "work and log audit event" in new ApplicationServiceFixture {
      val result = applicationService.withdraw(ApplicationId, withdrawApplicationRequest)
      result.futureValue mustBe (())

      verify(appAssessServiceMock).deleteApplicationAssessment(eqTo(ApplicationId))
      verify(auditServiceMock).logEventNoRequest("ApplicationWithdrawn", auditDetails)
    }
  }

  "withdraw an application" should {
    "work when there is a not found exception deleting application assessment and log audit event" in new ApplicationServiceFixture {
      when(appAssessServiceMock.removeFromApplicationAssessmentSlot(eqTo(ApplicationId))).thenReturn(
        Future.failed(new NotFoundException(s"No application assessments were found with applicationId $ApplicationId"))
      )

      val result = applicationService.withdraw(ApplicationId, withdrawApplicationRequest)
      result.futureValue mustBe (())

      verify(appAssessServiceMock).deleteApplicationAssessment(eqTo(ApplicationId))
      verify(auditServiceMock).logEventNoRequest("ApplicationWithdrawn", auditDetails)
    }
  }

  trait ApplicationServiceFixture {
    implicit val hc = HeaderCarrier()

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val appAssessServiceMock = mock[ApplicationAssessmentService]
    val auditServiceMock = mock[AuditService]

    when(appRepositoryMock.withdraw(eqTo(ApplicationId), eqTo(withdrawApplicationRequest))).thenReturn(Future.successful(()))
    when(appAssessServiceMock.removeFromApplicationAssessmentSlot(eqTo(ApplicationId))).thenReturn(Future.successful(()))
    when(appAssessServiceMock.deleteApplicationAssessment(eqTo(ApplicationId))).thenReturn(Future.successful(()))

    val applicationService = new ApplicationService {
      val appRepository = appRepositoryMock
      val appAssessService = appAssessServiceMock
      val auditService = auditServiceMock
    }
  }
}
