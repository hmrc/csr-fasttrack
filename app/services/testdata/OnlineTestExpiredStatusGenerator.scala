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

import java.util.UUID

import connectors.testdata.ExchangeObjects.OnlineTestProfileResponse
import model.ApplicationStatuses
import model.testdata.GeneratorConfig
import model.persisted.CubiksTestProfile
import org.joda.time.DateTime
import repositories._
import repositories.application.OnlineTestRepository

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object OnlineTestExpiredStatusGenerator extends OnlineTestExpiredStatusGenerator {
  // Theoretically previous status generator should be ONLINE_TEST_INVITED, ONLINE_TEST_STARTED, etc
  // However we need to generate an online test in the past, so that it is expired already at the moment of creation
  // So that we have consistent data.
  override val previousStatusGenerator = SubmittedStatusGenerator
  override val otRepository = onlineTestRepository
}

trait OnlineTestExpiredStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    val onlineTestProfile = CubiksTestProfile(
      cubiksUserId = 117344,
      participantScheduleId = 149245,
      invitationDate = DateTime.now().minusDays(7),
      expirationDate = DateTime.now(),
      onlineTestUrl = generatorConfig.cubiksUrl,
      token = UUID.randomUUID().toString
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.storeOnlineTestProfileAndUpdateStatusToInvite(candidateInPreviousStatus.applicationId.get, onlineTestProfile)
      - <- otRepository.updateStatus(candidateInPreviousStatus.userId, ApplicationStatuses.OnlineTestExpired)
    } yield {
      candidateInPreviousStatus.copy(
        applicationStatus = ApplicationStatuses.OnlineTestExpired,
        onlineTestProfile = Some(
          OnlineTestProfileResponse(onlineTestProfile.cubiksUserId, onlineTestProfile.token, onlineTestProfile.onlineTestUrl)
        )
      )
    }
  }
}
