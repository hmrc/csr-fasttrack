/*
 * Copyright 2018 HM Revenue & Customs
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

import model.{ ApplicationStatuses, ProgressStatuses }
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.{ AssistanceDetailsRepository, GeneralApplicationRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  val previousStatusGenerator = InProgressSchemePreferencesStatusGenerator
  override val adRepository = assistanceDetailsRepository
  override val appRepository = applicationRepository
}

trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository
  val appRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    val assistanceDetails = generatorConfig.assistanceDetails.getAssistanceDetails()

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = candidateInPreviousStatus.applicationId.get
      _ <- adRepository.update(appId, candidateInPreviousStatus.userId, assistanceDetails)
      _ <- appRepository.updateQuestionnaireStatus(
        candidateInPreviousStatus.applicationId.get,
        ProgressStatuses.AssistanceDetailsCompletedProgress
      )
    } yield {
      candidateInPreviousStatus.copy(
        applicationStatus = ApplicationStatuses.InProgress,
        assistanceDetails = Some(assistanceDetails)
      )
    }
  }
}
