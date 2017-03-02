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

package services

import java.io.File

import com.typesafe.config.ConfigFactory
import config.AssessmentEvaluationMinimumCompetencyLevel
import connectors.CSREmailClient
import model.ApplicationStatuses._
import model.AssessmentEvaluationCommands.{ AssessmentPassmarkPreferencesAndScores, OnlineTestEvaluationAndAssessmentCentreScores }
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.Commands.Implicits._
import model.EvaluationResults._
import model.PersistedObjects.{ OnlineTestPassmarkEvaluation, PreferencesWithQualification }
import model.ApplicationStatuses
import model.persisted.SchemeEvaluationResult
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.scalatest.mock.MockitoSugar
import play.Logger
import play.api.libs.json._
import reactivemongo.bson.{ BSONDocument, BSONString }
import reactivemongo.json.ImplicitBSONHandlers
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository, PersonalDetailsRepository }
import services.applicationassessment.AssessmentCentreService
import services.evaluation.AssessmentCentrePassmarkRulesEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import testkit.MongoRepositorySpec

import scala.io.Source
import scala.util.{ Failure, Success, Try }

class ApplicationAssessmentServiceSpec extends MongoRepositorySpec with MockitoSugar {

  import ApplicationAssessmentServiceSpec._
  import ImplicitBSONHandlers._

  lazy val service = new AssessmentCentreService {
    val assessmentCentreAllocationRepo: AssessmentCentreAllocationRepository = mock[AssessmentCentreAllocationRepository]
    val aasRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
    val fpRepository: FrameworkPreferenceRepository = mock[FrameworkPreferenceRepository]
    val otRepository: OnlineTestRepository = mock[OnlineTestRepository]
    val aRepository: GeneralApplicationRepository = applicationRepository
    val cdRepository: ContactDetailsRepository = mock[ContactDetailsRepository]
    val passmarkService: AssessmentCentrePassMarkSettingsService = mock[AssessmentCentrePassMarkSettingsService]
    val passmarkRulesEngine: AssessmentCentrePassmarkRulesEngine = AssessmentCentreService.passmarkRulesEngine
    val auditService: AuditService = mock[AuditService]
    val emailClient: CSREmailClient = mock[CSREmailClient]
    val personalDetailsRepo: PersonalDetailsRepository = mock[PersonalDetailsRepository]
  }

  val collectionName = CollectionNames.APPLICATION
  // set this test framework to run only one test - useful in debugging
  val DebugTestNameAppId: Option[String] = None // Some("oneLocationSuite_Amber_App1")
  // set this test framework to load only tests which contain the phrase in their path - useful in debugging
  val DebugTestOnlyPathPattern: Option[String] = None // Some("5_2_oneLocationMclDisabledIncomplete/")

  // TODO IAN: Please fix the tests once you tackle assessment centre evaluation
  "Assessment Centre Passmark Service" should {
    "for each test in the path evaluate scores" ignore {
      loadSuites foreach executeSuite
    }
  }

  def loadSuites = {
    val suites = new File(TestPath).listFiles filterNot (_.getName.startsWith(".")) sortBy(_.getName)
    require(suites.nonEmpty)
    suites
  }

  def executeSuite(suiteName: File) = {
    def loadPassmarkSettings = {
      val passmarkSettingsFile = new File(suiteName.getAbsolutePath + "/" + PassmarkSettingsFile)
      require(passmarkSettingsFile.exists(), s"File does not exist: ${passmarkSettingsFile.getAbsolutePath}")
      val passmarkSettingsJson = Json.parse(Source.fromFile(passmarkSettingsFile).getLines().mkString)
      passmarkSettingsJson.as[AssessmentCentrePassMarkSettingsResponse]
    }

    def loadTestCases = {
      val testCases = new File(s"$TestPath/${suiteName.getName}/")
        .listFiles
        .filterNot(f => ConfigFiles.contains(f.getName))
        .sortBy(_.getName)
      require(testCases.nonEmpty)
      testCases.sortBy(_.getName)
    }

    def loadConfig = {
      val configFile = new File(suiteName.getAbsolutePath + "/" + MCLSettingsFile)
      require(configFile.exists(), s"File does not exist: ${configFile.getAbsolutePath}")
      val configJson = Json.parse(Source.fromFile(configFile).getLines().mkString)
      configJson.as[AssessmentEvaluationMinimumCompetencyLevel]
    }

    val passmarkSettings = loadPassmarkSettings
    val testCases = loadTestCases
    val config = loadConfig
    testCases foreach (executeTestCase(_, loadConfig, passmarkSettings))
  }

  def executeTestCase(testCase: File, config: AssessmentEvaluationMinimumCompetencyLevel,
                      passmark: AssessmentCentrePassMarkSettingsResponse) = {
    log(s"File with tests: ${testCase.getAbsolutePath}")

    if (DebugTestOnlyPathPattern.isEmpty || testCase.getAbsolutePath.contains(DebugTestOnlyPathPattern.get)) {
      val tests = loadTests(testCase)
      tests foreach { t =>
        val appId = t.scores.applicationId
        log(s"Loading test: $appId")
        if (DebugTestNameAppId.isEmpty || appId == DebugTestNameAppId.get) {
          createApplication(appId)
          val testOnlineTestEvaluation = t.onlineTestPassmarkEvaluation
          val candidateScores = AssessmentPassmarkPreferencesAndScores(passmark, t.candidate, t.scores)
          val onlineTestEvaluationWithAssessmentCentreScores = OnlineTestEvaluationAndAssessmentCentreScores(
            testOnlineTestEvaluation.toOnlineTestPassmarkEvaluation,
            candidateScores
          )

          service.evaluateAssessmentCandidate(onlineTestEvaluationWithAssessmentCentreScores, config).futureValue

          val actualResult = findApplication(appId)
          val expectedResult = t.expected
          assert(testCase, appId, expectedResult, actualResult)
        } else {
          log("--> Skipped test")
        }
      }
      log(s"Executed test cases: ${tests.size}")
    } else {
      log("--> Skipped file")
    }
  }

  "Debug flag" should {
    "must be disabled" in {
      DebugTestNameAppId must be (empty)
      DebugTestOnlyPathPattern must be (empty)
    }
  }

  def assert(testCase: File, appId: String, expected: AssessmentScoreEvaluationTestExpectation, a: ActualResult) = {
    val testMessage = s"file=${testCase.getAbsolutePath}\napplicationId=$appId"

    val Message = s"Test location: $testMessage\n"
    withClue(s"$Message applicationStatus") {
      a.applicationStatus mustBe expected.applicationStatus
    }
    withClue(s"$Message minimumCompetencyLevel") {
      a.passedMinimumCompetencyLevel mustBe expected.passedMinimumCompetencyLevel
    }
    withClue(s"$Message competencyAverage") {
      a.competencyAverageResult mustBe expected.competencyAverage
    }
    withClue(s"$Message competencyAverage overallScore") {
      expected.overallScore.foreach { overallScore =>
        a.competencyAverageResult.get.overallScore mustBe overallScore
      }
    }
    withClue(s"$Message passmarkVersion") {
      a.passmarkVersion mustBe expected.passmarkVersion
    }
    withClue(s"$Message location1Scheme1") {
      a.location1Scheme1 mustBe expected.location1Scheme1
    }
    withClue(s"$Message location1Scheme2") {
      a.location1Scheme2 mustBe expected.location1Scheme2
    }
    withClue(s"$Message location2Scheme1") {
      a.location2Scheme1 mustBe expected.location2Scheme1
    }
    withClue(s"$Message location2Scheme2") {
      a.location2Scheme2 mustBe expected.location2Scheme2
    }
    withClue(s"$Message alternativeScheme") {
      a.alternativeScheme mustBe expected.alternativeScheme
    }
    val actualSchemes = a.schemesEvaluation.getOrElse(List()).map(x => (x.scheme, x.result)).toMap
    val expectedSchemes = expected.allSchemesEvaluationExpectations.getOrElse(List()).map(x => (x.scheme, x.result)).toMap

    val allSchemes = actualSchemes.keys ++ expectedSchemes.keys

    allSchemes.foreach { s =>
      withClue(s"$Message schemesEvaluation for scheme: " + s) {
        actualSchemes(s) mustBe expectedSchemes(s)
      }
    }
  }

  def loadTests(testCase: File) = {
    val tests = ConfigFactory.parseFile(new File(testCase.getAbsolutePath)).as[List[AssessmentServiceTest]]("tests")
    Logger.info(s"Found ${tests.length} tests")
    tests
  }

  def createApplication(appId: String) = Try(findApplication(appId)) match {
    case Success(_) => // do nothing
    case Failure(_) =>
      applicationRepository.collection.insert(
        BSONDocument(
          "applicationId" -> appId,
          "userId" -> ("user" + appId),
          "applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted)
      ).futureValue
  }

  private def findApplication(appId: String): ActualResult = {
    applicationRepository.collection.find(BSONDocument("applicationId" -> appId)).one[BSONDocument].map { docOpt =>
      require(docOpt.isDefined)
      val doc = docOpt.get
      val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
      val evaluationDoc = doc.getAs[BSONDocument]("assessment-centre-passmark-evaluation").get
      val passedMinimumCompetencyLevel = evaluationDoc.getAs[Boolean]("passedMinimumCompetencyLevel")
      val passmarkVersion = evaluationDoc.getAs[String]("passmarkVersion")
      val location1Scheme1 = evaluationDoc.getAs[String]("location1Scheme1")
      val location1Scheme2 = evaluationDoc.getAs[String]("location1Scheme2")
      val location2Scheme1 = evaluationDoc.getAs[String]("location2Scheme1")
      val location2Scheme2 = evaluationDoc.getAs[String]("location2Scheme2")
      val alternativeScheme = evaluationDoc.getAs[String]("alternativeScheme")
      val competencyAverage = evaluationDoc.getAs[CompetencyAverageResult]("competency-average")
      val schemesEvaluationDocsOpt = evaluationDoc.getAs[List[BSONDocument]]("schemes-evaluation")

      val schemesEvaluation = evaluationDoc.getAs[BSONDocument]("schemes-evaluation").map { doc =>
        doc.elements.collect {
          case (name, BSONString(result)) => SchemeEvaluationResult(model.Scheme.withName(name), Result(result))
        }.toList
      }.getOrElse(List())
      val schemesEvaluationOpt = if (schemesEvaluation.isEmpty) None else Some(schemesEvaluation)

      ActualResult(passedMinimumCompetencyLevel, passmarkVersion, applicationStatus, location1Scheme1, location1Scheme2, location2Scheme1,
        location2Scheme2, alternativeScheme, competencyAverage, schemesEvaluationOpt)
    }.futureValue
  }

  //scalastyle:off
  def log(s: String) = println(s)
  //scalastyle:on
}

object ApplicationAssessmentServiceSpec {

  case class AssessmentServiceTest(candidate: PreferencesWithQualification, scores: CandidateScoresAndFeedback,
                                   onlineTestPassmarkEvaluation: TestOnlineTestPassmarkEvaluation,
                                   expected: AssessmentScoreEvaluationTestExpectation)

  case class ActualResult(passedMinimumCompetencyLevel: Option[Boolean], passmarkVersion: Option[String],
                          applicationStatus: ApplicationStatuses.EnumVal, location1Scheme1: Option[String],
                          location1Scheme2: Option[String], location2Scheme1: Option[String],
                          location2Scheme2: Option[String], alternativeScheme: Option[String],
                          competencyAverageResult: Option[CompetencyAverageResult], schemesEvaluation: Option[List[SchemeEvaluationResult]])

  case class TestOnlineTestPassmarkEvaluation(location1Scheme1: String,
                                              location1Scheme2: Option[String] = None, location2Scheme1: Option[String] = None,
                                              location2Scheme2: Option[String] = None, alternativeScheme: Option[String] = None) {
    def toOnlineTestPassmarkEvaluation: OnlineTestPassmarkEvaluation = OnlineTestPassmarkEvaluation(
      Result(location1Scheme1),
      location1Scheme2.map(Result(_)),
      location2Scheme1.map(Result(_)),
      location2Scheme2.map(Result(_)),
      alternativeScheme.map(Result(_))
    )
  }

  val TestPath = "it/resources/applicationAssessmentServiceSpec"
  val PassmarkSettingsFile = "passmarkSettings.conf"
  val MCLSettingsFile = "mcl.conf"
  val ConfigFiles = List(PassmarkSettingsFile, MCLSettingsFile)
}
