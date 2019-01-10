/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.gform.submission.handlebars

import java.text.DecimalFormat

import uk.gov.hmrc.gform.Spec
import HandlebarsTemplateProcessorHelpers._
import org.scalacheck.Gen

class HandlebarsTemplateProcessorHelpersSpec extends Spec {
  "yesNoToEtmpChoice" must "return 1 when 0 is passed in" in {
    yesNoToEtmpChoice("0") shouldBe "1"
  }

  it must "return 0 when 1 is passed in" in {
    yesNoToEtmpChoice("1") shouldBe "0"
  }

  "dateToEtmpDate" must "return null when null is passed in" in {
    dateToEtmpDate(null) shouldBe null
  }

  it must "convert a date of the form yyyy-mm-dd into yyyymmdd" in {
    forAll(Gen.chooseNum[Long](1900, 2100), Gen.chooseNum[Long](1, 12), Gen.chooseNum[Long](1, 31)) { (y, m, d) =>
      val yearFormat = new DecimalFormat("0000")
      val monthAndDayFormat = new DecimalFormat("00")
      val inputDate = s"${yearFormat.format(y)}/${monthAndDayFormat.format(m)}/${monthAndDayFormat.format(d)}"
      val outputDate = s"${yearFormat.format(y)}${monthAndDayFormat.format(m)}${monthAndDayFormat.format(d)}"

      dateToEtmpDate(inputDate) shouldBe outputDate
    }
  }

  "either" must "select the first argument if it is non-null" in {
    forAll(Gen.alphaNumStr, Gen.alphaNumStr) { (v1, v2) =>
      either(v1, v2) shouldBe v1
      either(v1, null) shouldBe v1
    }
  }

  it must "select the second argument if the first is null" in {
    forAll(Gen.alphaNumStr) { v2 =>
      either(null, v2) shouldBe v2
    }
  }

  it must "return null if both arguments are null" in {
    either(null, null) shouldBe null
  }
}
