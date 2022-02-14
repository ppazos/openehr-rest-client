package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.ehr.*

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
import com.cabolabs.openehr.rm_1_0_2.ehr.EhrStatus
import java.nio.charset.StandardCharsets
import com.cabolabs.openehr.formats.OpenEhrJsonSerializer


@Log4j
class OpenEhrRestClient {

   String baseUrl // = 'http://192.168.1.110:8080/ehrbase/rest/openehr/v1'
   String baseAuthUrl
   String token

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
   Ehr createEhr()
   {
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }
      /*
      try
      {
         */
         def post = new URL(this.baseUrl +"/ehr").openConnection()
         def body = '' // '{"message":"this is a message"}' // add to send a status

         post.setRequestMethod("POST")
         post.setDoOutput(true)

         //post.setRequestProperty("Content-Type", "application/json") // add to send a status
         post.setRequestProperty("Prefer", "return=representation")
         post.setRequestProperty("Accept", "application/json")
         post.setRequestProperty("Authorization", "Bearer "+ this.token)

         //post.getOutputStream().write(body.getBytes("UTF-8"));
         def status = post.getResponseCode()

         if (status.equals(201))
         {
            def response_body = post.getInputStream().getText()
            //def json_parser = new JsonSlurper()
            //def ehr = json_parser.parseText(response_body)
            def parser = new OpenEhrJsonParser()
            def ehr = parser.parseEhr(response_body)
            return ehr

            /*
            // register the ehr_id for later commits
            ehr_ids << ehr.ehr_id.value

            return true
            */
         }
         else
         {
            //println post.getInputStream().getText()
         }
      /*
      }
      catch (Exception e) // connection issues or parsing the ehr, etc
      {
         println e.message
         return false
      }
      */
   }

   /**
    * Creates an EHR ith the given EHR_STATUS
    */
   Ehr createEhr(EhrStatus ehr_status)
   {
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }

      def post = new URL(this.baseUrl +"/ehr").openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serializeEhrStatus(ehr_status)

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      post.setRequestProperty("Content-Type", "application/json") // add to send a status
      post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")
      post.setRequestProperty("Authorization", "Bearer "+ this.token)

      post.getOutputStream().write(body.getBytes("UTF-8"));
      def status = post.getResponseCode()

      if (status.equals(201))
      {
         def response_body = post.getInputStream().getText()
         //def json_parser = new JsonSlurper()
         //def ehr = json_parser.parseText(response_body)
         def parser = new OpenEhrJsonParser()
         //println response_body
         def ehr = parser.parseEhr(response_body)
         return ehr
      }
      else
      {
         //println post.getInputStream().getText()
      }
   }

   /**
    * Creates an EHR ith the given EHR_STATUS and ehr_id
    */
   Ehr createEhr(EhrStatus ehr_status, String ehr_id)
   {
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }

      def post = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serializeEhrStatus(ehr_status)

      post.setRequestMethod("PUT")
      post.setDoOutput(true)

      post.setRequestProperty("Content-Type", "application/json") // add to send a status
      post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")
      post.setRequestProperty("Authorization", "Bearer "+ this.token)

      post.getOutputStream().write(body.getBytes("UTF-8"));
      def status = post.getResponseCode()

      if (status.equals(201))
      {
         def response_body = post.getInputStream().getText()
         //def json_parser = new JsonSlurper()
         //def ehr = json_parser.parseText(response_body)
         def parser = new OpenEhrJsonParser()
         //println response_body
         def ehr = parser.parseEhr(response_body)
         return ehr
      }
      else
      {
         //println post.getInputStream().getText()
      }
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
