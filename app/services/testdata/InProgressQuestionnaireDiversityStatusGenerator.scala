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

import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model.ProgressStatuses
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.faker.DataFaker._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object InProgressQuestionnaireDiversityStatusGenerator extends InProgressQuestionnaireDiversityStatusGenerator {
  override val previousStatusGenerator = InProgressQuestionnaireStartStatusGenerator
  override val appRepository = applicationRepository
  override val qRepository = questionnaireRepository
}

trait InProgressQuestionnaireDiversityStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    def getDiversityQuestionnaireQuestions = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some(Random.gender), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some(Random.sexualOrientation), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some(Random.ethnicGroup), None, None))
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, getDiversityQuestionnaireQuestions)
      _ <- appRepository.updateQuestionnaireStatus(
        candidateInPreviousStatus.applicationId.get,
        ProgressStatuses.DiversityQuestionsCompletedProgress
      )
    } yield {
      candidateInPreviousStatus
    }
  }
}
