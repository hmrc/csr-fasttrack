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

package repositories

import model.Commands.PassMarkReportQuestionnaireData
import model.PersistedObjects
import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import play.api.libs.json._
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson._
import services.reporting.SocioEconomicScoreCalculatorTrait
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait QuestionnaireRepository {
  def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit]
  def findQuestions(applicationId: String): Future[Map[String, String]]
  def passMarkReport: Future[Map[String, PassMarkReportQuestionnaireData]]
}

class QuestionnaireMongoRepository(socioEconomicCalculator: SocioEconomicScoreCalculatorTrait)(implicit mongo: () => DB)
  extends ReactiveRepository[PersistedAnswer, BSONObjectID]("questionnaire", mongo,
    PersistedObjects.Implicits.answerFormats, ReactiveMongoFormats.objectIdFormats) with QuestionnaireRepository {

  override def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit] = {

    val appId = "applicationId" -> applicationId

    collection.update(
      BSONDocument(appId),
      BSONDocument("$set" -> questions.map(q => s"questions.${q.question}" -> q.answer).foldLeft(document ++ appId)((d, v) => d ++ v)),
      upsert = true
    ) map {
        case _ => ()
      }
  }

  override def findQuestions(applicationId: String): Future[Map[String, String]] = {
    find(applicationId).map { questions =>
      (for {
        q <- questions
      } yield {
        val answer = q.answer.answer.getOrElse("")
        q.question -> answer
      }).toMap[String, String]
    }
  }

  override def passMarkReport: Future[Map[String, PassMarkReportQuestionnaireData]] = {
    // We need to ensure that the candidates have completed the last page of the questionnaire
    // however, only the first question on the employment page is mandatory, as if the answer is
    // unemployed, they don't need to answer other questions
    val firstEmploymentQuestion = "Which type of occupation did they have?"
    val query = BSONDocument(s"questions.$firstEmploymentQuestion" -> BSONDocument("$exists" -> BSONBoolean(true)))
    val queryResult = collection.find(query).cursor[BSONDocument](ReadPreference.nearest).collect[List]()
    queryResult.map(_.map(docToReport).toMap)
  }

  def find(applicationId: String): Future[List[PersistedQuestion]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("questions" -> 1, "_id" -> 0)

    case class Questions(questions: Map[String, PersistedAnswer])

    implicit object SearchFormat extends Format[Questions] {
      def reads(json: JsValue): JsResult[Questions] = JsSuccess(Questions(
        (json \ "questions").as[Map[String, PersistedAnswer]]
      ))

      def writes(s: Questions): JsValue = ???
    }

    collection.find(query, projection).one[Questions].map {
      case Some(q) => q.questions.map((q: (String, PersistedAnswer)) => PersistedQuestion(q._1, q._2)).toList
      case None => List()
    }
  }

  private def docToReport(document: BSONDocument): (String, PassMarkReportQuestionnaireData) = {
    val questionsDoc = document.getAs[BSONDocument]("questions")
    def getAnswer(question: String): Option[String] = {
      val questionDoc = questionsDoc.flatMap(_.getAs[BSONDocument](question))
      val answer = questionDoc.flatMap(_.getAs[String]("answer"))
      answer
    }

    val applicationId = document.getAs[String]("applicationId").get
    val gender = getAnswer("What is your gender identity?")
    val sexualOrientation = getAnswer("What is your sexual orientation?")
    val ethnicity = getAnswer("What is your ethnic group?")

    val employmentStatus = getAnswer("Which type of occupation did they have?")
    val isEmployed = employmentStatus.exists(s => !s.startsWith("Unemployed"))
    val parentEmploymentStatus = if (isEmployed) Some("Employed") else employmentStatus
    val parentOccupation = if (isEmployed) employmentStatus else None

    val parentEmployedOrSelf = getAnswer("Did they work as an employee or were they self-employed?")
    val parentCompanySize = getAnswer("Which size would best describe their place of work?")

    val qAndA = questionsDoc.toList.flatMap(_.elements).map {
      case (question, _) =>
        val answer = getAnswer(question).getOrElse("Unknown")
        (question, answer)
    }.toMap

    val socioEconomicScore = socioEconomicCalculator.calculate(qAndA)

    (applicationId, PassMarkReportQuestionnaireData(
      gender,
      sexualOrientation,
      ethnicity,
      parentEmploymentStatus,
      parentOccupation,
      parentEmployedOrSelf,
      parentCompanySize,
      socioEconomicScore
    ))
  }
}
