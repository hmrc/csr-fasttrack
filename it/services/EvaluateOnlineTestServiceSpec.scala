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
import connectors.PassMarkExchangeObjects.Settings
import mocks.{ OnlineIntegrationTestInMemoryRepository, PassMarkSettingsInMemoryRepository }
import model.EvaluationResults._
import model.OnlineTestCommands.CandidateEvaluationData
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationStatuses, Scheme }
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.Logger
import repositories.application.GeneralApplicationRepository
import repositories.{ FrameworkPreferenceRepository, FrameworkRepository, PassMarkSettingsRepository, TestReportRepository }
import services.onlinetesting.EvaluateOnlineTestService
import services.passmarksettings.PassMarkSettingsService
import services.testmodel.{ OnlineTestPassmarkServiceTest, SchemeEvaluationTestResult }
import testkit.IntegrationSpec

class EvaluateOnlineTestServiceSpec extends IntegrationSpec with MockitoSugar with OneAppPerSuite {

  lazy val service = new EvaluateOnlineTestService {

    val fpRepository = mock[FrameworkPreferenceRepository]
    val testReportRepository = mock[TestReportRepository]
    val onlineTestRepository = OnlineIntegrationTestInMemoryRepository
    val passMarkRulesEngine = EvaluateOnlineTestService.passMarkRulesEngine
    val pmsRepository: PassMarkSettingsRepository = PassMarkSettingsInMemoryRepository
    val passMarkSettingsService = new PassMarkSettingsService {
      override val fwRepository = mock[FrameworkRepository]
      override val pmsRepository = PassMarkSettingsInMemoryRepository
    }
    val applicationRepository = mock[GeneralApplicationRepository]
  }

  "Evaluate Online Test Service" should {
    "for each test in the path evaluate scores" in {
      implicit object DateTimeValueReader extends ValueReader[DateTime] {
        override def read(config: Config, path: String): DateTime = {
          DateTime.parse(config.getString(path))
        }
      }
      val suites = new File("it/resources/onlineTestPassmarkServiceSpec").listFiles.filterNot(_.getName.startsWith("."))
      // Test should fail in case the path is incorrect for the env and return 0 suites
      suites.nonEmpty mustBe true

      suites.foreach { suiteName =>
        val suiteFileName = suiteName.getName
        val passmarkSettingsFile = new File(suiteName.getAbsolutePath + "/passmarkSettings.conf")
        require(passmarkSettingsFile.exists(), s"File does not exist: ${passmarkSettingsFile.getAbsolutePath}")

        val passmarkSettings = ConfigFactory.parseFile(passmarkSettingsFile).as[Settings]("passmarkSettings")
        val testCases = new File(s"it/resources/onlineTestPassmarkServiceSpec/$suiteFileName/").listFiles

        // Test should fail in case the path is incorrect for the env and return 0 tests
        testCases.nonEmpty mustBe true

        testCases.filterNot(t => t.getName == "passmarkSettings.conf").foreach { testCase =>
          val testCaseFileName = testCase.getName
          val tests = ConfigFactory.parseFile(new File(testCase.getAbsolutePath))
            .as[List[OnlineTestPassmarkServiceTest]]("tests")

          tests.foreach { t =>
            val expected = t.expected
            val candidateScoreWithPassmark = CandidateEvaluationData(passmarkSettings,
              t.schemes.map(Scheme.withName), t.scores, ApplicationStatuses.stringToEnum(t.applicationStatus))

            val actual = service.onlineTestRepository.inMemoryRepo
            val appId = t.scores.applicationId

            service.evaluate(candidateScoreWithPassmark)

            val actualResult = actual(appId)

            val testMessage = s"suite=$suiteFileName, test=$testCaseFileName, applicationId=$appId"
            Logger.info(s"Processing $testMessage")

            withClue(testMessage + " location1Scheme1") {
              actualResult.evaluatedSchemes mustBe toSchemeEvaluationResult(expected.result)
            }
            withClue(testMessage + " applicationStatus") {
              actualResult.applicationStatus mustBe ApplicationStatuses.stringToEnum(expected.applicationStatus)
            }
            withClue(testMessage + " version") {
              actualResult.version mustBe passmarkSettings.version
            }
          }
        }
      }
    }
  }

  def toStr(r: Result): String = r.getClass.getSimpleName.split("\\$").head

  def toStr(r: Option[Result]): Option[String] = r.map(toStr)

  def toSchemeEvaluationResult(testResult: List[SchemeEvaluationTestResult]): List[SchemeEvaluationResult] = {
    testResult.map(t => SchemeEvaluationResult(Scheme.withName(t.scheme), Result(t.result)))
  }

}
