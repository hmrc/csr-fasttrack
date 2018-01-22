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

object InProgressQuestionnaireParentalOccupationStatusGenerator extends InProgressQuestionnaireParentalOccupationStatusGenerator {
  override val previousStatusGenerator = InProgressQuestionnaireEducationStatusGenerator
  override val appRepository = applicationRepository
  override val qRepository = questionnaireRepository
}

trait InProgressQuestionnaireParentalOccupationStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  //scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    def getParentalOccupationQuestionnaireQuestions: List[PersistedQuestion] = {
      val parentsOccupation = Random.parentsOccupation

      def getParentsOccupationDetail(parentsOccupation: String): Option[PersistedQuestion] = {
        if (parentsOccupation == "Employed") {
          Some(PersistedQuestion(
            "When you were 14, what kind of work did your highest-earning parent or guardian do?",
            PersistedAnswer(Some(Random.parentsOccupationDetails), None, None)
          ))
        } else {
          Some(PersistedQuestion(
            "When you were 14, what kind of work did your highest-earning parent or guardian do?",
            PersistedAnswer(Some(parentsOccupation), None, None)
          ))
        }
      }

      def getEmployeedOrSelfEmployeed(parentsOccupation: String): Option[PersistedQuestion] = {
        if (parentsOccupation == "Employed") {
          Some(PersistedQuestion(
            "Did they work as an employee or were they self employed?",
            PersistedAnswer(Some(Random.employeeOrSelf), None, None)
          ))
        } else {
          None
        }
      }

      def getSizeParentsEmployeer(parentsOccupation: String): Option[PersistedQuestion] = {
        if (parentsOccupation == "Employed") {
          Some(PersistedQuestion(
            "Which size would best describe their place of work?",
            PersistedAnswer(Some(Random.sizeParentsEmployeer), None, None)
          ))
        } else {
          None
        }
      }

      def getSuperviseEmployees(parentsOccupation: String): Option[PersistedQuestion] = {
        if (parentsOccupation == "Employed") {
          Some(PersistedQuestion(
            "Did they supervise employees?",
            PersistedAnswer(Some(Random.yesNoPreferNotToSay), None, None)
          ))
        } else {
          None
        }
      }

      List(
        Some(PersistedQuestion(
          "Do you have a parent or guardian that has completed a university degree course or equivalent?",
          PersistedAnswer(Some(Random.yesNoPreferNotToSay), None, None)
        )),
        getParentsOccupationDetail(parentsOccupation),
        getEmployeedOrSelfEmployeed(parentsOccupation),
        getSizeParentsEmployeer(parentsOccupation),
        getSuperviseEmployees(parentsOccupation)
      ).flatten
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, getParentalOccupationQuestionnaireQuestions)
      _ <- appRepository.updateQuestionnaireStatus(
        candidateInPreviousStatus.applicationId.get,
        ProgressStatuses.OccupationQuestionsCompletedProgress
      )
    } yield {
      candidateInPreviousStatus
    }
  }
  //scalastyle:on method.length
}
