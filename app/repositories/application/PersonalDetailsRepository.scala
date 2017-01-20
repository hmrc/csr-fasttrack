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

import model.Exceptions.PersonalDetailsNotFound
import model.{ ApplicationStatuses, PersistedObjects, ProgressStatuses }
import model.ApplicationStatuses.BSONEnumHandler
import model.PersistedObjects.{ PersonalDetails, PersonalDetailsWithUserId }
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PersonalDetailsRepository {

  val errorCode = 500

  def update(applicationId: String, userId: String, personalDetails: PersonalDetails): Future[Unit]

  def update(appId: String, userId: String, personalDetails: PersonalDetails,
    requiredApplicationStatuses: Seq[ApplicationStatuses.EnumVal],
    newApplicationStatus: ApplicationStatuses.EnumVal): Future[Unit]

  def find(applicationId: String): Future[PersonalDetails]

  def findPersonalDetailsWithUserId(applicationId: String): Future[PersonalDetailsWithUserId]
}

class PersonalDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[PersonalDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
    PersistedObjects.Implicits.persistedPersonalDetailsFormats, ReactiveMongoFormats.objectIdFormats)
    with PersonalDetailsRepository with ReactiveRepositoryHelpers {

  override def update(applicationId: String, userId: String, pd: PersonalDetails): Future[Unit] = {

    val persistedPersonalDetails = PersonalDetails(pd.firstName, pd.lastName, pd.preferredName, pd.dateOfBirth, pd.aLevel, pd.stemLevel)

    val query = BSONDocument("applicationId" -> applicationId, "userId" -> userId)

    val personalDetailsBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> ApplicationStatuses.InProgress,
      s"progress-status.${ProgressStatuses.PersonalDetailsCompletedProgress}" -> true,
      s"progress-status-timestamp.${ProgressStatuses.PersonalDetailsCompletedProgress}" -> DateTime.now(),
      "personal-details" -> persistedPersonalDetails
    ))

    collection.update(query, personalDetailsBSON, upsert = false) map { _ => () }
  }

  def update(applicationId: String, userId: String, personalDetails: PersonalDetails,
    requiredApplicationStatuses: Seq[ApplicationStatuses.EnumVal],
    newApplicationStatus: ApplicationStatuses.EnumVal
  ): Future[Unit] = {
    val PersonalDetailsCollection = "personal-details"


    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId, "userId" -> userId),
      BSONDocument("applicationStatus" -> BSONDocument("$in" -> requiredApplicationStatuses))
    ))

    val personalDetailsBSON = BSONDocument("$set" ->
      BSONDocument(
        s"progress-status.${ProgressStatuses.PersonalDetailsCompletedProgress}" -> true,
        s"progress-status-timestamp.${ProgressStatuses.PersonalDetailsCompletedProgress}" -> DateTime.now(),
        PersonalDetailsCollection -> personalDetails,
        "applicationStatus" -> newApplicationStatus
      )
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating personal details",
      PersonalDetailsNotFound(applicationId))

    collection.update(query, personalDetailsBSON) map validator
  }

  override def find(applicationId: String): Future[PersonalDetails] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("personal-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("personal-details").isDefined =>
        val root = document.getAs[BSONDocument]("personal-details").get
        val firstName = root.getAs[String]("firstName").get
        val lastName = root.getAs[String]("lastName").get
        val preferredName = root.getAs[String]("preferredName").get
        val dateOfBirth = root.getAs[LocalDate]("dateOfBirth").get
        val aLevel = root.getAs[Boolean]("aLevel").get
        val stemLevel = root.getAs[Boolean]("stemLevel").get

        PersonalDetails(firstName, lastName, preferredName, dateOfBirth, aLevel, stemLevel)

      case _ => throw PersonalDetailsNotFound(applicationId)
    }
  }

  def findPersonalDetailsWithUserId(applicationId: String): Future[PersonalDetailsWithUserId] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("userId" -> 1, "personal-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("personal-details").isDefined =>
        val userId = document.getAs[String]("userId").get
        val root = document.getAs[BSONDocument]("personal-details").get
        val preferredName = root.getAs[String]("preferredName").get

        PersonalDetailsWithUserId(preferredName, userId)
      case _ => throw PersonalDetailsNotFound(applicationId)
    }
  }

}
