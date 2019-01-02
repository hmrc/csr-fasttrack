/*
 * Copyright 2019 HM Revenue & Customs
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

import scheduler.allocation.{ ConfirmAttendanceReminderJob, ExpireAllocationJob }
import scheduler.assessment.{ EvaluateAssessmentScoreJob, NotifyAssessmentCentrePassedOrFailedJob }
import scheduler.onlinetesting._
import testkit.UnitWithAppSpec

class SchedulerSpec extends UnitWithAppSpec {

  "Scheduler" should {
    "contain send invitation job if it is enabled" in new TestFixture {
      val scheduler = serviceWithSendInvitationConfig(true)

      scheduler.scheduledJobs must contain(mockSendInvitationJob)
    }

    "not contain send invitation job if it is disabled" in new TestFixture {
      val scheduler = serviceWithSendInvitationConfig(false)

      scheduler.scheduledJobs must not contain mockSendInvitationJob
    }
  }

  trait TestFixture {
    val mockSendInvitationJob = mock[SendInvitationJob]

    def serviceWithSendInvitationConfig(enabled: Boolean) = {
      new Scheduler {
        val sendInvitationJob: SendInvitationJob = mockSendInvitationJob
        val expireOnlineTestJob: ExpireOnlineTestJob = mock[ExpireOnlineTestJob]
        val sendOnlineTestResultJob: SendOnlineTestResultJob = mock[SendOnlineTestResultJob]
        val firstReminderExpiryTestsJob: ReminderExpiryTestsJob = mock[ReminderExpiryTestsJob]
        val secondReminderExpiryTestsJob: ReminderExpiryTestsJob = mock[ReminderExpiryTestsJob]
        val retrieveOnlineTestPDFReportJob: RetrieveOnlineTestPDFReportJob = mock[RetrieveOnlineTestPDFReportJob]
        val evaluationCandidateScoreJob: EvaluateCandidateScoreJob = mock[EvaluateCandidateScoreJob]
        val confirmAttendanceReminderJob: ConfirmAttendanceReminderJob = mock[ConfirmAttendanceReminderJob]
        val evaluateAssessmentScoreJob: EvaluateAssessmentScoreJob = mock[EvaluateAssessmentScoreJob]
        val notifyAssessmentCentrePassedOrFailedJob: NotifyAssessmentCentrePassedOrFailedJob = mock[NotifyAssessmentCentrePassedOrFailedJob]
        val expireAllocationJob: ExpireAllocationJob = mock[ExpireAllocationJob]

        override private[config] def sendInvitationJobConfigValues = ScheduledJobConfig(enabled, Some("id"), Some(5), Some(5))
      }
    }
  }

}
