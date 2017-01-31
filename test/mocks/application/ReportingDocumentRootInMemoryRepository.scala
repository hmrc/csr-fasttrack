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

package mocks.application

import common.Constants.{ No, Yes }
import model.Commands._
import model.ReportExchangeObjects._
import model.UniqueIdentifier
import org.joda.time.LocalDate
import repositories.application.ReportingRepository

import scala.collection.mutable
import scala.concurrent.Future
object ReportingDocumentRootInMemoryRepository extends ReportingDocumentRootInMemoryRepository

/**
  * @deprecated Please use Mockito
  */
class ReportingDocumentRootInMemoryRepository extends ReportingRepository {

  val inMemoryRepo = new mutable.HashMap[String, ApplicationResponse]

  override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val app1 = ("user1", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      Some("user1@email.com"), None, Some("location1"), Some("location1Scheme1"), Some("location1Scheme2"), Some("location2"),
      Some("location2Scheme1"), Some("location2Scheme2"), None, Some("2016-12-25 13:00:14")))
    val app2 = ("user2", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      None, None, None, None, None, None, None, None, None, None))
    Future.successful(app1 :: app2 :: Nil)
  }

  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]] = Future.successful(List(
    CandidateProgressReportItem(UniqueIdentifier.randomUniqueIdentifier, Some("SUBMITTED"), Some("London"), Some("Business"),
      None, None, None, None, Some(Yes), Some(Yes), Some(Yes), Some(No), Some(No), Some(No), Some(No), None),
    CandidateProgressReportItem(UniqueIdentifier.randomUniqueIdentifier, Some("IN_PROGRESS"), Some("London"), Some("Business"),
      None, None, None, None, Some(Yes), Some(Yes), Some(Yes), Some(No), Some(No), Some(No), Some(No), None),
    CandidateProgressReportItem(UniqueIdentifier.randomUniqueIdentifier, Some("SUBMITTED"), Some("London"), Some("Business"),
      None, None, None, None, Some(Yes), Some(Yes), Some(Yes), Some(No), Some(No), Some(No), Some(No), None)
  ))

  override def applicationsForCandidateProgressReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] = Future.successful(List(
    ApplicationForCandidateProgressReport(UniqueIdentifier.randomUniqueIdentifier, UniqueIdentifier.randomUniqueIdentifier, Some("SUBMITTED"),
      List.empty, List.empty, None, None, None, None, None),
    ApplicationForCandidateProgressReport(UniqueIdentifier.randomUniqueIdentifier, UniqueIdentifier.randomUniqueIdentifier, Some("IN_PROGRESS"),
      List.empty, List.empty, None, None, None, None, None),
    ApplicationForCandidateProgressReport(UniqueIdentifier.randomUniqueIdentifier, UniqueIdentifier.randomUniqueIdentifier, Some("SUBMITTED"),
      List.empty, List.empty, None, None, None, None, None)
  ))

  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReport]] =
    Future.successful(
      List(
        AdjustmentReport("1", Some("John"), Some("Smith"), Some("Spiderman"), None, None, Some("Some adjustments"), Some(Yes), Some(Yes)),
        AdjustmentReport("2", Some("James"), Some("Jones"), Some("Batman"), None, None, Some("Some adjustments"), Some(Yes), Some(No)),
        AdjustmentReport("3", Some("Kathrine"), Some("Jones"), Some("Supergirl"), None, None, Some("Some adjustments"), Some(Yes), Some(No))
      )
    )

  override def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] =
    Future.successful(
      List(
        CandidateAwaitingAllocation("1", "John", "Smith", "Spiderman", "London", Some("Some adjustments"), new LocalDate(1988, 1, 21)),
        CandidateAwaitingAllocation("2", "James", "Jones", "Batman", "Bournemouth", Some("Some adjustments"), new LocalDate(1992, 11, 30)),
        CandidateAwaitingAllocation("3", "Katherine", "Jones", "Supergirl", "Queer Camel", None, new LocalDate(1990, 2, 12))
      )
    )

  override def candidateProgressReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] = ???

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] = ???

  override def allApplicationAndUserIds(frameworkId: String): Future[List[ApplicationUserIdReport]] = ???

  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] = ???
}
