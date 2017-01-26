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

package services.testdata

import model.ApplicationStatuses
import model.EvaluationResults.Result
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import services.testdata.faker.DataFaker.Random
import model.commands.exchange.testdata.AssistanceDetailsData

  case class PersonalData(
                           emailPrefix: String = s"tesf${Random.number() - 1}",
                           firstName: String = Random.getFirstname(1),
                           lastName: String = Random.getLastname(1),
                           preferredName: Option[String] = None,
                           dob: LocalDate = new LocalDate(1981, 5, 21),
                           postCode: Option[String] = None,
                           country: Option[String] = None,
                           edipCompleted: Option[Boolean] = None
                         ) {
    def getPreferredName: String = preferredName.getOrElse(s"Pref$firstName")

    def personalDetails: PersonalDetails = {
      PersonalDetails(firstName, lastName, preferredName.getOrElse(firstName), dob, aLevel = false, stemLevel = false,
        civilServant = false, department = None)
    }
  }

  object PersonalData {

    def apply(o: model.exchange.testdata.PersonalDataRequest, generatorId: Int): PersonalData = {
      val default = PersonalData()
      val fname = o.firstName.getOrElse(Random.getFirstname(generatorId))
      val emailPrefix = o.emailPrefix.map(e => s"$e-$generatorId")

      PersonalData(
        emailPrefix = emailPrefix.getOrElse(s"tesf${Random.number()}-$generatorId"),
        firstName = fname,
        lastName = o.lastName.getOrElse(Random.getLastname(generatorId)),
        preferredName = o.preferredName,
        dob = o.dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))).getOrElse(default.dob),
        postCode = o.postCode,
        country = o.country
      )
    }
  }

  case class GeneratorConfig(
    personalData: PersonalData = PersonalData(),
    setGis: Boolean = false,
    cubiksUrl: String,
    region: Option[String] = None,
    loc1scheme1Passmark: Option[Result] = None,
    loc1scheme2Passmark: Option[Result] = None,
    assistanceDetails: AssistanceDetailsData = AssistanceDetailsData(),
    previousStatus: Option[String] = None,
    confirmedAllocation: Boolean = true
  )

  object GeneratorConfig {
    def apply(cubiksUrlFromConfig: String, o: model.exchange.testdata.CreateCandidateInStatusRequest)(generatorId: Int): GeneratorConfig = {

      val statusData = StatusData(o.statusData)
      GeneratorConfig(
        personalData = o.personalData.map(PersonalData(_, generatorId)).getOrElse(PersonalData()),
        assistanceDetails = o.assistanceDetailsData.map(AssistanceDetailsData(_, generatorId)).getOrElse(AssistanceDetailsData()),
        previousStatus = statusData.previousApplicationStatus,
        cubiksUrl = cubiksUrlFromConfig,
        region = o.region,
        loc1scheme1Passmark = o.loc1scheme1EvaluationResult.map(Result.apply),
        loc1scheme2Passmark = o.loc1scheme2EvaluationResult.map(Result.apply),
        confirmedAllocation = statusData.applicationStatus == ApplicationStatuses.AllocationConfirmed.name
      )
    }
  }

  case class StatusData(applicationStatus: String,
                        previousApplicationStatus: Option[String] = None,
                        progressStatus: Option[String] = None)

  object StatusData {
    def apply(o: model.exchange.testdata.StatusDataRequest): StatusData = {
      StatusData(applicationStatus = o.applicationStatus,
        previousApplicationStatus = o.previousApplicationStatus,
        progressStatus = o.progressStatus
      )
    }
  }
