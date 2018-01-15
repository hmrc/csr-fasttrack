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

package controllers

import config.TestFixtureBase
import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.QuestionnaireRepository
import repositories.application.GeneralApplicationRepository
import services.AuditService
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

import scala.language.postfixOps

class QuestionnaireControllerSpec extends PlaySpec with Results {

  "The Questionnaire API" should {

    "append questions to the questionnaire for the current application" in new TestFixture {
      val appId = "1234"

      status(TestQuestionnaireController.addSection(appId, "section1")(addQuestionnaireSection(appId, "section1")(
        s"""
           |{
           |  "questions": [
           |   {"question":"parent occupation"   , "answer": {"unknown":true } },
           |   {"question":"other stuff" , "answer": {"answer": "other", "otherDetails":"something" } }
           |  ]
           |}
           |""".stripMargin
      ))) mustBe ACCEPTED

      verify(questionnaireRepoMock).addQuestions(eqTo(appId), eqTo(List(
        PersistedQuestion(question = "parent occupation", answer = PersistedAnswer(answer = None, otherDetails = None, unknown = Some(true))),
        PersistedQuestion(question = "other stuff", answer = PersistedAnswer(answer = Some("other"),
          otherDetails = Some("something"), unknown = None))
      )))
      verify(mockAuditService)
        .logEvent(eqTo("QuestionnaireSectionSaved"), eqTo(Map("section" -> "section1")))(any[HeaderCarrier], any[RequestHeader])

      status(TestQuestionnaireController.addSection(appId, "section2")(addQuestionnaireSection(appId, "section2")(
        s"""
           |{
           |  "questions": [
           |   {"question":"income"   , "answer": {"unknown":true } },
           |   {"question":"stuff 1" , "answer": {"answer": "other"} },
           |   {"question":"stuff 2" , "answer": {"answer": "other", "otherDetails":"something" } }
           |  ]
           |}
           |""".stripMargin
      ))) mustBe ACCEPTED

      verify(questionnaireRepoMock).addQuestions(eqTo(appId), eqTo(List(
        PersistedQuestion(question = "income", answer = PersistedAnswer(answer = None, otherDetails = None, unknown = Some(true))),
        PersistedQuestion(question = "stuff 1", answer = PersistedAnswer(answer = Some("other"),
          otherDetails = None, unknown = None)),
        PersistedQuestion(question = "stuff 2", answer = PersistedAnswer(answer = Some("other"),
          otherDetails = Some("something"), unknown = None))
      )))

      verify(mockAuditService)
        .logEvent(eqTo("QuestionnaireSectionSaved"), eqTo(Map("section" -> "section2")))(any[HeaderCarrier], any[RequestHeader])
    }

    "return a system error on invalid json" in new TestFixture {
      val result = TestQuestionnaireController.addSection("1234", "section1")(addQuestionnaireSection("1234", "section1")(
        s"""
           |{
           |  "wrongField1":"wrong",
           |  "wrongField2":"wrong"
           |}
        """.stripMargin
      ))

      status(result) mustBe BAD_REQUEST
    }
  }

  trait TestFixture extends TestFixtureBase {
    val questionnaireRepoMock = mock[QuestionnaireRepository]
    val generalApplicationRepoMock = mock[GeneralApplicationRepository]

    object TestQuestionnaireController extends QuestionnaireController {
      override val qRepository: QuestionnaireRepository = questionnaireRepoMock
      override val appRepository: GeneralApplicationRepository = generalApplicationRepoMock
      override val auditService: AuditService = mockAuditService
    }

    when(questionnaireRepoMock.addQuestions(any[String], any[List[PersistedQuestion]])).thenReturnAsync()
    when(generalApplicationRepoMock.updateQuestionnaireStatus(any[String], any[String])).thenReturnAsync()

    def addQuestionnaireSection(applicationId: String, section: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.QuestionnaireController.addSection(applicationId, section).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
