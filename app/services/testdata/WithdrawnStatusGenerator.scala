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
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object WithdrawnStatusGenerator extends WithdrawnStatusGenerator {
  override val appRepository: GeneralApplicationMongoRepository = applicationRepository
}

trait WithdrawnStatusGenerator extends BaseGenerator {
  val appRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {

    for {
      candidateInPreviousStatus <- StatusGeneratorFactory.getGenerator(generatorConfig.previousStatus.getOrElse(
        ApplicationStatuses.Submitted.name
      )).generate(generationId, generatorConfig)
      _ <- appRepository.withdraw(candidateInPreviousStatus.applicationId.get, model.Commands.WithdrawApplicationRequest("test", Some("test"),
        "test"))
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = ApplicationStatuses.Withdrawn)
    }
  }
}
