/*
 * Copyright 2016 HM Revenue & Customs
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

package testkit

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Span }
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.test.Helpers
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import repositories.MongoDbConnection
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }
import scala.language.postfixOps

trait MongoRepositorySpec extends PlaySpec with Inside with Inspectors with ScalaFutures with IndexesReader {
  import ImplicitBSONHandlers._

  // System-wide setting for integration test timeouts.
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(5000, Millis)))

  implicit final def app: Application = new GuiceApplicationBuilder().build

  implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext

  val timeout = 10 seconds
  val collectionName: String

  implicit def mongo: () => DefaultDB = {
    val conn = new MongoDbConnection() { }.db
    conn
  }

  override def withFixture(test: NoArgTest) = {
    Helpers.running(app) {
      val collection = mongo().collection[JSONCollection](collectionName)
      Await.ready(collection.remove(Json.obj()), timeout)
      val res = super.withFixture(test)
      res
    }
  }
}

trait IndexesReader {
  this: ScalaFutures =>

  def indexesWithFields(repo: ReactiveRepository[_, _])(implicit ec: ExecutionContext): Seq[Seq[String]] = {
    val indexesManager = repo.collection.indexesManager
    val indexes = indexesManager.list().futureValue
    indexes.map(_.key.map(_._1))
  }
}
