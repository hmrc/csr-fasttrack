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

import java.io

import common.FutureEx
import model.Commands.{ CandidateDetailsReportItem, CsvExtract }
import model.{ AssessmentCentreIndicator, Scheme }
import model.Scheme.Scheme
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection
import repositories.{ CollectionNames, LocationSchemeRepository, LocationSchemes }
import reactivemongo.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class PreviousYearCandidatesDetailsRepository(locationSchemeRepository: LocationSchemeRepository) {

  val schemesHeader: String = Scheme.AllSchemes.zipWithIndex.map { case (_, idx) =>
      s"Scheme ${idx + 1}"
  }.mkString(",")

  lazy val locationSize: Future[Int] = locationSchemeRepository.getSchemesAndLocations.map(_.size)

  lazy val locationHeader: Future[String] = locationSchemeRepository.getSchemesAndLocations.map { locationSchemes =>
    locationSchemes.zipWithIndex.map { case (_, idx) =>
      s"Location ${idx + 1}"
    }.mkString(",")
  }

  lazy val applicationDetailsHeader = {
    locationHeader.map { locHeader =>
      "FrameworkId,Application status,First name,Last name,Preferred name,Date of birth," +
        "A level,Stem level,Civil Servant,Civil Service Department," + schemesHeader + "," + locHeader + "," +
        "Has disability,Disability Description,GIS,Needs support for online assessment," +
        "Support for online assessment description,Needs support at venue,Support at venue description," +
        "Assessment centre area,Assessment Centre,Assessment centre indicator version"
    }
  }

  val mediaHeader = "Referred By Media"

  val contactDetailsHeader = "Email,Address line1,Address line2,Address line3,Address line4,Postcode,Outside UK,Country,Phone"

  val questionnaireDetailsHeader = "What is your gender identity?,What is your sexual orientation?,What is your ethnic group?," +
    "Did you live in the UK between the ages of 14 and 18?," +
  "What was your home postcode when you were 14?," +
  "Aged 14 to 16 what was the name of your school?," +
  "What type of school was this?," +
  "Aged 16 to 18 what was the name of your school or college? (if applicable)," +
  "Were you at any time eligible for free school meals?," +
  "Do you have a parent or guardian that has completed a university degree course or equivalent?," +
  "\"When you were 14, what kind of work did your highest-earning parent or guardian do?\"," +
  "Did they work as an employee or were they self-employed?," +
  "Which size would best describe their place of work?," +
  "Did they supervise employees?"

  val onlineTestReportHeader = "Competency status,Competency norm,Competency tscore,Competency percentile,Competency raw,Competency sten," +
    "Numerical status,Numerical norm,Numerical tscore,Numerical percentile,Numerical raw,Numerical sten," +
    "Verbal status,Verbal norm,Verbal tscore,Verbal percentile,Verbal raw,Verbal sten," +
    "Situational status,Situational norm,Situational tscore,Situational percentile,Situational raw,Situational sten"

  val assessmentCentreDetailsHeader = "Assessment venue,Assessment date,Assessment session,Assessment slot,Assessment confirmed"

  def genAssessmentScoresHeaders(exercise: String) = {
    List(s"$exercise - attended",
    s"$exercise - incomplete",
    s"$exercise - last updated by",
    s"$exercise - version",
    s"$exercise - submitted date",
    s"$exercise - saved date",
    s"$exercise - feedback",
    s"$exercise - Motivation fit score",
    s"$exercise - Building capability for all score",
    s"$exercise - Changing and improving score",
    s"$exercise - Making Effective Decisions score",
    s"$exercise - Delivering at pace score",
      s"$exercise - Collaborating and partnering score",
      s"$exercise - Leading and communicating score"
    ).mkString(",")
  }

  val assessmentScoresHeader =
    genAssessmentScoresHeaders("Assessor Interview") + "," +
    genAssessmentScoresHeaders("Assessor Group Exercise") + "," +
    genAssessmentScoresHeaders("Assessor Written Exercise") +   "," +
    genAssessmentScoresHeaders("QAC/Final Interview") + "," +
    genAssessmentScoresHeaders("QAC/Final Group Exercise") + "," +
    genAssessmentScoresHeaders("QAC/Final Written Exercise")


  def applicationDetailsStream(): Future[Enumerator[CandidateDetailsReportItem]]

  def findMedia(): Future[CsvExtract[String]]

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

  val mediaCollection = mongo().collection[JSONCollection](CollectionNames.MEDIA_2017)

  val questionnaireCollection = mongo().collection[JSONCollection](CollectionNames.QUESTIONNAIRE_2017)

  val onlineTestReportsCollection = mongo().collection[JSONCollection](CollectionNames.ONLINE_TEST_REPORT_2017)

  val assessmentCentresCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT_2017)

  val assessmentScoresCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_ASSESSMENT_SCORES_2017)

  override def applicationDetailsStream(): Future[Enumerator[CandidateDetailsReportItem]] = {
    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

      locationSize.flatMap { locSize =>
        locationSchemeRepo.getSchemesAndLocations.map { schemesAndLocations =>
          applicationDetailsCollection.find(Json.obj(), projection)
            .cursor[BSONDocument](ReadPreference.primaryPreferred)
            .enumerate().map { doc =>
              val csvContent = makeRow(
                  List(doc.getAs[String]("frameworkId")) :::
                  List(doc.getAs[String]("applicationStatus")) :::
                  personalDetails(doc) :::
                  schemePreferences(doc).padTo(Scheme.AllSchemes.size, None) :::
                  locationPreferences(schemesAndLocations, doc).padTo(locSize, None) :::
                  assistanceDetails(doc) :::
                  assessmentCentreIndicator(doc): _*
              )
            CandidateDetailsReportItem(
              doc.getAs[String]("applicationId").getOrElse(""),
              doc.getAs[String]("userId").getOrElse(""), csvContent
            )
          }
        }
      }
  }

  override def findMedia(): Future[CsvExtract[String]] = {
    val projection = Json.obj("_id" -> 0)

    mediaCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val csvRecord = makeRow(
            doc.getAs[String]("media")
          )
          doc.getAs[String]("userId").getOrElse("") -> csvRecord
        }
      CsvExtract(mediaHeader, csvRecords.toMap)
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
            contactDetails.flatMap(cd => mapYesNo(cd.getAs[Boolean]("outsideUk"))),
            contactDetails.flatMap(_.getAs[String]("country")),
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
          getAnswer("Did you live in the UK between the ages of 14 and 18?", questions),
          getAnswer("What was your home postcode when you were 14?", questions),
          getAnswer("Aged 14 to 16 what was the name of your school?", questions),
          getAnswer("Which type of school was this?", questions),
          getAnswer("Aged 16 to 18 what was the name of your school or college? (if applicable)", questions),
          getAnswer("Were you at any time eligible for free school meals?", questions),
          getAnswer("Do you have a parent or guardian that has completed a university degree course or equivalent?", questions),
          getAnswer("When you were 14, what kind of work did your highest-earning parent or guardian do?", questions),
          getAnswer("Did they work as an employee or were they self-employed?", questions),
          getAnswer("Which size would best describe their place of work?", questions),
          getAnswer("Did they supervise employees?", questions)
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
        val csvRecords = docs.map { doc =>
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

    assessmentCentresCollection.find(Json.obj(), projection)
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
        CsvExtract(assessmentCentreDetailsHeader, csvRecords.toMap)
      }
  }

  override def findAssessmentScores(): Future[CsvExtract[String]] = {

    val projection = Json.obj("_id" -> 0)

    assessmentScoresCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map { docs =>
        val csvRecords = docs.map { doc =>
          val csvRecord = makeRow(
            assessmentScores(doc, "interview", None) :::
            assessmentScores(doc, "groupExercise", None) :::
            assessmentScores(doc, "writtenExercise", None) :::
            assessmentScores(doc, "interview", Some("reviewer")) :::
            assessmentScores(doc, "groupExercise", Some("reviewer")) :::
            assessmentScores(doc, "writtenExercise", Some("reviewer")): _*
          )
          doc.getAs[String]("applicationId").getOrElse("") -> csvRecord
        }
        CsvExtract(assessmentScoresHeader, csvRecords.toMap)
      }
  }

  private def assessmentScores(doc: BSONDocument, exercise: String, parentKey: Option[String]): List[Option[String]] = {
    import repositories.BSONDateTimeHandler

    val baseDoc = parentKey.map(key => doc.getAs[BSONDocument](key)).getOrElse(Some(doc)).flatMap(_.getAs[BSONDocument](exercise))

    List(
      baseDoc.flatMap(bd => mapYesNo(bd.getAs[Boolean]("attended"))),
      baseDoc.flatMap(bd => mapYesNo(bd.getAs[Boolean]("assessmentIncomplete"))),
      baseDoc.flatMap(_.getAs[String]("updatedBy").map(_.toString)),
      baseDoc.flatMap(_.getAs[String]("version").map(_.toString)),
      baseDoc.flatMap(_.getAs[DateTime]("submittedDate").map(_.toString)),
      baseDoc.flatMap(_.getAs[DateTime]("savedDate").map(_.toString)),
      baseDoc.flatMap(_.getAs[String]("feedback")),
      baseDoc.flatMap(_.getAs[Double]("motivationFit").map(_.toString)),
      baseDoc.flatMap(_.getAs[Double]("buildingCapabilityForAll").map(_.toString)),
      baseDoc.flatMap(_.getAs[Double]("changingAndImproving").map(_.toString)),
      baseDoc.flatMap(_.getAs[Double]("makingEffectiveDecisions").map(_.toString)),
      baseDoc.flatMap(_.getAs[Double]("deliveringAtPace").map(_.toString)),
      baseDoc.flatMap(_.getAs[Double]("collaboratingAndPartnering").map(_.toString)),
      baseDoc.flatMap(_.getAs[Double]("leadingAndCommunicating").map(_.toString))
    )
  }

  private def schemePreferences(doc: BSONDocument): List[Option[String]] = {
    doc.getAs[List[String]]("schemes").map(_.map(Some(_))).getOrElse(Nil)
  }

  private def locationPreferences(schemesAndLocations: List[LocationSchemes], doc: BSONDocument): List[Option[String]] = {
    val locationIds = doc.getAs[List[String]]("scheme-locations").getOrElse(Nil)
    val lookupTable = schemesAndLocations.groupBy(_.id).mapValues(_.head)
    locationIds.map(locationId => Some(lookupTable(locationId).locationName))
  }

  private def assessmentCentreIndicator(doc: BSONDocument): List[Option[String]] = {
    val aciDoc = doc.getAs[AssessmentCentreIndicator]("assessment-centre-indicator")

    List(
        aciDoc.map(_.area),
        aciDoc.map(_.assessmentCentre),
        Some(aciDoc.flatMap(_.version).map(_.toString).getOrElse("0"))
    )
  }

  private def mapYesNo(potentialValue: Option[Boolean]): Option[String] = potentialValue.map { value =>
    if (value) "Yes" else "No"
  }.orElse(Some("No"))

  private def assistanceDetails(doc: BSONDocument) = {
    val assistanceDetails = doc.getAs[BSONDocument]("assistance-details")
    List(
      assistanceDetails.flatMap(_.getAs[String]("hasDisability")),
      assistanceDetails.flatMap(_.getAs[String]("hasDisabilityDescription")),
      assistanceDetails.flatMap(ad => mapYesNo(ad.getAs[Boolean]("guaranteedInterview"))),
      assistanceDetails.flatMap(ad => mapYesNo(ad.getAs[Boolean]("needsSupportForOnlineAssessment"))),
      assistanceDetails.flatMap(_.getAs[String]("needsSupportForOnlineAssessmentDescription")),
      assistanceDetails.flatMap(ad => mapYesNo(ad.getAs[Boolean]("needsSupportAtVenue"))),
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
      personalDetails.flatMap(pd => mapYesNo(pd.getAs[Boolean]("aLevel"))),
      personalDetails.flatMap(pd => mapYesNo(pd.getAs[Boolean]("stemLevel"))),
      personalDetails.flatMap(pd => mapYesNo(pd.getAs[Boolean]("civilServant"))),
      personalDetails.flatMap(_.getAs[String]("department").map(_.toString))
    )
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

}
