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

import model.Commands
import model.Commands.CreateApplicationRequest
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import repositories.{ CollectionNames, RandomSelection, ReactiveRepositoryHelpers }
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats


trait ReportingRepository {

}

class ReportingMongoRepository(timeZoneService: TimeZoneService)(implicit mongo: () => DB)
   extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
        Commands.Implicits.createApplicationRequestFormats,
        ReactiveMongoFormats.objectIdFormats) with ReportingRepository with RandomSelection with ReactiveRepositoryHelpers {

    }
