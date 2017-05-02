# Event Data Wikipedia Agent

Crossref Event Data Wikipedia Agent. Monitors the Event Stream on all available Wikimedia properties for edits. This Agent interfaces with the Percolator. It does not supply Action IDs because it is a very high-volume, relatively high noise agent and the trade-off for duplicates is in favour of not checking.

## Tests

### Unit tests

    time docker-compose -f docker-compose-unit-tests.yml run -w /usr/src/app test lein test :unit

## Demo

    time docker-compose -f docker-compose-unit-tests.yml run -w /usr/src/app test lein repl

## Config

 - `PERCOLATOR_URL_BASE` e.g. https://percolator.eventdata.crossref.org
 - `JWT_TOKEN`
 - `STATUS_SERVICE_BASE`
 - `ARTIFACT_BASE`, e.g. https://artifact.eventdata.crossref.org
