package tracks.app.routes

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import tracks.app.utils.parseCommonSearchOptions
import tracks.core.RangicBadRequestException
import tracks.core.RangicInternalServerException
import tracks.core.elasticsearch.ElasticClient

// When deployed, these routes are children of 'host:port/api/trails'
fun Route.search() {
    get("/search") {
        /**
         - &q= for general search (default)
            - Searches all location name fields, all date fields
        - &cities= (or &countries= or &sites= or &states=) for location name search
        - date filtering (&startDate= and &endDate=)
        - by location (&lat=, &lon= and optional &radius=)
         */
        val options = parseCommonSearchOptions(context.parameters)
        val query = context.parameters["q"] ?: ""
        val startDate = context.parameters["startDate"]
        val endDate = context.parameters["endDate"]
        val countries = context.parameters["countries"]
        val states = context.parameters["states"]
        val cities = context.parameters["cities"]
        val sites = context.parameters["sites"]
        val lat = context.parameters["lat"]
        val lon = context.parameters["lon"]
        val radius = context.parameters["radius"]

        val isDates = startDate != null || endDate != null
        val isName = countries != null || states != null || cities != null || sites != null
        val isLocation = lat != null || lon != null || radius != null

        // Only one type of search is allowed, with the caveat that query text is allowed for all
        // the search types.
        val count = listOf(isDates, isName, isLocation).filter { it }.size
        if (count > 1) {
            throw RangicBadRequestException("More than one search type")
        }
        val results = ElasticClient().use {
            try {
                when {
                    count == 0 -> {
                        it.query(query, options)
                    }
                    isDates -> {
                        it.dates(startDate, endDate, query, options)
                    }
                    isName -> {
                        it.names(toList(countries), toList(states), toList(cities), toList(sites), query, options)
                    }
                    isLocation -> {
                        if (lat == null || lon == null) {
                            throw RangicBadRequestException("Both 'lat' and 'lon' must be specified")
                        }
                        it.nearby(lat.toDouble(), lon.toDouble(), radius?.toDouble() ?: 10.0, query, options)
                    }
                    else -> {
                        throw RangicInternalServerException("Unknown search type")
                    }
                }
            } catch (t: Throwable) {
println("Exception: ${ElasticClient.toReadable(t)}")
                throw t
            }
        }

        call.respond(results)
    }
}

fun toList(str: String?): List<String> {
    str?.let {
        return it.split(",")
    }
    return emptyList()
}
