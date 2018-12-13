package org.broadinstitute.gdr.encode.explorer.web

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.ExplorerApp
import org.broadinstitute.gdr.encode.explorer.export.{ExportController, ExportRequest}
import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.implicits._

/** Web API for the Explorer application. */
class ExplorerApi(app: ExplorerApp) {

  private implicit val filterQueryDecoder: QueryParamDecoder[FieldFilter.Filters] =
    queryParam =>
      FieldFilter
        .parseFilters(queryParam.value.split('|').toList, app.fields)
        .leftMap(_.map(err => ParseFailure(err, err)))

  private val cohortPattern = "[a-zA-Z0-9\\-_]+".r

  private implicit val cohortQueryDecoder: QueryParamDecoder[String] =
    queryParam =>
      if (cohortPattern.pattern.matcher(queryParam.value).matches()) {
        queryParam.value.validNel
      } else {
        val err =
          "Cohort name can only contain alphanumeric characters, underscores, and hyphens"
        ParseFailure(err, err).invalidNel
      }

  // Ugly as objects, but http4s has somehow designed their syntax
  // to make it not work as a val.
  private object FilterQueryDecoder
      extends OptionalValidatingQueryParamDecoderMatcher[FieldFilter.Filters]("filter")
  private object CohortQueryDecoder
      extends OptionalValidatingQueryParamDecoderMatcher[String]("cohortName")

  def routes: Http[IO, IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "api" / "dataset" =>
          Ok(app.datasetController.datasetInfo.asJson)

        case GET -> Root / "api" / "facets" =>
          Ok(app.facetsController.getFacets.map(_.asJson))

        case GET -> Root / "api" / "count" :? FilterQueryDecoder(maybeFilters) =>
          maybeFilters match {
            case None                => Ok(app.countController.count(Map.empty).map(_.asJson))
            case Some(Invalid(errs)) => BadRequest(errs.map(_.sanitized).asJson)
            case Some(Valid(filters)) =>
              Ok(app.countController.count(filters).map(_.asJson))
          }

        case GET -> Root / "api" / "export"
              :? FilterQueryDecoder(maybeFilters)
                +& CohortQueryDecoder(maybeCohort) =>
          handleExport(maybeFilters, maybeCohort)
      }
      .orNotFound

  private def handleExport(
    maybeFilters: Option[ValidatedNel[ParseFailure, FieldFilter.Filters]],
    maybeCohort: Option[ValidatedNel[ParseFailure, String]]
  ): IO[Response[IO]] = {
    val validatedFilters = maybeFilters.getOrElse(Map.empty.validNel)
    val validatedCohort =
      maybeCohort.fold(Option.empty[String].validNel[ParseFailure])(_.map(Some(_)))

    val response = (validatedFilters, validatedCohort).tupled match {
      case Invalid(errs) => BadRequest(errs.map(_.sanitized).asJson)
      case Valid((filters, cohort)) =>
        Ok(app.exportController.export(ExportRequest(cohort, filters)))
    }

    response.handleErrorWith {
      case e: ExportController.IllegalExportSize => BadRequest(e.getMessage)
    }
  }
}
