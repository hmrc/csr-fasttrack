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

package scheduler.onlinetesting

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import config.ScheduledJobConfig
import connectors.CSREmailClient
import model.{ FirstReminder, ReminderNotice, SecondReminder }
import repositories._
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.AuditService
import services.onlinetesting.{ OnlineTestExpiryService, OnlineTestExpiryServiceImpl }

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object FirstReminderExpiryTestsJob extends ReminderExpiryTestsJob {
  override val conf = config.MicroserviceAppConfig.firstReminderOnlineTestJobConfig
  val configPrefix = "scheduling.online-testing.first-reminder-expiry-tests-job."
  val name = "FirstReminderExpiryTestsJob"
  val reminderNotice = FirstReminder
  val service = new OnlineTestExpiryServiceImpl(
    onlineTestRepository, contactDetailsRepository, CSREmailClient, AuditService, HeaderCarrier()
  )
}

object SecondReminderExpiryTestsJob extends ReminderExpiryTestsJob {
  override val conf = config.MicroserviceAppConfig.secondReminderOnlineTestJobConfig
  val configPrefix = "scheduling.online-testing.second-reminder-expiry-tests-job."
  val name = "SecondReminderExpiryTestsJob"
  val reminderNotice = SecondReminder
  val service = new OnlineTestExpiryServiceImpl(
    onlineTestRepository, contactDetailsRepository, CSREmailClient, AuditService, HeaderCarrier()
  )
}

trait ReminderExpiryTestsJob extends SingleInstanceScheduledJob with BasicJobConfig[ScheduledJobConfig] {
  val service: OnlineTestExpiryService
  val reminderNotice: ReminderNotice
  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    service.processNextTestForReminder(reminderNotice)
  }
}
