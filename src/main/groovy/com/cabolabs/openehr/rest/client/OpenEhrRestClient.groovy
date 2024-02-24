package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.ehr.*
import com.cabolabs.openehr.rm_1_0_2.composition.Composition
import com.cabolabs.openehr.rm_1_0_2.demographic.PartyRelationship

import com.cabolabs.openehr.dto_1_0_2.ehr.EhrDto
import com.cabolabs.openehr.dto_1_0_2.demographic.ActorDto

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.util.logging.*

import com.cabolabs.openehr.formats.OpenEhrJsonParserQuick
import com.cabolabs.openehr.formats.OpenEhrJsonSerializer
import com.cabolabs.openehr.opt.model.OperationalTemplate
import com.cabolabs.openehr.opt.parser.OperationalTemplateParser
import com.cabolabs.openehr.opt.instance_generator.JsonInstanceCanonicalGenerator2
import java.util.Properties
import java.io.InputStream
import java.nio.charset.StandardCharsets
import net.pempek.unicode.UnicodeBOMInputStream
import org.apache.log4j.*

import com.cabolabs.openehr.rest.client.auth.Authentication

@Log4j
class OpenEhrRestClient {

   Authentication auth
   String  baseUrl // = 'http://192.168.1.110:8080/ehrbase/rest/openehr/v1'
   //String  baseAuthUrl
   //String  baseAdminUrl
   //boolean performDbTruncation
   //String  token

   Map lastError = [:] // parsed JSON that contains an error response
   Map headers = [:] // extra headers to use in the POST endpoints like committer

   int lastResponseCode


   //AuthTypeEnum auth
   ContentTypeEnum accept
   PreferEnum      prefer

   // TODO: refactor to share common code

   OpenEhrRestClient (
      String baseUrl,
      Authentication auth,
      ContentTypeEnum accept = ContentTypeEnum.JSON,
      PreferEnum prefer      = PreferEnum.REPRESENTATION
   ) {
      this.baseUrl = baseUrl
      this.auth   = auth
      this.accept = accept
      this.prefer = prefer
   }

   int getLastResponseCode()
   {
      this.lastResponseCode
   }

   // value example: 'name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"'
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setCommitterHeader(String value)
   {
      // TODO: add a header object so we can validate it's required contents,
      // with a string the user can set anything...
      this.headers["openEHR-AUDIT_DETAILS.committer"] = value
   }

   // value example: code_string="532"
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setLifecycleStateHeader(String value)
   {
      this.headers["openEHR-VERSION.lifecycle_state"] = value
   }

   // value example: code_string="251"
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setChangeTypeHeader(String value)
   {
      this.headers["openEHR-AUDIT_DETAILS.change_type"] = value
   }

   // value example: value="An updated composition contribution description"
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setDescriptionHeader(String value)
   {
      this.headers["openEHR-AUDIT_DETAILS.description"] = value
   }

   // auth using the auth service of the same REST API, which returns a JWT access token
   // if the API of the SUT doesn't provide an auth service, an open access API could be
   // tested or a new method to implement OAuth authentication should be added, see the
   // following link on how to run automated tests with OAuth:
   // https://www.baeldung.com/oauth-api-testing-with-spring-mvc
   //
   /*
   boolean auth(String user, String pass)
   {
      if (!user || !pass)
      {
         throw new Exception('Authentication: both user and pass are required')
      }

      def req = new URL(this.baseAuthUrl +"/auth").openConnection()
      def body = '' // '{"message":"this is a message"}' // add to send a status

      req.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      //req.setRequestProperty("Content-Type", "application/json") // add to send a status
      //req.setRequestProperty("Prefer",     this.prefer.toString())
      req.setRequestProperty("Accept",       this.accept.toString())

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      String params = "email=${user}&password=${pass}&format=json"
      byte[] reqData = params.getBytes(StandardCharsets.UTF_8)
      req.setRequestProperty("Content-Length", Integer.toString(reqData.length))


      try
      {
         DataOutputStream wr = new DataOutputStream(req.getOutputStream())
         wr.write(reqData)
      }
      catch (java.net.ConnectException e)
      {
         // TODO: define our errors
         throw new Exception("There was a problem connecting with the server")
      }



      //req.getOutputStream().write(body.getBytes("UTF-8"));
      def status = req.getResponseCode()

      if (status.equals(200))
      {
         def response_body = req.getInputStream().getText()

         //println response_body

         def json_parser = new JsonSlurper()
         def json = json_parser.parseText(response_body)
         this.token = json.token

         //println this.token
         return true
      }
      else
      {
         println req.getInputStream().getText() // FIXME: read error stream
         return false
      }
   }
   */

   /**
    * Creates a default EHR, no payload was provided and returns the full representation of the EHR created.
    * @return EHR created.
    */
   EhrDto createEhr()
   {
      def post = new URL(this.baseUrl +"/ehr").openConnection()

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      post.setRequestProperty("Prefer",        this.prefer.toString())
      post.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(post)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])



      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = post.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = post.getErrorStream().getText()
      }

      def status = post.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)

      return null // no ehr is returned if there is an error
   }

   EhrDto createEhr(String ehr_id)
   {
      def post = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      post.setRequestMethod("PUT")
      post.setDoOutput(true)

      post.setRequestProperty("Prefer",        this.prefer.toString())
      post.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(post)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = post.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = post.getErrorStream().getText()
      }

      def status = post.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)

      return null // no ehr is returned if there is an error
   }

   /**
    * Creates an EHR ith the given EHR_STATUS
    */
   EhrDto createEhr(EhrStatus ehr_status)
   {
      def post = new URL(this.baseUrl +"/ehr").openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(ehr_status)

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      post.setRequestProperty("Content-Type",  "application/json") // add to send a status
      post.setRequestProperty("Prefer",        this.prefer.toString())
      post.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(post)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])



      post.getOutputStream().write(body.getBytes("UTF-8"));


      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = post.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = post.getErrorStream().getText()
      }

      def status = post.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)

      return null // no ehr is returned if there is an error
   }

   /**
    * Creates an EHR ith the given EHR_STATUS and ehr_id
    */
   EhrDto createEhr(EhrStatus ehr_status, String ehr_id)
   {
      def post = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(ehr_status)

      post.setRequestMethod("PUT")
      post.setDoOutput(true)

      post.setRequestProperty("Content-Type",  "application/json") // add to send a status
      post.setRequestProperty("Prefer",        this.prefer.toString())
      post.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(post)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])



      post.getOutputStream().write(body.getBytes("UTF-8"));
      def status = post.getResponseCode()
      this.lastResponseCode = status

      String response_body
      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = post.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = post.getErrorStream().getText()
      }

      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         //println response_body
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)

      return null // no ehr is returned if there is an error
   }

   EhrDto getEhr(String ehr_id)
   {
      def get = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      get.setRequestMethod("GET")
      get.setDoOutput(true)
      get.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(get)

      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = get.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = get.getErrorStream().getText()
      }

      def status = get.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(200))
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)

      return null // no ehr is returned if there is an error
   }

   // COMPOSITION

   Composition createComposition(String ehr_id, Composition compo)
   {
      def req = new URL("${this.baseUrl}/ehr/${ehr_id}/composition").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])



      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(compo)

      req.getOutputStream().write(body.getBytes("UTF-8"));


      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }


      def status = req.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         def compo_out = parser.parseJson(response_body)
         return compo_out
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)


      return null // no compo is returned if there is an error
   }

   Composition getComposition(String ehr_id, String version_uid)
   {
      def req = new URL("${this.baseUrl}/ehr/${ehr_id}/composition/${version_uid}").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }


      def status = req.getResponseCode()
      this.lastResponseCode = status

      if (status.equals(200))
      {
         def parser = new OpenEhrJsonParserQuick()
         def compo_out = parser.parseJson(response_body)
         return compo_out
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)


      return null // no compo is returned if there is an error
   }

   /**
    * version_uid is the VERSION.uid of the object to be updated, in the format versioned_object_id::system_id::version
    */
   Composition updateComposition(String ehr_id, Composition compo, String version_uid)
   {
      def parts = version_uid.split('::')
      if (parts.size() < 3)
      {
         throw new Exception("Wrong format for version_uid")
      }
      def versioned_object_id = parts[0]

      def req = new URL("${this.baseUrl}/ehr/${ehr_id}/composition/${versioned_object_id}").openConnection()

      req.setRequestMethod("PUT")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())
      req.setRequestProperty("If-Match",      version_uid)

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // TODO: this is the same code as in POST composition
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(compo)

      req.getOutputStream().write(body.getBytes("UTF-8"));


      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }

      def status = req.getResponseCode()
      this.lastResponseCode = status

      //println status

      // NOTE: the openEHR API responds 200 for updates
      // TODO: need to check for other 2xx codes and report a warning since it's not strictly compliant
      if (status.equals(200))
      {
         def parser = new OpenEhrJsonParserQuick()
         def compo_out = parser.parseJson(response_body)
         return compo_out
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)


      return null // no compo is returned if there is an error
   }

   // TEMPLATE

   // TODO: we need a way to serialize OperationalTemplate to XML in openEHR-OPT
   def uploadTemplate(String opt)
   {
      def req = new URL("${this.baseUrl}/definition/template/adl1.4").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: the upload template request will always be XML until we have a JSON schema for OPT
      req.setRequestProperty("Content-Type",  "application/xml")
      req.setRequestProperty("Accept",        "application/xml") //this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // TODO: Need some AUDIT for uploading OPTs..

      // NOTE: getOutputStream() actually sends the request
      // https://github.com/frohoff/jdk8u-dev-jdk/blob/master/src/share/classes/sun/net/www/protocol/http/HttpURLConnection.java#L1281-L1292
      req.getOutputStream().write(opt.getBytes("UTF-8"));


      def status = req.getResponseCode()
      this.lastResponseCode = status

      // TODO: make configurable if it accepts a 409 Conflict as successful for this service
      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201)) // Created
      {
         return req.getInputStream()?.getText()
      }
      else if (status.equals(204)) // No Content
      {
         return opt // returns the same opt as the input
      }
      else if (status.equals(409)) // Conflict
      {
         // NOTE: this is more a warning than an error
         this.lastError = [error: "Conflict: template already exists"]
      }
      else
      {
         // NOTE: we could parse the error but different CDRs might return whatever they want,
         //       though we could check try parsing JSON then XML then treat it as string.
         this.lastError = [error: req.getErrorStream()?.getText()]
      }

      return null // no object is returned if there is an error
   }


   ActorDto createActor(ActorDto actor)
   {
      def req = new URL("${this.baseUrl}/demographic/actor").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])



      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()

      // FIXME: serialize DTO!
      def body = serializer.serialize(actor)

      req.getOutputStream().write(body.getBytes("UTF-8"));


      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }


      def status = req.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def out = parser.parseActorDto(response_body) // NOTE: parseJson doesn't work with the ActorDto
         return out
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)


      return null // no compo is returned if there is an error
   }

   PartyRelationship createRelationship(PartyRelationship relationship)
   {
      def req = new URL("${this.baseUrl}/demographic/relationship").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(relationship)

      req.getOutputStream().write(body.getBytes("UTF-8"));


      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }


      def status = req.getResponseCode()
      this.lastResponseCode = status

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def out = parser.parseJson(response_body)
         return out
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)


      return null // no compo is returned if there is an error
   }


   /**
   updateEhrStatus
   getComposition at time
   createActor
   updateActor
   getActor
   createRelationship
   updateRelationship
   getRelationship
   createDirectory
   updateDirectory
   getDirectory
   createContribution
   getContribution
   getVersionedComposition
   ...
   executeStoredQuery
   */


   // QUERY (tests)

   def storeQuery(String qualified_name, String version, String type, String query)
   {

   }

   List listQueries()
   {
      def req = new URL("${this.baseUrl}/definition/query").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)

      // NOTE: JSON only for now
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      String response_body

      try
      {
         // this throws an exception if the response status code is not 2xx
         response_body = req.getInputStream().getText()
      }
      catch (Exception e)
      {
         // for 4xx errors, the server will return a JSON payload error
         response_body = req.getErrorStream().getText()
      }


      def status = req.getResponseCode()
      this.lastResponseCode = status

      if (status.equals(200))
      {
         // def parser = new OpenEhrJsonParserQuick()
         // def compo_out = parser.parseJson(response_body)
         // return compo_out

         // TODO: we don't have a standard class for the query structure yet, so we return the JSON object directly
         def json_parser = new JsonSlurper()
         return json_parser.parseText(response_body)
      }


      // Expects a JSON error
      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)


      return null // no compo is returned if there is an error
   }

   static String removeBOM(byte[] bytes)
   {
      def inputStream = new ByteArrayInputStream(bytes)
      def bomInputStream = new UnicodeBOMInputStream(inputStream)
      bomInputStream.skipBOM() // NOP if no BOM is detected
      def br = new BufferedReader(new InputStreamReader(bomInputStream))
      return br.text // http://docs.groovy-lang.org/latest/html/groovy-jdk/java/io/BufferedReader.html#getText()
   }

   /* FIXME: trucation should be an extension of the REST Client
   def truncateServer()
   {
      if (performDbTruncation) {
         def req = new URL(this.baseAdminUrl + "/truncate_all").openConnection()
         req.setRequestProperty("Authorization", "Bearer " + this.token)
         int code = req.getResponseCode()
         //req.getInputStream().getText()
      }
   }
   */
}
