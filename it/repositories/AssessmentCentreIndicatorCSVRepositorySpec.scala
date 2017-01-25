package repositories

import testkit.{ ShortTimeout, UnitWithAppSpec }

class AssessmentCentreIndicatorCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  "North South Indicator Repository" should {
    "parse file with expected number of post code areas" in {
      val result = AssessmentCentreIndicatorCSVRepository.assessmentCentreIndicators
      result.size mustBe 121
    }
  }

  "calculateFsacIndicator" should {
    "return no indicator when in UK but no postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(None, outsideUk = false)
      result mustBe None
    }
    "return default indicator when outside UK and no postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(None, outsideUk = true)
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "return default indicator when in UK and no postcode match is found" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("BOGUS3"), outsideUk = false)
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "return default indicator when in UK for an empty postcode " in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some(""), outsideUk = false)
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "ignore postcode if outside UK and return the default indicator" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("OX1 4DB"), outsideUk = true)
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "return London for Oxford postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("OX1 4DB"), outsideUk = false)
      result mustBe Some("London")
    }
    "return Newcastle for Edinburgh postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("EH1 3EG"), outsideUk = false)
      result mustBe Some("Newcastle")
    }
    "return London even when postcode is lowercase" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("ec1v 3eg"), outsideUk = false)
      result mustBe Some("London")
    }
  }

}
