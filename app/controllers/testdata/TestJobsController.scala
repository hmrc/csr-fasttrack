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

package controllers.testdata

import play.api.mvc.Action
import scheduler.allocation.ExpireAllocationJob
import scheduler.assessment.EvaluateAssessmentScoreJob
import scheduler.onlinetesting.{ EvaluateCandidateScoreJob, SendInvitationJob, SendOnlineTestResultJob }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object TestJobsController extends TestJobsController

class TestJobsController extends BaseController {

  def inviteCandidate = Action.async { implicit request =>
    SendInvitationJob.tryExecute().map { _ =>
      Ok("Invite candidate score job started")
    }
  }

  def evaluateCandidate = Action.async { implicit request =>
    EvaluateCandidateScoreJob.tryExecute().map { _ =>
      Ok("Evaluate candidate score job started")
    }
  }

  def sendOnlineTestResult = Action.async { implicit request =>
    SendOnlineTestResultJob.tryExecute().map { _ =>
      Ok("Send online test result job started")
    }
  }

  def evaluateAssessmentScore = Action.async { implicit request =>
    EvaluateAssessmentScoreJob.tryExecute().map { _ =>
      Ok("Evaluate assessment score job started")
    }
  }

  def processExpiredAllocations = Action.async { implicit request =>
    ExpireAllocationJob.tryExecute().map { _ =>
      Ok("Expire allocation job started")
    }
  }
}
