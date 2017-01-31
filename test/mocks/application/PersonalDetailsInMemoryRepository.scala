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

package mocks.application

import mocks.InMemoryStorage
import model.ApplicationStatuses
import model.Exceptions.PersonalDetailsNotFound
import model.PersistedObjects.PersonalDetailsWithUserId
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import repositories.application.PersonalDetailsRepository

import scala.concurrent.Future

object PersonalDetailsInMemoryRepository extends PersonalDetailsRepository with InMemoryStorage[PersonalDetails] {
  // Seed with test data.
  inMemoryRepo +=
    "111-111" ->
    PersonalDetails(
      "Jo",
      "Bloggs",
      "Joey",
      LocalDate.now(),
      aLevel = true,
      stemLevel = true,
      civilServant = false,
      department = None
    )

  override def update(applicationId: String, userId: String, pd: PersonalDetails): Future[Unit] = {
    super.update(applicationId, userId, pd)
  }

  override def update(appId: String, userId: String, personalDetails: PersonalDetails,
    requiredStatuses: Seq[ApplicationStatuses.EnumVal], newApplicationStatus: ApplicationStatuses.EnumVal
  ): Future[Unit] = Future.successful(())

  override def notFound(applicationId: String) = throw PersonalDetailsNotFound(applicationId)

  def findPersonalDetailsWithUserId(applicationId: String): Future[PersonalDetailsWithUserId] = ???
}
