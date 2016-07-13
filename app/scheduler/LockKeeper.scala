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

package scheduled

import java.util.UUID

import org.joda.time.Duration
import play.modules.reactivemongo.ReactiveMongoPlugin
import repositories._
import uk.gov.hmrc.play.config.RunMode

import scala.concurrent.{ ExecutionContext, Future }

trait LockKeeper {
  val repo: LockRepository
  val lockId: String
  val serverId: String
  val forceLockReleaseAfter: Duration
  val greedyLockingEnabled: Boolean

  def isLocked(implicit ec: ExecutionContext): Future[Boolean] = repo.isLocked(lockId, serverId)

  def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
    repo.lock(lockId, serverId, forceLockReleaseAfter)
      .flatMap { acquired =>
        if (acquired) {
          body.flatMap {
            case x =>
              if (greedyLockingEnabled) {
                Future.successful(Some(x))
              } else {
                repo.releaseLock(lockId, serverId).map(_ => Some(x))
              }
          }
        } else {
          Future.successful(None)
        }
      }.recoverWith { case ex => repo.releaseLock(lockId, serverId).flatMap(_ => Future.failed(ex)) }
  }

}

object LockKeeper extends RunMode {
  private implicit val connection = {
    import play.api.Play.current
    ReactiveMongoPlugin.mongoConnector.db
  }

  lazy val generatedServerId = UUID.randomUUID().toString

  def apply(lockIdToUse: String, forceLockReleaseAfterToUse: scala.concurrent.duration.Duration) = new LockKeeper {
    val forceLockReleaseAfter: Duration = Duration.millis(forceLockReleaseAfterToUse.toMillis)
    val serverId = generatedServerId
    val lockId = lockIdToUse
    val repo: LockRepository = new LockMongoRepository
    val greedyLockingEnabled: Boolean = true
  }
}
