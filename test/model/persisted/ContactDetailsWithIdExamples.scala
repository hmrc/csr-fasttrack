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

package model.persisted

import model.Commands.Address
import model.PersistedObjects.ContactDetailsWithId
import model.exchange.UserIdExamples

object ContactDetailsWithIdExamples {
  val ContactDetails1 = ContactDetailsWithId(UserIdExamples.userId1.toString, false, Address("address1"), Some("AAA121"),
    None, "email1", Some("555555551"))
  val ContactDetails2 = ContactDetailsWithId(UserIdExamples.userId2.toString, false, Address("address2"), Some("AAA122"),
    None, "email1", Some("555555552"))
  val ContactDetails3 = ContactDetailsWithId(UserIdExamples.userId3.toString, false, Address("address3"), Some("AAA123"),
    None, "email1", Some("555555553"))
  val ContactDetails4 = ContactDetailsWithId(UserIdExamples.userId4.toString, false, Address("address4"), Some("AAA124"),
    None, "email1", Some("555555554"))
  val ContactDetails5 = ContactDetailsWithId(UserIdExamples.userId5.toString, false, Address("address5"), Some("AAA125"),
    None, "email1", Some("555555555"))

  val ContactDetailsList = List(ContactDetails1, ContactDetails2, ContactDetails3, ContactDetails4, ContactDetails5)
}
