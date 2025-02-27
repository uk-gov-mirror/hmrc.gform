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

package uk.gov.hmrc.gform.formtemplate

import uk.gov.hmrc.gform.core.{ FOpt, fromOptA }
import uk.gov.hmrc.gform.exceptions.UnexpectedState
import uk.gov.hmrc.gform.sharedmodel.formtemplate._
import cats.implicits._
import scala.util.{ Failure, Success, Try }
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.Destinations

trait Rewriter {
  def rewrite(formTemplate: FormTemplate): FOpt[FormTemplate] = fromOptA(validateAndRewriteBooleanExprs(formTemplate))

  private def mkComponentTypeLookup(formComponent: FormComponent): Map[FormComponentId, ComponentType] = {
    val mainComponent: Map[FormComponentId, ComponentType] = Map(formComponent.id -> formComponent.`type`)
    val subComponent: Map[FormComponentId, ComponentType] = formComponent match {
      case IsGroup(group) => group.fields.map(fc => fc.id -> fc.`type`).toMap
      case IsRevealingChoice(revealingChoice) =>
        revealingChoice.options.toList.flatMap(_.revealingFields).map(fc => fc.id -> fc.`type`).toMap
      case _ => Map.empty[FormComponentId, ComponentType]
    }
    mainComponent ++ subComponent
  }

  private def lookupFromPage(fields: List[FormComponent]): Map[FormComponentId, ComponentType] =
    fields.foldLeft(Map.empty[FormComponentId, ComponentType])((acc, fc) => mkComponentTypeLookup(fc) ++ acc)

  private def missingFormComponentId[A](formComponentId: FormComponentId): Either[UnexpectedState, A] =
    Left(UnexpectedState(s"Missing component with id $formComponentId"))

  private def nonNestedFormComponentValidIf(formComponent: FormComponent): List[ValidIf] =
    formComponent.validIf.toList ++ formComponent.validators.map(_.validIf)

  private def formComponentValidIf(formComponent: FormComponent): List[ValidIf] =
    fetchFromFormComponent(nonNestedFormComponentValidIf)(formComponent)

  private def formComponentIncludeIf(formComponent: FormComponent): List[IncludeIf] =
    fetchFromFormComponent(_.includeIf.toList)(formComponent)

  private def fetchFromFormComponent[A](f: FormComponent => List[A])(formComponent: FormComponent): List[A] =
    formComponent match {
      case fc @ IsGroup(group) => f(fc) ++ group.fields.flatMap(fetchFromFormComponent(f))
      case fc @ IsRevealingChoice(revealingChoice) =>
        f(fc) ++ revealingChoice.options.toList
          .flatMap(_.revealingFields)
          .flatMap(fetchFromFormComponent(f))
      case fc => f(fc)
    }

  private def validateAndRewriteBooleanExprs(formTemplate: FormTemplate): Either[UnexpectedState, FormTemplate] = {

    val fcLookupDeclaration: Map[FormComponentId, ComponentType] = formTemplate.destinations match {
      case dl: Destinations.DestinationList  => lookupFromPage(dl.declarationSection.fields)
      case dp: Destinations.DestinationPrint => Map.empty[FormComponentId, ComponentType]
    }

    val fcLookup: Map[FormComponentId, ComponentType] =
      formTemplate.sections.foldLeft(fcLookupDeclaration) {
        case (acc, Section.NonRepeatingPage(page)) => acc ++ lookupFromPage(page.fields)
        case (acc, Section.RepeatingPage(page, _)) => acc ++ lookupFromPage(page.fields)
        case (acc, Section.AddToList(_, _, _, _, _, _, pages, _, _, _, _)) =>
          acc ++ pages.toList.flatMap(page => lookupFromPage(page.fields))
      }

    val validIfsDeclaration = formTemplate.destinations match {
      case dl: Destinations.DestinationList  => dl.declarationSection.fields.flatMap(formComponentValidIf)
      case dp: Destinations.DestinationPrint => Nil
    }

    def traverseFormComponents[A](f: FormComponent => List[A]): List[A] = formTemplate.sections.flatMap {
      case Section.NonRepeatingPage(page) => page.fields.flatMap(f)
      case Section.RepeatingPage(page, _) => page.fields.flatMap(f)
      case Section.AddToList(_, _, _, _, _, _, pages, _, _, _, _) =>
        pages.toList.flatMap(page => page.fields.flatMap(f))
    }

    val validIfs: List[ValidIf] = traverseFormComponents(formComponentValidIf) ++ validIfsDeclaration

    val fieldsIncludeIfs: List[IncludeIf] = traverseFormComponents(formComponentIncludeIf)

    val includeIfs: List[IncludeIf] = formTemplate.sections.flatMap {
      case Section.NonRepeatingPage(page) => page.includeIf.toList
      case Section.RepeatingPage(page, _) => page.includeIf.toList
      case Section.AddToList(_, _, _, _, includeIf, _, pages, _, _, _, _) =>
        includeIf.toList ++ pages.toList.flatMap(_.includeIf.toList)
    } ++ fieldsIncludeIfs

    def validate(
      c: String,
      optionsSize: Int,
      formComponentId: FormComponentId,
      exprString: String,
      componentDescription: String
    ): Either[UnexpectedState, Unit] =
      Try(c.toInt) match {
        case Success(index) =>
          val maxIndex = optionsSize - 1
          if (maxIndex < index) {
            Left(
              UnexpectedState(
                s"Expression '$exprString' has wrong index $c. $componentDescription $formComponentId has only $optionsSize elements. Use index from 0 to $maxIndex"
              )
            )
          } else Right(())
        case Failure(f) =>
          Left(UnexpectedState(s"Expression '$exprString' is invalid. '$c' needs to be a number"))
      }

    def rewrite(booleanExpr: BooleanExpr): Either[UnexpectedState, BooleanExpr] = booleanExpr match {
      case Not(booleanExpr) => rewrite(booleanExpr).map(Not(_))
      case And(booleanExprL, booleanExprR) =>
        for {
          l <- rewrite(booleanExprL)
          r <- rewrite(booleanExprR)
        } yield And(l, r)
      case Or(booleanExprL, booleanExprR) =>
        for {
          l <- rewrite(booleanExprL)
          r <- rewrite(booleanExprR)
        } yield Or(l, r)
      case be @ Contains(ctx @ FormCtx(formComponentId), Constant(c)) =>
        val exprString = s"$formComponentId contains $c"
        fcLookup
          .get(formComponentId)
          .fold[Either[UnexpectedState, BooleanExpr]](missingFormComponentId(formComponentId)) {
            case Choice(_, options, _, _, _, _) =>
              validate(c, options.size, formComponentId, exprString, "Choice").map(_ => be)
            case RevealingChoice(options, _) =>
              validate(c, options.size, formComponentId, exprString, "Revealing choice").map(_ => be)
            case otherwise => Right(be)
          }
      case be @ EqualsWithConstant(ctx @ FormCtx(formComponentId), Constant(c), swapped) =>
        val exprString = if (swapped) s"$c = $formComponentId" else s"$formComponentId = $c"

        def invalidUsage(component: String): Either[UnexpectedState, BooleanExpr] =
          Left(
            UnexpectedState(
              s"Multivalue $component cannot be used together with '='. Replace '$exprString' with '$formComponentId contains $c' instead."
            )
          )

        val rewriter = Contains(ctx, Constant(c))
        fcLookup
          .get(formComponentId)
          .fold[Either[UnexpectedState, BooleanExpr]](missingFormComponentId(formComponentId)) {
            case Choice(Radio | YesNo, options, _, _, _, _) =>
              validate(c, options.size, formComponentId, exprString, "Choice").map(_ => rewriter)
            case Choice(Checkbox, _, _, _, _, _) => invalidUsage("choice")
            case RevealingChoice(_, true)        => invalidUsage("revealing choice")
            case RevealingChoice(options, false) =>
              validate(c, options.size, formComponentId, exprString, "Revealing choice").map(_ => rewriter)
            case otherwise => Right(be)
          }
      case be => Right(be)
    }

    type Possible[A] = Either[UnexpectedState, A]

    val rewriteIncludeIfRules: Possible[List[(IncludeIf, IncludeIf)]] =
      includeIfs.traverse { includeIf =>
        rewrite(includeIf.booleanExpr)
          .map(booleanExpr => includeIf -> IncludeIf(booleanExpr)): Possible[(IncludeIf, IncludeIf)]
      }
    val rewriteValidIfRules: Possible[List[(ValidIf, ValidIf)]] =
      validIfs.traverse { validIf =>
        rewrite(validIf.booleanExpr)
          .map(booleanExpr => validIf -> ValidIf(booleanExpr)): Possible[(ValidIf, ValidIf)]
      }

    for {
      includeIfRules <- rewriteIncludeIfRules
      validIfRules   <- rewriteValidIfRules
    } yield {
      val includeIfRulesLookup: Map[IncludeIf, IncludeIf] = includeIfRules.toMap
      val validIfRulesLookup: Map[ValidIf, ValidIf] = validIfRules.toMap

      def replaceIncludeIf(includeIf: Option[IncludeIf]): Option[IncludeIf] =
        includeIf.flatMap(includeIfRulesLookup.get)

      def replaceValidIf(validIf: Option[ValidIf]): Option[ValidIf] =
        validIf.flatMap(validIfRulesLookup.get)

      def replaceValidator(validator: FormComponentValidator): FormComponentValidator =
        validator.copy(validIf = validIfRulesLookup.get(validator.validIf).getOrElse(validator.validIf))

      def replaceFormComponentNested(formComponent: FormComponent): FormComponent = formComponent match {
        case IsGroup(group) =>
          replaceFormComponent(formComponent).copy(
            `type` = group.copy(fields = group.fields.map(replaceFormComponent))
          )
        case IsRevealingChoice(revealingChoice) =>
          replaceFormComponent(formComponent).copy(
            `type` = revealingChoice.copy(options =
              revealingChoice.options.map(rcElement =>
                rcElement.copy(revealingFields = rcElement.revealingFields.map(replaceFormComponent))
              )
            )
          )
        case otherwise => replaceFormComponent(formComponent)
      }

      def replaceFormComponent(formComponent: FormComponent): FormComponent = formComponent.copy(
        validIf = replaceValidIf(formComponent.validIf),
        includeIf = replaceIncludeIf(formComponent.includeIf),
        validators = formComponent.validators.map(replaceValidator)
      )

      def replaceFields(fields: List[FormComponent]): List[FormComponent] = fields.map(replaceFormComponentNested)

      def replaceDeclarationSection(declarationSection: DeclarationSection): DeclarationSection =
        declarationSection.copy(fields = replaceFields(declarationSection.fields))

      def replaceDestinations(destinations: Destinations): Destinations = destinations match {
        case dl: Destinations.DestinationList =>
          dl.copy(declarationSection = replaceDeclarationSection(dl.declarationSection))
        case dp: Destinations.DestinationPrint => dp
      }

      formTemplate.copy(
        sections = formTemplate.sections.map {
          case s: Section.NonRepeatingPage =>
            s.copy(
              page = s.page.copy(includeIf = replaceIncludeIf(s.page.includeIf), fields = replaceFields(s.page.fields))
            )
          case s: Section.RepeatingPage =>
            s.copy(
              page = s.page.copy(includeIf = replaceIncludeIf(s.page.includeIf), fields = replaceFields(s.page.fields))
            )
          case s: Section.AddToList =>
            s.copy(
              includeIf = replaceIncludeIf(s.includeIf),
              pages = s.pages.map(page =>
                page.copy(includeIf = replaceIncludeIf(page.includeIf), fields = replaceFields(page.fields))
              )
            )
        },
        destinations = replaceDestinations(formTemplate.destinations)
      )
    }
  }
}
