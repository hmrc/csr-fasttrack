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

import com.typesafe.config.{ Config, ConfigFactory }
import config.AssessmentEvaluationMinimumCompetencyLevel
import connectors.CSREmailClient
import model.ApplicationStatuses._
import model.AssessmentEvaluationCommands.{ AssessmentPassmarkPreferencesAndScores, OnlineTestEvaluationAndAssessmentCentreScores }
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.Commands.Implicits._
import model.EvaluationResults._
import model.PersistedObjects.{ OnlineTestPassmarkEvaluation, PreferencesWithQualification }
import model.persisted.SchemeEvaluationResult
import model.{ Scheme, ApplicationStatuses }
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
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
import services.testmodel.SchemeEvaluationTestResult
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

  implicit object SchemeReader extends ValueReader[Scheme.Scheme] {
    override def read(config: Config, path: String): Scheme.Scheme = {
      Scheme.withName(config.getString(path))
    }
  }

  "Assessment Centre Passmark Service" should {
    "for each test in the path evaluate scores" in  {
      loadSuites foreach executeSuite
    }
  }

  def loadSuites = {
    val suites = new File(TestPath).listFiles filterNot (_.getName.startsWith(".")) sortBy(_.getName)
    require(suites.nonEmpty)
    log(s"**** suites = $suites")
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
//    val config = loadConfig
    // TODO IS: revert temp change
    testCases.filter( file => {
      log(s"file = $file")
//      file.getName.endsWith("RUN.conf")
      true
    })
      .foreach (executeTestCase(_, loadConfig, passmarkSettings))
  }

  def executeTestCase(testCase: File, config: AssessmentEvaluationMinimumCompetencyLevel,
                      passmark: AssessmentCentrePassMarkSettingsResponse) = {
    log(s"File with tests: ${testCase.getAbsolutePath}")

    if (DebugTestOnlyPathPattern.isEmpty || testCase.getAbsolutePath.contains(DebugTestOnlyPathPattern.get)) {
      val tests = loadTests(testCase)
      tests foreach { t =>
        logTestData(t)
        val appId = t.scores.applicationId
        log(s"Loading test: $appId")
        if (DebugTestNameAppId.isEmpty || appId == DebugTestNameAppId.get) {
          createApplicationInDb(appId)
//          val testOnlineTestEvaluation = t.onlineTestPassmarkEvaluation
          val candidateScores = AssessmentPassmarkPreferencesAndScores(passmark, t.candidate, t.scores)
          val schemeEvaluationResults = toSchemeEvaluationResult(t.onlineTestPassmarkEvaluation)
          val onlineTestEvaluationWithAssessmentCentreScores = OnlineTestEvaluationAndAssessmentCentreScores(
            schemeEvaluationResults,
//            testOnlineTestEvaluation.toOnlineTestPassmarkEvaluation,
            candidateScores
          )

          service.evaluateAssessmentCandidate(onlineTestEvaluationWithAssessmentCentreScores, config).futureValue

          val actualResult = findApplicationFromDb(appId)
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
      DebugTestNameAppId mustBe empty
      DebugTestOnlyPathPattern mustBe empty
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
    val actualSchemes = a.schemesEvaluation.getOrElse(List()).map(x => (x.schemeName, x.result)).toMap
    val expectedSchemes = expected.allSchemesEvaluationExpectations.getOrElse(List()).map(x => (x.schemeName, x.result)).toMap

    val allSchemes = actualSchemes.keys ++ expectedSchemes.keys

    allSchemes.foreach { s =>
      withClue(s"$Message schemesEvaluation for scheme: " + s) {
        actualSchemes(s) mustBe expectedSchemes(s)
      }
    }

    val actualOverallSchemes = a.overallEvaluation.getOrElse(List()).map(x => (x.schemeName, x.result)).toMap
    val expectedOverallSchemes = expected.overallSchemesEvaluationExpectations.getOrElse(List()).map(x => (x.schemeName, x.result)).toMap

    val allOverallSchemes = actualSchemes.keys ++ expectedSchemes.keys

    log(s"**** actualOverallSchemes=$actualOverallSchemes")
    log(s"**** expectedOverallSchemes=$expectedOverallSchemes")

    allOverallSchemes.foreach { s =>
      withClue(s"$Message overall scheme evaluation for scheme: " + s) {
        actualOverallSchemes(s) mustBe expectedOverallSchemes(s)
      }
    }
  }

  def loadTests(testCase: File) = {
    val tests = ConfigFactory.parseFile(new File(testCase.getAbsolutePath)).as[List[AssessmentServiceTestTODO]]("tests")
    Logger.info(s"Found ${tests.length} tests")
    tests
  }

  def createApplicationInDb(appId: String) = Try(findApplicationFromDb(appId)) match {
    case Success(_) => // do nothing
    case Failure(_) =>
      applicationRepository.collection.insert(
        BSONDocument(
          "applicationId" -> appId,
          "userId" -> ("user" + appId),
          "applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted)
      ).futureValue
  }

  private def findApplicationFromDb(appId: String): ActualResult = {
    applicationRepository.collection.find(BSONDocument("applicationId" -> appId)).one[BSONDocument].map { docOpt =>
      require(docOpt.isDefined)
      val doc = docOpt.get
      val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
      val evaluationDoc = doc.getAs[BSONDocument]("assessment-centre-passmark-evaluation").get
      val passedMinimumCompetencyLevel = evaluationDoc.getAs[Boolean]("passedMinimumCompetencyLevel")
      val passmarkVersion = evaluationDoc.getAs[String]("passmarkVersion")
      val competencyAverage = evaluationDoc.getAs[CompetencyAverageResult]("competency-average")
//      val schemesEvaluationDocsOpt = evaluationDoc.getAs[List[BSONDocument]]("schemes-evaluation")

      val schemesEvaluation = evaluationDoc.getAs[BSONDocument]("schemes-evaluation").map { doc =>
        doc.elements.collect {
          case (name, BSONString(result)) => PerSchemeEvaluation(name, Result(result))
        }.toList
      }.getOrElse(List())

      val schemesEvaluationOpt = if (schemesEvaluation.isEmpty) None else Some(schemesEvaluation)

      val overallEvaluation = evaluationDoc.getAs[BSONDocument]("overall-evaluation").map { doc =>
        doc.elements.collect {
          case (name, BSONString(result)) => PerSchemeEvaluation(name, Result(result))
        }.toList
      }.getOrElse(List())

      val overallEvaluationOpt = if (overallEvaluation.isEmpty) None else Some(overallEvaluation)

      ActualResult(passedMinimumCompetencyLevel, passmarkVersion, applicationStatus,
        competencyAverage, schemesEvaluationOpt, overallEvaluationOpt
      )
    }.futureValue
  }

  //scalastyle:off
  def log(s: String) = println(s)

  def logTestData(data: AssessmentServiceTestTODO) = {
    log("**** Test data")
    log(s"candidate: PreferencesWithQualification = ${data.candidate}")
    log(s"scores: CandidateScoresAndFeedback = ${data.scores}")
    log(s"onlineTestPassmarkEvaluation: List[SchemeEvaluationTestResult] = ${data.onlineTestPassmarkEvaluation}")
    log(s"expected: AssessmentScoreEvaluationTestExpectation = ${data.expected}")
  }
  //scalastyle:on
}

object ApplicationAssessmentServiceSpec {

  case class AssessmentServiceTestTODO(candidate: PreferencesWithQualification, scores: CandidateScoresAndFeedback,
                                   onlineTestPassmarkEvaluation: List[SchemeEvaluationTestResult],
                                   expected: AssessmentScoreEvaluationTestExpectation)
/*
  case class AssessmentServiceTest(candidate: PreferencesWithQualification, scores: CandidateScoresAndFeedback,
                                   onlineTestPassmarkEvaluation: TestOnlineTestPassmarkEvaluation,
                                   expected: AssessmentScoreEvaluationTestExpectation)
*/
  case class ActualResult(passedMinimumCompetencyLevel: Option[Boolean],
                          passmarkVersion: Option[String],
                          applicationStatus: ApplicationStatuses.EnumVal,
                          competencyAverageResult: Option[CompetencyAverageResult],
                          schemesEvaluation: Option[List[PerSchemeEvaluation]],
                          overallEvaluation: Option[List[PerSchemeEvaluation]]
                         )

  val TestPath = "it/resources/applicationAssessmentServiceSpec"
  val PassmarkSettingsFile = "passmarkSettings.conf"
  val MCLSettingsFile = "mcl.conf"
  val ConfigFiles = List(PassmarkSettingsFile, MCLSettingsFile)

  // Convert from a SchemeEvaluationTestResult to a SchemeEvaluationResult
  def toSchemeEvaluationResult(testResult: List[SchemeEvaluationTestResult]): List[SchemeEvaluationResult] = {
    testResult.map(t => SchemeEvaluationResult(Scheme.withName(t.scheme), Result(t.result)))
  }
}
