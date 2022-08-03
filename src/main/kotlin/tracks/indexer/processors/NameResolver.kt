package tracks.indexer.processors

import tracks.core.models.*
import tracks.indexer.models.*
import tracks.indexer.utils.NameLookup

// Key=<Name>:<Type>
// Country              State ("" - none)           City                        Site
// USA: key (String) => Map<String,List<String>> => Map<String,List<String>> => Map<String,List<SiteId>>
// Country                  City                        Site
// Mexico: key (String) => Map<String,List<String>> => Map<String,List<SiteId>>

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
                    secondsPerLocation.add(lqr.duration())
                    acceptedNearbyIndices.add(request.size - 1)
//println("near: ${lqr.start.timeOnly()} ${lqr.start.lat},${lqr.start.lon}")
                }
            }

        workspace.processedGps?.waypoints?.forEach { wpt ->
            wpt.rangicStart?.let { startPoint ->
                if (movementData[startPoint.dateTime().toString()]?.type == MovementCategory.STOPPED_WALKING) {
                    request.add(NameRequest(wpt.lat!!, wpt.lon!!))
                    secondsPerLocation.add(wpt.rangicDurationSeconds?.toInt() ?: 0)
                    acceptedNearbyIndices.add(request.size - 1)
//println("waypoint ${request.size - 1} ${startPoint.timeOnly()} ${startPoint.lat},${startPoint.lon}")
//                } else {
//println("waypoint type @${startPoint.timeOnly()} ${movementData[startPoint.dateTime().toString()]?.type}")
                }
            }
        }
//println("nearby (to resolve): ${acceptedNearbyIndices.map { "${request[it].lat},${request[it].lon}" } } ")

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

        val nearbyRequest = mutableListOf<NameRequest>()
        names.forEachIndexed { index, location ->
            if (acceptedNearbyIndices.contains(index)) {
                location.nearby.forEach {
                    nearbyRequest.add(NameRequest(it.point.lat, it.point.lon))
                }
            }
        }

        // To get full info on nearby sites, they need to be resolved. This provides the 'inside' info, as well as
        // the country, state & city. The 'inside' info is used to set the hierarchical info
        // The 'nearby' info is ignored, as it provides no additional, useful info
        val nearbyNames = NameLookup.resolveOsmPoi(nearbyRequest) ?: emptyList()
        val nearbyResolved = mutableMapOf<String,OsmPoiLocationResponse>()
        nearbyNames.forEach { ln ->
            ln.inside.forEach { poi ->
                nearbyResolved[poi.id] = ln
            }
        }

        val countrySeconds = mutableMapOf<String, Int>().withDefault { 0 }
        val stateSeconds = mutableMapOf<String, Int>().withDefault { 0 }
        val citySeconds = mutableMapOf<String, Int>().withDefault { 0 }
        val siteSeconds = mutableMapOf<String, Int>().withDefault { 0 }
        val siteLists = mutableMapOf<String, List<OsmPoi>>()

        // The LocationNames contains the country,state,city names with an empty sites list. The Set contains the
        // id of the sites
        val countryToLocation = mutableMapOf<String, Pair<LocationNames,MutableSet<String>>>()
        val siteToChildren = mutableMapOf<String, Pair<LocationNamesSite,MutableSet<String>>>()

// TODO: Sort the hierarchical lists by seconds (consider accumulating seconds at the innermost site, as it allows
//      differentiating time spent at the innermost site versus the containing sites)

        val assignedSites = mutableSetOf<String>()
        names.forEachIndexed { index, location ->
            val seconds = secondsPerLocation[index]
            location.countryName?.let { countrySeconds[it] = countrySeconds.getValue(it) + seconds }
            location.stateName?.let { stateSeconds[it] = stateSeconds.getValue(it) + seconds }
            location.cityName?.let { citySeconds[it] = citySeconds.getValue(it) + seconds }

            // At one point, nearby was used as a backup for an empty inside list. With the 'acceptedNearbyIndices',
            // which improves (& shrinks) the nearby list, this backup is no longer deemed useful.
            val poiList = location.inside
            if (poiList.isNotEmpty()) {
                val countryKey = mutableListOf(location.countryName, location.stateName, location.cityName)
                    .filterNotNull()
                    .joinToString(",")

                if (!countryToLocation.containsKey(countryKey)) {
                    countryToLocation[countryKey] = Pair(
                        LocationNames(
                            location.countryName,
                            location.countryCode,
                            location.stateName,
                            location.cityName,
                            mutableListOf()
                        ),
                        mutableSetOf()
                    )
                }

                val outermostSite = poiList.last().id
                if (!assignedSites.contains(outermostSite)) {
                    poiList.forEach { assignedSites.add(it.id) }
                    countryToLocation[countryKey]?.second?.add(outermostSite)
                }
                addHierarchicalSites(poiToSiteList(poiList), siteToChildren, assignedSites)

                val siteIds = poiList.joinToString(",") { it.id }
                siteSeconds[siteIds] = siteSeconds.getValue(siteIds) + seconds
                siteLists[siteIds] = poiList
                if (poiList.size > 1) {
                    siteSeconds[poiList.first().id] = siteSeconds.getValue(poiList.first().id) + seconds
                }
            }

            // Some locations, notably slow moving runs, always should include nearby
            if (acceptedNearbyIndices.contains(index) && location.nearby.isNotEmpty()) {
                // Lookup each nearby POI in the set of resolved nearby names - this provides
                // the country, state, city & any containing sites.
                location.nearby.forEach { poi ->
                    nearbyResolved[poi.id]?.let { ln ->
                        val siteIds = ln.inside.joinToString(",") { it.id }
                        if (!siteLists.containsKey(siteIds) && ln.inside.isNotEmpty()) {
                            val countryKey = mutableListOf(location.countryName, location.stateName, location.cityName)
                                .filterNotNull()
                                .joinToString(",")
                            if (!countryToLocation.containsKey(countryKey)) {
                                countryToLocation[countryKey] = Pair(
                                    LocationNames(
                                        location.countryName,
                                        location.countryCode,
                                        location.stateName,
                                        location.cityName,
                                        mutableListOf()
                                    ),
                                    mutableSetOf()
                                )
                            }

                            val outermostSite = ln.inside.last().id
                            if (!assignedSites.contains(outermostSite)) {
                                ln.inside.forEach { assignedSites.add(it.id) }
                                countryToLocation[countryKey]?.second?.add(outermostSite)
                            }
                            addHierarchicalSites(poiToSiteList(ln.inside), siteToChildren, assignedSites)

                            siteLists[siteIds] = ln.inside
                            siteSeconds[siteIds] = 0
                        }
                    }
                }
            }
        }

        workspace.hierarchicalNames = countryToLocation
            .map { kv ->
                val locationName = kv.value.first
                locationName.sites.clear()
                locationName.sites.addAll(fillSiteList(kv.value.second.toList(), siteToChildren, siteSeconds))
                locationName
            }
        workspace.countries = countrySeconds.keys.sortedByDescending { countrySeconds[it] }
        workspace.states = stateSeconds.keys.sortedByDescending { stateSeconds[it] }
        workspace.cities = citySeconds.keys.sortedByDescending { citySeconds[it] }
        workspace.sites = siteSeconds.keys
            .sortedByDescending { key -> siteSeconds[key] }
            .filter { key ->
                siteLists.containsKey(key) && siteLists.getValue(key).isNotEmpty()
            }
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
    }

    private fun fillSiteList(siteIdList: List<String>, siteToChildren: MutableMap<String, Pair<LocationNamesSite, MutableSet<String>>>,
                             siteSeconds: Map<String,Int>): List<LocationNamesSite>
    {
        val filledList = mutableListOf<LocationNamesSite>()
        siteIdList
            .sortedByDescending { siteSeconds[it] ?: 0 }
            .forEach { siteId ->
            siteToChildren[siteId]?.let { pair ->
                pair.first.children.clear()
                pair.first.children.addAll(fillSiteList(pair.second.toList(), siteToChildren, siteSeconds))
                filledList.add(pair.first)
            } ?: run {
                println("ERROR: Unable to find site in siteToChildren collection: $siteId")
            }
        }
        return filledList.sortedByDescending { siteSeconds[it.id] ?: 0 }
    }

    private fun addHierarchicalSites(
            siteList: List<LocationNamesSite>,
            siteToChildren: MutableMap<String, Pair<LocationNamesSite, MutableSet<String>>>,
            assignedSites: MutableSet<String>) {
        if (siteList.isEmpty()) {
            return
        }

        val site = siteList.first()
//        if (!assignedSites.contains(site.id)) {
//            return
//        }
        assignedSites.add(site.id)

        if (!siteToChildren.containsKey(site.id)) {
            siteToChildren[site.id] = Pair(site, mutableSetOf())
        }

        if (siteList.size > 1) {
//if (site.id == "way/370672707") {
//    println("Adding children ${siteList.slice(IntRange(1, siteList.size - 1))}")
//}
            siteToChildren[site.id]?.second?.add(siteList[1].id)
            addHierarchicalSites(siteList.slice(IntRange(1, siteList.size - 1)), siteToChildren, assignedSites)
        }
    }

    private fun poiToSiteList(poiList: List<OsmPoi>): List<LocationNamesSite> {
        val siteList = mutableListOf<LocationNamesSite>()
        poiList.reversed().forEach { poi ->
            siteList.add(LocationNamesSite(poi.id, poi.name, poi.point.lat, poi.point.lon, mutableListOf()))
        }
        return siteList
    }
//
//    private fun poiToSiteNames(poiList: List<OsmPoi>): LocationNamesSite? {
//        var topLevel: LocationNamesSite? = null
//        var current: LocationNamesSite? = null
//        poiList.reversed().forEach { poi ->
//            val site = LocationNamesSite(poi.id, poi.name, poi.point.lat, poi.point.lon, mutableListOf())
//            if (topLevel == null) {
//                topLevel = site
//            } else {
//                current?.children?.add(site)
//            }
//            current = site
//        }
//        return topLevel
//    }
}
