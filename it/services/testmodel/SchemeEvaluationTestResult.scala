package services.testmodel

import model.persisted.SchemeEvaluationResult
import play.api.libs.json.Json

case class SchemeEvaluationTestResult(scheme: String, result: String)

object SchemeEvaluationTestResult {
  implicit val schemeEvaluationTestResultFormat = Json.format[SchemeEvaluationTestResult]
}


