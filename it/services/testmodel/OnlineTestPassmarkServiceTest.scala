package services.testmodel

import model.PersistedObjects.CandidateTestReport
import play.api.libs.json.Json

case class OnlineTestPassmarkServiceTest(schemes: List[String],
                                         scores: CandidateTestReport,
                                         expected: ScoreEvaluationTestExpectation)

object OnlineTestPassmarkServiceTest {

  import model.PersistedObjects.Implicits.candidateTestReportFormats

  implicit val onlineTestPassmarkServiceTestFormat = Json.format[OnlineTestPassmarkServiceTest]
}

