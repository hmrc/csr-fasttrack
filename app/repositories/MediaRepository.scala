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

import model.{ Commands, UniqueIdentifier }
import model.Commands._
import model.Exceptions.CannotAddMedia
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MediaRepository {
  def create(addMedia: AddMedia): Future[Unit]

  def findAll: Future[Map[UniqueIdentifier, String]]
}

class MediaMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[AddMedia, BSONObjectID](CollectionNames.MEDIA, mongo,
      Commands.Implicits.mediaFormats, ReactiveMongoFormats.objectIdFormats) with MediaRepository {

  override def create(addMedia: AddMedia): Future[Unit] = insert(addMedia).map { _ => ()
  } recover {
    case e: WriteResult => throw new CannotAddMedia(addMedia.userId)
  }

  def findAll: Future[Map[UniqueIdentifier, String]] = {
    val query = BSONDocument()
    collection.find(query).cursor[BSONDocument]().collect[List]().map {
      _.map { d =>
        val userId = d.getAs[String]("userId").get
        val media = d.getAs[String]("media").getOrElse("")
        UniqueIdentifier(userId) -> media
      }.toMap
    }
  }
}
