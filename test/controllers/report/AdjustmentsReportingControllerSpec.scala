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

package controllers.report

import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.exchange.AssistanceDetails
import model.report.AdjustmentReportItem
import model.{ Adjustments, AdjustmentsComment, AssessmentCentreIndicator }
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.libs.json.JsArray
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import testkit.MockitoImplicits._

import scala.language.postfixOps

class AdjustmentsReportingControllerSpec extends BaseReportingControllerSpec {

  "Reporting controller create adjustment report" should {
    "return the adjustment report when we execute adjustment reports" in new AdjustmentsTestFixture {
      when(contactDetailsRepoMock.findAll).thenReturnAsync(
        List(
          ContactDetailsWithId("1", outsideUk = false, Address("First Line", None, None, None), Some("HP18 9DN"),
            None, "joe@bloggs.com", None),
          ContactDetailsWithId("2", outsideUk = false, Address("First Line", None, None, None), Some("HP18 9DN"),
            None, "joe@bloggs.com", None),
          ContactDetailsWithId("3", outsideUk = false, Address("First Line", None, None, None), Some("HP18 9DN"),
            None, "joe@bloggs.com", None)
        )
      )
      val result = controller.createAdjustmentReports(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]

      finalResult.size mustBe 3
      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe Some("joe@bloggs.com")
    }

    "return the adjustment report without contact details data" in new AdjustmentsTestFixture {
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)
      val result = controller.createAdjustmentReports(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size mustBe 3

      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe None
      (headValue \ "telephone").asOpt[String] mustBe None
    }

    "return no adjustments if there's no data on the server" in new AdjustmentsTestFixture {
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)
      when(reportingRepoMock.adjustmentReport(any[String])).thenReturnAsync(Nil)
      val result = controller.createAdjustmentReports(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult mustBe empty
    }
  }

  trait AdjustmentsTestFixture extends TestFixture {
    def createAdjustmentsReport(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAdjustmentReports(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    when(reportingRepoMock.adjustmentReport(any[String])).thenReturnAsync(
      List(
        AdjustmentReportItem("1", Some("123"), Some("John"), Some("Smith"), Some("Spiderman"),
          None, None, Some("SUBMITTED"), Some(AssistanceDetails("Yes", None, Some(true), needsSupportForOnlineAssessment = true,
            Some("more time"), needsSupportAtVenue = true, Some("big chair"))),
          Some(Adjustments(None, None, None, None)), Some(AdjustmentsComment("comment")), Some(AssessmentCentreIndicator("Sutton", "London"))),
        AdjustmentReportItem("2", Some("123"), Some("Mary"), Some("Smith"), Some("Spiderwoman"),
          None, None, Some("SUBMITTED"), Some(AssistanceDetails("Yes", None, Some(true), needsSupportForOnlineAssessment = true,
            Some("more time"), needsSupportAtVenue = true, Some("big chair"))),
          Some(Adjustments(None, None, None, None)), Some(AdjustmentsComment("comment")), Some(AssessmentCentreIndicator("Sutton", "London"))),
        AdjustmentReportItem("3", Some("123"), Some("Peter"), Some("Smith"), Some("Spiderchild"),
          None, None, Some("SUBMITTED"), Some(AssistanceDetails("Yes", None, Some(true), needsSupportForOnlineAssessment = true,
            Some("more time"), needsSupportAtVenue = true, Some("big chair"))),
          Some(Adjustments(None, None, None, None)), Some(AdjustmentsComment("comment")), Some(AssessmentCentreIndicator("Sutton", "London")))
      )
    )
  }
}
