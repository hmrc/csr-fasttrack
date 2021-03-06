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

package repositories.application

import common.Constants.{ No, Yes }
import model.Commands._
import model.{ ApplicationStatuses, PersistedObjects, ProgressStatuses }
import model.PersistedObjects.ContactDetails
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object Utils {
  def chooseOne[T](values: Seq[T]): T =
    values(Random.nextInt(values.size))
}

trait TestDataRepository {
  def createApplications(num: Int, onlyAwaitingAllocation: Boolean = false): Future[Unit]
}

trait TestDataContactDetailsRepository {
  def createContactDetails(num: Int): Future[Unit]
}

class TestDataContactDetailsMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[ContactDetails, BSONObjectID](CollectionNames.CONTACT_DETAILS, mongo,
      PersistedObjects.Implicits.contactDetailsFormats, ReactiveMongoFormats.objectIdFormats) with TestDataContactDetailsRepository {
  import Utils.chooseOne

  val postcodes = Seq("AB1 3CD", "BC1 2DE", "CD28 7EF", "DE23 8FG")

  override def createContactDetails(num: Int): Future[Unit] = Future.successful {
    for (i <- 0 until num) {
      createSingleContactDetail(i)
    }
  }

  private def createSingleContactDetail(id: Int): Future[Unit] = {

    val contactDetails = ContactDetails(
      outsideUk = false, Address("1st High Street"), Some(chooseOne(postcodes)), None, s"test_$id@test.com", Some("123456789")
    )
    val contactDetailsBson = BSONDocument("$set" -> BSONDocument(
      "contact-details" -> contactDetails
    ))

    collection.update(BSONDocument("userId" -> id.toString), contactDetailsBson, upsert = true) map (_ => ())
  }
}

class TestDataMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[ContactDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
      PersistedObjects.Implicits.contactDetailsFormats, ReactiveMongoFormats.objectIdFormats) with TestDataRepository {
  import Utils.chooseOne

  val applicationStatuses = Seq(ApplicationStatuses.Created, ApplicationStatuses.InProgress, ApplicationStatuses.Submitted,
    ApplicationStatuses.Withdrawn)

  val firstNames = Seq("John", "Chris", "James", "Paul")
  val lastNames = Seq("Clerk", "Smith", "Gulliver", "Swift")
  val preferredName = Seq("Superman", "Batman", "Spiderman", "Wolverine", "Hulk")

  val locationsAndRegions = Seq("Bath" -> "London", "Blackburn" -> "London", "Bournemouth" -> "London", "Brighton" -> "London",
    "Bromley" -> "London", "Bristol" -> "London", "Northern Ireland" -> "London", "Cambridge" -> "London", "Motherwell" -> "Newcastle",
    "Newcastle upon Tyne" -> "Newcastle", "Nottingham" -> "Newcastle", "Outer Hebrides" -> "Newcastle", "Paisley" -> "Newcastle",
    "Perth" -> "Newcastle", "Peterborough" -> "Newcastle", "Sheffield" -> "Newcastle", "St Albans" -> "Newcastle")

  val dateOfBirth = Seq(LocalDate.parse("1980-12-12"), LocalDate.parse("1981-12-12"),
    LocalDate.parse("1982-12-12"), LocalDate.parse("1983-12-12"), LocalDate.parse("1984-12-12"), LocalDate.parse("1985-12-12"),
    LocalDate.parse("1987-12-12"), LocalDate.parse("1987-12-12"))

  override def createApplications(num: Int, onlyAwaitingAllocation: Boolean = false): Future[Unit] =
    Future.sequence(
      (0 until num).map { i => createSingleApplication(i, onlyAwaitingAllocation) }
    ).map(_ => ())

  private def createSingleApplication(id: Int, onlyAwaitingAllocation: Boolean = false): Future[Unit] = {

    val document = buildSingleApplication(id, onlyAwaitingAllocation)

    collection.update(BSONDocument("userId" -> id.toString), document, upsert = true) map (_ => ())
  }

  private def createProgress(
    personalDetails: Option[BSONDocument],
    frameworks: Option[BSONDocument],
    assistance: Option[BSONDocument],
    isSubmitted: Option[Boolean],
    isWithdrawn: Option[Boolean]
  ) = {
    var progress = BSONDocument()

    progress = personalDetails.map(_ => progress ++ (ProgressStatuses.PersonalDetailsCompletedProgress.name -> true)).getOrElse(progress)
    progress = frameworks.map(_ => progress ++ (ProgressStatuses.SchemesPreferencesCompletedProgress.name -> true)).getOrElse(progress)
    progress = frameworks.map(_ => progress ++ (ProgressStatuses.SchemeLocationsCompletedProgress.name -> true)).getOrElse(progress)
    progress = assistance.map(_ => progress ++ (ProgressStatuses.AssistanceDetailsCompletedProgress.name -> true)).getOrElse(progress)
    progress = isSubmitted.map(_ => progress ++ (ProgressStatuses.SubmittedProgress.name -> true)).getOrElse(progress)
    progress = isWithdrawn.map(_ => progress ++ (ProgressStatuses.WithdrawnProgress.name -> true)).getOrElse(progress)

    progress
  }

  private def buildSingleApplication(id: Int, onlyAwaitingAllocation: Boolean = false) = {
    val personalDetails = createPersonalDetails(id, onlyAwaitingAllocation)
    val frameworks = createLocations(id, onlyAwaitingAllocation)
    val assistanceDetails = createAssistanceDetails(id, onlyAwaitingAllocation)
    val onlineTests = createOnlineTests(id, onlyAwaitingAllocation)
    val submitted = isSubmitted(id)(personalDetails, frameworks, assistanceDetails)
    val withdrawn = isWithdrawn(id)(personalDetails, frameworks, assistanceDetails)

    val progress = createProgress(personalDetails, frameworks, assistanceDetails, submitted, withdrawn)

    val applicationStatus = if (onlyAwaitingAllocation) ApplicationStatuses.AwaitingAllocationNotified else chooseOne(applicationStatuses)
    var document = BSONDocument(
      "applicationId" -> id.toString,
      "userId" -> id.toString,
      "frameworkId" -> "FastTrack-2015",
      "applicationStatus" -> applicationStatus
    )
    document = buildDocument(document)(personalDetails.map(d => "personal-details" -> d))
    document = buildDocument(document)(frameworks.map(d => "assessment-centre-indicator" -> d))
    document = buildDocument(document)(assistanceDetails.map(d => "assistance-details" -> d))
    document = buildDocument(document)(onlineTests.map(d => "online-tests" -> d))
    document = document ++ ("progress-status" -> progress)

    document

  }

  private def buildDocument(document: BSONDocument)(f: Option[(String, BSONDocument)]) = {
    f.map(d => document ++ d).getOrElse(document)
  }

  private def createAssistanceDetails(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 7 == 0 && !buildAlways => None
    case _ =>
      Some(BSONDocument(
        "hasDisability" -> "No",
        "guaranteedInterview" -> true,
        "needsSupportForOnlineAssessment" -> true,
        "needsSupportForOnlineAssessmentDescription" -> "needsSupportForOnlineAssessment description",
        "needsSupportAtVenue" -> true,
        "needsSupportAtVenueDescription" -> "needsSupportAtVenue description",
        "typeOfAdjustments" -> BSONArray("Time extension", "Braille test paper", "Stand up and move around", "Other")
      ))
  }

  private def createPersonalDetails(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 5 == 0 && !buildAlways => None
    case _ =>
      Some(BSONDocument(
        "firstName" -> chooseOne(firstNames),
        "lastName" -> chooseOne(lastNames),
        "preferredName" -> chooseOne(preferredName),
        "dateOfBirth" -> chooseOne(dateOfBirth),
        "aLevel" -> true,
        "stemLevel" -> true,
        "civilServant" -> false
      ))
  }

  private def createLocations(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 11 == 0 && !buildAlways => None
    case _ =>
      val regionAndCentre = chooseOne(locationsAndRegions)
      Some(BSONDocument(
        "area" -> regionAndCentre._1,
        "assessmentCentre" -> regionAndCentre._2
      ))
  }

  private def createOnlineTests(id: Int, buildAlways: Boolean = false) = id match {
    case x if x % 12 == 0 && !buildAlways => None
    case _ =>
      Some(BSONDocument(
        "cubiksUserId" -> 117344,
        "token" -> "32cf213b-697e-414b-a954-7d92f3e3e682",
        "onlineTestUrl" -> "https://uat.cubiksonline.com/CubiksOnline/Standalone/PEnterFromExt.aspx?key=fc831fb6-1cb7-4c6d-9e9b".concat(
          "-1e508db76711&hash=A07B3B39025E6F34639E5CEA70A6F668402E4673"
        ),
        "invitationDate" -> DateTime.now().minusDays(5),
        "expirationDate" -> DateTime.now().plusDays(2),
        "participantScheduleId" -> 149245,
        "completionDate" -> DateTime.now()
      ))
  }

  private def isSubmitted(id: Int)(ps: Option[BSONDocument], fl: Option[BSONDocument], as: Option[BSONDocument]) = (ps, fl, as) match {
    case (Some(_), Some(_), Some(_)) if id % 2 == 0 => Some(true)
    case _ => None
  }

  private def isWithdrawn(id: Int)(ps: Option[BSONDocument], fl: Option[BSONDocument], as: Option[BSONDocument]) = (ps, fl, as) match {
    case (Some(_), Some(_), Some(_)) if id % 3 == 0 => Some(true)
    case _ => None
  }

  //scalastyle:off method.length
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
    appStatus: ApplicationStatuses.EnumVal = ApplicationStatuses.InProgress,
    progressStatusBSON: BSONDocument = buildProgressStatusBSON()) = {
    val application = BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument(
        "aLevel" -> true,
        "stemLevel" -> true,
        "civilServant" -> false
      ),
      "scheme-locations" -> BSONArray("2643743", "2643123"),
      "schemes" -> BSONArray("Commercial", "Business"),
      "assistance-details" -> BSONDocument(
        "hasDisability" -> "Yes",
        "hasDisabilityDescription" -> "I have one arm shorter",
        "needsSupportForOnlineAssessment" -> true,
        "needsSupportForOnlineAssessmentDescription" -> "I think 50% more time",
        "needsSupportAtVenue" -> true,
        "needsSupportAtVenueDescription" -> "I need coloured paper",
        "guaranteedInterview" -> false,
        "adjustmentsComment" -> "additional comments",
        "typeOfAdjustments" -> BSONArray(
          "onlineTestsTimeExtension",
          "onlineTestsOther",
          "assessmentCenterTimeExtension",
          "coloured paper",
          "braille test paper",
          "room alone",
          "rest breaks",
          "reader/assistant",
          "stand up and move around",
          "assessmentCenterOther"
        ),
        "adjustmentsConfirmed" -> true,
        "onlineTests" -> BSONDocument(
          "otherInfo" -> "other adjustments",
          "extraTimeNeededNumerical" -> 60,
          "extraTimeNeeded" -> 25
        ),
        "assessmentCenter" -> BSONDocument(
          "otherInfo" -> "Other assessment centre adjustment",
          "extraTimeNeeded" -> 30
        )
      ),
      "issue" -> "this candidate has changed the email"
    ) ++ progressStatusBSON

    collection.insert(application)
  }
  //scalastyle:on method.length

  //scalastyle:off line.size.limit
  private def buildProgressStatusBSON(statusesAndDates: Map[ProgressStatuses.EnumVal, DateTime] = Map((ProgressStatuses.AssistanceDetailsCompleted, DateTime.now))): BSONDocument = {
    val dates = statusesAndDates.foldLeft(BSONDocument()) { (doc, map) =>
      doc ++ BSONDocument(s"${map._1}" -> map._2)
    }

    val statuses = statusesAndDates.keys.foldLeft(BSONDocument()) { (doc, status) =>
      doc ++ BSONDocument(s"$status" -> true)
    }

    BSONDocument(
      "progress-status" -> statuses,
      "progress-status-timestamp" -> dates
    )
  }
  //scalastyle:on

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    ))
  }
}
