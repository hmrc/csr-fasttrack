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

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.Commands.Address
import model.PersistedObjects.{ContactDetails, PersonalDetails}
import org.joda.time.LocalDate
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.{AssistanceDetailsRepository, GeneralApplicationRepository, PersonalDetailsRepository}
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressPersonalDetailsStatusGenerator extends InProgressPersonalDetailsStatusGenerator {
  override val previousStatusGenerator = CreatedStatusGenerator
  override val appRepository = applicationRepository
  override val pdRepository = personalDetailsRepository
  override val cdRepository = contactDetailsRepository
}

trait InProgressPersonalDetailsStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository


  //scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    def getPersonalDetails(candidateInformation: DataGenerationResponse) = {
      PersonalDetails(
        candidateInformation.firstName,
        candidateInformation.lastName,
        "Pref" + candidateInformation.firstName,
        new LocalDate(2015, 5, 21),
        Random.bool,
        Random.bool
      )
    }

    def getContactDetails(candidateInformation: DataGenerationResponse) = {

      def makeRandAddressOption = if (Random.bool) { Some(Random.addressLine) } else { None }

      ContactDetails(
        Address(
          Random.addressLine,
          makeRandAddressOption,
          makeRandAddressOption,
          makeRandAddressOption
        ),
        "AB1 2CD",
        candidateInformation.email,
        Some("07770 774 914")
      )
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      pd = getPersonalDetails(candidateInPreviousStatus)
      cd = getContactDetails(candidateInPreviousStatus)
      _ <- pdRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        pd, List(model.ApplicationStatuses.Created), model.ApplicationStatuses.InProgress)
      _ <- cdRepository.update(candidateInPreviousStatus.userId, cd)
    } yield {
      candidateInPreviousStatus.copy(personalDetails = Some(pd), contactDetails = Some(cd))
    }
  }
  //scalastyle:off method.length
}
