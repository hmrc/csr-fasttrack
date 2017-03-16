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

import model.Exceptions.{ ApplicationNotFound, AssistanceDetailsNotFound, CannotUpdateAssistanceDetails }
import model.exchange.AssistanceDetails
import model.persisted.ApplicationAssistanceDetails
import model.{ ApplicationStatuses, ProgressStatuses }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.{ ReactiveRepositoryHelpers, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssistanceDetailsRepository {
  def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit]

  def updateToGis(applicationId: String): Future[Unit]

  def find(applicationId: String): Future[AssistanceDetails]

  def find(cubiksUserId: Int): Future[AssistanceDetails]

  def findApplication(cubiksUserId: Int): Future[ApplicationAssistanceDetails]
}

class AssistanceDetailsMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[AssistanceDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
      AssistanceDetails.assistanceDetailsFormat, ReactiveMongoFormats.objectIdFormats) with AssistanceDetailsRepository
    with ReactiveRepositoryHelpers {

  val AssistanceDetailsCollection = "assistance-details"

  override def update(applicationId: String, userId: String, ad: AssistanceDetails): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId, "userId" -> userId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> ApplicationStatuses.InProgress,
      s"progress-status.${ProgressStatuses.AssistanceDetailsCompletedProgress}" -> true,
      AssistanceDetailsCollection -> ad
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating assistance details",
      CannotUpdateAssistanceDetails(userId))

    collection.update(query, updateBSON, upsert = false) map validator
  }

  override def updateToGis(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "assistance-details.hasDisability" -> "Yes",
      "assistance-details.guaranteedInterview" -> true,
      "assistance-details.needsSupportForOnlineAssessment" -> false
    )) ++ BSONDocument("$unset" -> BSONDocument(
      "assistance-details.needsSupportForOnlineAssessmentDescription" -> BSONString("")
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "update candidate as GIS",
      CannotUpdateAssistanceDetails(applicationId))

    collection.update(query, updateBSON, upsert = false) map validator
  }

  override def find(applicationId: String): Future[AssistanceDetails] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(AssistanceDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](AssistanceDetailsCollection).isDefined =>
        document.getAs[AssistanceDetails](AssistanceDetailsCollection).get

      case _ => throw AssistanceDetailsNotFound(applicationId)
    }
  }

  override def find(cubiksUserId: Int): Future[AssistanceDetails] = {
    val query = BSONDocument("online-tests.cubiksUserId" -> cubiksUserId)
    val projection = BSONDocument(AssistanceDetailsCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](AssistanceDetailsCollection).isDefined =>
        document.getAs[AssistanceDetails](AssistanceDetailsCollection).get

      case _ => throw AssistanceDetailsNotFound(s"cubiksUserId=$cubiksUserId")
    }
  }

  override def findApplication(cubiksUserId: Int): Future[ApplicationAssistanceDetails] = {
    val query = BSONDocument("online-tests.cubiksUserId" -> cubiksUserId)
    val projection = BSONDocument(
      "applicationId" -> 1,
      "applicationStatus" -> 1,
      AssistanceDetailsCollection -> 1,
      "_id" -> 0
    )
    collection.find(query, projection).one[BSONDocument].map {
      case Some(document) =>
        (for {
          appId <- document.getAs[String]("applicationId")
          appStatus <- document.getAs[ApplicationStatuses.EnumVal]("applicationStatus")
          appDetails <- document.getAs[AssistanceDetails](AssistanceDetailsCollection)
        } yield {
          ApplicationAssistanceDetails(appId, appStatus, appDetails)
        }).getOrElse(throw new IllegalStateException("ApplicationAssistanceDetails cannot be retrieved from the database"))
      case None =>
        throw ApplicationNotFound(s"cubiksUserId=$cubiksUserId")
    }
  }
}
