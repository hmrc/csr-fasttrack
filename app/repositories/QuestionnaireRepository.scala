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

import model.ReportExchangeObjects.PassMarkReportQuestionnaireData
import model.PersistedObjects
import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import play.api.libs.json._
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson._
import services.reporting.SocioEconomicScoreCalculator
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait QuestionnaireRepository {
  def addQuestions(applicationId: String, questions: List[PersistedQuestion]): Future[Unit]
  def findQuestions(applicationId: String): Future[Map[String, String]]
  def passMarkReport: Future[Map[String, Map[String, String]]]
  def diversityReport: Future[Map[String, Map[String, String]]]
}

object QuestionnaireRepository {
  val genderQuestionText = "What is your gender identity?"
  val sexualOrientationQuestionText = "What is your sexual orientation?"
  val ethnicityQuestionText = "What is your ethnic group?"
}

class QuestionnaireMongoRepository(socioEconomicCalculator: SocioEconomicScoreCalculator)(implicit mongo: () => DB)
    extends ReactiveRepository[PersistedAnswer, BSONObjectID](CollectionNames.QUESTIONNAIRE, mongo,
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

  override def diversityReport: Future[Map[String, Map[String, String]]] = {

    val query = BSONDocument()
    val queryResult = collection.find(query).cursor[BSONDocument]().collect[List]().map { listOfDocs =>
      listOfDocs.map { d =>
        val applicationId = d.getAs[String]("applicationId").get
        val questionsDoc = d.getAs[BSONDocument]("questions")

        val qAndA = questionsDoc.toList.flatMap(_.elements).map {
          case (question, _) =>
            val answer = getAnswer(questionsDoc, question).getOrElse("Unknown")
            (question, answer)
        }.toMap
        applicationId -> qAndA
      }.toMap
    }
    queryResult
  }

  override def passMarkReport: Future[Map[String, Map[String, String]]] = {
    diversityReport
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

  private def getAnswer(questionsDoc: Option[BSONDocument], question: String): Option[String] = {
    val questionDoc = questionsDoc.flatMap(_.getAs[BSONDocument](question))
    val answer = questionDoc.flatMap(_.getAs[String]("answer"))
    answer
  }

  private def docToReport(document: BSONDocument): (String, PassMarkReportQuestionnaireData) = {
    import QuestionnaireRepository._
    val questionsDoc = document.getAs[BSONDocument]("questions")

    val applicationId = document.getAs[String]("applicationId").get
    val gender = getAnswer(questionsDoc, genderQuestionText)
    val sexualOrientation = getAnswer(questionsDoc, sexualOrientationQuestionText)
    val ethnicity = getAnswer(questionsDoc, ethnicityQuestionText)

    val employmentStatus = getAnswer(questionsDoc, "Which type of occupation did they have?")
    val isEmployed = employmentStatus.exists(s => !s.startsWith("Unemployed"))
    val parentEmploymentStatus = if (isEmployed) Some("Employed") else employmentStatus
    val parentOccupation = if (isEmployed) employmentStatus else None

    val parentEmployedOrSelf = getAnswer(questionsDoc, "Did they work as an employee or were they self-employed?")
    val parentCompanySize = getAnswer(questionsDoc, "Which size would best describe their place of work?")

    val qAndA = questionsDoc.toList.flatMap(_.elements).map {
      case (question, _) =>
        val answer = getAnswer(questionsDoc, question).getOrElse("Unknown")
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
