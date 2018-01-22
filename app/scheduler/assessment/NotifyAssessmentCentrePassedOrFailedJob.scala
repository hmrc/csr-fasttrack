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

package scheduler.assessment

import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import config.ScheduledJobConfig
import model.EmptyRequestHeader
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.onlinetesting.BasicJobConfig
import services.applicationassessment.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object NotifyAssessmentCentrePassedOrFailedJob extends NotifyAssessmentCentrePassedOrFailedJob {
  val assessmentCentreService: AssessmentCentreService = AssessmentCentreService
}

trait NotifyAssessmentCentrePassedOrFailedJob extends SingleInstanceScheduledJob with NotifyAssessmentCentrePassedOrFailedJobConfig {
  val assessmentCentreService: AssessmentCentreService

  override implicit val ec = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))
  implicit val hc = HeaderCarrier()
  implicit val rh = EmptyRequestHeader

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] =
    assessmentCentreService.processNextAssessmentCentrePassedOrFailedApplication
}

trait NotifyAssessmentCentrePassedOrFailedJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  override val conf = config.MicroserviceAppConfig.notifyAssessmentCentrePassedOrFailedJobConfig
  val configPrefix = "scheduling.notify-assessment-centre-passed-or-failed-job."
  val name = "NotifyAssessmentCentrePassedOrFailedJob"
}
