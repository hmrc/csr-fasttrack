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

package services.applicationassessment

import model.AssessmentExercise.AssessmentExercise
import model.CandidateScoresCommands.{ ExerciseScoresAndFeedback, ScoresAndFeedback }
import model.{  AssessmentExercise, EmptyRequestHeader }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.time.{ Seconds, Span }
import repositories.ApplicationAssessmentScoresRepository
import services.AuditService
import testkit.UnitSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class AssessmentCentreScoresRemovalServiceSpec extends UnitSpec {

  val ApplicationId = "1111-1111"
  val NotFoundApplicationId = "Not-Found-Id"

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))
  implicit val hc = HeaderCarrier()
  implicit val rh = EmptyRequestHeader

  val AuditDetails = Map(
    "applicationId" -> ApplicationId
  )

  "Remove scores and feedback" must {
    "Remove an exercise and log an audit event to say it happened" in new TestFixture {
      when(assessorAasRepositoryMock.removeExercise(any[String], any[AssessmentExercise])).thenReturn(Future.successful(unit))
      when(reviewerAasRepositoryMock.removeExercise(any[String], any[AssessmentExercise])).thenReturn(Future.successful(unit))

      val result: Unit = service.removeScoresAndFeedback(ApplicationId, AssessmentExercise.interview).futureValue

      verify(auditServiceMock).logEvent("ApplicationExerciseScoresAndFeedbackRemoved", AuditDetails + ("exercise" -> "interview"))
    }
  }

  trait TestFixture {
    val auditServiceMock: AuditService = mock[AuditService]
    val assessorAasRepositoryMock: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
    val reviewerAasRepositoryMock: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]

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
      )
    )

    val service = new AssessmentCentreScoresRemovalService {
      val assessorAssessmentScoresRepo: ApplicationAssessmentScoresRepository = assessorAasRepositoryMock
      val reviewerAssessmentScoresRepo: ApplicationAssessmentScoresRepository = reviewerAasRepositoryMock
      val auditService: AuditService = auditServiceMock
    }
  }
}
