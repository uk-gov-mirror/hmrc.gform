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

package uk.gov.hmrc.bforms.core

import org.scalatest.{ EitherValues, FlatSpec, Matchers, OptionValues }
import uk.gov.hmrc.bforms.exceptions.InvalidState

/**
 * Created by dimitra on 03/04/17.
 */
class FormatParserSpec extends FlatSpec with Matchers with EitherValues with OptionValues {

  "YYY-MM-DD" should "be passed as it is" in {
    val res = FormatParser.validate("2017-05-06")
    res.right.value should be(GeneralDate)
  }

  "after today -2" should "be parsed successfully" in {
    val res = FormatParser.validate("after today -2")
    res.right.value should be(DateExpression(After, Today, OffsetDate(-2)))
  }

  "after 2017-04-02 -2" should "be parsed successfully" in {
    val res = FormatParser.validate("after 2017-04-02 -2")
    res.right.value should be(DateExpression(After, AnyDate(2017, 4, 2), OffsetDate(-2)))
  }

  "after next-05-06 -2" should "be parsed successfully" in {
    val res = FormatParser.validate("after next-05-06 -2")
    res.right.value should be(DateExpression(After, NextDate(5, 6), OffsetDate(-2)))
  }

  "after sampleField -2" should "be parsed successfully" in {
    val res = FormatParser.validate("after sampleField -2")
    res.right.value should be(DateExpression(After, AnyWord("sampleField"), OffsetDate(-2)))
  }

  "after previous-05-06 0" should "be parsed successfully" in {
    val res = FormatParser.validate("after previous-05-06 0")
    res.right.value should be(DateExpression(After, PreviousDate(5, 6), OffsetDate(0)))
  }

  "before anyFieldId anotherWord 9" should "throw exception" in {
    val res = FormatParser.validate("before anyFieldId anotherWord 9")

    res.left.value should be(
      InvalidState(
        """Unable to parse format expression before anyFieldId anotherWord 9.
          |Errors:
          |before anyFieldId anotherWord 9:1: unexpected characters; expected '(\+|-)?\d+$'
          |before anyFieldId anotherWord 9                  ^""".stripMargin
      )
    )
  }

  "after 2016-6-9 9" should "throw exception" in {
    val res = FormatParser.validate("after 2016-6-9 9")

    res.left.value should be(
      InvalidState(
        """|Unable to parse format expression after 2016-6-9 9.
           |Errors:
           |after 2016-6-9 9:1: unexpected characters; expected '0[1-9]|1[012]' or '\s+'
           |after 2016-6-9 9           ^""".stripMargin
      )
    )
  }

  "before today -2" should "be parsed successfully" in {
    val res = FormatParser.validate("before today -2")
    res.right.value should be(DateExpression(Before, Today, OffsetDate(-2)))
  }

  "before 2017-04-02 -2" should "be parsed successfully" in {
    val res = FormatParser.validate("before 2017-04-02 -2")
    res.right.value should be(DateExpression(Before, AnyDate(2017, 4, 2), OffsetDate(-2)))
  }

}
