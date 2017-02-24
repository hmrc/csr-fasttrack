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

package services.testdata

import model.OnlineTestCommands.TestResult
import model.PersistedObjects._
import model.testdata.{ GeneratorConfig, OnlineTestScores }
import repositories._
import repositories.application.OnlineTestRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object OnlineTestCompletedWithXMLReportStatusGenerator extends OnlineTestCompletedWithXMLReportStatusGenerator {
  override val previousStatusGenerator = OnlineTestCompletedStatusGenerator
  override val otRepository = onlineTestRepository
  override val trRepository = testReportRepository
}

trait OnlineTestCompletedWithXMLReportStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val trRepository: TestReportRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- trRepository.saveOnlineTestReport(generateCandidateTestReport(
        candidateInPreviousStatus.applicationId.get,
        generatorConfig.setGis, generatorConfig.testScores
      ))
      _ <- otRepository.updateXMLReportSaved(candidateInPreviousStatus.applicationId.get)
    } yield {
      candidateInPreviousStatus
    }
  }

  private def generateCandidateTestReport(applicationId: String, setGis: Boolean, testScores: Option[OnlineTestScores]) = {
    def scoreOrDefault(score: Option[Double]) = score.fold(Some(25.0)) { s => Some(s) }

    val verbalTestResult = TestResult("Completed", "Demonstration norm (for software testing purposes only)",
      scoreOrDefault(testScores.flatMap(s => s.verbalTScore)), Some(1.0), Some(7.0), None)
    val numericalTestResult = TestResult("Completed", "Demonstration norm (for software testing purposes only)",
      scoreOrDefault(testScores.flatMap(s => s.numericalTScore)), Some(2.0), Some(3.0), None)
    val competencyTestResult = TestResult("Completed", "CTQ: DEMONSTRATION NORM 1", scoreOrDefault(testScores.flatMap(s => s.competencyTScore)),
      None, None, None)
    val situationalTestResult = TestResult("Completed", "Fast Track 1.0",
      scoreOrDefault(testScores.flatMap(s => s.situationalTScore)), Some(11.0), Some(39.0), Some(3.0))

    if (setGis) {
      CandidateTestReport(applicationId, "XML", Some(competencyTestResult), None, None, Some(situationalTestResult))
    } else {
      CandidateTestReport(applicationId, "XML", Some(competencyTestResult),
        Some(numericalTestResult), Some(verbalTestResult), Some(situationalTestResult))
    }
  }
}
