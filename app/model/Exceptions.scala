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

package model

// scalastyle:off number.of.methods
// scalastyle:off number.of.types
object Exceptions {
  sealed class ConnectorException(message: String) extends Exception(message)

  case class NotFoundException(m: Option[String] = None) extends Exception(m.getOrElse("")) {
    def this(m: String) = this(Some(m))
  }

  case class CannotUpdateCubiksTest(m: String) extends Exception(m)

  case class CannotExtendCubiksTest(m: String) extends Exception(m)

  case class UnexpectedException(m: String) extends Exception(m)

  case class CannotUpdateRecord(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateContactDetails(userId: String) extends Exception(userId)

  case class PersonalDetailsNotFound(applicationId: String) extends Exception(applicationId)

  case class ApplicationNotFound(id: String) extends Exception(id)

  case class PassMarkSettingsNotFound() extends Exception

  case class ContactDetailsNotFound(userId: String) extends Exception(userId)

  case class CannotAddMedia(userId: String) extends Exception(userId)

  case class AssistanceDetailsNotFound(id: String) extends Exception(id)

  case class CannotUpdateAssistanceDetails(id: String) extends Exception(id)

  case class CannotUpdateReview(applicationId: String) extends Exception(applicationId)

  case class TooManyEntries(msg: String) extends Exception(msg)

  case class NoResultsReturned(reason: String) extends Exception(reason)

  case class NoSuchVenueException(reason: String) extends Exception(reason)

  case class NoSuchVenueDateException(reason: String) extends Exception(reason)

  case class IncorrectStatusInApplicationException(reason: String) extends Exception(reason)

  case class InvalidStatusException(reason: String) extends Exception(reason)

  case class EmailTakenException() extends Exception()

  case class NotEligibleForLocation() extends Exception()

  case class NotEligibleForScheme() extends Exception()

  case class DataFakingException(message: String) extends Exception(message)

  case class DataGenerationException(message: String) extends Exception(message)

  case class OnlineTestPassmarkEvaluationNotFound(appId: String) extends Exception(s"Application id: $appId")

  case class OnlineTestFirstLocationResultNotFound(appId: String) extends Exception(s"application Id: $appId")

  case class SchemePreferencesNotFound(applicationId: String) extends Exception(applicationId)

  case class LocationPreferencesNotFound(applicationId: String) extends Exception(applicationId)

  case class AdjustmentsCommentNotFound(applicationId: String) extends Exception(applicationId)

  case class CannotUpdateAdjustmentsComment(applicationId: String) extends Exception(applicationId)

  case class CannotRemoveAdjustmentsComment(applicationId: String) extends Exception(applicationId)
}
// scalastyle:on
