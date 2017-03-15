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

package services.onlinetesting

import model.OnlineTestCommands._
import repositories.TestReportRepository
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.evaluation._
import services.onlinetesting.evaluation.ApplicationStatusCalculator
import services.passmarksettings.OnlineTestPassMarkSettingsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EvaluateOnlineTestService extends EvaluateOnlineTestService {
  val testReportRepository = repositories.testReportRepository
  val onlineTestRepository = repositories.onlineTestRepository
  val applicationRepository = repositories.applicationRepository
  val passMarkRulesEngine = OnlineTestPassmarkRulesEngine
  val passMarkSettingsService = OnlineTestPassMarkSettingsService
}

trait EvaluateOnlineTestService extends ApplicationStatusCalculator {
  val testReportRepository: TestReportRepository
  val onlineTestRepository: OnlineTestRepository
  val applicationRepository: GeneralApplicationRepository
  val passMarkRulesEngine: OnlineTestPassmarkRulesEngine
  val passMarkSettingsService: OnlineTestPassMarkSettingsService

  def nextCandidateReadyForEvaluation: Future[Option[CandidateEvaluationData]] = {
    passMarkSettingsService.tryGetLatestVersion().flatMap {
      case None => Future.successful(None)
      case Some(passmark) =>
        onlineTestRepository.nextApplicationPassMarkProcessing(passmark.version).flatMap {
          case None => Future.successful(None)
          case Some(candidate) =>
            val schemesFut = applicationRepository.getSchemes(candidate.applicationId)
            val reportFut = testReportRepository.getReportByApplicationId(candidate.applicationId)
            for {
              schemes <- schemesFut
              reportOpt <- reportFut
            } yield {
              reportOpt.map(report => CandidateEvaluationData(passmark, schemes, report, candidate.applicationStatus))
            }
        }
    }
  }

  def evaluate(score: CandidateEvaluationData): Future[Unit] = {
    val evaluatedSchemes = passMarkRulesEngine.evaluate(score)
    val applicationStatus = determineNewStatus(evaluatedSchemes.map(_.result), score.applicationStatus)

    onlineTestRepository.savePassMarkScore(
      score.scores.applicationId,
      score.passmarkSettings.version,
      evaluatedSchemes,
      applicationStatus
    )
  }
}
