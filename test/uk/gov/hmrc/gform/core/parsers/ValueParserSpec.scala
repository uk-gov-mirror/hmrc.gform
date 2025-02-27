/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.gform.core.parsers

import cats.Eval
import cats.data.NonEmptyList
import org.scalatest.prop.TableDrivenPropertyChecks
import parseback.LineStream
import parseback.compat.cats._
import parseback.util.Catenable
import parseback.util.Catenable.Single

import scala.language.implicitConversions
import uk.gov.hmrc.gform.Helpers._
import uk.gov.hmrc.gform.Spec
import uk.gov.hmrc.gform.core._
import uk.gov.hmrc.gform.exceptions.UnexpectedState
import uk.gov.hmrc.gform.formtemplate._
import uk.gov.hmrc.gform.sharedmodel.AvailableLanguages
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.Destination.HmrcDms
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.{ DestinationId, Destinations }

import scala.language.implicitConversions

class ValueParserSpec extends Spec with TableDrivenPropertyChecks {

  implicit def implicitToFormComponentId(str: String): FormComponentId = FormComponentId(str)

  "ValueParser" should "parse integer" in {
    val res = ValueParser.validate("${1}")
    res.right.value should be(TextExpression(Constant("1")))
  }
  it should "parse integer with decimal point" in {
    val res = ValueParser.validate("${1.}")
    res.right.value should be(TextExpression(Constant("1.")))
  }

  it should "parse multi digit integer" in {
    val res = ValueParser.validate("${12}")
    res.right.value should be(TextExpression(Constant("12")))
  }
  it should "parse an empty string" in {
    val res = ValueParser.validate("${''}")
    res.right.value should be(TextExpression(Constant("")))
  }
  it should "parse an anything except singe quote" in {
    val res = ValueParser.validate("${' --+===> '}")
    res.right.value should be(TextExpression(Constant("--+===> ")))
  }
  it should "parse double digit integer" in {
    val res = ValueParser.validate("${1234}")
    res.right.value should be(TextExpression(Constant("1234")))
  }

  it should "parse multi digit integer with thousand separators" in {
    val res = ValueParser.validate("${1,234}")
    res.right.value should be(TextExpression(Constant("1,234")))
  }

  it should "parse double digit decimal fraction" in {
    val res = ValueParser.validate("${.12}")
    res.right.value should be(TextExpression(Constant(".12")))
  }

  it should "parse multi digit decimal fraction" in {
    val res = ValueParser.validate("${.123}")
    res.right.value should be(TextExpression(Constant(".123")))
  }

  it should "parse number plus decimal fraction" in {
    val res = ValueParser.validate("${1,234.1}")
    res.right.value should be(TextExpression(Constant("1,234.1")))
  }

  it should "parse number plus double digit decimal fraction" in {
    val res = ValueParser.validate("${1,234.12}")
    res.right.value should be(TextExpression(Constant("1,234.12")))
  }

  it should "parse number plus multi digit decimal fraction" in {
    val res = ValueParser.validate("${1,234.123}")
    res.right.value should be(TextExpression(Constant("1,234.123")))
  }

  it should "parse ${firstName}" in {
    val res = ValueParser.validate("${firstName}")
    res.right.value should be(TextExpression(FormCtx("firstName")))
  }

  it should "parse ${age - 1}" in {
    val res = ValueParser.validate("${age - 1}")
    res.right.value should be(TextExpression(Subtraction(FormCtx("age"), Constant("1"))))
  }

  it should "parse ${age -1}" in {
    val res = ValueParser.validate("${age -1}")
    res.right.value should be(TextExpression(Subtraction(FormCtx("age"), Constant("1"))))
  }

  it should "parse ${age-1}" in {
    val res = ValueParser.validate("${age-1}")
    res.right.value should be(TextExpression(Subtraction(FormCtx("age"), Constant("1"))))
  }

  it should "parse ${form.firstName}" in {
    val res = ValueParser.validate("${form.firstName}")
    res.right.value should be(TextExpression(FormCtx("firstName")))
  }

  it should "parse ${user.enrolledIdentifier}" in {
    val res = ValueParser.validate("${user.enrolledIdentifier}")
    res.right.value should be(TextExpression(UserCtx(UserField.EnrolledIdentifier)))
  }

  it should "parse anything except singe quote" in {
    val res = ValueParser.validate("${' --+===>., '}")
    res.right.value should be(TextExpression(Constant("--+===>., ")))
  }

  it should "fail to parse ${user.enrolledIdentifier" in {

    val res = ValueParser.validate("${user.enrolledIdentifier")
    res.left.value should be(
      UnexpectedState("""Unable to parse expression ${user.enrolledIdentifier.
                        |Errors:
                        |${user.enrolledIdentifier: unexpected end-of-file; expected '}'""".stripMargin)
    )
  }

  it should "fail to parse ${user.enrolledIdentifiers}" in {

    val res = ValueParser.validate("${user.enrolledIdentifiers}")
    res.left.value should be(
      UnexpectedState(
        """Unable to parse expression ${user.enrolledIdentifiers}.
          |Errors:
          |${user.enrolledIdentifiers}:1: unexpected characters; expected '+' or '}' or '\s+' or '*' or '-' or 'else'
          |${user.enrolledIdentifiers}                         ^""".stripMargin
      )
    )
  }

  it should "parse ${user.enrolments.<identifierName>.<referenceName>}" in {

    val validIdentifiersCombinations = Table(
      // format: off
      ("serviceName", "identifierName"),
      ("HMRC-AS-AGENT", "AgentReferenceNumber"),
      ("test-)(*&^%$#@@@)", ")(*&^%$#@@@)")
      // format: on
    )

    val invalidIdentifiersCombinations = Table(
      // format: off
      ("serviceName", "identifierName"),
      ("HMRC.AA.AGENT", "AgentReferenceNumber"),  // serviceName    cannot include `.`
      ("HMRC AS-AGENT", "AgentReferenceNumber"),  // serviceName    cannot include ` `
      ("HMRC-AS-AGENT", "Agent.ReferenceNumber"), // identifierName cannot include `.`
      ("HMRC-AS-AGENT", "Agent}ReferenceNumber"), // identifierName cannot include `}`
      ("HMRC-AS-AGENT", "Agent ReferenceNumber"), // identifierName cannot include ` `
      ("HMRC-AS-AGENT", "Agent=ReferenceNumber")  // identifierName cannot include `=`
      // format: on
    )

    forAll(validIdentifiersCombinations) { (serviceName, identifierName) ⇒
      val res = ValueParser.validate(s"$${user.enrolments.$serviceName.$identifierName}")
      res.right.value should be(
        TextExpression(UserCtx(UserField.Enrolment(ServiceName(serviceName), IdentifierName(identifierName))))
      )
    }

    forAll(invalidIdentifiersCombinations) { (serviceName, identifierName) ⇒
      val res = ValueParser.validate("${user.enrolments." + serviceName + "." + identifierName + "}")
      res.left.value.error should include(
        s"Unable to parse expression $${user.enrolments.$serviceName.$identifierName}"
      )
    }
  }

  it should "parse ${someId.sum}" in {
    val res = ValueParser.validate("${age.sum}")

    res.right.value should be(TextExpression(Sum(FormCtx("age"))))
  }

  it should "parse ${firstName * secondName}" in {
    val res = ValueParser.validate("${firstName * secondName}")
    res.right.value should be(TextExpression(Multiply(FormCtx("firstName"), FormCtx("secondName"))))
  }

  it should "parse ${firstName * auth.secondName}" in {
    val res = ValueParser.validate("${firstName * auth.sautr}")
    res.right.value should be(TextExpression(Multiply(FormCtx("firstName"), AuthCtx(SaUtr))))
  }

  it should "parse ${auth.email}" in {
    val res = ValueParser.validate("${auth.email}")
    res.right.value should be(TextExpression(AuthCtx(EmailId)))
  }

  it should "parse ${a - b  * c}" in {
    val res = ValueParser.validate("${firstName - secondName * thirdname}")
    res.right.value should be(
      TextExpression(Subtraction(FormCtx("firstName"), Multiply(FormCtx("secondName"), FormCtx("thirdname"))))
    )
  }

  it should "parse string constant" in {
    val res = ValueParser.validate("'constant'")
    res.right.value should be(TextExpression(Constant("constant")))
  }

  it should "parse string constant including space" in {
    val res = ValueParser.validate("'const ant'")
    res.right.value should be(TextExpression(Constant("const ant")))
  }

  it should "parse number as a choice selections" in {
    val res = ValueParser.validate("1")
    res.right.value should be(ChoiceExpression(List(1)))
  }

  it should "parse numbers separated by comma as a choice selections" in {
    val res = ValueParser.validate("1,2,3,4")
    res.right.value should be(ChoiceExpression(List(1, 2, 3, 4)))
  }

  /** Date cases
    */
  it should "parse Date" in {
    val res = ValueParser.validate("2015-01-15")
    res.right.value should be(DateExpression(ExactDateValue(2015, 1, 15)))
  }

  it should "parse lastDay" in {
    val res = ValueParser.validate("2015-01-lastDay")
    res.right.value should be(DateExpression(ExactDateValue(2015, 1, 31)))
  }

  it should "parse firstDay" in {
    val res = ValueParser.validate("2015-01-firstDay")
    res.right.value should be(DateExpression(ExactDateValue(2015, 1, 1)))
  }

  it should "throw exception on 1 digit month " in {
    val res = ValueParser.validate("2015-1-12")
    res.left.value should be(UnexpectedState("""Unable to parse expression 2015-1-12.
                                               |Errors:
                                               |2015-1-12:1: unexpected characters; expected '0[1-9]|1[012]' or '\s+'
                                               |2015-1-12     ^""".stripMargin))
  }

  it should "throw exception on year digits" in {
    val res = ValueParser.validate("201568-01-12")
    res.left.value should be(UnexpectedState("""Unable to parse expression 201568-01-12.
                                               |Errors:
                                               |201568-01-12:1: unexpected characters; expected ',' or '\s+'
                                               |201568-01-12      ^""".stripMargin))
  }

  it should "throw exception on Date format" in {
    val res = ValueParser.validate("65841-351")
    res.left.value should be(UnexpectedState("""Unable to parse expression 65841-351.
                                               |Errors:
                                               |65841-351:1: unexpected characters; expected ',' or '\s+'
                                               |65841-351     ^""".stripMargin))
  }

  it should "parse next Date setting next year" in {
    val res = ValueParser.validate("next-01-15")

    res.right.value should be(DateExpression(NextDateValue(1, 15)))
  }

  it should "parse next Date setting current year" in {
    val res = ValueParser.validate("next-04-15")

    res.right.value should be(DateExpression(NextDateValue(4, 15)))
  }

  it should "parse last Date setting current year" in {
    val res = ValueParser.validate("last-01-15")

    res.right.value should be(DateExpression(PreviousDateValue(1, 15)))
  }

  it should "parse last Date setting previous year" in {
    val res = ValueParser.validate("last-04-15")

    res.right.value should be(DateExpression(PreviousDateValue(4, 15)))
  }

  it should "parse Date setting current Date" in {
    val res = ValueParser.validate("today")

    res.right.value should be(DateExpression(TodayDateValue))
  }

  it should "parse submissionReference" in {
    val res = ValueParser.validate("${form.submissionReference}")

    res.right.value should be(TextExpression(FormTemplateCtx(FormTemplateProp.SubmissionReference)))
  }

  it should "parse else expression" in {
    implicit def liftToFormCtx(s: String): FormCtx = FormCtx(s)
    val table = Table(
      ("expression", "catenable"),
      // format: off
      ("a + b + c + d",              Add(Add(Add("a", "b"), "c"), "d")),
      ("(a + b) + c + d",            Add(Add(Add("a", "b"), "c"), "d")),
      ("(a + b + c) + d",            Add(Add(Add("a", "b"), "c"), "d")),
      ("((a + b) + c) + d",          Add(Add(Add("a", "b"), "c"), "d")),
      ("a + (b + c + d)",            Add("a", Add(Add("b", "c"), "d"))),
      ("a + (b + (c + d))",          Add("a", Add("b", Add("c", "d")))),
      ("(a + b) + (c + d)",          Add(Add("a", "b"), Add("c", "d"))),
      ("a + b - c + d",              Add(Add("a", Subtraction("b", "c")), "d")),
      ("a + (b - c) + d",            Add(Add("a", Subtraction("b", "c")), "d")),
      ("(a + b) - (c + d)",          Subtraction(Add("a", "b"), Add("c", "d"))),
      ("a + b * c + d",              Add(Add("a", Multiply("b", "c")), "d")),
      ("a + (b * c) + d",            Add(Add("a", Multiply("b", "c")), "d")),
      ("(a + b) * (c + d)",          Multiply(Add("a", "b"), Add("c", "d"))),
      ("a + b else c + d",           Add(Add("a", Else("b", "c")), "d")),
      ("a + (b else c) + d",         Add(Add("a", Else("b", "c")), "d")),
      ("(a + b) else (c + d)",       Else(Add("a", "b"), Add("c", "d"))),
      ("a - b + c - d",              Add(Subtraction("a", "b"), Subtraction("c", "d"))),
      ("a - b - c - d",              Subtraction(Subtraction(Subtraction("a", "b"), "c"), "d")),
      ("a - (b - (c - d))",          Subtraction("a", Subtraction("b", Subtraction("c", "d")))),
      ("a - b * c - d",              Subtraction(Subtraction("a", Multiply("b", "c")), "d")),
      ("a - b else c - d",           Subtraction(Subtraction("a", Else("b", "c")), "d")),
      ("a * b + c * d",              Add(Multiply("a", "b"), Multiply("c", "d"))),
      ("a * b - c * d",              Subtraction(Multiply("a", "b"), Multiply("c", "d"))),
      ("a * b * c * d",              Multiply(Multiply(Multiply("a", "b"), "c"), "d")),
      ("a * (b * (c * d))",          Multiply("a", Multiply("b", Multiply("c", "d")))),
      ("a * b else c * d",           Multiply(Multiply("a", Else("b", "c")), "d")),
      ("a else b + c else d",        Add(Else("a", "b"), Else("c", "d"))),
      ("a else b - c else d",        Subtraction(Else("a", "b"), Else("c", "d"))),
      ("a else b * c else d",        Multiply(Else("a", "b"), Else("c", "d"))),
      ("a else b else c else d",     Else("a", Else("b", Else("c", "d")))),
      ("a else (b else (c else d))", Else("a", Else("b", Else("c", "d"))))
      // format: on
    )

    forAll(table) { (expression, expected) ⇒
      val res = ValueParser.validate("${" + expression + "}")

      res.right.value shouldBe TextExpression(expected)
    }
  }

  it should "else expressions should be rewriten to nesting on right hand side" in {
    implicit def liftToFormCtx(s: String): FormCtx = FormCtx(s)
    val table = Table(
      ("expression", "catenable"),
      // format: off
      (Add(Add(Add("a", "b"), "c"), "d"),               Add(Add(Add("a", "b"), "c"), "d")),
      (Else(Add("a", "b"), Add("c", "d")),              Else(Add("a", "b"), Add("c", "d"))),
      (Else(Else(Else(Else("a", "b"), "c"), "d"), "e"), Else("a", Else("b", Else("c", Else("d", "e"))))),
      (Else("a", Else("b", Else("c", "d"))),            Else("a", Else("b", Else("c", "d")))),
      (Add(Else("a", "b"), Else("c", "d")),             Add(Else("a", "b"), Else("c", "d"))),
      (Add(Else(Else("a", "b"), "c"), "d"),             Add(Else("a", Else("b", "c")), "d")),
      (Subtraction(Else(Else("a", "b"), "c"), "d"),     Subtraction(Else("a", Else("b", "c")), "d")),
      (Multiply(Else(Else("a", "b"), "c"), "d"),        Multiply(Else("a", Else("b", "c")), "d")),
      (Sum(Else(Else("a", "b"), "c")),                  Sum(Else("a", Else("b", "c"))))
      // format: on
    )

    forAll(table) { (expression, expected) ⇒
      expression.rewrite shouldBe expected
    }
  }

  it should "fail parse unclosed parenthesis" in {
    val res = ValueParser.validate("${name")
    res.left.value should be(UnexpectedState("""|Unable to parse expression ${name.
                                                |Errors:
                                                |${name: unexpected end-of-file; expected '}'""".stripMargin))
  }

  val plainFormTemplate = FormTemplate(
    FormTemplateId("IPT100"),
    toLocalisedString("Insurance Premium Tax Return"),
    Some(ResearchBanner),
    Default,
    OnePerUser(ContinueOrDeletePage.Show),
    Destinations
      .DestinationList(
        NonEmptyList.of(
          HmrcDms(
            DestinationId("TestHmrcDmsId"),
            "TestHmrcDmsFormId",
            Constant("TestHmrcDmsCustomerId"),
            "TestHmrcDmsClassificationType",
            "TestHmrcDmsBusinessArea",
            "",
            true,
            true,
            true,
            Some(true),
            true
          )
        ),
        ackSection,
        DeclarationSection(
          toSmartString("Declaration"),
          None,
          None,
          Some(toSmartString("ContinueLabel")),
          Nil
        )
      ),
    HmrcAgentWithEnrolmentModule(
      RequireMTDAgentEnrolment,
      EnrolmentAuth(ServiceId("TEST"), DoCheck(Always, RejectAccess, RegimeIdCheck(RegimeId("TEST"))))
    ),
    "test-email-template-id",
    Some(
      NonEmptyList.of(
        EmailParameter("fullName", FormCtx("directorFullName")),
        EmailParameter("email", FormCtx("directorEmail"))
      )
    ),
    None,
    List.empty[Section],
    Nil,
    AvailableLanguages.default,
    None,
    SummarySection(
      toSmartString("Title"),
      toSmartString("Header"),
      toSmartString("Footer"),
      Some(toSmartString("ContinueLabel"))
    ),
    true
  )

  val yourDetailsSection = Section.NonRepeatingPage(
    Page(
      toSmartString("Your details"),
      None,
      None,
      None,
      None,
      None,
      List(
        FormComponent(
          FormComponentId("firstName"),
          Text(ShortText.default, Value),
          toSmartString("Your first name"),
          None,
          None,
          includeIf = None,
          validIf = None,
          mandatory = false,
          editable = true,
          submissible = true,
          derived = false,
          errorMessage = None
        ),
        FormComponent(
          FormComponentId("lastName"),
          Text(ShortText.default, Value),
          toSmartString("Your last name"),
          None,
          None,
          includeIf = None,
          validIf = None,
          mandatory = false,
          editable = true,
          submissible = true,
          derived = false,
          errorMessage = None
        )
      ),
      None,
      None,
      None,
      None
    )
  )

  val formTemplateWithOneSection = plainFormTemplate.copy(sections = List(yourDetailsSection))

  "Expr.validate" should "return Valid if expression include fieldName id present in the form template" in {

    val res = FormTemplateValidator
      .validate(List(Text(ShortText.default, FormCtx("firstName"))), formTemplateWithOneSection)
    res should be(Valid)
  }

  it should "return Valid if expression Add fields present in the form template" in {
    val res =
      FormTemplateValidator
        .validate(
          List(Text(ShortText.default, Add(FormCtx("firstName"), FormCtx("lastName")))),
          formTemplateWithOneSection
        )
    res should be(Valid)
  }

  it should "return Valid if expression Multiply fields present in the form template" in {
    val res =
      FormTemplateValidator
        .validate(
          List(Text(ShortText.default, Multiply(FormCtx("firstName"), FormCtx("lastName")))),
          formTemplateWithOneSection
        )
    res should be(Valid)
  }

  it should "return Invalid if expression include fieldName id not present in the form template" in {
    val res = FormTemplateValidator
      .validate(List(Text(ShortText.default, FormCtx("firstNameTypo"))), formTemplateWithOneSection)
    res should be(Invalid("Form field 'firstNameTypo' is not defined in form template."))
  }
  it should "return invalid and not be parsed as empty string" in {
    val res = FormTemplateValidator
      .validate(List(Text(ShortText.default, FormCtx("'' * ''"))), formTemplateWithOneSection)
    res should be(Invalid("Form field ''' * ''' is not defined in form template."))
  }

  "Parser - contextField" should "parse form field with date offset as DateCtx (form.)" in {
    val result = ValueParser.contextField(LineStream[Eval]("form.dateField + 1d")).value.toOption
    result shouldBe Some(
      Single(
        DateCtx(DateExprWithOffset(DateFormCtxVar(FormCtx(FormComponentId("dateField"))), OffsetYMD(OffsetUnit.Day(1))))
      )
    )
  }

  it should "parse form field with date offset as DateCtx" in {
    val result = ValueParser.contextField(LineStream[Eval]("dateField + 1d")).value.toOption.flatMap(uncons)
    result shouldBe Some(
      DateCtx(DateExprWithOffset(DateFormCtxVar(FormCtx(FormComponentId("dateField"))), OffsetYMD(OffsetUnit.Day(1))))
    )
  }

  it should "parse TODAY as DateCtx" in {
    val result = ValueParser.contextField(LineStream[Eval]("TODAY")).value.toOption.flatMap(uncons)
    result shouldBe Some(DateCtx(DateValueExpr(TodayDateExprValue)))
  }

  it should "parse TODAY with offset as DateCtx" in {
    val result = ValueParser.contextField(LineStream[Eval]("TODAY + 1m")).value.toOption.flatMap(uncons)
    result shouldBe Some(DateCtx(DateExprWithOffset(DateValueExpr(TodayDateExprValue), OffsetYMD(OffsetUnit.Month(1)))))
  }

  it should "parse TODAY with offset as DateCtx y m d" in {
    val result = ValueParser.contextField(LineStream[Eval]("TODAY + 2y + 3m + 4d")).value.toOption.flatMap(uncons)
    result shouldBe Some(
      DateCtx(
        DateExprWithOffset(
          DateValueExpr(TodayDateExprValue),
          OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Month(3), OffsetUnit.Day(4))
        )
      )
    )
  }

  it should "parse fixed date string as DateCtx" in {
    val result = ValueParser.contextField(LineStream[Eval]("01012020")).value.toOption.flatMap(uncons)
    result shouldBe Some(DateCtx(DateValueExpr(ExactDateExprValue(2020, 1, 1))))
  }

  private def uncons[A](cat: Catenable[A]) =
    cat.uncons match {
      case Some((expr, _)) =>
        Some(expr)
      case None => None
    }

  it should "support year/month/day offset for dates" in {
    val today = DateValueExpr(TodayDateExprValue)
    val fieldReference = DateFormCtxVar(FormCtx("dateField"))
    val table = Table(
      ("expression", "dateExpr", "offset"),
      ("TODAY + 2y + 3m + 4d", today, OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Month(3), OffsetUnit.Day(4))),
      ("TODAY + 2y + 3d + 4m", today, OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Day(3), OffsetUnit.Month(4))),
      ("TODAY + 2d + 3m + 4y", today, OffsetYMD(OffsetUnit.Day(2), OffsetUnit.Month(3), OffsetUnit.Year(4))),
      ("TODAY + 2d + 3y + 4m", today, OffsetYMD(OffsetUnit.Day(2), OffsetUnit.Year(3), OffsetUnit.Month(4))),
      ("TODAY + 2m + 3d + 4y", today, OffsetYMD(OffsetUnit.Month(2), OffsetUnit.Day(3), OffsetUnit.Year(4))),
      ("TODAY + 2m + 3y + 4d", today, OffsetYMD(OffsetUnit.Month(2), OffsetUnit.Year(3), OffsetUnit.Day(4))),
      ("TODAY + 2y + 3m", today, OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Month(3))),
      ("TODAY + 2m + 3y", today, OffsetYMD(OffsetUnit.Month(2), OffsetUnit.Year(3))),
      ("TODAY + 2y + 4d", today, OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Day(4))),
      ("TODAY + 2d + 4y", today, OffsetYMD(OffsetUnit.Day(2), OffsetUnit.Year(4))),
      ("TODAY + 3m + 4d", today, OffsetYMD(OffsetUnit.Month(3), OffsetUnit.Day(4))),
      ("TODAY + 3d + 4m", today, OffsetYMD(OffsetUnit.Day(3), OffsetUnit.Month(4))),
      ("TODAY + 2y", today, OffsetYMD(OffsetUnit.Year(2))),
      ("TODAY + 2m", today, OffsetYMD(OffsetUnit.Month(2))),
      ("TODAY + 2d", today, OffsetYMD(OffsetUnit.Day(2))),
      ("TODAY - 2y - 3m - 4d", today, OffsetYMD(OffsetUnit.Year(-2), OffsetUnit.Month(-3), OffsetUnit.Day(-4))),
      ("TODAY - 2y - 3m", today, OffsetYMD(OffsetUnit.Year(-2), OffsetUnit.Month(-3))),
      ("TODAY - 2y - 4d", today, OffsetYMD(OffsetUnit.Year(-2), OffsetUnit.Day(-4))),
      ("TODAY - 3m - 4d", today, OffsetYMD(OffsetUnit.Month(-3), OffsetUnit.Day(-4))),
      ("TODAY - 2y", today, OffsetYMD(OffsetUnit.Year(-2))),
      ("TODAY - 2m", today, OffsetYMD(OffsetUnit.Month(-2))),
      ("TODAY - 2d", today, OffsetYMD(OffsetUnit.Day(-2))),
      (
        "dateField + 2y + 3m + 4d",
        fieldReference,
        OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Month(3), OffsetUnit.Day(4))
      ),
      ("dateField + 2y + 3m", fieldReference, OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Month(3))),
      ("dateField + 2y + 4d", fieldReference, OffsetYMD(OffsetUnit.Year(2), OffsetUnit.Day(4))),
      ("dateField + 3m + 4d", fieldReference, OffsetYMD(OffsetUnit.Month(3), OffsetUnit.Day(4))),
      ("dateField + 2y", fieldReference, OffsetYMD(OffsetUnit.Year(2))),
      ("dateField + 2m", fieldReference, OffsetYMD(OffsetUnit.Month(2))),
      ("dateField + 2d", fieldReference, OffsetYMD(OffsetUnit.Day(2))),
      (
        "dateField - 2y - 3m - 4d",
        fieldReference,
        OffsetYMD(OffsetUnit.Year(-2), OffsetUnit.Month(-3), OffsetUnit.Day(-4))
      ),
      ("dateField - 2y - 3m", fieldReference, OffsetYMD(OffsetUnit.Year(-2), OffsetUnit.Month(-3))),
      ("dateField - 2y - 4d", fieldReference, OffsetYMD(OffsetUnit.Year(-2), OffsetUnit.Day(-4))),
      ("dateField - 3m - 4d", fieldReference, OffsetYMD(OffsetUnit.Month(-3), OffsetUnit.Day(-4))),
      ("dateField - 2y", fieldReference, OffsetYMD(OffsetUnit.Year(-2))),
      ("dateField - 2m", fieldReference, OffsetYMD(OffsetUnit.Month(-2))),
      ("dateField - 2d", fieldReference, OffsetYMD(OffsetUnit.Day(-2)))
    )

    def expected(dateExpr: DateExpr, offset: OffsetYMD) = TextExpression(DateCtx(DateExprWithOffset(dateExpr, offset)))

    forAll(table) { (expression, dateExpr, offset) ⇒
      ValueParser.validate("${" + expression + "}") shouldBe Right(expected(dateExpr, offset))
    }
  }

  it should "not support repeated year/month/day offset" in {
    val table = Table(
      "expression",
      "TODAY + 2y + 3m + 4m",
      "TODAY + 2y + 3y",
      "TODAY + 2m + 3m",
      "TODAY + 2d + 3d"
    )

    forAll(table) { expression ⇒
      ValueParser.validate("${" + expression + "}").isLeft shouldBe true
    }
  }
}
