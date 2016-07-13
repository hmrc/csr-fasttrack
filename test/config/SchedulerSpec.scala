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

package config

import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import scheduler.onlinetesting.SendInvitationJob
import testkit.ShortTimeout

class SchedulerSpec extends PlaySpec with ShortTimeout {

  "Scheduler" should {
    "contain send invitation job if it is enabled" in new WithApplication {
      val scheduler = new Scheduler {
        override def sendInvitationJobConfigValues = ScheduledJobConfig(true, Some("id"), Some(5), Some(5))
      }

      scheduler.scheduledJobs must contain(SendInvitationJob)
    }

    "not contain send invitation job if it is disabled" in new WithApplication {
      val scheduler = new Scheduler {
        override def sendInvitationJobConfigValues = ScheduledJobConfig(false, Some("id"), Some(5), Some(5))
      }

      scheduler.scheduledJobs must not contain SendInvitationJob
    }
  }
}
