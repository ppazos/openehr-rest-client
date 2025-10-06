package com.cabolabs.openehr.rest.client

class QueryResult {

   def headers // only for prpojections: archetypeId, path, name, rmTypeName

   // the result of the query, can be a list or a map for 'query_result_list' or 'query_result_grouped' respectively.
   // the items will be summaries or the full object if retrieveData was set
   def result

   boolean retrieveData = false

   // the type of the result, can be 'query_result_count', 'query_result_list' or 'query_result_grouped'
   String resultType

   // pagination for query_result_list or query_result_grouped
   int offset
   int max

   // result for query_result_count
   int count
}