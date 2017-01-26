/*
 * Copyright 2017 HM Revenue & Customs
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

import model.exchange.AssistanceDetails
import repositories._
import repositories.application.AssistanceDetailsRepository

import scala.concurrent.Future

object AssistanceDetailsService extends AssistanceDetailsService {
  val adRepository = assistanceDetailsRepository
}

trait AssistanceDetailsService {
  val adRepository: AssistanceDetailsRepository

  def update(applicationId: String, userId: String, assistanceDetails: AssistanceDetails): Future[Unit] = {
    adRepository.update(applicationId, userId, assistanceDetails)
  }

  def find(applicationId: String, userId: String): Future[AssistanceDetails] = {
    adRepository.find(applicationId)
  }
}
