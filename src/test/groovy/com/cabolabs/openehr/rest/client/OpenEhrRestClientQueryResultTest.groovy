package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rest.client.auth.NoAuth
import com.sun.net.httpserver.HttpServer
import spock.lang.Shared
import spock.lang.Specification

/**
 * Query execution against a local stub HTTP server (no Atomik needed): the response parsing of
 * executeQuery() depends on the request parameters (retrieveData), which regressed in 0.4.7 when the
 * parsing moved to a shared method that could no longer see them
 * (MissingPropertyException: No such property: parameters).
 */
class OpenEhrRestClientQueryResultTest extends Specification {

   @Shared HttpServer server
   @Shared String body = '{"_type": "query_result_count", "count": 3}'
   @Shared int status = 200

   def setupSpec()
   {
      server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
      server.createContext('/api/v1/query/', { exchange ->
         byte[] bytes = body.getBytes('UTF-8')
         exchange.responseHeaders.add('Content-Type', 'application/json')
         exchange.sendResponseHeaders(status, bytes.length)
         exchange.responseBody.withStream { it.write(bytes) }
      })
      server.start()
   }

   def cleanupSpec()
   {
      server.stop(0)
   }

   private OpenEhrRestClient client()
   {
      new OpenEhrRestClient("http://127.0.0.1:${server.address.port}/api/v1", new NoAuth())
   }

   def "count query result"()
   {
      given:
      body = '{"_type": "query_result_count", "count": 3}'

      when:
      QueryResult result = client().executeQuery('q-1', [:])

      then:
      result.resultType == 'query_result_count'
      result.count == 3
   }

   def "list query result without retrieveData returns summary items"()
   {
      given:
      body = '''{"_type": "query_result_list",
                 "result": [
                   {"uid": "u1::SYS::1", "type": "COMPOSITION", "startTime": "2026-10-06T10:00:00.000Z",
                    "timeCommitted": "2026-10-06T10:00:00.000Z", "timeCreated": "2026-10-06T10:00:00.000Z"},
                   {"uid": "u2::SYS::1", "type": "COMPOSITION", "startTime": "2026-10-06T11:00:00.000Z",
                    "timeCommitted": "2026-10-06T11:00:00.000Z", "timeCreated": "2026-10-06T11:00:00.000Z"}
                 ],
                 "pagination": {"offset": 0, "max": 10}}'''

      when:
      QueryResult result = client().executeQuery('q-1', ['max': '10'])

      then:
      result.resultType == 'query_result_list'
      !result.retrieveData
      result.result.size() == 2
      result.result[0] instanceof QueryResultItemSummary
      result.result[0].uid == 'u1::SYS::1'
      result.max == 10
   }

   def "retrieveData given as the String 'false' is treated as false"()
   {
      given: 'a Map<String, String> sends retrieveData as text, and the text "false" must not read as true'
      body = '{"_type": "query_result_list", "result": [{"uid": "u1::SYS::1", "type": "PERSON", "timeCommitted": "2026-10-06T10:00:00.000Z", "timeCreated": "2026-10-06T10:00:00.000Z"}]}'

      when:
      QueryResult result = client().executeQuery('q-1', ['retrieveData': 'false'])

      then:
      !result.retrieveData
      result.result[0] instanceof QueryResultItemSummary
   }

   def "empty list result with retrieveData true (a search that finds nothing)"()
   {
      given:
      body = '{"_type": "query_result_list", "result": [], "pagination": {"offset": 0, "max": 10}}'

      when:
      QueryResult result = client().executeQuery('q-1', ['retrieveData': 'true', 'resolveRefs': 'true', 'max': '10', 'offset': '0'])

      then:
      result != null
      result.resultType == 'query_result_list'
      result.retrieveData
      result.result == []
   }

   def "grouped result with retrieveData false"()
   {
      given:
      body = '{"_type": "query_result_grouped", "result": {"ehr-1": [{"uid": "u1::SYS::1", "type": "COMPOSITION", "startTime": "2026-10-06T10:00:00.000Z", "timeCommitted": "2026-10-06T10:00:00.000Z", "timeCreated": "2026-10-06T10:00:00.000Z"}]}}'

      when:
      QueryResult result = client().executeQuery('q-1', [:])

      then:
      result.resultType == 'query_result_grouped'
      result.result['ehr-1'].size() == 1
   }
}
