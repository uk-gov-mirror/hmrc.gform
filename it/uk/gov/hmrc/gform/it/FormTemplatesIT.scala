/*
 * Copyright 2020 HM Revenue & Customs
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
package uk.gov.hmrc.gform.it

import akka.http.scaladsl.model.StatusCodes
import cats.data.NonEmptyList
import org.scalatest.BeforeAndAfterEach
import org.scalatest.time.{ Millis, Seconds, Span }
import play.api.libs.json.{ JsObject, Json }
import uk.gov.hmrc.gform.Helpers.toSmartString
import uk.gov.hmrc.gform.it.sample.FormTemplateSample
import uk.gov.hmrc.gform.sharedmodel.formtemplate.InternalLink.PrintSummaryPdf
import uk.gov.hmrc.gform.sharedmodel.formtemplate.Section.NonRepeatingPage
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.Destination.HmrcDms
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.DestinationId
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.Destinations.DestinationList
import uk.gov.hmrc.gform.sharedmodel.formtemplate.{ AcknowledgementSection, Anonymous, AuthCtx, ContinueOrDeletePage, DeclarationSection, Default, FormTemplate, FormTemplateRaw, GG, LinkCtx, OnePerUser, Text, TextWithRestrictions, Value }
import uk.gov.hmrc.gform.sharedmodel.{ AvailableLanguages, LangADT, LocalisedString, SmartString }

import scala.concurrent.ExecutionContext.Implicits.global

class FormTemplatesIT extends ITSpec with FormTemplateSample with BeforeAndAfterEach {

  override implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(500, Millis))

  override protected def afterEach(): Unit = {
    formTemplateRepo.removeAll().futureValue
    formTemplateRawRepo.removeAll().futureValue
    ()
  }

  "create template" should "insert the template into mongo db" in {

    Given("I POST a form template")
    val result = post(basicFormTemplate.toString()).to("/formtemplates").send()

    Then("The form template should be saved successfully")
    result.status shouldBe StatusCodes.NoContent.intValue
    result.body shouldBe ""

    val formTemplates = formTemplateRepo.findAll().futureValue
    formTemplates.size shouldBe 2
    formTemplates.map(_._id.value) shouldBe List("specimen-BASIC", "BASIC")
    assertBasicFormTemplate(formTemplates.head)
  }

  "get all templates" should "return all form templates" in {
    Given("I POST a form template")
    post(basicFormTemplate.toString()).to("/formtemplates").send()

    When("I get all form templates")
    val result = get("/formtemplates").send()

    Then("I receive the list of all form templates")
    result.status shouldBe StatusCodes.OK.intValue
    Json.parse(result.body).as[List[String]] shouldBe List("specimen-BASIC", "BASIC")
  }

  "get requested template" should "return the requested template" in {
    Given("I POST a form template")
    post(basicFormTemplate.toString()).to("/formtemplates").send()

    When("I get the form template by id")
    val result = get("/formtemplates/BASIC").send()

    Then("I receive form template")
    result.status shouldBe StatusCodes.OK.intValue
    assertBasicFormTemplate(Json.parse(result.body).as[FormTemplate])
  }

  "get requested template in raw format" should "return the requested template in raw format" in {
    Given("I POST a form template")
    post(basicFormTemplate.toString()).to("/formtemplates").send()

    When("I get the form template by id")
    val result = get("/formtemplates/BASIC/raw").send()

    Then("I receive form template")
    result.status shouldBe StatusCodes.OK.intValue
    Json.parse(result.body).as[JsObject] shouldBe basicFormTemplate
  }

  "delete template" should "delete the requested template" in {
    Given("I POST a form template")
    post(basicFormTemplate.toString()).to("/formtemplates").send()

    When("I delete the form template by id")
    val result = delete("/formtemplates/BASIC").send()

    Then("The template should be deleted successfully")
    result.status shouldBe StatusCodes.NoContent.intValue
    result.body shouldBe ""
    val getTemplateResponse = get("/formtemplates/BASIC").send()
    getTemplateResponse.status shouldBe StatusCodes.NotFound.intValue
  }

  def assertBasicFormTemplate(formTemplate: FormTemplate): Unit = {
    formTemplate.formName shouldBe LocalisedString(Map(LangADT.En -> "Test form name"))
    formTemplate.sections.size shouldBe 1
    formTemplate.sections.head shouldBe a[NonRepeatingPage]
    val nonRepeatingPage = formTemplate.sections.head.asInstanceOf[NonRepeatingPage]
    nonRepeatingPage.page.title shouldBe toSmartString("Page1")
    val nonRepeatingPageFields = nonRepeatingPage.page.fields
    nonRepeatingPageFields.size shouldBe 1
    nonRepeatingPageFields.head.id.value shouldBe "textField1"
    //nonRepeatingPageFields.head.mandatory shouldBe true
    nonRepeatingPageFields.head.derived shouldBe false
    nonRepeatingPageFields.head.submissible shouldBe true
    nonRepeatingPageFields.head.onlyShowOnSummary shouldBe false
    nonRepeatingPageFields.head.editable shouldBe true
    nonRepeatingPageFields.head.label shouldBe toSmartString("Text field 1")
    nonRepeatingPageFields.head.validators shouldBe List.empty
    nonRepeatingPageFields.head.`type` shouldBe Text(TextWithRestrictions(0, 1000), Value)
    formTemplate.draftRetrievalMethod shouldBe OnePerUser(ContinueOrDeletePage.Show)
    formTemplate.formCategory shouldBe Default
    formTemplate.languages shouldBe AvailableLanguages.default
    formTemplate.parentFormSubmissionRefs shouldBe List.empty
    formTemplate.summarySection.title shouldBe toSmartString("Check your answers", "Gwiriwch eich atebion")
    formTemplate.summarySection.header shouldBe toSmartString(
      "Make sure the information you have given is correct",
      "Gwnewch yn siŵr bod yr wybodaeth a roddwyd gennych yn gywir"
    )
    formTemplate.summarySection.footer shouldBe SmartString(
      LocalisedString(
        Map(
          LangADT.En -> "##Now send your form\n\nYou need to submit your form on the next screen.\n\nBefore you do this you can [print or save a PDF copy of your answers (opens in a new window or tab)]({0}).",
          LangADT.Cy -> "##Nawr anfonwch eich ffurflen\n\nMae angen i chi gyflwyno’ch ffurflen ar y sgrin nesaf.\n\nCyn i chi wneud hyn gallwch [argraffu neu gadw copi PDF o’ch atebion (yn agor ffenestr neu dab newydd)]({1})."
        )
      ),
      List(LinkCtx(link = PrintSummaryPdf), LinkCtx(link = PrintSummaryPdf))
    )
    formTemplate.authConfig shouldBe Anonymous
    formTemplate.displayHMRCLogo shouldBe true
    formTemplate.destinations shouldBe a[DestinationList]
    formTemplate.emailTemplateId shouldBe "email_template_id"
    val destinationList = formTemplate.destinations.asInstanceOf[DestinationList]
    destinationList.destinations shouldBe NonEmptyList.one(
      HmrcDms(
        DestinationId("HMRCDMS"),
        "TSTHMRCDMS",
        AuthCtx(GG),
        "ClassificationType",
        "BusinessArea",
        "true",
        true,
        false,
        false,
        None,
        false
      )
    )
    destinationList.acknowledgementSection shouldBe AcknowledgementSection(
      toSmartString("Acknowledgement Page"),
      None,
      None,
      List.empty,
      true,
      None,
      None,
      true
    )
    destinationList.declarationSection shouldBe DeclarationSection(
      toSmartString("Declaration Page"),
      None,
      None,
      None,
      List.empty
    )

    val formTemplateRaw = formTemplateRawRepo.find("_id" -> "BASIC").futureValue
    formTemplateRaw shouldBe List(FormTemplateRaw(basicFormTemplate.as[JsObject]))
    ()
  }
}
