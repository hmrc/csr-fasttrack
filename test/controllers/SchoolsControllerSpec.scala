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
import model.School
import model.School.schoolFormat
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.schools.SchoolsService
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class SchoolsControllerSpec extends UnitWithAppSpec {

  "Search for schools controller" should {
    "return empty list if no schools are found" in new TestFixture {
      when(mockSchoolsService.getSchools(any[String])).thenReturn(Future.successful(List.empty))

      val result = TestSchoolsController.getSchools("my school")(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result).as[List[School]] mustBe List.empty
    }

    "return matching schools if schools are found" in new TestFixture {
      val schools = List(School(
        typeId = "IRN",
        id = "id1",
        name = "Abbey Christian Brothers Grammar School",
        address1 = Some("77a Ashgrove Road"),
        address2 = None,
        address3 = None,
        address4 = None,
        postCode = Some("BT34 2QN"),
        phaseOfEducation = Some("Grammar"),
        typeOfEstablishment = Some("Voluntary")
      ))

      when(mockSchoolsService.getSchools(any[String])).thenReturn(Future.successful(schools))

      val result = TestSchoolsController.getSchools("my school")(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result).as[List[School]] mustBe schools
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockSchoolsService = mock[SchoolsService]

    object TestSchoolsController extends SchoolsController {
      val schoolsService = mockSchoolsService
    }
  }
}
