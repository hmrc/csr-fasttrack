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

package repositories.application

import common.FutureEx
import model.Commands.{ CandidateDetailsReportItem, CsvExtract }
import model.Scheme
import model.Scheme.Scheme
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection
import repositories.{ CollectionNames, LocationSchemeRepository }
import reactivemongo.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class PreviousYearCandidatesDetailsRepository(locationSchemeRepository: LocationSchemeRepository) {

  val schemesHeader: String = Scheme.AllSchemes.zipWithIndex.map { case (idx, value) =>
      s"Scheme $idx"
  }.mkString(",")

  lazy val locationSize: Future[Int] = locationSchemeRepository.getSchemesAndLocations.map(_.size)

  lazy val locationHeader: Future[String] = locationSchemeRepository.getSchemesAndLocations.map { locationSchemes =>
    locationSchemes.zipWithIndex.map { case (idx, _) =>
      s"Location $idx"
    }.mkString(",")
  }

  lazy val applicationDetailsHeader = {
    locationHeader.map { locHeader =>
      "FrameworkId,Application status,First name,Last name,Preferred name,Date of birth," +
        "A level,Stem level,Civil Servant,Civil Service Department," + schemesHeader + "," + locationHeader + "," +
        "Has disability,Disability Description,Guaranteed Interview,Needs support for online assessment," +
        "support for online assessment description,Needs support at venue,support at venue description"
    }
  }

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Phone"

  val questionnaireDetailsHeader = "What is your gender identity?,What is your sexual orientation?,What is your ethnic group?," +
    "Between the ages of 11 to 16 in which school did you spend most of your education?," +
    "Between the ages of 16 to 18 in which school did you spend most of your education?," +
    "What was your home postcode when you were 14?,During your school years were you at any time eligible for free school meals?," +
    "Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?,Parent/guardian work status," +
    "Which type of occupation did they have?,Did they work as an employee or were they self-employed?," +
    "Which size would best describe their place of work?,Did they supervise any other employees?"

  val onlineTestReportHeader = "Competency status,Competency norm,Competency tscore,Competency percentile,Competency raw,Competency sten," +
    "Numerical status,Numerical norm,Numerical tscore,Numerical percentile,Numerical raw,Numerical sten," +
    "Verbal status,Verbal norm,Verbal tscore,Verbal percentile,Verbal raw,Verbal sten," +
    "Situational status,Situational norm,Situational tscore,Situational percentile,Situational raw,Situational sten"

  val assessmentCenterDetailsHeader = "Assessment venue,Assessment date,Assessment session,Assessment slot,Assessment confirmed"

  val assessmentScoresHeader = "Assessment attended,Assessment incomplete,Leading and communicating interview," +
    "Leading and communicating group exercise,Leading and communicating written exercise,Delivering at pace interview," +
    "Delivering at pace group exercise,Delivering at pace written exercise,Making effective decisions interview," +
    "Making effective decisions group exercise,Making effective decisions written exercise,Changing and improving interview," +
    "Changing and improving group exercise,Changing and improving written exercise,Building capability for all interview," +
    "Building capability for all group exercise,Building capability for all written exercise,Motivation fit interview," +
    "Motivation fit group exercise,Motivation fit written exercise,Interview feedback,Group exercise feedback," +
    "Written exercise feedback"

  def applicationDetailsStream(): Future[Enumerator[CandidateDetailsReportItem]]

  def findContactDetails(): Future[CsvExtract[String]]

  def findOnlineTestReports(): Future[CsvExtract[String]]

  def findAssessmentCentreDetails(): Future[CsvExtract[String]]

  def findAssessmentScores(): Future[CsvExtract[String]]

  def findQuestionnaireDetails(): Future[CsvExtract[String]]

}

class PreviousYearCandidatesDetailsMongoRepository(locationSchemeRepo: LocationSchemeRepository)(implicit mongo: () => DB)
  extends PreviousYearCandidatesDetailsRepository(locationSchemeRepo) {

  val applicationDetailsCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_2017)

  val contactDetailsCollection = mongo().collection[JSONCollection](CollectionNames.CONTACT_DETAILS_2017)

  val questionnaireCollection = mongo().collection[JSONCollection](CollectionNames.QUESTIONNAIRE_2017)

  val onlineTestReportsCollection = mongo().collection[JSONCollection](CollectionNames.ONLINE_TEST_REPORT_2017)

  val assessmentCentersCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT_2017)

  val assessmentScoresCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT_SCORES_2017)



  override def applicationDetailsStream(): Future[Enumerator[CandidateDetailsReportItem]] = {
    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

      locationSize.map { locSize =>
      applicationDetailsCollection.find(Json.obj(), projection)
        .cursor[BSONDocument](ReadPreference.primaryPreferred)
        .enumerate().map { doc =>
          val csvContent = makeRow(
            List(doc.getAs[String]("frameworkId")) :::
              List(doc.getAs[String]("applicationStatus")) :::
              personalDetails(doc) :::
              schemePreferences(doc).padTo(Scheme.AllSchemes.size, None) :::
              locationPreferences(doc).padTo(locSize, None) :::
              assistanceDetails(doc): _*
          )
          CandidateDetailsReportItem(
            doc.getAs[String]("applicationId").getOrElse(""),
            doc.getAs[String]("userId").getOrElse(""), csvContent
          )
        }
      }
  }

  override def findContactDetails(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    contactDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val contactDetails = doc.getAs[BSONDocument]("contact-details")
          val address = contactDetails.flatMap(_.getAs[BSONDocument]("address"))
          val csvRecord = makeRow(
            contactDetails.flatMap(_.getAs[String]("email")),
            address.flatMap(_.getAs[String]("line1")),
            address.flatMap(_.getAs[String]("line2")),
            address.flatMap(_.getAs[String]("line3")),
            address.flatMap(_.getAs[String]("line4")),
            contactDetails.flatMap(_.getAs[String]("postCode")),
            contactDetails.flatMap(_.getAs[String]("phone"))
          )
          doc.getAs[String]("userId").getOrElse("") -> csvRecord
        }
        CsvExtract(contactDetailsHeader, csvRecords.toMap)
      }
  }

  def findQuestionnaireDetails(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    def getAnswer(question: String, doc: Option[BSONDocument]) = {
      val questionDoc = doc.flatMap(_.getAs[BSONDocument](question))
      val isUnknown = questionDoc.flatMap(_.getAs[Boolean]("unknown")).contains(true)
      isUnknown match {
        case true => Some("Unknown")
        case _ => questionDoc.flatMap(q => q.getAs[String]("answer")
          .orElse(q.getAs[String]("otherDetails")))
      }
    }

    questionnaireCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val questions = doc.getAs[BSONDocument]("questions")
          val csvRecord = makeRow(
            getAnswer("What is your gender identity?", questions),
            getAnswer("What is your sexual orientation?", questions),
            getAnswer("What is your ethnic group?", questions),
            getAnswer("Between the ages of 11 to 16, in which school did you spend most of your education?", questions),
            getAnswer("Between the ages of 16 to 18, in which school did you spend most of your education?", questions),
            getAnswer("What was your home postcode when you were 14?", questions),
            getAnswer("During your school years, were you at any time eligible for free school meals?", questions),
            getAnswer("Did any of your parent(s) or guardian(s) complete a university degree course or equivalent?", questions),
            getAnswer("Parent/guardian work status", questions),
            getAnswer("Which type of occupation did they have?", questions),
            getAnswer("Did they work as an employee or were they self-employed?", questions),
            getAnswer("Which size would best describe their place of work?", questions),
            getAnswer("Did they supervise any other employees?", questions)
          )
          doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(questionnaireDetailsHeader, csvRecords.toMap)
      }
  }

  override def findOnlineTestReports(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    def onlineTestScore(test: String, doc: BSONDocument) = {
      val scoreDoc = doc.getAs[BSONDocument](test)
      scoreDoc.flatMap(_.getAs[String]("status")) ::
        scoreDoc.flatMap(_.getAs[String]("norm")) ::
        scoreDoc.flatMap(_.getAs[Double]("tScore").map(_.toString)) ::
        scoreDoc.flatMap(_.getAs[Double]("percentile").map(_.toString)) ::
        scoreDoc.flatMap(_.getAs[Double]("raw").map(_.toString)) ::
        scoreDoc.flatMap(_.getAs[Double]("sten").map(_.toString)) ::
        Nil
    }

    onlineTestReportsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map {
          doc =>
            val csvRecord = makeRow(
              onlineTestScore("competency", doc) :::
                onlineTestScore("numerical", doc) :::
                onlineTestScore("verbal", doc) :::
                onlineTestScore("situational", doc): _*
            )
            doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(onlineTestReportHeader, csvRecords.toMap)
      }
  }

  override def findAssessmentCentreDetails(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    assessmentCentersCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map {
          doc =>
            val csvRecord = makeRow(
              doc.getAs[String]("venue"),
              doc.getAs[String]("date"),
              doc.getAs[String]("session"),
              doc.getAs[Int]("slot").map(_.toString),
              doc.getAs[Boolean]("confirmed").map(_.toString)
            )
            doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(assessmentCenterDetailsHeader, csvRecords.toMap)
      }
  }

  override def findAssessmentScores(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    assessmentScoresCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val csvRecord = makeRow(assessmentScores(doc): _*)
          doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(assessmentScoresHeader, csvRecords.toMap)
      }
  }

  private def assessmentScores(doc: BSONDocument) = {
    val leadingAndCommunicating = doc.getAs[BSONDocument]("leadingAndCommunicating")
    val deliveringAtPace = doc.getAs[BSONDocument]("deliveringAtPace")
    val makingEffectiveDecisions = doc.getAs[BSONDocument]("makingEffectiveDecisions")
    val changingAndImproving = doc.getAs[BSONDocument]("changingAndImproving")
    val buildingCapabilityForAll = doc.getAs[BSONDocument]("buildingCapabilityForAll")
    val motivationFit = doc.getAs[BSONDocument]("motivationFit")
    val feedback = doc.getAs[BSONDocument]("feedback")
    List(
      doc.getAs[Boolean]("attendancy").map(_.toString),
      doc.getAs[Boolean]("assessmentIncomplete").map(_.toString),
      leadingAndCommunicating.flatMap(_.getAs[Double]("interview").map(_.toString)),
      leadingAndCommunicating.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      leadingAndCommunicating.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      deliveringAtPace.flatMap(_.getAs[Double]("interview").map(_.toString)),
      deliveringAtPace.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      deliveringAtPace.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      makingEffectiveDecisions.flatMap(_.getAs[Double]("interview").map(_.toString)),
      makingEffectiveDecisions.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      makingEffectiveDecisions.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      changingAndImproving.flatMap(_.getAs[Double]("interview").map(_.toString)),
      changingAndImproving.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      changingAndImproving.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      buildingCapabilityForAll.flatMap(_.getAs[Double]("interview").map(_.toString)),
      buildingCapabilityForAll.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      buildingCapabilityForAll.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      motivationFit.flatMap(_.getAs[Double]("interview").map(_.toString)),
      motivationFit.flatMap(_.getAs[Double]("groupExercise").map(_.toString)),
      motivationFit.flatMap(_.getAs[Double]("writtenExercise").map(_.toString)),
      feedback.flatMap(_.getAs[String]("interviewFeedback").map(_.toString)),
      feedback.flatMap(_.getAs[String]("groupExerciseFeedback").map(_.toString)),
      feedback.flatMap(_.getAs[String]("writtenExerciseFeedback").map(_.toString))
    )
  }

  private def schemePreferences(doc: BSONDocument): List[Option[String]] = {
    doc.getAs[List[String]]("schemes").get.map(Some(_))
  }

  private def locationPreferences(doc: BSONDocument): List[Option[String]] = doc.getAs[List[String]]("scheme-locations").get.map(Some(_))

  private def assistanceDetails(doc: BSONDocument) = {
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details")
    List(
      assistanceDetails.flatMap(_.getAs[String]("hasDisability")),
      assistanceDetails.flatMap(_.getAs[String]("hasDisabilityDescription")),
      assistanceDetails.flatMap(_.getAs[String]("guaranteedInterview")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportForOnlineAssessment")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportForOnlineAssessmentDescription")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportAtVenue")),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportAtVenueDescription"))
    )
  }

  private def personalDetails(doc: BSONDocument): List[Option[String]] = {
    val personalDetails = doc.getAs[BSONDocument]("personal-details")
    List(
      personalDetails.flatMap(_.getAs[String]("firstName")),
      personalDetails.flatMap(_.getAs[String]("lastName")),
      personalDetails.flatMap(_.getAs[String]("preferredName")),
      personalDetails.flatMap(_.getAs[String]("dateOfBirth")),
      personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(_.toString)),
      personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(_.toString)),
      personalDetails.flatMap(_.getAs[Boolean]("civilServant").map(_.toString)),
      personalDetails.flatMap(_.getAs[String]("department").map(_.toString))
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

}
