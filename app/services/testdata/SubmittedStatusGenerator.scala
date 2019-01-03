/*
 * Copyright 2019 HM Revenue & Customs
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

import model.ApplicationStatuses
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object SubmittedStatusGenerator extends SubmittedStatusGenerator {
  override val previousStatusGenerator = InProgressReviewStatusGenerator
  override val appRepository = applicationRepository
  override val assessmentCentreIndicatorRepo = AssessmentCentreIndicatorCSVRepository
}

trait SubmittedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val assessmentCentreIndicatorRepo: AssessmentCentreIndicatorRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      postCode = for {
        cd <- candidateInPreviousStatus.contactDetails
        ps <- cd.postCode
      } yield ps
      assessmentCentreIndicator = assessmentCentreIndicatorRepo.calculateIndicator(postCode)
      _ <- appRepository.updateAssessmentCentreIndicator(candidateInPreviousStatus.applicationId.get, assessmentCentreIndicator)
      _ <- appRepository.submit(candidateInPreviousStatus.applicationId.get)
    } yield {
      candidateInPreviousStatus.copy(
        applicationStatus = ApplicationStatuses.Submitted,
        assessmentCentreIndicator = Some(assessmentCentreIndicator)
      )
    }
  }
}
