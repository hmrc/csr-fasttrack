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

package services.assistancedetails

import model.ApplicationStatuses._
import model.exchange.AssistanceDetails
import repositories._
import repositories.application.{ AssistanceDetailsRepository, GeneralApplicationRepository, OnlineTestRepository }
import services.onlinetesting.OnlineTestService
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object AssistanceDetailsService extends AssistanceDetailsService {
  val adRepository = assistanceDetailsRepository
  val appRepository = applicationRepository
  val onlineTestingRepo = onlineTestRepository
  val onlineTestService = OnlineTestService
  val trRepository = testReportRepository
}

trait AssistanceDetailsService {
  val adRepository: AssistanceDetailsRepository
  val appRepository: GeneralApplicationRepository
  val onlineTestService: OnlineTestService
  val onlineTestingRepo: OnlineTestRepository
  val trRepository: TestReportRepository

  def update(applicationId: String, userId: String, assistanceDetails: AssistanceDetails): Future[Unit] = {
    adRepository.update(applicationId, userId, assistanceDetails)
  }

  def find(applicationId: String, userId: String): Future[AssistanceDetails] = {
    adRepository.find(applicationId)
  }

  def updateToGis(applicationId: String): Future[Unit] = {
    for {
      _ <- adRepository.updateToGis(applicationId)
      _ <- mayBeResetOnlineTests(applicationId)
      _ <- mayBeRemoveNonGisTestReports(applicationId)
    } yield { () }
  }

  private def mayBeResetOnlineTests(applicationId: String) = {
    onlineTestingRepo.getOnlineTestApplication(applicationId).flatMap {
      case Some(onlineTestApp) if List(OnlineTestInvited, OnlineTestStarted, OnlineTestExpired).contains(onlineTestApp.applicationStatus)  =>
        onlineTestService.registerAndInviteApplicant(onlineTestApp).map { _ => () }
      case _ => Future.successful(())
    }
  }

  private def mayBeRemoveNonGisTestReports(applicationId: String) = {
    onlineTestingRepo.getOnlineTestApplication(applicationId).flatMap {
      case Some(onlineTestApp) if OnlineTestCompleted == onlineTestApp.applicationStatus  =>
        trRepository.removeNonGis(applicationId).map { _ => () }
      case _ => Future.successful(())
    }
  }
}
