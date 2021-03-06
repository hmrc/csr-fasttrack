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

import model.Exceptions.NotFoundException
import model.FlagCandidatePersistedObject.FlagCandidate
import reactivemongo.api.DB
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FlagCandidateRepository {

  def tryGetCandidateIssue(appId: String): Future[Option[FlagCandidate]]

  def save(flagCandidate: FlagCandidate): Future[Unit]

  def remove(appId: String): Future[Unit]

  def removeNoCheck(appId: String): Future[Unit]
}

class FlagCandidateMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[FlagCandidate, BSONObjectID](CollectionNames.APPLICATION, mongo,
      FlagCandidate.FlagCandidateFormats, ReactiveMongoFormats.objectIdFormats) with FlagCandidateRepository {

  def tryGetCandidateIssue(appId: String): Future[Option[FlagCandidate]] = {
    val query = BSONDocument("applicationId" -> appId)
    val projection = BSONDocument("applicationId" -> 1, "issue" -> 1)

    collection.find(query, projection).one[BSONDocument].map { docOpt =>
      docOpt.map(flagCandidateHandler.read) match {
        case flag @ Some(FlagCandidate(_, Some(_))) => flag
        case _ => None
      }
    }
  }

  def save(flagCandidate: FlagCandidate): Future[Unit] = {
    val query = BSONDocument("applicationId" -> flagCandidate.applicationId)
    val result = BSONDocument("$set" -> BSONDocument(
      "issue" -> flagCandidate.issue
    ))

    collection.update(query, result, upsert = false).map(validateResult(flagCandidate.applicationId))
  }

  def remove(appId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> appId)
    val result = BSONDocument("$unset" -> BSONDocument("issue" -> ""))

    collection.update(query, result, upsert = false).map(validateResult(appId))
  }

  def removeNoCheck(appId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> appId)
    val result = BSONDocument("$unset" -> BSONDocument("issue" -> ""))

    collection.update(query, result).map( _ => () )
  }

  private def validateResult(appId: String)(writeResult: UpdateWriteResult) = writeResult.n match {
    case 0 => throw new NotFoundException(s"No application found with applicationId=$appId")
    case _ => ()
  }
}
