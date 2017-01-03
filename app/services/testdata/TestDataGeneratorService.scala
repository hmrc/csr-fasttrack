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

package services.testdata

import connectors.AuthProviderClient
import connectors.AuthProviderClient.UserRole
import connectors.testdata.ExchangeObjects.DataGenerationResponse
import repositories.MongoDbConnection
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

object TestDataGeneratorService extends TestDataGeneratorService {
}

trait TestDataGeneratorService {

  def clearDatabase()(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _ <- MongoDbConnection.mongoConnector.db().drop()
      _ <- AuthProviderClient.removeAllUsers()
      _ <- RegisteredStatusGenerator.createUser(
        1,
        "test_service_manager_1@mailinator.com", "CSR Test", "Service Manager", AuthProviderClient.TechnicalAdminRole
      )
    } yield {
      ()
    }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: String,
                       role: UserRole)(implicit hc: HeaderCarrier): Future[List[DataGenerationResponse]] = {
    Future.successful {
      val parNumbers = (1 to numberToGenerate).par
      parNumbers.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(2)
      )
      parNumbers.map { candidateGenerationId =>
        val fut = RegisteredStatusGenerator.createUser(
          candidateGenerationId,
          s"test_service_manager_$emailPrefix$candidateGenerationId@mailinator.com", "CSR Test", "Service Manager", role
        )
        Await.result(fut, 5 seconds)
      }.toList
    }
  }

  def createCandidatesInSpecificStatus(numberToGenerate: Int,
                                       generatorForStatus: (GeneratorConfig) => BaseGenerator,
                                       configGenerator: (Int) => GeneratorConfig
                                      )(implicit hc: HeaderCarrier): Future[List[DataGenerationResponse]] = {
    Future.successful {

      val parNumbers = (1 to numberToGenerate).par
      parNumbers.tasksupport = new ForkJoinTaskSupport(
        new scala.concurrent.forkjoin.ForkJoinPool(2)
      )

      // one wasted generation of config
      val config = configGenerator(1)
      val generator = generatorForStatus(config)

      parNumbers.map { candidateGenerationId =>
        Await.result(
          generator.generate(candidateGenerationId, configGenerator(candidateGenerationId)),
          10 seconds
        )
      }.toList

    }
  }
}
