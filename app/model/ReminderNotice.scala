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

package model

/**
 * @param event the event description for the audit log
 * @param template email templete to be used for the notification
 * @param notificationStatus progress status to be added upon notification
 * @param hoursBeforeReminder when the reminder should be sent (in hours before the test expiry date)
 */
sealed case class ReminderNotice(event: String, template: String, notificationStatus: String, hoursBeforeReminder: Int)

object FirstReminder extends ReminderNotice(
  "FirstReminderBeforeTestExpirationSent",
  "fset_fasttrack_app_online_test_reminder_3_day",
  ProgressStatuses.OnlineTestFirstExpiryNotification.name,
  24 * 3 // three days before test expiry date
)

object SecondReminder extends ReminderNotice(
  "SecondReminderBeforeTestExpirationSent",
  "fset_fasttrack_app_online_test_reminder_1_day",
  ProgressStatuses.OnlineTestSecondExpiryNotification.name,
  24 // one day before test expiry date
)
