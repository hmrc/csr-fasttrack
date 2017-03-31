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

package repositories

import factories.{ DateTimeFactory, UUIDFactory }
import model.AssessmentExercise.AssessmentExercise
import model.{ AssessmentExercise, CandidateScoresCommands, UniqueIdentifier }
import model.CandidateScoresCommands._
import model.Exceptions.ApplicationNotFound
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ApplicationAssessmentScoresRepository {
  def allScores: Future[Map[String, CandidateScoresAndFeedback]]

  def tryFind(applicationId: String): Future[Option[CandidateScoresAndFeedback]]

  def save(exerciseScoresAndFeedback: ExerciseScoresAndFeedback,
           newVersion: Option[String] = Some(UUIDFactory.generateUUID())): Future[Unit]

  def findNonSubmittedScores(assessorId: String) : Future[List[CandidateScoresAndFeedback]]

  def saveAll(scoresAndFeedback: CandidateScoresAndFeedback,
           newVersion: Option[String] = Some(UUIDFactory.generateUUID())): Future[Unit]

  def removeExercise(applicationId: String, exercise: AssessmentExercise): Future[Unit]
}

class ApplicationAssessmentScoresMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
    extends ReactiveRepository[CandidateScoresAndFeedback, BSONObjectID](CollectionNames.APPLICATION_ASSESSMENT_SCORES, mongo,
      CandidateScoresCommands.Implicits.CandidateScoresAndFeedbackFormats, ReactiveMongoFormats.objectIdFormats)
    with ApplicationAssessmentScoresRepository with ReactiveRepositoryHelpers {

  def tryFind(applicationId: String): Future[Option[CandidateScoresAndFeedback]] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.find(query).one[BSONDocument].map { _.map(candidateScoresAndFeedback.read) }
  }

  def findNonSubmittedScores(assessorId: String): Future[List[CandidateScoresAndFeedback]] = {
    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(
        s"${AssessmentExercise.interview}.submittedDate" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"${AssessmentExercise.interview}.updatedBy" -> assessorId
      ),
      BSONDocument(
        s"${AssessmentExercise.groupExercise}.submittedDate" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"${AssessmentExercise.groupExercise}.updatedBy" -> assessorId
      ),
      BSONDocument(
        s"${AssessmentExercise.writtenExercise}.submittedDate" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"${AssessmentExercise.writtenExercise}.updatedBy" -> assessorId
      )
    ))

    collection.find(query).cursor[BSONDocument]().collect[List]().map { _.map(candidateScoresAndFeedback.read) }
  }

  def allScores: Future[Map[String, CandidateScoresAndFeedback]] = {
    val query = BSONDocument()
    val queryResult = collection.find(query).cursor[BSONDocument](ReadPreference.nearest).collect[List]()
    queryResult.map { docs =>
      docs.map { doc =>
        val cf = candidateScoresAndFeedback.read(doc)
        (cf.applicationId, cf)
      }.toMap
    }
  }

  def save(exerciseScoresAndFeedback: ExerciseScoresAndFeedback,
           newVersion: Option[String] = Some(UUIDFactory.generateUUID())): Future[Unit] = {
    val applicationId = exerciseScoresAndFeedback.applicationId
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"${exerciseScoresAndFeedback.exercise}.version" -> BSONDocument("$exists" -> BSONBoolean(false))),
        BSONDocument(s"${exerciseScoresAndFeedback.exercise}.version" -> exerciseScoresAndFeedback.scoresAndFeedback.version))
      ))
    )

    val scoresAndFeedback = exerciseScoresAndFeedback.scoresAndFeedback
    val applicationScoresBSON = exerciseScoresAndFeedback.scoresAndFeedback.version match {
      case Some(_) => BSONDocument(
        s"${exerciseScoresAndFeedback.exercise}" -> scoresAndFeedback.copy(version = newVersion)
      )
      case _ => BSONDocument(
        "applicationId" -> exerciseScoresAndFeedback.applicationId,
        s"${exerciseScoresAndFeedback.exercise}" -> scoresAndFeedback.copy(version = newVersion)
      )
    }

    val candidateScoresAndFeedbackBSON = BSONDocument("$set" -> applicationScoresBSON)

    val validator = singleUpdateValidator(applicationId, "Application with correct version not found")

    collection.update(query, candidateScoresAndFeedbackBSON, upsert = exerciseScoresAndFeedback.scoresAndFeedback.version.isEmpty) map validator
  }

  def saveAll(scoresAndFeedback: CandidateScoresAndFeedback,
              newVersion: Option[String] = Some(UUIDFactory.generateUUID())): Future[Unit] = {
    val applicationId = scoresAndFeedback.applicationId
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("$or" -> BSONArray(BSONDocument(
        s"${AssessmentExercise.interview}.version" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"${AssessmentExercise.interview}.version" -> scoresAndFeedback.interview.flatMap(_.version))
      )),
      BSONDocument("$or" -> BSONArray(BSONDocument(
        s"${AssessmentExercise.groupExercise}.version" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"${AssessmentExercise.groupExercise}.version" -> scoresAndFeedback.groupExercise.flatMap(_.version))
      )),
      BSONDocument("$or" -> BSONArray(BSONDocument(
        s"${AssessmentExercise.writtenExercise}.version" -> BSONDocument("$exists" -> BSONBoolean(false)),
        s"${AssessmentExercise.writtenExercise}.version" -> scoresAndFeedback.writtenExercise.flatMap(_.version))
      ))
    ))

    val candidateScoresAndFeedbackBSON = BSONDocument("$set" -> scoresAndFeedback.setVersion(newVersion))

    val validator = singleUpdateValidator(applicationId, "Application with correct version not found for 'Review scores'")

    collection.update(query, candidateScoresAndFeedbackBSON, upsert = scoresAndFeedback.allVersionsEmpty) map validator
  }

  def removeExercise(applicationId: String, exercise: AssessmentExercise): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$unset" -> BSONDocument(exercise.toString -> ""))

    val validator = singleUpdateValidator(applicationId, "Could not find application",
      ApplicationNotFound(s"Could not find application '$applicationId'"))

    collection.update(query, update).map(validator)
  }

}
