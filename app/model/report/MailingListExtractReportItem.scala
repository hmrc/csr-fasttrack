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

package model.report

import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.ReportExchangeObjects.ApplicationForMailingListExtractReport
import play.api.libs.json.Json

case class MailingListExtractReportItem(
  userId: String,
  applicationStatus: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  preferredName: Option[String],
  outsideUk: Boolean,
  address: Address,
  postCode: Option[PostCode],
  country: Option[String],
  email: String,
  phone: Option[PhoneNumber]
)

case object MailingListExtractReportItem {
  def apply(application: ApplicationForMailingListExtractReport, contactDetails: ContactDetailsWithId): MailingListExtractReportItem = {
    MailingListExtractReportItem(
      userId = application.userId.toString(),
      applicationStatus = application.applicationStatus,
      firstName = application.firstName,
      lastName = application.lastName,
      preferredName = application.preferredName,
      outsideUk = contactDetails.outsideUk,
      address = contactDetails.address,
      postCode = contactDetails.postCode,
      country = contactDetails.country,
      email = contactDetails.email,
      phone = contactDetails.phone
    )
  }

  implicit val addressFormats = Json.format[Address]
  implicit val mailingListExtractReportItemFormat = Json.format[MailingListExtractReportItem]
}