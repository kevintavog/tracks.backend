package tracks.core.elasticsearch

object IndexInit {
    val tracksMappings =
        """
          {
            "properties": {
              "id": {
                "type": "keyword"
              },
              "startTime": {
                "type": "date"
              },
              "endTime": {
                "type": "date"
              },
              "startTimeLocal": {
                "type": "date"
              },
              "endTimeLocal": {
                "type": "date"
              },
              "year": {
                "type": "text"
              },
              "month": {
                "type": "text"
              },
              "dayOfMonth": {
                "type": "text"
              },
              "dayOfWeek": {
                "type": "text"
              },
              "sites": {
                "properties": {
                  "lat": {
                    "type": "float"
                  },
                  "lon": {
                    "type": "float"
                  },
                  "name": {
                    "type": "text"
                  },
				  "tags": {
					"properties": {
                      "key": {
                        "type": "text"
                      },
                      "value": {
                        "type": "text"
                      }
                    }
                  }
                }
              },
              "bounds": {
                "properties": {
                  "min": {
                    "type": "geo_point"
                  },
                  "max": {
                    "type": "geo_point"
                  }
                }
              }
            }
          }
        """.trimIndent()

}
