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

package scheduler.onlinetesting

import connectors.PassMarkExchangeObjects.OnlineTestPassmarkSettings
import model.ApplicationStatuses
import model.OnlineTestCommands.CandidateEvaluationData
import model.PersistedObjects.CandidateTestReport
import org.joda.time.DateTime
import org.mockito.Matchers.any
import org.mockito.Mockito._
import services.onlinetesting.EvaluateOnlineTestService
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EvaluateCandidateScoreJobSpec extends UnitWithAppSpec with ShortTimeout {

  "Evaluate candidate score job" should {
    "skip evaluation when no candidate ready for evaluation found" in new TestFixture {
      when(passmarkServiceMock.nextCandidateReadyForEvaluation).thenReturn(Future.successful(None))
      evaluateCandidateScoreJob.tryExecute().futureValue
      verify(passmarkServiceMock, never).evaluate(any[CandidateEvaluationData])
    }

    "evaluate the score successfully for ONLINE_TEST_COMPLETED" in new TestFixture {
      val onlineTestCompletedCandidateScore = CandidateEvaluationData(
        OnlineTestPassmarkSettings(List(), "version", DateTime.now(), "user"), Nil, CandidateTestReport("appId", "type"),
        ApplicationStatuses.OnlineTestCompleted
      )
      when(passmarkServiceMock.nextCandidateReadyForEvaluation).thenReturn(Future.successful(Some(onlineTestCompletedCandidateScore)))
      when(passmarkServiceMock.evaluate(onlineTestCompletedCandidateScore)).thenReturn(Future.successful(()))
      evaluateCandidateScoreJob.tryExecute().futureValue
      verify(passmarkServiceMock).evaluate(onlineTestCompletedCandidateScore)
    }
  }

  trait TestFixture {
    val passmarkServiceMock = mock[EvaluateOnlineTestService]
    val evaluateCandidateScoreJob = new EvaluateCandidateScoreJob {
      val passmarkService = passmarkServiceMock
    }
  }
}
