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

import org.joda.time.Duration
import testkit.MongoRepositorySpec

class LockRepositorySpec extends MongoRepositorySpec {
  val lockTimeout = new Duration(1000L)

  override val collectionName = "locks"

  def repo = new LockMongoRepository()

  "Lock Repository" should {
    "insert a lock when the database is empty" in {
      val result = repo.lock("lockId", "owner", lockTimeout).futureValue

      result must be(true)
    }

    "fail to insert another lock when the first one has not yet expired" in {
      repo.lock("lockId", "owner", lockTimeout).futureValue
      val result = repo.lock("lockId", "owner", lockTimeout).futureValue

      result must be(false)
    }

    "be locked when one lock has expired, but another one has been created afterwards" in {
      repo.lock("lockId", "owner", new Duration(500L)).futureValue
      Thread.sleep(505L)
      repo.lock("lockId", "owner", new Duration(500L)).futureValue
      val isLocked = repo.isLocked("lockId", "owner").futureValue

      isLocked must be(true)
    }

    "is not locked when the lock has expired" in {
      repo.lock("lockId", "owner", new Duration(500L)).futureValue
      Thread.sleep(501L)
      val result = repo.isLocked("lockId", "owner").futureValue

      result must be(false)
    }

    "has no lock when the lock has been released" in {
      repo.lock("lockId", "owner", lockTimeout).futureValue
      repo.releaseLock("lockId", "owner").futureValue
      val result = repo.isLocked("lockId", "owner").futureValue

      result must be(false)
    }
  }
}