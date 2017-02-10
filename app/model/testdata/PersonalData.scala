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

package model.testdata

import model.Commands.{ Address, PhoneNumber, PostCode }
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import services.testdata.faker.DataFaker.Random

case class PersonalData(
                         emailPrefix: String = s"tesf${Random.number() - 1}",
                         firstName: String = Random.getFirstname(1),
                         lastName: String = Random.getLastname(1),
                         preferredName: Option[String] = None,
                         dob: LocalDate = new LocalDate(1981, 5, 21),
                         postCode: Option[String] = Some(Random.postCode),
                         country: Option[String] = None
                       ) {
  def getPreferredName: String = preferredName.getOrElse(s"Pref$firstName")

  def personalDetails: PersonalDetails = {
    PersonalDetails(firstName, lastName, preferredName.getOrElse(firstName), dob, aLevel = false, stemLevel = false,
      civilServant = false, department = None)
  }
}

object PersonalData {

  def apply(request: model.exchange.testdata.PersonalDataRequest, generatorId: Int): PersonalData = {
    val default = PersonalData()
    val fname = request.firstName.getOrElse(Random.getFirstname(generatorId))
    val emailPrefix = request.emailPrefix.map(e => s"$e-$generatorId")

    case class PostCodeCountry(postCode: Option[String], country: Option[String])

    val postCodeCountry = if (!request.postCode.isDefined && !request.country.isDefined) {
      if (Random.bool) {
        PostCodeCountry(Some(Random.postCode), None)
      } else {
        PostCodeCountry(None, Some(Random.country))
      }
    } else {
      PostCodeCountry(request.postCode, request.country)
    }


    PersonalData(
      emailPrefix = emailPrefix.getOrElse(s"tesf${Random.number()}-$generatorId"),
      firstName = fname,
      lastName = request.lastName.getOrElse(Random.getLastname(generatorId)),
      preferredName = request.preferredName,
      dob = request.dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))).getOrElse(default.dob),
      postCode = postCodeCountry.postCode,
      country = postCodeCountry.country
    )
  }
}
