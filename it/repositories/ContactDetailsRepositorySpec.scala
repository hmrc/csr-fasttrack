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

package repositories

import model.Commands.Address
import model.PersistedObjects.ContactDetails
import testkit.MongoRepositorySpec


class ContactDetailsRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.CONTACT_DETAILS

  def repo = new ContactDetailsMongoRepository()

  "Contact details" should {
    "create indexes for the repository" in {
      val repo = repositories.contactDetailsRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("userId"))
      indexes.size mustBe 2
    }

    "return empty list for empty contact details" in {
      repo.findAll.futureValue must be (empty)
    }

    "return list of contact details" in {
      insert("1", ContactDetails(outsideUk = false, Address("line1a"), Some("123"), None, "email1@email.com", Some("12345")))
      insert("2", ContactDetails(outsideUk = true, Address("line1b"), None, Some("Mongolia"), "email2@email.com", Some("67890")))

      val result = repo.findAll.futureValue
      result.size mustBe 2
    }

    "return only the first 10 documents if there is more than 10" in {
      for (i <- 1 to 10) {
        insert(i.toString, ContactDetails(outsideUk = false, Address(s"line$i"), Some(s"123$i"), None, s"email$i@email.com", Some(s"12345$i")))
      }

      val result = repo.findAll.futureValue
      result.size mustBe 10
    }
  }

  private def insert(userId: String, cd: ContactDetails) = repo.update(userId, cd).futureValue

}
