/*
 * Copyright 2016 HM Revenue & Customs
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

package mocks

import model.Commands.Address
import model.Exceptions.ContactDetailsNotFound
import model.PersistedObjects.{ ContactDetails, ContactDetailsWithId }
import repositories.ContactDetailsRepository

import scala.concurrent.Future

object ContactDetailsInMemoryRepository extends ContactDetailsInMemoryRepository

class ContactDetailsInMemoryRepository extends ContactDetailsRepository with InMemoryStorage[ContactDetails] {
  // Seed with test data.
  inMemoryRepo +=
    "000-000" ->
    ContactDetails(Address("First Line", None, None, None), "HP18 9DN", "joe@bloggs.com", None)

  override def notFound(userId: String) = throw new ContactDetailsNotFound(userId)

  override def findByPostCode(postCode: String): Future[List[ContactDetailsWithId]] = Future.successful(List.empty[ContactDetailsWithId])

  override def findAll: Future[List[ContactDetailsWithId]] = Future.successful(List.empty[ContactDetailsWithId])
}
