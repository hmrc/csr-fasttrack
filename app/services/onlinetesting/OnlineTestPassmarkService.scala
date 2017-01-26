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

import model.ApplicationStatuses
import model.EvaluationResults.{ RuleCategoryResult, _ }
import model.OnlineTestCommands._
import model.PersistedObjects.ApplicationIdWithUserIdAndStatus
import model.Scheme.Scheme
import play.api.Logger
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.evaluation._
import services.passmarksettings.PassMarkSettingsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO LT: Next to work on
object OnlineTestPassmarkService extends OnlineTestPassmarkService {
  val pmsRepository = passMarkSettingsRepository
  val fpRepository = frameworkPreferenceRepository
  val trRepository = testReportRepository
  val oRepository = onlineTestRepository
  val passmarkRulesEngine = OnlineTestPassmarkRulesEngine
  val passMarkSettingsService = PassMarkSettingsService
  val appRepository = applicationRepository
}

trait OnlineTestPassmarkService {
  val fpRepository: FrameworkPreferenceRepository
  val trRepository: TestReportRepository
  val oRepository: OnlineTestRepository
  val passmarkRulesEngine: OnlineTestPassmarkRulesEngine
  val pmsRepository: PassMarkSettingsRepository
  val passMarkSettingsService: PassMarkSettingsService
  val appRepository: GeneralApplicationRepository

  def nextCandidateScoreReadyForEvaluation: Future[Option[CandidateScoresWithPreferencesAndPassmarkSettings]] = {
    passMarkSettingsService.tryGetLatestVersion().flatMap {
      case Some(settings) =>
        oRepository.nextApplicationPassMarkProcessing(settings.version).flatMap {
          case Some(ApplicationIdWithUserIdAndStatus(appId, _, appStatus)) =>
            for {
              schemes <- appRepository.getSchemes(appId)
              reportOpt <- trRepository.getReportByApplicationId(appId)
            } yield reportOpt.map {
              report => CandidateScoresWithPreferencesAndPassmarkSettings(settings, schemes, report, appStatus)
            }
          case _ =>
            Future.successful(None)
        }
      case _ =>
        Logger.error("No settings exist in PassMarkSettings")
        Future.successful(None)
    }
  }

  private def determineApplicationStatus(setting: String, result: Map[Scheme, Result]) = {
    ApplicationStatuses.AwaitingAllocation
  }

  def provideResult(rules: Seq[Result], ruleCategoryResult: RuleCategoryResult) = {
    val AC = ApplicationStatuses.AwaitingAllocation
    val ARE = ApplicationStatuses.AwaitingOnlineTestReevaluation

    if (rules.contains(Green)) {
      AC
    } else {
      ARE
    }
  }

  def evaluateCandidateScore(score: CandidateScoresWithPreferencesAndPassmarkSettings): Future[Unit] = {
    val result = passmarkRulesEngine.evaluate(score)
    val applicationStatus = determineApplicationStatus(score.passmarkSettings.setting, result)

    oRepository.savePassMarkScore(
      score.scores.applicationId,
      score.passmarkSettings.version,
      result,
      applicationStatus
    )
  }
}
