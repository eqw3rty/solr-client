package nl.elmar.solr.client.search

import play.api.libs.json.{JsArray, JsString}

class SearchRequestSpec extends org.specs2.mutable.Specification {

  "SearchRequest" should {
    "render empty query" in {
      SearchRequest.bodyWriter.writes(SearchRequest()).toString === """{"query":"*:*"}"""
    }

    "render result grouping" in {
      val query =
        SearchRequest(
          start = Some(10),
          rows = Some(20),
          grouping = Some(
            ResultGrouping("field1", sort = Some(Sorting("field2", SortOrder.Desc)))
          )
        )
      SearchRequest.bodyWriter.writes(query).toString === """{"query":"*:*","params":{"start":10,"rows":20,"group":true,"group.field":"field1","group.sort":"field2 desc","group.ngroups":false}}"""
    }

    "render facets" in {
      val facets = FacetDefinition(
        name = "countries",
        metadata = FacetMetadata.Terms(
          field = "country",
          domain = Some(FacetMetadata.Domain("country" :: Nil)),
          subFacets = FacetDefinition(
            name = "uniqueCount",
            metadata = FacetMetadata.Unique("groupField", "unique")
          ) :: Nil
        )
      ) :: Nil
      val query = SearchRequest(facets = facets)
      SearchRequest.bodyWriter.writes(query).toString === """{"query":"*:*","facet":{"countries":{"type":"terms","field":"country","facet":{"uniqueCount":"unique(groupField)"},"domain":{"excludeTags":["country"]}}}}"""
    }

    "render AND expression" in {
      import ValueExpression._

      val exp = AND(Term.Long(1), AND(Term.Long(2), AND(Term.Long(3), Term.Long(4))))

      SearchRequest.renderValueExpression(exp) === "(1 AND 2 AND 3 AND 4)"
    }

    "render AND and OR expression combination" in {
      import ValueExpression._

      val exp =
        OR(
          Term Long 0,
          OR(
            AND(Term Long 1, AND(Term Long 2, AND(Term Long 3, Term Long 4))),
            Term Long 10
          )
        )

      SearchRequest.renderValueExpression(exp) === "(0 OR (1 AND 2 AND 3 AND 4) OR 10)"
    }

    "render filter expression" in {
      val fd1 = FilterExpression.Field("test1", ValueExpression.Term.Long(0), Some("testtag1"))

      SearchRequest.renderFilterExpression(fd1) === "{!tag=testtag1} test1:0"
    }

    "render field expressions combined with OR" in {

      val fd1 = FilterExpression.Field("test1", ValueExpression.Term.Long(0), Some("testtag1"))
      val fd2 = FilterExpression.Field("test2", ValueExpression.Term.String("testexp"), Some("testtag2"))
      val or = FilterExpression.OR(fd1, fd2)

      SearchRequest.renderFilterExpression(or) === "{!tag=testtag1} test1:0 OR {!tag=testtag2} test2:testexp"
    }

    "quote string containing spaces" in {
      val name = FilterExpression.Field("name", ValueExpression.Term.String("John"))
      SearchRequest.renderFilterExpression(name) === "name:John"
      val fullName = FilterExpression.Field("fullName", ValueExpression.Term.String("John Doe"))
      SearchRequest.renderFilterExpression(fullName) === "fullName:\"John Doe\""
    }

    "render filed collapsing in fq parameter" in {
      val request =
        SearchRequest(
          fieldCollapsing = Some(FieldCollapsing("groupId", Sorting("price", SortOrder.Asc))))
      val json = SearchRequest.bodyWriter.writes(request)
      (json \ "params" \ "fq").get === JsString("{!collapse field=groupId sort='price asc'}")
    }

    "render wildcard filter expression" in {
      import ValueExpression._
      val request =
        SearchRequest(
          filter = FilterExpression.Field("source", OR(Wildcard.PrefixMatch("abc"), Term.String("zzz*"))) :: Nil
        )
      val json = SearchRequest.bodyWriter.writes(request)
      (json \ "filter").get === JsArray(JsString("source:(abc* OR \"zzz*\")") :: Nil)
    }
  }

}
