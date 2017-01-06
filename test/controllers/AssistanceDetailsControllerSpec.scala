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

package controllers

import config.TestFixtureBase
import model.Exceptions.{AssistanceDetailsNotFound, CannotUpdateAssistanceDetails}
import model.exchange.AssistanceDetailsExamples
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.Helpers._
import services.assistancedetails.AssistanceDetailsService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps
import play.api.libs.json.Json

class AssistanceDetailsControllerSpec extends UnitWithAppSpec {

  "Find" should {
    "return OK and the corresponding assistance details" in new TestFixture {
      when(mockAssistanceDetailsService.find(AppId, UserId)
      ).thenReturn(Future.successful((AssistanceDetailsExamples.DisabilityGisAndAdjustments)))
      val result = controller.find(UserId, AppId)(fakeRequest)
      status(result) must be(OK)
      contentAsJson(result) must be(Json.toJson(AssistanceDetailsExamples.DisabilityGisAndAdjustments))
    }

    "return NOT_FOUND" in new TestFixture {
      when(mockAssistanceDetailsService.find(AppId, UserId)).thenReturn(Future.failed(new AssistanceDetailsNotFound(AppId)))
      val result = controller.find(UserId, AppId)(fakeRequest)
      status(result) must be(NOT_FOUND)
      contentAsString(result) must be(s"cannot find assistance details for application: $AppId")
    }
  }

  "Update" should {
    "return CREATED and update the details and audit AssistanceDetailsSaved event" in new TestFixture {
      val Request = fakeRequest(AssistanceDetailsExamples.DisabilityGisAndAdjustments)
      when(mockAssistanceDetailsService.update(AppId, UserId, AssistanceDetailsExamples.DisabilityGisAndAdjustments)
      ).thenReturn(Future.successful(()))
      val result = controller.update(UserId, AppId)(Request)
      status(result) must be(CREATED)
      verify(mockAuditService).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return BAD_REQUEST when there is a CannotUpdateAssistanceDetails exception" in new TestFixture {
      val Request = fakeRequest(AssistanceDetailsExamples.DisabilityGisAndAdjustments)
      when(mockAssistanceDetailsService.update(AppId, UserId, AssistanceDetailsExamples.DisabilityGisAndAdjustments)).
        thenReturn(Future.failed(CannotUpdateAssistanceDetails(UserId)))
      val result: Future[Result] = controller.update(UserId, AppId).apply(Request)
      status(result) must be(BAD_REQUEST)
      verify(mockAuditService, times(0)).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockAssistanceDetailsService = mock[AssistanceDetailsService]

    val controller = new AssistanceDetailsController {
      val assistanceDetailsService = mockAssistanceDetailsService
      val auditService = mockAuditService
    }
  }
}
