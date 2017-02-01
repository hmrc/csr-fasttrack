package services.testmodel

import play.api.libs.json.Json

case class ScoreEvaluationTestExpectation(result: List[SchemeEvaluationTestResult],
                                          applicationStatus: String,
                                          passmarkVersion: Option[String])

object ScoreEvaluationTestExpectation {
  implicit val scoreEvaluationTestExpectationFormat = Json.format[ScoreEvaluationTestExpectation]
}
