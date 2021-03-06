/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories

import model.ReportExchangeObjects.PassMarkReportQuestionnaireData
import model.PersistedObjects.{PersistedAnswer, PersistedQuestion}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import services.reporting.SocioEconomicScoreCalculator
import testkit.MongoRepositorySpec

class QuestionnaireRepositorySpec extends MongoRepositorySpec with MockitoSugar {

  override val collectionName = CollectionNames.QUESTIONNAIRE

  "The Questionnaire Repo" should {

    "create collection, append questions to the application and overwrite existing questions" in new Fixture {

      val applicationId = System.currentTimeMillis() + ""
      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("what?", PersistedAnswer(Some("nothing"), None, None)))).futureValue
      val result = questionnaireRepo.find(applicationId).futureValue
      result.size mustBe 1

      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("what?", PersistedAnswer(Some("nada"), None, None)))).futureValue
      val result1 = questionnaireRepo.find(applicationId).futureValue
      result1.size mustBe 1
      result1.head.answer.answer mustBe Some("nada")

      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("where?", PersistedAnswer(None, None, Some(true))))).futureValue
      val result2 = questionnaireRepo.find(applicationId).futureValue
      result2.size mustBe 2
    }

    "find questions should return a map of questions/answers ignoring the non answered ones" in new Fixture {

      val applicationId = System.currentTimeMillis() + ""

      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("what?", PersistedAnswer(Some("nada"), None, None)))).futureValue
      questionnaireRepo.addQuestions(applicationId, List(PersistedQuestion("where?", PersistedAnswer(None, None, Some(true))))).futureValue
      val result2 = questionnaireRepo.findQuestions(applicationId).futureValue

      result2.keys.size mustBe 2
      result2("where?") mustBe ""
    }

    // TODO Remove ignore and fix the test when passmark story is implemented
    "return data relevant to the pass mark modelling report" ignore new Fixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaires()

      val report = questionnaireRepo.passMarkReport.futureValue

      report mustBe Map(
        applicationId1 -> PassMarkReportQuestionnaireData(
          Some("Male"), Some("Lesbian"), Some("Black"), Some("Unemployed"), None, Some("Self-employed"),
          None, "SES Score"),
        applicationId2 -> PassMarkReportQuestionnaireData(
          Some("Female"), Some("Lesbian"), Some("White"), Some("Employed"), Some("Modern professional"), Some("Part-time employed"),
          Some("Large (26-500)"), "SES Score")
      )
    }

    // TODO Remove ignore and fix the test when passmark story is implemented
    "calculate the socioeconomic score for the pass mark modelling report" ignore new Fixture {
      when(socioEconomicCalculator.calculate(any())).thenReturn("SES Score")
      submitQuestionnaire()

      questionnaireRepo.passMarkReport.futureValue

      verify(socioEconomicCalculator).calculate(Map(
        "What is your gender identity?" -> "Male",
        "What is your sexual orientation?" -> "Lesbian",
        "What is your ethnic group?" -> "Black",
        "Which type of occupation did they have?" -> "Unemployed",
        "Did they work as an employee or were they self-employed?" -> "Self-employed",
        "Which size would best describe their place of work?" -> "Unknown"
      ))
    }
  }

  trait Fixture {
    val applicationId1 = "abc"
    val applicationId2 = "123"
    val applicationId3 = "partiallyCompleteId"
    val submittedQuestionnaire1 = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some("Male"), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some("Lesbian"), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some("Black"), None, None)),
      PersistedQuestion("Which type of occupation did they have?", PersistedAnswer(Some("Unemployed"), None, None)),
      PersistedQuestion("Did they work as an employee or were they self-employed?", PersistedAnswer(Some("Self-employed"), None, None)),
      PersistedQuestion("Which size would best describe their place of work?", PersistedAnswer(None, None, Some(true)))
    )
    val submittedQuestionnaire2 = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some("Female"), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some("Lesbian"), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some("White"), None, None)),
      PersistedQuestion("Which type of occupation did they have?", PersistedAnswer(Some("Modern professional"), None, None)),
      PersistedQuestion("Did they work as an employee or were they self-employed?", PersistedAnswer(Some("Part-time employed"), None, None)),
      PersistedQuestion("Which size would best describe their place of work?", PersistedAnswer(Some("Large (26-500)"), None, None))
    )
    val partiallyCompleteQuestionnaire = List(
      PersistedQuestion("What is your gender identity?", PersistedAnswer(Some("Female"), None, None)),
      PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some("Lesbian"), None, None)),
      PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some("White"), None, None))
    )

    val socioEconomicCalculator = mock[SocioEconomicScoreCalculator]
    def questionnaireRepo = new QuestionnaireMongoRepository(socioEconomicCalculator)

    def submitQuestionnaire(): Unit =
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire1).futureValue

    def submitQuestionnaires(): Unit = {
      questionnaireRepo.addQuestions(applicationId1, submittedQuestionnaire1).futureValue
      questionnaireRepo.addQuestions(applicationId2, submittedQuestionnaire2).futureValue
      questionnaireRepo.addQuestions(applicationId3, partiallyCompleteQuestionnaire).futureValue
    }
  }
}
