package repositories

import model.AssessmentCentreIndicator
import testkit.{ ShortTimeout, UnitWithAppSpec }

class AssessmentCentreIndicatorCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  "North South Indicator Repository" should {
    "parse file with expected number of post code areas" in {
      val result = AssessmentCentreIndicatorCSVRepository.assessmentCentreIndicators
      result.size mustBe 121
    }
  }

  "calculateFsacIndicator" should {
    "return default indicator when no postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(None)
      result mustBe AssessmentCentreIndicator("London", "London", Some("3"))
    }
    "return default indicator when no postcode match is found" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("BOGUS3"))
      result mustBe AssessmentCentreIndicatorCSVRepository.DefaultIndicator
    }
    "return default indicator for an empty postcode " in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some(""))
      result mustBe AssessmentCentreIndicatorCSVRepository.DefaultIndicator
    }
    "ignore postcode if outside UK and return the default indicator" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("OO1 4DB"))
      result mustBe AssessmentCentreIndicatorCSVRepository.DefaultIndicator
    }
    "return London for Oxford postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("OX1 4DB"))
      result mustBe AssessmentCentreIndicator("Oxford", "London", Some("3"))
    }
    "return Newcastle for Edinburgh postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("EH1 3EG"))
      result mustBe AssessmentCentreIndicator("Edinburgh", "Newcastle", Some("3"))
    }
    "return London even when postcode is lowercase" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("ec1v 3eg"))
      result mustBe AssessmentCentreIndicator("East Central london", "London", Some("3"))
    }
    "return London for Llandrindod postcode (mix lowercase + uppercase)" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("lD1v 3eg"))
      result mustBe AssessmentCentreIndicator("Llandrindod wells", "London", Some("3"))
    }
  }

  "Assessment Centre Indicator Repository" should {
    "contains only Newcastle and London assessment centre" in {
      val actualAssessmentCentres = AssessmentCentreIndicatorCSVRepository.assessmentCentreIndicators
        .values.map(_.assessmentCentre).toSet
      actualAssessmentCentres mustBe Set("London", "Newcastle")
    }
  }
}
