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

package repositories

import model.{ ApplicationStatuses, AssessmentPassmarkEvaluation }
import model.ApplicationStatuses._
import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocationResult
import model.EvaluationResults.{ AssessmentRuleCategoryResult, CompetencyAverageResult }
import model.Exceptions.ApplicationNotFound
import model.ReportExchangeObjects._
import reactivemongo.bson.BSONDocument
//import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.application.{ GeneralApplicationMongoRepository, TestDataMongoRepository }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

import scala.concurrent.Await

class ApplicationRepositorySpec extends MongoRepositorySpec {

  import ImplicitBSONHandlers._

  val frameworkId = "FastTrack-2015"

  val collectionName = CollectionNames.APPLICATION

  def applicationRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)

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

  "Application repository" must {
    "create indexes for the repository" in {
      val repo = repositories.applicationRepository

      val indexes = indexesWithFields(repo)
      indexes must contain(List("_id"))
      indexes must contain(List("applicationId", "userId"))
      indexes must contain(List("userId", "frameworkId"))
      indexes must contain(List("online-tests.token"))
      indexes must contain(List("applicationStatus"))
      indexes must contain(List("online-tests.invitationDate"))
      indexes.size mustBe 6
    }
  }

  "Finding an application by User Id" must {

    "throw a NotFound exception when application doesn't exists" in {
      applicationRepo.findByUserId("invalidUser", "invalidFramework")
      an[ApplicationNotFound] must be thrownBy Await.result(applicationRepo.findByUserId("invalidUser", "invalidFramework"), timeout)
    }

    "throw an exception not of the type ApplicationNotFound when application is corrupt" in {
      Await.ready(applicationRepo.collection.insert(BSONDocument(
        "userId" -> "validUser",
        "frameworkId" -> "validFrameworkField"
        // but application Id framework, which is mandatory, so will fail to deserialise
      )), timeout)

      val thrown = the[Exception] thrownBy Await.result(applicationRepo.findByUserId("validUser", "validFrameworkField"), timeout)
      thrown must not be an[ApplicationNotFound]
    }
  }

  "Finding applications by user id" must {
    "return an empty list when no records for an applicationid exist" in {
      applicationRepo.find(List("appid-1")).futureValue.size mustBe 0
    }

    "return a list of Candidates when records for an applicationid exist" in {
      val appResponse = applicationRepo.create("userId1", "framework").futureValue

      val result = applicationRepo.find(List(appResponse.applicationId)).futureValue

      result.size mustBe 1
      result.head.applicationId.get mustBe appResponse.applicationId
      result.head.userId mustBe "userId1"
    }
  }

  "find applications for assessment allocation" must {
    "return an empty list when there are no applications" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(0).futureValue

      val result = applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 0, 5).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
    "return an empty list when there are no applications awaiting for allocation" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200).futureValue

      val result = applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 0, 5).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
    "return a one item list when there are applications awaiting for allocation and start item and end item is the same" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, onlyAwaitingAllocation = true)

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 2, 2).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result must have size 1
    }
    "return a non empty list when there are applications" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, onlyAwaitingAllocation = true)

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 0, 5).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result must not be empty
    }
    "return an empty list when start is beyond the number of results" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, onlyAwaitingAllocation = true).futureValue

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), Int.MaxValue, Int.MaxValue).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
    "return an empty list when start is higher than end" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, onlyAwaitingAllocation = true).futureValue

      val result = applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 2, 1).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
  }

  "next application ready for assessment score evaluation" must {
    "return the only application ready for evaluation" in {
      createApplication("app1", ApplicationStatuses.AssessmentScoresAccepted)

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("1").futureValue

      result must not be empty
      result.get mustBe "app1"
    }

    "return the next application when the passmark is different" in {
      createApplicationWithPassmark("app1", ApplicationStatuses.AwaitingAssessmentCentreReevaluation, "1")

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("2").futureValue
      result must not be empty
    }

    "return none when application has already passmark and it has not changed" in {
      createApplicationWithPassmark("app1", ApplicationStatuses.AwaitingAssessmentCentreReevaluation, "1")

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("1").futureValue
      result mustBe empty
    }

    "return none when there are no candidates in ASSESSMENT_SCORES_ACCEPTED status" in {
      createApplication("app1", ApplicationStatuses.AssessmentScoresEntered)

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("1").futureValue
      result mustBe empty
    }
  }

  "save assessment score evaluation" must {
    "save a score evaluation and update the application status when the application is in ASSESSMENT_SCORES_ACCEPTED status" in {
      createApplication("app1", ApplicationStatuses.AssessmentScoresAccepted)

      val result = AssessmentRuleCategoryResult(Some(true), competencyAverageResult, schemesEvaluation = Nil, overallEvaluation = Nil)
      applicationRepo.saveAssessmentScoreEvaluation(
        AssessmentPassmarkEvaluation("app1", "1", "2", result, ApplicationStatuses.AwaitingAssessmentCentreReevaluation)).futureValue

      val status = getApplicationStatus("app1")
      status mustBe ApplicationStatuses.AwaitingAssessmentCentreReevaluation
    }

    "save a score evaluation and update the application status when the application is in AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION" in {
      createApplication("app1", ApplicationStatuses.AwaitingAssessmentCentreReevaluation)

      val result = AssessmentRuleCategoryResult(Some(true), competencyAverageResult, schemesEvaluation = Nil, overallEvaluation = Nil)
      applicationRepo.saveAssessmentScoreEvaluation(
        AssessmentPassmarkEvaluation("app1", "1", "2", result, ApplicationStatuses.AssessmentScoresAccepted)).futureValue

      val status = getApplicationStatus("app1")
      status mustBe ApplicationStatuses.AssessmentScoresAccepted
    }

    "fail to save a score evaluation when candidate has been withdrawn" in {
      createApplication("app1", ApplicationStatuses.Withdrawn)

      val result = AssessmentRuleCategoryResult(Some(true), competencyAverageResult, schemesEvaluation = Nil, overallEvaluation = Nil)
      applicationRepo.saveAssessmentScoreEvaluation(AssessmentPassmarkEvaluation(
        "app1", "1", "2", result, ApplicationStatuses.AssessmentScoresAccepted)).futureValue

      val status = getApplicationStatus("app1")
      status mustBe ApplicationStatuses.Withdrawn
    }

    def getApplicationStatus(appId: String) = {
      applicationRepo.collection.find(BSONDocument("applicationId" -> "app1")).one[BSONDocument].map { docOpt =>
        docOpt must not be empty
        val doc = docOpt.get
        doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
      }.futureValue
    }
  }

  "review" must {
    "change progress status to review" in {
      createApplication("app1", ApplicationStatuses.InProgress)

      applicationRepo.review("app1").futureValue

      val status = getApplicationStatus("app1")
      status mustBe ApplicationStatuses.InProgress.name

      val progressResponse = applicationRepo.findProgress("app1").futureValue
      progressResponse.review mustBe true
    }
  }

  def getApplicationStatus(appId: String) = {
    applicationRepo.collection.find(BSONDocument("applicationId" -> "app1")).one[BSONDocument].map { docOpt =>
      docOpt must not be empty
      val doc = docOpt.get
      doc.getAs[String]("applicationStatus").get
    }.futureValue
  }

  def createApplication(appId: String, appStatus: ApplicationStatuses.EnumVal): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus
    )).futureValue
  }

  def createApplicationWithPassmark(appId: String, appStatus: String, passmarkVersion: String): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "assessment-centre-passmark-evaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion
      )
    )).futureValue
  }

  def createApplicationWithFrameworkEvaluations(appId: String,
                                                frameworkId: String,
                                                appStatus: String,
                                                passmarkVersion: String,
                                                frameworkSchemes: OnlineTestPassmarkEvaluationSchemes): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> appStatus,
      "progress-status" -> BSONDocument(
        appStatus.toLowerCase -> true
      ),
      "passmarkEvaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion,
        "location1Scheme1" -> frameworkSchemes.location1Scheme1.get,
        "location1Scheme2" -> frameworkSchemes.location1Scheme2.get,
        "location2Scheme1" -> frameworkSchemes.location2Scheme1.get,
        "location2Scheme2" -> frameworkSchemes.location2Scheme2.get,
        "alternativeScheme" -> frameworkSchemes.alternativeScheme.get
        )
      )).futureValue
  }
  def createApplicationWithSummaryScoresAndSchemeEvaluations(appId: String,
                                                             frameworkId: String,
                                                             appStatus: String,
                                                             passmarkVersion: String,
                                                             scores: CandidateScoresSummary,
                                                             scheme: SchemeEvaluation): Unit = {

    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> appStatus,
      "progress-status" -> BSONDocument(
        "assessment_centre_passed" -> true
      ),
      "assessment-centre-passmark-evaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion,
        "competency-average" -> BSONDocument(
          "leadingAndCommunicatingAverage" -> scores.avgLeadingAndCommunicating.get,
          "collaboratingAndPartneringAverage" -> scores.avgCollaboratingAndPartnering.get,
          "deliveringAtPaceAverage" -> scores.avgDeliveringAtPace.get,
          "makingEffectiveDecisionsAverage" -> scores.avgMakingEffectiveDecisions,
          "changingAndImprovingAverage" -> scores.avgChangingAndImproving,
          "buildingCapabilityForAllAverage" -> scores.avgBuildingCapabilityForAll,
          "motivationFitAverage" -> scores.avgMotivationFit,
          "overallScore" -> scores.totalScore
        ),
        "schemes-evaluation" -> BSONDocument(
          "Commercial" -> scheme.commercial.get,
          "DigitalAndTechnology" -> scheme.digitalAndTechnology.get,
          "Business" -> scheme.business.get,
          "ProjectDelivery" -> scheme.projectDelivery.get,
          "Finance" -> scheme.finance.get
        )
      )
    )).futureValue
  }
}
