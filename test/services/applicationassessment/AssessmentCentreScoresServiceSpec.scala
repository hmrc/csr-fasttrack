/*
 * Copyright 2017 HM Revenue & Customs
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

package services.applicationassessment

import model.{ ApplicationStatuses, AssessmentExercise, EmptyRequestHeader }
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ExerciseScoresAndFeedback, ScoresAndFeedback }
import model.Exceptions.NotFoundException
import org.mockito.Mockito._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec
import repositories.{ ApplicationAssessmentScoresRepository, AssessmentCentreAllocationRepository }
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import testkit.MockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class AssessmentCentreScoresServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  val ApplicationId = "1111-1111"
  val NotFoundApplicationId = "Not-Found-Id"

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))
  implicit val hc = HeaderCarrier()
  implicit val rh = EmptyRequestHeader

  val AuditDetails = Map(
    "applicationId" -> ApplicationId
  )

  "Save scores and feedback" must {
    "save feedback and log an audit event for an attended candidate" in new ApplicationAssessmentServiceFixture {
      when(aasRepositoryMock.save(any[ExerciseScoresAndFeedback], any[Option[String]])).thenReturn(Future.successful(()))
      when(aRepositoryMock.updateStatus(any[String], any[ApplicationStatuses.EnumVal])).thenReturn(Future.successful(()))

      service.saveScoresAndFeedback(ApplicationId, exerciseScoresAndFeedback).futureValue


      verify(auditServiceMock).logEvent("ApplicationScoresAndFeedbackSaved", AuditDetails)
      verify(auditServiceMock).logEvent(s"ApplicationStatusSetTo${ApplicationStatuses.AssessmentScoresEntered}", AuditDetails)
    }

    "save feedback and log an audit event for a 'failed to attend' candidate" in new ApplicationAssessmentServiceFixture {
      when(aasRepositoryMock.save(any[ExerciseScoresAndFeedback], any[Option[String]])).thenReturn(Future.successful(()))
      when(aRepositoryMock.updateStatus(any[String], any[ApplicationStatuses.EnumVal])).thenReturn(Future.successful(()))

      val result: Unit = service.saveScoresAndFeedback(ApplicationId,
        exerciseScoresAndFeedback.copy(scoresAndFeedback = exerciseScoresAndFeedback.scoresAndFeedback.copy(attended = false))
      ).futureValue

      verify(auditServiceMock).logEvent("ApplicationScoresAndFeedbackSaved", AuditDetails)
      verify(auditServiceMock).logEvent(s"ApplicationStatusSetTo${ApplicationStatuses.AssessmentScoresEntered}", AuditDetails)
    }
  }

  trait ApplicationAssessmentServiceFixture {

    val applicationAssessmentRepositoryMock: AssessmentCentreAllocationRepository = mock[AssessmentCentreAllocationRepository]
    val auditServiceMock: AuditService = mock[AuditService]
    val aRepositoryMock: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val aasRepositoryMock: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
    val personalDetailsRepoMock: PersonalDetailsRepository = mock[PersonalDetailsRepository]

    val exerciseScoresAndFeedback = ExerciseScoresAndFeedback("app1", AssessmentExercise.interview,
      ScoresAndFeedback(
        attended = true,
        assessmentIncomplete = false,
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some("xyz"),
        "xyz"
      ))
    when(aasRepositoryMock.tryFind("app1")).thenReturn(Future.successful(Some(CandidateScoresAndFeedback("app1",
      interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))))

    when(applicationAssessmentRepositoryMock.delete(eqTo(ApplicationId))).thenReturn(Future.successful(()))
    when(applicationAssessmentRepositoryMock.delete(eqTo(NotFoundApplicationId))).thenReturn(
      Future.failed(new NotFoundException("No application assessments were found"))
    )

    val service = new AssessmentCentreScoresService {
      val assessmentScoresRepo: ApplicationAssessmentScoresRepository = aasRepositoryMock
      val appRepo: GeneralApplicationRepository = aRepositoryMock
      val assessmentCentreAllocationRepo: AssessmentCentreAllocationRepository = applicationAssessmentRepositoryMock
      val personalDetailsRepo: PersonalDetailsRepository = personalDetailsRepoMock
      val auditService: AuditService = auditServiceMock
    }
  }
}
