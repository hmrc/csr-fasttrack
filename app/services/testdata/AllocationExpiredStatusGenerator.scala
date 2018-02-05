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

package services.testdata

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.ApplicationStatuses
import model.Commands.AssessmentCentreAllocation
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestMongoRepository, OnlineTestRepository }
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AllocationExpiredStatusGenerator extends AllocationExpiredStatusGenerator {
  override val previousStatusGenerator = AllocationStatusGenerator
  override val otRepository: OnlineTestMongoRepository = onlineTestRepository
  override val aaRepository: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  override val appRepository: GeneralApplicationRepository = applicationRepository

  val SlotFindingLockObj = new Object()
}

trait AllocationExpiredStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val aaRepository: AssessmentCentreAllocationRepository
  val appRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {

    def getApplicationAssessment(candidate: DataGenerationResponse): Future[AssessmentCentreAllocation] = {
      for {
        availableAssessment <- Random.availableAssessmentVenueAndDate
      } yield {
        AssessmentCentreAllocation(
          candidate.applicationId.get,
          availableAssessment.venue.venueName,
          availableAssessment.date,
          availableAssessment.session,
          availableAssessment.slotNumber,
          confirmed = false
        )
      }
    }

    val newStatus = ApplicationStatuses.AllocationExpired

    AllocationExpiredStatusGenerator.SlotFindingLockObj.synchronized {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig.copy(confirmedAllocation = false))
        randomAssessment <- getApplicationAssessment(candidateInPreviousStatus)
        _ <- aaRepository.create(List(randomAssessment))
        expiryDate = randomAssessment.expireDate//.minusYears(2)
        _ <- otRepository.saveCandidateAllocationStatus(candidateInPreviousStatus.applicationId.get, newStatus, Some(expiryDate))
//        _ <- appRepository.updateAllocationExpiryDate(candidateInPreviousStatus.applicationId.get, expiryDate)
      } yield {
        candidateInPreviousStatus.copy(
          applicationStatus = newStatus,
          applicationAssessment = Some(randomAssessment),
          allocationExpireDate = Some(expiryDate)
        )
      }
    }
  }
}
