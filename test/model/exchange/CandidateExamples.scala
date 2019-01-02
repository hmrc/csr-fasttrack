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

package model.exchange

import connectors.ExchangeObjects.Candidate

object CandidateExamples {
  val Candidate1 = Candidate("FirstName1", "LastName1", Some("PrefferredName1"), "email1@localhost", UserIdExamples.userId1.toString())
  val Candidate2 = Candidate("FirstName2", "LastName2", Some("PrefferredName2"), "email2@localhost", UserIdExamples.userId2.toString())
  val Candidate3 = Candidate("FirstName3", "LastName3", Some("PrefferredName3"), "email3@localhost", UserIdExamples.userId3.toString())
  val Candidate4 = Candidate("FirstName4", "LastName4", Some("PrefferredName4"), "email4@localhost", UserIdExamples.userId4.toString())
  val Candidate5 = Candidate("FirstName5", "LastName5", Some("PrefferredName5"), "email5@localhost", UserIdExamples.userId5.toString())
  val Candidates = List(Candidate1, Candidate2, Candidate3, Candidate4, Candidate5)
}
