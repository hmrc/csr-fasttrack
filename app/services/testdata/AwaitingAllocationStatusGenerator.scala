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

import model.EvaluationResults.Amber
import model.persisted.SchemeEvaluationResult
import model.testdata.GeneratorConfig
import model.{ ApplicationStatuses, Scheme }
import repositories._
import repositories.application.OnlineTestRepository

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object AwaitingAllocationStatusGenerator extends AwaitingAllocationStatusGenerator {
  override val previousStatusGenerator = OnlineTestCompletedWithPDFReportStatusGenerator
  override val otRepository = onlineTestRepository
}

trait AwaitingAllocationStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    val evaluationResult = List(SchemeEvaluationResult(Scheme.Business, Amber))

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.savePassMarkScore(candidateInPreviousStatus.applicationId.get, "passmarkVersion1",
        evaluationResult, Some(ApplicationStatuses.AwaitingAllocation))
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = ApplicationStatuses.AwaitingAllocation)
    }
  }
}
