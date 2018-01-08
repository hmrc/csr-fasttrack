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

object InProgressQuestionnaireEducationStatusGenerator extends InProgressQuestionnaireEducationStatusGenerator {
  override val previousStatusGenerator = InProgressQuestionnaireDiversityStatusGenerator
  override val appRepository = applicationRepository
  override val qRepository = questionnaireRepository
}

trait InProgressQuestionnaireEducationStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  //scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    def getEducationQuestionnaireQuestions = {
      val didYouLiveInUkBetween14and18Answer = Random.yesNo
      def getWhatWasYourHomePostCodeWhenYouWere14 = {
        if (didYouLiveInUkBetween14and18Answer == "Yes") {
          Some(PersistedQuestion(
            "What was your home postcode when you were 14?",
            PersistedAnswer(Some(Random.homePostcode), None, None)
          ))
        } else {
          None
        }
      }

      def getSchoolName14to16Answer = {
        if (didYouLiveInUkBetween14and18Answer == "Yes") {
          Some(PersistedQuestion(
            "Aged 14 to 16 what was the name of your school?",
            PersistedAnswer(Some(Random.age14to16School), None, None)
          ))
        } else {
          None
        }
      }

      def getSchoolName16to18Answer = {
        if (didYouLiveInUkBetween14and18Answer == "Yes") {
          Some(PersistedQuestion(
            "Aged 16 to 18 what was the name of your school?",
            PersistedAnswer(Some(Random.age16to18School), None, None)
          ))
        } else {
          None
        }
      }

      def getFreeSchoolMealsAnswer = {
        if (didYouLiveInUkBetween14and18Answer == "Yes") {
          Some(PersistedQuestion(
            "Were you at any time eligible for free school meals?",
            PersistedAnswer(Some(Random.yesNoPreferNotToSay), None, None)
          ))
        } else {
          None
        }
      }

      List(
        Some(PersistedQuestion("Did you live in the UK between the ages of 14 and 18?", PersistedAnswer(
          Some(didYouLiveInUkBetween14and18Answer), None, None
        ))),
        getWhatWasYourHomePostCodeWhenYouWere14,
        getSchoolName14to16Answer,
        getSchoolName16to18Answer,
        getFreeSchoolMealsAnswer
      ).flatten
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, getEducationQuestionnaireQuestions)
      _ <- appRepository.updateQuestionnaireStatus(
        candidateInPreviousStatus.applicationId.get,
        ProgressStatuses.EducationQuestionsCompletedProgress
      )
    } yield {
      candidateInPreviousStatus
    }
  }
  //scalastyle:on method.length
}
