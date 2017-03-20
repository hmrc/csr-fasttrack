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

import config.AssessmentEvaluationMinimumCompetencyLevel
import connectors.EmailClient
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ExerciseScoresAndFeedback, ScoresAndFeedback }
import model.Commands._
import model.EvaluationResults._
import model.Exceptions.{ IncorrectStatusInApplicationException, NotFoundException }
import model.PersistedObjects.{ ApplicationForNotification, ContactDetails }
import model.Scheme._
import model.{ AssessmentPassmarkEvaluation, _ }
import model.persisted._
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository, PersonalDetailsRepository }
import repositories.{ ApplicationAssessmentScoresRepository, _ }
import services.AuditService
import services.evaluation.AssessmentCentrePassmarkRulesEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class AssessmentCentreServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

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

      applicationAssessmentService.saveScoresAndFeedback(ApplicationId, exerciseScoresAndFeedback).futureValue


      verify(auditServiceMock).logEvent("ApplicationScoresAndFeedbackSaved", AuditDetails)
      verify(auditServiceMock).logEvent(s"ApplicationStatusSetTo${ApplicationStatuses.AssessmentScoresEntered}", AuditDetails)
    }

    "save feedback and log an audit event for a 'failed to attend' candidate" in new ApplicationAssessmentServiceFixture {
      when(aasRepositoryMock.save(any[ExerciseScoresAndFeedback], any[Option[String]])).thenReturn(Future.successful(()))
      when(aRepositoryMock.updateStatus(any[String], any[ApplicationStatuses.EnumVal])).thenReturn(Future.successful(()))

      val result = applicationAssessmentService.saveScoresAndFeedback(ApplicationId,
        exerciseScoresAndFeedback.copy(scoresAndFeedback = exerciseScoresAndFeedback.scoresAndFeedback.copy(attended = false))
      ).futureValue

      verify(auditServiceMock).logEvent("ApplicationScoresAndFeedbackSaved", AuditDetails)
      verify(auditServiceMock).logEvent(s"ApplicationStatusSetTo${ApplicationStatuses.AssessmentScoresEntered}", AuditDetails)
    }
  }

  "delete an Application Assessment" must {
    "return a deletion success response when an application id exists" in new ApplicationAssessmentServiceFixture {
      applicationAssessmentService.removeFromAssessmentCentreSlot(ApplicationId).futureValue
      verify(auditServiceMock).logEventNoRequest("AssessmentCentreAllocationStatusReset", AuditDetails)
      verify(auditServiceMock).logEventNoRequest("AssessmentCentreAllocationDeleted", AuditDetails)
      verify(onlineTestRepositoryMock).removeCandidateAllocationStatus(eqTo(ApplicationId))

    }
    "return a not found response when an application id does not exist" in new ApplicationAssessmentServiceFixture {
      val result = applicationAssessmentService.removeFromAssessmentCentreSlot(NotFoundApplicationId)
      result.failed.futureValue mustBe a[NotFoundException]
    }
  }

  "next Assessment Candidate" must {
    "return an assessment candidate score with application Id" in new ApplicationAssessmentServiceFixture {
      val onlineTestEvaluation = OnlineTestPassmarkEvaluation("passmark", List(SchemeEvaluationResult(Business, Green)))
      when(onlineTestRepositoryMock.findPassmarkEvaluation("app1")).thenReturn(Future.successful(onlineTestEvaluation))
      val result = applicationAssessmentService.nextAssessmentCandidateReadyForEvaluation.futureValue

      result must not be empty
      result.get.assessmentScores.scores.applicationId must be("app1")
      result.get.assessmentScores.schemes mustBe schemes
      result.get.onlineTestEvaluation mustBe onlineTestEvaluation
    }

    "return none if there is no passmark settings set" in new ApplicationAssessmentServiceFixture {
      when(acpmsServiceMock.getLatestVersion).thenReturn(Future.successful(None))

      val result = applicationAssessmentService.nextAssessmentCandidateReadyForEvaluation.futureValue

      result mustBe empty
    }

    "return none if there is no application ready for assessment score evaluation" in new ApplicationAssessmentServiceFixture {
      when(aRepositoryMock.nextApplicationReadyForAssessmentScoreEvaluation(any[String])).thenReturn(Future.successful(None))

      val result = applicationAssessmentService.nextAssessmentCandidateReadyForEvaluation.futureValue

      result mustBe empty
    }
  }

  "evaluate assessment scores" must {
    "save failed evaluation result and emit audit event" in new ApplicationAssessmentServiceFixture {
      val scores = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, schemes,
        CandidateScoresAndFeedback("app1", interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))

      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None, None)
      val result = AssessmentRuleCategoryResult(
        passedMinimumCompetencyLevel = None,
        competencyAverageResult = competencyAverageResult,
        schemesEvaluation = Nil,
        overallEvaluation = List(SchemeEvaluationResult(Scheme.Business, Red))
      )
      val onlineTestEvaluation = OnlineTestPassmarkEvaluation("passmark", List(SchemeEvaluationResult(Business, Green)))
      when(passmarkRulesEngineMock.evaluate(onlineTestEvaluation, scores, config)).thenReturn(result)
      when(aRepositoryMock.saveAssessmentScoreEvaluation(AssessmentPassmarkEvaluation(
        "app1", "1", "passmark", result, ApplicationStatuses.AssessmentCentreFailed)))
        .thenReturn(Future.successful(()))

      applicationAssessmentService.evaluateAssessmentCandidate(
        OnlineTestEvaluationAndAssessmentCentreScores(onlineTestEvaluation, scores), config
      ).futureValue

      verify(aRepositoryMock).saveAssessmentScoreEvaluation(
        AssessmentPassmarkEvaluation("app1", "1", "passmark", result, ApplicationStatuses.AssessmentCentreFailed))
      verify(auditServiceMock).logEventNoRequest(
        "ApplicationAssessmentEvaluated",
        Map("applicationId" -> "app1", "applicationStatus" -> ApplicationStatuses.AssessmentCentreFailed)
      )
    }

    "save passed evaluation result and emit and audit event" in new ApplicationAssessmentServiceFixture {
      val scores = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, schemes,
        CandidateScoresAndFeedback("app1", interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None, None)
      val result = AssessmentRuleCategoryResult(
        passedMinimumCompetencyLevel = None,
        competencyAverageResult = competencyAverageResult,
        schemesEvaluation = List(SchemeEvaluationResult(Scheme.Business, Green)),
        overallEvaluation = List(SchemeEvaluationResult(Scheme.Business, Green))
      )
      val onlineTestEvaluation = OnlineTestPassmarkEvaluation("passmark", List(SchemeEvaluationResult(Business, Green)))
      when(passmarkRulesEngineMock.evaluate(onlineTestEvaluation, scores, config)).thenReturn(result)
      when(aRepositoryMock.saveAssessmentScoreEvaluation(
        AssessmentPassmarkEvaluation("app1", "1", "passmark", result, ApplicationStatuses.AssessmentCentrePassed)))
        .thenReturn(Future.successful(()))

      applicationAssessmentService.evaluateAssessmentCandidate(
        OnlineTestEvaluationAndAssessmentCentreScores(onlineTestEvaluation, scores), config
      ).futureValue

      verify(aRepositoryMock).saveAssessmentScoreEvaluation(
        AssessmentPassmarkEvaluation("app1", "1", "passmark", result, ApplicationStatuses.AssessmentCentrePassed))
      verify(auditServiceMock).logEventNoRequest(
        "ApplicationAssessmentEvaluated",
        Map("applicationId" -> "app1", "applicationStatus" -> ApplicationStatuses.AssessmentCentrePassed)
      )
    }
  }

  "process assessment centre passed or failed application" must {
    "return successful if there no applications with assessment centre passed or failed" in new ApplicationAssessmentServiceFixture {
      when(aRepositoryMock.nextAssessmentCentrePassedOrFailedApplication()).thenReturn(Future.successful(None))
      applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication.futureValue must be(())
    }

    "return successful if there is one application with assessment centre passed and send notification email and update application status to " +
      "ASSESSMENT_CENTRE_PASSED_NOTIFIED and audit AssessmentCentrePassedEmailed event and audit new status " +
      "with event ApplicationAssessmentPassedNotified" in new ApplicationAssessmentServiceFixture {
        when(aRepositoryMock.nextAssessmentCentrePassedOrFailedApplication()).thenReturn(Future.successful(
          Some(ApplicationForNotification("appId1", "userId1", "preferredName1", ApplicationStatuses.AssessmentCentrePassed))
        ))
        when(emailClientMock.sendAssessmentCentrePassed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier]))
          .thenReturn(Future.successful(()))
        when(aRepositoryMock.updateStatus(eqTo("appId1"), eqTo(ApplicationStatuses.AssessmentCentrePassedNotified)))
          .thenReturn(Future.successful(()))

        applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication.futureValue must be(())

        verify(emailClientMock).sendAssessmentCentrePassed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier])
        verify(aRepositoryMock).updateStatus("appId1", ApplicationStatuses.AssessmentCentrePassedNotified)
        val auditDetails = Map("userId" -> "userId1", "email" -> "email@mailinator.com")
        verify(auditServiceMock).logEventNoRequest(eqTo("AssessmentCentrePassedEmailed"), eqTo(auditDetails))
        val auditDetailsNewStatus = Map(
          "applicationId" -> "appId1",
          "applicationStatus" -> ApplicationStatuses.AssessmentCentrePassedNotified.name
        )
        verify(auditServiceMock).logEventNoRequest("ASSESSMENT_CENTRE_PASSED_NOTIFIED", auditDetailsNewStatus)
      }

    "return successful if there is one application with assessment centre failed and send notification email and update application status to " +
      "ASSESSMENT_CENTRE_FAILED_NOTIFIED and audit AssessmentCentreFailedEmailed event and audit new status with event " +
      "ApplicationAssessmentPassedNotified" in new ApplicationAssessmentServiceFixture {
        when(aRepositoryMock.nextAssessmentCentrePassedOrFailedApplication()).thenReturn(Future.successful(
          Some(ApplicationForNotification("appId1", "userId1", "preferredName1", ApplicationStatuses.AssessmentCentreFailed))
        ))
        when(emailClientMock.sendAssessmentCentreFailed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier]))
          .thenReturn(Future.successful(()))
        when(aRepositoryMock.updateStatus(eqTo("appId1"), eqTo(ApplicationStatuses.AssessmentCentreFailedNotified)))
          .thenReturn(Future.successful(()))

        applicationAssessmentService.processNextAssessmentCentrePassedOrFailedApplication.futureValue must be(())

        verify(emailClientMock).sendAssessmentCentreFailed(eqTo("email@mailinator.com"), eqTo("preferredName1"))(any[HeaderCarrier])
        verify(aRepositoryMock).updateStatus("appId1", ApplicationStatuses.AssessmentCentreFailedNotified)
        val auditDetails = Map("userId" -> "userId1", "email" -> "email@mailinator.com")
        verify(auditServiceMock).logEventNoRequest(eqTo("AssessmentCentreFailedEmailed"), eqTo(auditDetails))
        val auditDetailsNewStatus = Map(
          "applicationId" -> "appId1",
          "applicationStatus" -> ApplicationStatuses.AssessmentCentreFailedNotified.name
        )
        verify(auditServiceMock).logEventNoRequest("ASSESSMENT_CENTRE_FAILED_NOTIFIED", auditDetailsNewStatus)
      }
  }

  "email candidate" must {
    "throw IncorrectStatusInApplicationException when we pass ONLINE_TEST_COMPLETED" in new ApplicationAssessmentServiceFixture {
      val application = ApplicationForNotification("appId1", "userId1", "preferredName1", ApplicationStatuses.OnlineTestCompleted)
      val result = applicationAssessmentService.emailCandidate(application, "email@mailinator.com")
      result.failed.futureValue mustBe a[IncorrectStatusInApplicationException]
    }
  }

  trait ApplicationAssessmentServiceFixture {

    val applicationAssessmentRepositoryMock = mock[AssessmentCentreAllocationRepository]
    val onlineTestRepositoryMock = mock[OnlineTestRepository]
    val auditServiceMock = mock[AuditService]
    val emailClientMock = mock[EmailClient]
    val aRepositoryMock = mock[GeneralApplicationRepository]
    val acpmsServiceMock = mock[AssessmentCentrePassMarkSettingsService]
    val aasRepositoryMock = mock[ApplicationAssessmentScoresRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val passmarkRulesEngineMock = mock[AssessmentCentrePassmarkRulesEngine]
    val personalDetailsRepoMock = mock[PersonalDetailsRepository]

    val contactDetails = ContactDetails(outsideUk = false, Address("address1"), Some("postCode1"), None, "email@mailinator.com",
      Some("11111111"))
    when(cdRepositoryMock.find("userId1")).thenReturn(Future.successful(contactDetails))

    val Threshold = PassMarkSchemeThreshold(10.0, 20.0)
    val PassmarkSettings = AssessmentCentrePassMarkSettings(List(
      AssessmentCentrePassMarkScheme(Scheme.Business, Some(Threshold)),
      AssessmentCentrePassMarkScheme(Scheme.Commercial, Some(Threshold)),
      AssessmentCentrePassMarkScheme(Scheme.DigitalAndTechnology, Some(Threshold)),
      AssessmentCentrePassMarkScheme(Scheme.Finance, Some(Threshold)),
      AssessmentCentrePassMarkScheme(Scheme.ProjectDelivery, Some(Threshold))
    ), AssessmentCentrePassMarkInfo("1", DateTime.now, "user"))
    val PassmarkSchemesNotSet = PassmarkSettings.schemes.map(p => AssessmentCentrePassMarkScheme(p.scheme))

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
    val competencyAverageResult = CompetencyAverageResult(
      leadingAndCommunicatingAverage = 2.0d,
      collaboratingAndPartneringAverage = 2.0d,
      deliveringAtPaceAverage = 2.0d,
      makingEffectiveDecisionsAverage = 2.0d,
      changingAndImprovingAverage = 2.0d,
      buildingCapabilityForAllAverage = 2.0d,
      motivationFitAverage = 4.0d,
      overallScore = 16.0d
    )
    val schemes = List(Business)
    when(acpmsServiceMock.getLatestVersion).thenReturn(Future.successful(Some(PassmarkSettings)))
    when(aRepositoryMock.nextApplicationReadyForAssessmentScoreEvaluation(any[String])).thenReturn(Future.successful(Some("app1")))
    when(aRepositoryMock.getSchemes(any[String])).thenReturn(Future.successful(schemes))
    when(aasRepositoryMock.tryFind("app1")).thenReturn(Future.successful(Some(CandidateScoresAndFeedback("app1",
      interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))))

    when(applicationAssessmentRepositoryMock.delete(eqTo(ApplicationId))).thenReturn(Future.successful(()))
    when(applicationAssessmentRepositoryMock.delete(eqTo(NotFoundApplicationId))).thenReturn(
      Future.failed(new NotFoundException("No application assessments were found"))
    )

    when(onlineTestRepositoryMock.removeCandidateAllocationStatus(eqTo(ApplicationId))).thenReturn(Future.successful(()))

    val applicationAssessmentService = new AssessmentCentreService {
      val assessmentCentreAllocationRepo: AssessmentCentreAllocationRepository = applicationAssessmentRepositoryMock
      val otRepository = onlineTestRepositoryMock
      val auditService = auditServiceMock
      val emailClient = emailClientMock
      val passmarkService: AssessmentCentrePassMarkSettingsService = acpmsServiceMock
      val aRepository = aRepositoryMock
      val aasRepository: ApplicationAssessmentScoresRepository = aasRepositoryMock
      val cdRepository = cdRepositoryMock
      val passmarkRulesEngine: AssessmentCentrePassmarkRulesEngine = passmarkRulesEngineMock
      val personalDetailsRepo: PersonalDetailsRepository = personalDetailsRepoMock
    }
  }
}
