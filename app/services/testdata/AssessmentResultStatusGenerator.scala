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

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.ApplicationStatuses
import model.EvaluationResults._
import model.persisted.SchemeEvaluationResult
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository

import scala.concurrent.duration._
import language.postfixOps
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }

object AwaitingAssessmentCentreReevalationStatusGenerator extends AssessmentResultStatusGenerator {
  override val previousStatusGenerator = AssessmentScoresAcceptedStatusGenerator
  override val aRepository = applicationRepository
  override val locationSchemeRepository = repositories.fileLocationSchemeRepository

  private def randScore = Random.randDouble(1.5, 2.5)

  private def randResult = Random.randOne(List(Amber))

  override val status = ApplicationStatuses.AwaitingAssessmentCentreReevaluation

  override def getAssessmentRuleCategoryResult =
    AssessmentRuleCategoryResult(Some(true), Some(Amber), Some(Amber), Some(Amber), Some(Amber),
      Some(Amber), Some(CompetencyAverageResult(
        leadingAndCommunicatingAverage = randScore,
        collaboratingAndPartneringAverage = randScore,
        deliveringAtPaceAverage = randScore,
        makingEffectiveDecisionsAverage = randScore,
        changingAndImprovingAverage = randScore,
        buildingCapabilityForAllAverage = randScore,
        motivationFitAverage = randScore,
        overallScore = Random.randDouble(10.5, 17.5)
      )), Some(schemes.map { schemeName =>
        SchemeEvaluationResult(schemeName, randResult)
      }))
}

object AssessmentCentreFailedStatusGenerator extends AssessmentResultStatusGenerator {
  override val previousStatusGenerator = AssessmentScoresAcceptedStatusGenerator
  override val aRepository = applicationRepository
  override val locationSchemeRepository = repositories.fileLocationSchemeRepository

  private def randScore = Random.randDouble(0.5, 1.5)

  private def randResult = Random.randOne(List(Red))

  override val status = ApplicationStatuses.AssessmentCentreFailed
  override def getAssessmentRuleCategoryResult =
    AssessmentRuleCategoryResult(Some(true), Some(Red), Some(Red), Some(Red), Some(Red),
      Some(Red), Some(CompetencyAverageResult(
        leadingAndCommunicatingAverage = randScore,
        collaboratingAndPartneringAverage = randScore,
        deliveringAtPaceAverage = randScore,
        makingEffectiveDecisionsAverage = randScore,
        changingAndImprovingAverage = randScore,
        buildingCapabilityForAllAverage = randScore,
        motivationFitAverage = randScore,
        overallScore = Random.randDouble(3.5, 10.5)
      )), Some(schemes.map { schemeName =>
        SchemeEvaluationResult(schemeName, randResult)
      }))
}

object AssessmentCentrePassedStatusGenerator extends AssessmentResultStatusGenerator {
  override val previousStatusGenerator = AssessmentScoresAcceptedStatusGenerator
  override val aRepository = applicationRepository
  override val locationSchemeRepository = repositories.fileLocationSchemeRepository

  private def randScore = Random.randDouble(3.0, 4.0)

  private def randResult = Random.randOne(List(Amber, Green))

  override val status = ApplicationStatuses.AssessmentCentrePassed

  override def getAssessmentRuleCategoryResult: AssessmentRuleCategoryResult =
    AssessmentRuleCategoryResult(Some(true), Some(Green), Some(Green), Some(Green), Some(Green),
      Some(Green), Some(CompetencyAverageResult(
        leadingAndCommunicatingAverage = randScore,
        collaboratingAndPartneringAverage = randScore,
        deliveringAtPaceAverage = randScore,
        makingEffectiveDecisionsAverage = randScore,
        changingAndImprovingAverage = randScore,
        buildingCapabilityForAllAverage = randScore,
        motivationFitAverage = randScore,
        overallScore = Random.randDouble(21.0, 28.0)
      )), Some(schemes.map { scheme =>
        SchemeEvaluationResult(scheme, randResult)
      }))
}

trait AssessmentResultStatusGenerator extends ConstructiveGenerator {
  val aRepository: GeneralApplicationRepository
  val locationSchemeRepository: LocationSchemeRepository

  val status: ApplicationStatuses.EnumVal
  def getAssessmentRuleCategoryResult: AssessmentRuleCategoryResult

  lazy val schemes = fileLocationSchemeRepository.schemeInfoList.map(_.id)

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = candidateInPreviousStatus.applicationId.get
      _ <- aRepository.saveAssessmentScoreEvaluation(appId, "version1", getAssessmentRuleCategoryResult,
        status)
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = status)
    }
  }
}
