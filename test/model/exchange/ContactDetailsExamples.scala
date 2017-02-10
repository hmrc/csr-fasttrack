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

package model.exchange

import model.Commands.{ Address, ContactDetails }

object ContactDetailsExamples {
  val ContactDetails1 = ContactDetails(Some(UserIdExamples.userId1.toString()), "email1", Address("address1"), Some("AAA121"))
  val ContactDetails2 = ContactDetails(Some(UserIdExamples.userId2.toString()), "email2", Address("address2"), Some("AAA122"))
  val ContactDetails3 = ContactDetails(Some(UserIdExamples.userId3.toString()), "email3", Address("address3"), Some("AAA123"))
  val ContactDetails4 = ContactDetails(Some(UserIdExamples.userId4.toString()), "email4", Address("address4"), Some("AAA124"))
  val ContactDetails5 = ContactDetails(Some(UserIdExamples.userId5.toString()), "email5", Address("address5"), Some("AAA125"))
  val ContactDetailsList = List(ContactDetails1, ContactDetails2, ContactDetails3, ContactDetails4, ContactDetails5)
}
