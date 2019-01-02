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

package repositories

import model.Commands
import model.Commands.AssessmentCentreAllocation
import model.Exceptions.{ NotFoundException, TooManyEntries }
import org.joda.time.LocalDate
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessmentCentreAllocationRepository {
  def find(applicationId: String): Future[Option[AssessmentCentreAllocation]]
  def findOne(applicationId: String): Future[AssessmentCentreAllocation]
  def findAll: Future[List[AssessmentCentreAllocation]]
  def findAllForDate(venue: String, date: LocalDate): Future[List[AssessmentCentreAllocation]]
  def findAllForVenue(venue: String): Future[List[AssessmentCentreAllocation]]
  def findByApplicationIds(applicationIds: List[String]): Future[List[AssessmentCentreAllocation]]
  def create(applications: List[AssessmentCentreAllocation]): Future[Seq[AssessmentCentreAllocation]]
  def confirmAllocation(applicationId: String): Future[Unit]
  def delete(applicationId: String): Future[Unit]
  // This version does not throw an exception of no document was found
  def deleteNoCheck(applicationId: String): Future[Unit]
}

class AssessmentCentreAllocationMongoRepository()(implicit mongo: () => DB)
    extends ReactiveRepository[AssessmentCentreAllocation, BSONObjectID](CollectionNames.APPLICATION_ASSESSMENT, mongo,
      Commands.Implicits.applicationAssessmentFormat, ReactiveMongoFormats.objectIdFormats) with AssessmentCentreAllocationRepository {

  override def findOne(applicationId: String): Future[AssessmentCentreAllocation] = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )

    collection.find(query).one[BSONDocument] map {
      case Some(applicationAssessment) => parseApplicationAssessment(applicationAssessment)
      case _ => throw new NotFoundException(s"Assessment allocation not found for id $applicationId")
    }
  }

  override def find(applicationId: String): Future[Option[AssessmentCentreAllocation]] = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )

    collection.find(query).one[BSONDocument] map {
      case Some(applicationAssessment) => Some(parseApplicationAssessment(applicationAssessment))
      case _ => None
    }
  }

  override def findByApplicationIds(applicationIds: List[String]): Future[List[AssessmentCentreAllocation]] = {
    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    getApplicationAssessments(query)
  }


  override def findAll: Future[List[AssessmentCentreAllocation]] = {
    val query = BSONDocument()

    getApplicationAssessments(query)
  }

  override def findAllForDate(venue: String, date: LocalDate): Future[List[AssessmentCentreAllocation]] = {
    val query = BSONDocument(
      "venue" -> venue,
      "date" -> date
    )

    getApplicationAssessments(query)
  }

  override def delete(applicationId: String): Future[Unit] = {
    val query = BSONDocument(
      "applicationId" -> applicationId
    )

    collection.remove(query, firstMatchOnly = false).map { writeResult =>
      if (writeResult.n == 0) {
        throw new NotFoundException(s"No assessment allocation was found with applicationId $applicationId")
      } else if (writeResult.n > 1) {
        throw TooManyEntries(s"Deletion successful, but too many assessment allocations matched for applicationId $applicationId.")
      } else {
        ()
      }
    }
  }

  override def deleteNoCheck(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.remove(query, firstMatchOnly = false).map( _ => () )
  }

  private def getApplicationAssessments(query: BSONDocument) = {
    collection.find(query).cursor[BSONDocument]().collect[List]().map {
      _.map(parseApplicationAssessment)
    }
  }

  override def findAllForVenue(venue: String): Future[List[AssessmentCentreAllocation]] = {
    val query = BSONDocument("venue" -> venue)

    collection.find(query).cursor[BSONDocument]().collect[List]().map {
      _.map(parseApplicationAssessment)
    }
  }

  override def create(applications: List[AssessmentCentreAllocation]): Future[Seq[AssessmentCentreAllocation]] = {
    val applicationsBSON = applications.map { app =>
      BSONDocument(
        "applicationId" -> app.applicationId,
        "venue" -> app.venue,
        "date" -> app.date,
        "session" -> app.session,
        "slot" -> app.slot,
        "confirmed" -> app.confirmed
      )
    }

    val bulkDocs = applicationsBSON.map(implicitly[collection.ImplicitlyDocumentProducer](_))

    collection.bulkInsert(ordered = false)(bulkDocs: _*) map {
      result => result.writeErrors.map(r => applications(r.index))
    }
  }

  override def confirmAllocation(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val confirmedBSON = BSONDocument("$set" -> BSONDocument("confirmed" -> true))
    collection.update(query, confirmedBSON, upsert = false) map { _ => () }
  }

  private def parseApplicationAssessment(item: BSONDocument): AssessmentCentreAllocation = {
    AssessmentCentreAllocation(
      item.getAs[String]("applicationId").get,
      item.getAs[String]("venue").get,
      item.getAs[LocalDate]("date").get,
      item.getAs[String]("session").get,
      item.getAs[Int]("slot").get,
      item.getAs[Boolean]("confirmed").get
    )
  }
}
