package tracks.indexer.processors

import tracks.core.models.TransportationMode
import tracks.core.models.containingSegment
import tracks.core.models.dateTime
import tracks.indexer.models.*
import tracks.indexer.utils.NameLookup

object NameResolver {
    fun process(workspace: GpxWorkspace) {
        // Lookup waypoints and segments when they're associated with walking (not vehicular movement)
        val movementData = mutableMapOf<String, MovementItem>()
        workspace.processedGps?.let { gps ->
            MovementCategorizer.process(gps).forEach {
                movementData[it.startTime.toString()] = it
            }
        }

        // In order to get the prominent countries, states, cities & sites, assume walking/stopping is the
        // most important activity (rather than using a vehicle)

        val acceptedNearbyIndices = mutableSetOf<Int>()
        val secondsPerLocation = mutableListOf<Int>()
        val request = mutableListOf<NameRequest>()

        // Short areas that are slow moving are likely near an interesting location - grab names from there
        workspace.lowQualityRuns
            .filter { it.type == LowQualityType.LOW_MOVEMENT || it.type == LowQualityType.NO_MOVEMENT }
            .filter { it.duration() < AnalyzerSettings.minSecondsLowMovementForStop }
            .forEach { lqr ->
                val segment = workspace.currentSegments.containingSegment(lqr.start.time!!, lqr.end.time!!)
                if (segment == null || segment.transportationTypes.first().mode == TransportationMode.foot) {
                    request.add(NameRequest(lqr.start.lat!!, lqr.start.lon!!))
                    acceptedNearbyIndices.add(request.size)
                    secondsPerLocation.add(lqr.duration())
                }
            }

        workspace.processedGps?.waypoints?.forEach { wpt ->
            wpt.rangicStart?.let { startPoint ->
                if (movementData[startPoint.dateTime().toString()]?.type == MovementCategory.STOPPED_WALKING) {
                    request.add(NameRequest(wpt.lat!!, wpt.lon!!))
                    secondsPerLocation.add(wpt.rangicDurationSeconds?.toInt() ?: 0)
                }
            }
        }

        workspace.processedGps?.tracks?.forEach { track ->
            track.segments.forEach { segment ->
                val first = segment.points.first()
                if (movementData[first.dateTime().toString()]?.type == MovementCategory.WALKING) {
                    secondsPerLocation.add(segment.seconds.toInt())
                    request.add(NameRequest(first.lat!!, first.lon!!))
                    request.add(NameRequest(segment.points.last().lat!!, segment.points.last().lon!!))
                    // The seconds are given to the starting point, this is a placeholder
                    // Make sure the secondsPerLocation & names collections are the same size.
                    secondsPerLocation.add(0)
                    var meters = 0.0
                    var seconds = 0.0
                    segment.points.forEach { pt ->
                        meters += pt.calculatedMeters
                        seconds += pt.calculatedSeconds
                        if (meters >= AnalyzerSettings.maxMetersBetweenPlacenames &&
                            seconds >= AnalyzerSettings.maxSecondsBetweenPlacenames) {
                            request.add(NameRequest(pt.lat!!, pt.lon!!))
                            secondsPerLocation.add(0)
                            meters = 0.0
                            seconds = 0.0
                        }
                    }
                }
            }
        }

        val names = NameLookup.resolveOsmPoi(request) ?: emptyList()
        if (names.size != secondsPerLocation.size) {
            println("Why aren't the sizes the same? ${names.size} & ${secondsPerLocation.size}")
        }

        val countries = mutableMapOf<String, Int>().withDefault { 0 }
        val states = mutableMapOf<String, Int>().withDefault { 0 }
        val cities = mutableMapOf<String, Int>().withDefault { 0 }
        val sites = mutableMapOf<String, Int>().withDefault { 0 }
        val siteLists = mutableMapOf<String, List<OsmPoi>>()
        names.forEachIndexed { index, location ->
            val seconds = secondsPerLocation[index]
            location.countryName?.let { countries[it] = countries.getValue(it) + seconds }
            location.stateName?.let { states[it] = states.getValue(it) + seconds }
            location.cityName?.let { cities[it] = cities.getValue(it) + seconds }

            // For each entry, prefer the 'inside' - those POIs a location is enclosed in.
            // Fall back to those that are nearby otherwise
            val poiList = location.inside.ifEmpty { location.nearby }
            if (poiList.isNotEmpty()) {
                val siteIds = poiList.joinToString(",") { it.id }
                sites[siteIds] = sites.getValue(siteIds) + seconds
                siteLists[siteIds] = poiList
            }

            // Some locations, notably slow moving runs, always should include nearby
            if (acceptedNearbyIndices.contains(index) && location.inside.isNotEmpty() && location.nearby.isNotEmpty()) {
                val siteIds = location.nearby.joinToString(",") { it.id }
                siteLists[siteIds] = location.nearby
                sites[siteIds] = 0
            }
        }

        workspace.countries = countries.keys.sortedByDescending { countries[it] }
        workspace.states = states.keys.sortedByDescending { states[it] }
        workspace.cities = cities.keys.sortedByDescending { cities[it] }
        workspace.sites = sites.keys
            .sortedByDescending { key -> sites[key] }
            .map { key ->
                val poiList = siteLists.getValue(key)
                val first = poiList.first()
                SiteResponse(
                    poiList.map { it.name },
                    first.point.lat,
                    first.point.lon,
                    first.tags
                )
            }
println("sites: ${workspace.sites.map { it.name.joinToString(",") } }")
    }
}
