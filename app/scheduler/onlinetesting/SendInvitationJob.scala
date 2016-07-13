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

package scheduler.onlinetesting

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import config.ScheduledJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService

import scala.concurrent.{ ExecutionContext, Future }

object SendInvitationJob extends SendInvitationJob {
  val onlineTestingService = OnlineTestService
}

trait SendInvitationJob extends SingleInstanceScheduledJob with SendInvitationJobConfig {
  val onlineTestingService: OnlineTestService

  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    onlineTestingService.nextApplicationReadyForOnlineTesting().flatMap {
      case Some(application) =>
        onlineTestingService.registerAndInviteApplicant(application)
      case None =>
        Future.successful(Unit)
    }
  }
}

trait SendInvitationJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.sendInvitationJobConfig
  val configPrefix = "scheduling.online-testing.send-invitation-job."
  val name = "SendInvitationJob"

}
