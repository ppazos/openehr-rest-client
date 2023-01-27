package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.ehr.*
import com.cabolabs.openehr.dto_1_0_2.ehr.EhrDto

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.cabolabs.openehr.formats.OpenEhrJsonParser
import com.cabolabs.openehr.opt.parser.OperationalTemplateParser
import com.cabolabs.openehr.opt.model.OperationalTemplate
import com.cabolabs.openehr.opt.instance_generator.JsonInstanceCanonicalGenerator2
import java.util.Properties
import java.io.InputStream
import net.pempek.unicode.UnicodeBOMInputStream
import org.apache.log4j.*
import groovy.util.logging.*
import java.nio.charset.StandardCharsets
import com.cabolabs.openehr.formats.OpenEhrJsonSerializer


@Log4j
class OpenEhrRestClient {

   String baseUrl // = 'http://192.168.1.110:8080/ehrbase/rest/openehr/v1'
   String baseAuthUrl
   String token
   Map lastError = [:] // parsed JSON that contains an error response

   OpenEhrRestClient (String baseUrl, String baseAuthUrl)
   {
      this.baseUrl = baseUrl
      this.baseAuthUrl = baseAuthUrl
   }

   // auth using the auth service of the same REST API, which returns a JWT access token
   // if the API of the SUT doesn't provide an auth service, an open access API could be
   // tested or a new method to implement OAuth authentication should be added, see the
   // following link on how to run automated tests with OAuth:
   // https://www.baeldung.com/oauth-api-testing-with-spring-mvc
   //
   String auth(String user, String pass)
   {
      def post = new URL(this.baseAuthUrl +"/auth").openConnection()
      def body = '' // '{"message":"this is a message"}' // add to send a status

      post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      //post.setRequestProperty("Content-Type", "application/json") // add to send a status
      //post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      String params = "email=${user}&password=${pass}&format=json"
      byte[] postData = params.getBytes(StandardCharsets.UTF_8)
      post.setRequestProperty("Content-Length", Integer.toString(postData.length))


      try
      {
         DataOutputStream wr = new DataOutputStream(post.getOutputStream())
         wr.write(postData)
      }
      catch (java.net.ConnectException e)
      {
         // TODO: define our errors
         throw new Exception("There was a problem connecting with the server")
      }



      //post.getOutputStream().write(body.getBytes("UTF-8"));
      def status = post.getResponseCode()

      if (status.equals(200))
      {
         def response_body = post.getInputStream().getText()

         //println response_body

         def json_parser = new JsonSlurper()
         def json = json_parser.parseText(response_body)
         this.token = json.token

         //println this.token
      }
      else
      {
         println post.getInputStream().getText()
      }
   }

   /**
    * Creates a default EHR, no payload was provided and returns the full representation of the EHR created.
    * @return EHR created.
    */
   EhrDto createEhr()
   {
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }

      def post = new URL(this.baseUrl +"/ehr").openConnection()
      def body = '' // '{"message":"this is a message"}' // add to send a status

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      //post.setRequestProperty("Content-Type", "application/json") // add to send a status
      post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")
      post.setRequestProperty("Authorization", "Bearer "+ this.token)

      // required commiter header
      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", 'name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')


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

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParser()
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
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }

      def post = new URL(this.baseUrl +"/ehr").openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(ehr_status)

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      post.setRequestProperty("Content-Type", "application/json") // add to send a status
      post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")
      post.setRequestProperty("Authorization", "Bearer "+ this.token)

      // required commiter header
      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", 'name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')


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

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParser()
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
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }

      def post = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(ehr_status)

      post.setRequestMethod("PUT")
      post.setDoOutput(true)

      post.setRequestProperty("Content-Type", "application/json") // add to send a status
      post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")
      post.setRequestProperty("Authorization", "Bearer "+ this.token)

      // required commiter header
      post.setRequestProperty("openEHR-AUDIT_DETAILS.committer", 'name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')


      post.getOutputStream().write(body.getBytes("UTF-8"));
      def status = post.getResponseCode()

      String response_body = post.getInputStream().getText()

      if (status.equals(201))
      {
         def parser = new OpenEhrJsonParser()
         //println response_body
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      def json_parser = new JsonSlurper()
      this.lastError = json_parser.parseText(response_body)

      return null // no ehr is returned if there is an error
   }

   static String removeBOM(byte[] bytes)
   {
      def inputStream = new ByteArrayInputStream(bytes)
      def bomInputStream = new UnicodeBOMInputStream(inputStream)
      bomInputStream.skipBOM() // NOP if no BOM is detected
      def br = new BufferedReader(new InputStreamReader(bomInputStream))
      return br.text // http://docs.groovy-lang.org/latest/html/groovy-jdk/java/io/BufferedReader.html#getText()
   }
}