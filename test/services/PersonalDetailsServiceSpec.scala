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

package services

import model.Commands.{ Address, PhoneNumber, PostCode, UpdateGeneralDetails }
import model.PersistedObjects.ContactDetails
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.{ AssessmentCentreIndicatorRepository, ContactDetailsRepository }
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.application.PersonalDetailsService
import org.mockito.Mockito._
import org.scalatest.Inside

import scala.concurrent.Future

class PersonalDetailsServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with Inside {

  "Personal Details Service Spec" must {

    "return personal details" in new TestFixture {
      val personalDetails = PersonalDetails("fname", "lname", "prefname", LocalDate.now, aLevel = true, stemLevel = true, civilServant = false,
        department = None
      )
      val address = Address("line1", Some("line2"), Some("line3"), Some("line4"))
      val contactDetails = ContactDetails(address, "QQ1 1QQ": PostCode, "test@test.com", Some("0123456789": PhoneNumber))

      when(mockPersonalDetailsRepo.find("appId")).thenReturn(Future.successful(personalDetails))

      when(mockContactDetailsRepo.find("userId")).thenReturn(Future.successful(contactDetails))

      val result = personalDetailService.find("userId", "appId").futureValue

      inside (result) {
        case UpdateGeneralDetails(fname, lname, prefname, email, dob, addr, postcode, phone, alevel, stemlevel, isCivilServant, dept) =>
          fname mustBe personalDetails.firstName
          lname mustBe personalDetails.lastName
          prefname mustBe personalDetails.preferredName
          email mustBe contactDetails.email
          dob mustBe personalDetails.dateOfBirth
          addr mustBe address
          postcode mustBe contactDetails.postCode
          phone mustBe contactDetails.phone
          alevel mustBe personalDetails.aLevel
          stemlevel mustBe personalDetails.stemLevel
          isCivilServant mustBe personalDetails.civilServant
          dept mustBe personalDetails.department
      }

    }
  }

  trait TestFixture {

    val mockPersonalDetailsRepo = mock[PersonalDetailsRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockAuditService = mock[AuditService]
    val mockAssessmentCentreIndicatorRepo = mock[AssessmentCentreIndicatorRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]

    val personalDetailService = new PersonalDetailsService {
      override def appRepo: GeneralApplicationRepository = mockAppRepo
      override def personalDetailsRepo: PersonalDetailsRepository = mockPersonalDetailsRepo
      override def contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo
      override def auditService: AuditService = mockAuditService
    }
  }
}

