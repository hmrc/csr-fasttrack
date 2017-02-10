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
import model.PersistedObjects.ContactDetails
import model.persisted.PersonalDetails
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {
    val personalDetails = generatorConfig.personalData.personalDetails

    def getContactDetails(candidateInformation: DataGenerationResponse) = {

      def makeRandAddressOption = if (Random.bool) { Some(Random.addressLine) } else { None }

      case class PostCodeCountry(postCode: Option[String], country: Option[String])

      val postCodeCountry = if (!generatorConfig.personalData.postCode.isDefined && !generatorConfig.personalData.country.isDefined) {
        if (Random.bool) {
          PostCodeCountry(Some(Random.postCode), None)
        } else {
          PostCodeCountry(None, Some(Random.country))
        }
      } else {
        PostCodeCountry(generatorConfig.personalData.postCode, generatorConfig.personalData.country)
      }

      ContactDetails(
        false,
        Address(
          Random.addressLine,
          makeRandAddressOption,
          makeRandAddressOption,
          makeRandAddressOption
        ),
        postCodeCountry.postCode,
        postCodeCountry.country,
        generatorConfig.personalData.emailPrefix,
        Some("07770 774 914")
      )
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      cd = getContactDetails(candidateInPreviousStatus)
      _ <- pdRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        personalDetails, List(model.ApplicationStatuses.Created), model.ApplicationStatuses.InProgress)
      _ <- cdRepository.update(candidateInPreviousStatus.userId, cd)
    } yield {
      candidateInPreviousStatus.copy(personalDetails = Some(personalDetails), contactDetails = Some(cd))

    }
  }
  //scalastyle:off method.length
}
