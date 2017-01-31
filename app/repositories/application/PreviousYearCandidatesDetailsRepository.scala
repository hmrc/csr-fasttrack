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

import model.Commands.CandidateDetailsReportItem
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection
import repositories.CollectionNames
import reactivemongo.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait PreviousYearCandidatesDetailsRepository {

  def findApplicationDetails() : Future[List[CandidateDetailsReportItem]]

  def findContactDetails() : Future[Map[String, String]]

}


class PreviousYearCandidatesDetailsMongoRepository(implicit mongo: () => DB) extends PreviousYearCandidatesDetailsRepository {

  val applicationDetailsCollection = mongo().collection[JSONCollection](CollectionNames.APPLICATION_2016)

  val contactDetailsCollection = mongo().collection[JSONCollection](CollectionNames.CONTACT_DETAILS_2016)

  override def findApplicationDetails(): Future[List[CandidateDetailsReportItem]] = {

    val projection = Json.obj("_id" -> 0, "progress-status" -> 0, "progress-status-dates" -> 0)

    applicationDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map {
      _.map { bson =>
        val personalDetails = bson.getAs[BSONDocument]("personal-details")
        val csvContent = makeRow(
          bson.getAs[String]("applicationId"),
          bson.getAs[String]("applicationStatus"),
          personalDetails.flatMap(_.getAs[String]("firstName")),
          personalDetails.flatMap(_.getAs[String]("lastName")),
          personalDetails.flatMap(_.getAs[String]("preferredName")),
          personalDetails.flatMap(_.getAs[String]("dateOfBirth")),
          personalDetails.flatMap(_.getAs[String]("aLevel")),
          personalDetails.flatMap(_.getAs[String]("stemLevel"))
        )
        CandidateDetailsReportItem(bson.getAs[String]("applicationId").getOrElse(""),
          bson.getAs[String]("userId").getOrElse(""), csvContent)
      }
    }
  }

  override def findContactDetails(): Future[Map[String, String]] = {

    val projection = Json.obj("_id" -> 0)

    contactDetailsCollection.find(Json.obj(), projection)
      .cursor[BSONDocument](ReadPreference.primaryPreferred)
      .collect[List]().map {
      _.map { bson => {
           val contactDetails = bson.getAs[BSONDocument]("contact-details")
           val address = contactDetails.flatMap(_.getAs[BSONDocument]("address"))
           val csvRecord = makeRow(
              address.flatMap(_.getAs[String]("line1")),
              address.flatMap(_.getAs[String]("line2")),
              address.flatMap(_.getAs[String]("line3")),
              address.flatMap(_.getAs[String]("line4")),
              contactDetails.flatMap(_.getAs[String]("postCode")),
              contactDetails.flatMap(_.getAs[String]("email")),
              contactDetails.flatMap(_.getAs[String]("phone"))
          )
          bson.getAs[String]("userId").getOrElse("") -> csvRecord
        }
      }.toMap
    }
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

}
