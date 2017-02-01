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
import model.Adjustments
import model.Commands._
import model.report.AdjustmentReportItem
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

  override def overallReportNotWithdrawn(frameworkId: String): Future[List[Report]] = overallReport(frameworkId)

  override def overallReport(frameworkId: String): Future[List[Report]] = Future.successful(List(
    Report("123", Some("SUBMITTED"), Some("London"), Some("Business"), None, None, None, None,
      Some(Yes), Some(Yes), Some(Yes), Some(No), Some(No), Some(No), Some(No), None),
    Report("456", Some("IN_PROGRESS"), Some("London"), Some("Business"), None, None, None, None,
      Some(Yes), Some(Yes), Some(Yes), Some(No), Some(No), Some(No), Some(No), None),
    Report("789", Some("SUBMITTED"), Some("London"), Some("Business"), None, None, None, None,
      Some(Yes), Some(Yes), Some(Yes), Some(No), Some(No), Some(No), Some(No), None)
  ))

  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] =
    Future.successful(
      List(
        AdjustmentReportItem("1", Some("123"), Some("John"), Some("Smith"), Some("Spiderman"),
          None, None, Some("Yes"), Some("SUBMITTED"), Some("time"), Some("help"), Some("Yes"),
          Some(Adjustments(None,None,None,None)), Some("comment")),
        AdjustmentReportItem("2", Some("123"), Some("Mary"), Some("Smith"), Some("Spiderwoman"),
          None, None, Some("Yes"), Some("SUBMITTED"), Some("time"), Some("help"), Some("Yes"),
          Some(Adjustments(None,None,None,None)), Some("comment")),
        AdjustmentReportItem("3", Some("123"), Some("Peter"), Some("Smith"), Some("Spiderchild"),
          None, None, Some("Yes"), Some("SUBMITTED"), Some("time"), Some("help"), Some("Yes"),
          Some(Adjustments(None,None,None,None)), Some("comment"))
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

  override def overallReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] = ???

  override def applicationsWithAssessmentScoresAccepted(frameworkId: String): Future[List[ApplicationPreferences]] = ???

  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = ???

  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] = ???
}
